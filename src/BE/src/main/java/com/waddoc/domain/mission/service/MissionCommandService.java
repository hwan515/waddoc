package com.waddoc.domain.mission.service;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.mission.dto.CreateMissionRequest;
import com.waddoc.domain.mission.dto.CreateMissionResponse;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseRequest;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MissionRepository missionRepository;
    private final CareCaseRepository careCaseRepository;
    private final AccessControlService accessControlService;

    public CreateMissionResponse createMission(
            AuthenticatedUser authenticatedUser,
            CreateMissionRequest request
    ) {
        accessControlService.assertAdmin(authenticatedUser);

        CareCase careCase = careCaseRepository.findByPublicId(request.getCaseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        if (missionRepository.findByCareCase(careCase).isPresent()) {
            throw new BusinessException(ErrorCode.MISSION_ALREADY_EXISTS);
        }

        Mission savedMission = createMissionForDispatch(
                careCase,
                request.getVehicleId(),
                request.getDestination(),
                request.getScheduledTime().atZoneSameInstant(KST).toLocalDateTime()
        );
        return CreateMissionResponse.from(savedMission);
    }

    public Mission createMissionForDispatch(
            CareCase careCase,
            String vehicleId,
            String destination,
            LocalDateTime dispatchedAt
    ) {
        return missionRepository.findByCareCase(careCase)
                .orElseGet(() -> missionRepository.save(
                        Mission.builder()
                                .careCase(careCase)
                                .vehicleId(vehicleId)
                                .destination(destination)
                                .dispatchedAt(dispatchedAt)
                                .build()
                ));
    }

    public UpdateMissionPhaseResponse updateMissionPhase(
            AuthenticatedUser authenticatedUser,
            String missionId,
            UpdateMissionPhaseRequest request
    ) {
        accessControlService.assertAdmin(authenticatedUser);

        Mission mission = missionRepository.findByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        validatePhaseTransition(mission, request.getPhase());

        MissionPhase previousPhase = mission.getPhase();
        mission.updatePhase(request.getPhase());

        Mission savedMission = missionRepository.save(mission);
        return UpdateMissionPhaseResponse.of(savedMission, previousPhase);
    }

    private void validatePhaseTransition(Mission mission, MissionPhase targetPhase) {
        MissionPhase currentPhase = mission.getPhase();
        if (currentPhase == targetPhase) {
            return;
        }

        if (currentPhase == MissionPhase.COMPLETED || currentPhase == MissionPhase.FAILED) {
            throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
        }

        if (currentPhase == MissionPhase.INCIDENT) {
            if (mission.getPreviousPhase() != targetPhase) {
                throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
            }
            return;
        }

        if (targetPhase == MissionPhase.INCIDENT || targetPhase == MissionPhase.FAILED) {
            return;
        }

        if (nextPhaseOf(currentPhase) != targetPhase) {
            throw new BusinessException(ErrorCode.MISSION_PHASE_TRANSITION_INVALID);
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
