package com.waddoc.domain.admin.service;

import com.waddoc.domain.admin.dto.AdminDemoMissionActionResponse;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.dispatch.service.RobotWaypointCommandClient;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.config.DispatchAssignmentPolicy;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminDemoMissionService {

    private static final EnumSet<MissionPhase> ARRIVE_ALLOWED_PHASES =
            EnumSet.of(MissionPhase.DISPATCHED, MissionPhase.EN_ROUTE);
    private static final EnumSet<MissionPhase> COMPLETE_ALLOWED_PHASES =
            EnumSet.of(
                    MissionPhase.DISPATCHED,
                    MissionPhase.EN_ROUTE,
                    MissionPhase.ARRIVED,
                    MissionPhase.VERIFYING,
                    MissionPhase.CONSULTING,
                    MissionPhase.RETURNING
            );
    private static final EnumSet<MissionPhase> RESETTABLE_ACTIVE_PHASES =
            EnumSet.of(
                    MissionPhase.DISPATCHED,
                    MissionPhase.EN_ROUTE,
                    MissionPhase.ARRIVED,
                    MissionPhase.VERIFYING,
                    MissionPhase.CONSULTING,
                    MissionPhase.RETURNING
            );

    private final AccessControlService accessControlService;
    private final MissionRepository missionRepository;
    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final RobotWaypointCommandClient robotWaypointCommandClient;
    private final DispatchAssignmentPolicy dispatchAssignmentPolicy;
    private final DemoModePolicy demoModePolicy;
    private final Clock clock;

    public AdminDemoMissionActionResponse dispatchMission(
            AuthenticatedUser authenticatedUser,
            String missionId
    ) {
        Mission mission = getDemoMission(authenticatedUser, missionId);
        if (mission.getPhase() != MissionPhase.CREATED) {
            throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
        }

        MissionPhase previousPhase = mission.getPhase();
        boolean waypointCommandSent = false;
        boolean dummyCompleted = false;

        // 데모 호출로 먼저 생성된 mission은 vehicleId 없이 남을 수 있어 수동 배차 직전에 기본 차량을 보정한다.
        assignDefaultVehicleIfMissing(mission);
        completeOtherActiveMissionsOnSameVehicle(mission);

        if (mission.getTargetWaypointNumber() != null) {
            robotWaypointCommandClient.dispatchMission(
                    mission.getPublicId(),
                    mission.getVehicleId(),
                    mission.getTargetWaypointNumber(),
                    mission.getDestination()
            );
            advanceMissionTo(mission, MissionPhase.EN_ROUTE);
            waypointCommandSent = true;
        } else {
            advanceMissionTo(mission, MissionPhase.COMPLETED);
            dummyCompleted = true;
        }

        completeDispatchOutbox(mission);
        missionRepository.save(mission);
        return AdminDemoMissionActionResponse.of(mission, previousPhase, waypointCommandSent, dummyCompleted);
    }

    public AdminDemoMissionActionResponse arriveMission(
            AuthenticatedUser authenticatedUser,
            String missionId
    ) {
        Mission mission = getDemoMission(authenticatedUser, missionId);
        if (!ARRIVE_ALLOWED_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
        }

        MissionPhase previousPhase = mission.getPhase();
        advanceMissionTo(mission, MissionPhase.ARRIVED);
        missionRepository.save(mission);
        return AdminDemoMissionActionResponse.of(mission, previousPhase, false, false);
    }

    public AdminDemoMissionActionResponse completeMission(
            AuthenticatedUser authenticatedUser,
            String missionId
    ) {
        Mission mission = getDemoMission(authenticatedUser, missionId);
        if (!COMPLETE_ALLOWED_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
        }

        MissionPhase previousPhase = mission.getPhase();
        advanceMissionTo(mission, MissionPhase.COMPLETED);
        missionRepository.save(mission);
        return AdminDemoMissionActionResponse.of(mission, previousPhase, false, false);
    }

    private void completeOtherActiveMissionsOnSameVehicle(Mission mission) {
        String vehicleId = mission.getVehicleId();
        if (vehicleId == null || vehicleId.isBlank()) {
            return;
        }

        List<Mission> activeMissions = missionRepository.findAllByVehicleIdAndPhaseIn(vehicleId, RESETTABLE_ACTIVE_PHASES);
        for (Mission activeMission : activeMissions) {
            if (activeMission.getId() != null && activeMission.getId().equals(mission.getId())) {
                continue;
            }
            advanceMissionTo(activeMission, MissionPhase.COMPLETED);
        }
    }

    private Mission getDemoMission(AuthenticatedUser authenticatedUser, String missionId) {
        accessControlService.assertAdmin(authenticatedUser);
        if (!demoModePolicy.isOperatorDispatchOnly()) {
            throw new BusinessException(ErrorCode.DEMO_MODE_DISABLED);
        }

        return missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
    }

    private void completeDispatchOutbox(Mission mission) {
        dispatchOutboxRepository.findWithPatientByCareCasePublicId(mission.getCareCase().getPublicId())
                .ifPresent(outbox -> {
                    if (!outbox.isCompleted()) {
                        outbox.markCompleted();
                    }
                });
    }

    private void assignDefaultVehicleIfMissing(Mission mission) {
        if (mission.getVehicleId() != null && !mission.getVehicleId().isBlank()) {
            return;
        }
        mission.assignVehicle(dispatchAssignmentPolicy.getDefaultVehicleId());
    }

    private void advanceMissionTo(Mission mission, MissionPhase targetPhase) {
        while (mission.getPhase() != targetPhase) {
            MissionPhase nextPhase = nextPhaseOf(mission.getPhase());
            if (nextPhase == null) {
                throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
            }
            mission.updatePhase(nextPhase, LocalDateTime.now(KstTime.resolve(clock)));
        }
    }

    private MissionPhase nextPhaseOf(MissionPhase phase) {
        return switch (phase) {
            case CREATED -> MissionPhase.DISPATCHED;
            case DISPATCHED -> MissionPhase.EN_ROUTE;
            case EN_ROUTE -> MissionPhase.ARRIVED;
            case ARRIVED -> MissionPhase.VERIFYING;
            case VERIFYING -> MissionPhase.CONSULTING;
            case CONSULTING -> MissionPhase.RETURNING;
            case RETURNING -> MissionPhase.COMPLETED;
            default -> null;
        };
    }
}
