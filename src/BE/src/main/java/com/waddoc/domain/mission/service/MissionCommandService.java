package com.waddoc.domain.mission.service;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.mission.dto.CreateMissionRequest;
import com.waddoc.domain.mission.dto.CreateMissionResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId(request.getVehicleId())
                .destination(request.getDestination())
                .dispatchedAt(request.getScheduledTime().atZoneSameInstant(KST).toLocalDateTime())
                .build();

        Mission savedMission = missionRepository.save(mission);
        return CreateMissionResponse.from(savedMission);
    }
}
