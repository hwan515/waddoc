package com.waddoc.domain.mission.service;

import com.waddoc.domain.mission.dto.MissionDetailResponse;
import com.waddoc.domain.mission.dto.MissionListResponse;
import com.waddoc.domain.mission.dto.MissionSummaryResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionQueryService {

    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;

    public MissionListResponse getMissions(
            AuthenticatedUser authenticatedUser,
            LocalDate date,
            MissionPhase phase
    ) {
        accessControlService.assertAdmin(authenticatedUser);

        List<MissionSummaryResponse> missions = findMissions(date, phase).stream()
                .map(MissionSummaryResponse::from)
                .toList();

        return MissionListResponse.of(missions);
    }

    public MissionDetailResponse getMissionDetail(AuthenticatedUser authenticatedUser, String missionId) {
        accessControlService.assertAdmin(authenticatedUser);

        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        return MissionDetailResponse.from(mission);
    }

    private List<Mission> findMissions(LocalDate date, MissionPhase phase) {
        if (date == null && phase == null) {
            return missionRepository.findAllForAdminDashboard();
        }

        if (date == null) {
            return missionRepository.findAllForAdminDashboardByPhase(phase);
        }

        LocalDateTime fromDateTime = date.atStartOfDay();
        LocalDateTime toDateTime = date.plusDays(1).atStartOfDay();

        if (phase == null) {
            return missionRepository.findAllForAdminDashboardByDateRange(fromDateTime, toDateTime);
        }

        return missionRepository.findAllForAdminDashboardByPhaseAndDateRange(
                phase,
                fromDateTime,
                toDateTime
        );
    }
}
