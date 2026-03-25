package com.waddoc.domain.admin.service;

import com.waddoc.domain.admin.dto.AdminDemoMissionActionResponse;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.dispatch.service.RobotWaypointCommandClient;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

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

    private final AccessControlService accessControlService;
    private final MissionRepository missionRepository;
    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final RobotWaypointCommandClient robotWaypointCommandClient;
    private final DemoModePolicy demoModePolicy;

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

        if (mission.getTargetWaypointNumber() != null) {
            robotWaypointCommandClient.dispatchToWaypoint(mission.getTargetWaypointNumber());
            advanceMissionTo(mission, MissionPhase.DISPATCHED);
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

    private void advanceMissionTo(Mission mission, MissionPhase targetPhase) {
        while (mission.getPhase() != targetPhase) {
            MissionPhase nextPhase = nextPhaseOf(mission.getPhase());
            if (nextPhase == null) {
                throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
            }
            mission.updatePhase(nextPhase);
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
