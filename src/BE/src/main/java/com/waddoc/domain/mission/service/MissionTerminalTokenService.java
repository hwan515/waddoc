package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MissionTerminalTokenService {

    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public IssueMissionTerminalTokenResponse issueToken(String missionId, AuthenticatedUser authenticatedUser) {
        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        accessControlService.assertAssignedDoctorOrAdmin(authenticatedUser, mission.getCareCase());

        return issueTokenInternal(mission, authenticatedUser.userId(), authenticatedUser.role().name());
    }

    @Transactional(readOnly = true)
    public IssueMissionTerminalTokenResponse issueTokenForDeviceClaim(String missionId, String terminalId) {
        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        // 차량 단말이 후보 조회 후 확정한 미션에 한해 mission-terminal 토큰을 발급한다.
        return issueTokenInternal(mission, terminalId, "DEVICE_TERMINAL");
    }

    private IssueMissionTerminalTokenResponse issueTokenInternal(Mission mission, String actorId, String actorRole) {
        // 차량 단말은 본인확인과 환자 토큰 발급 범위만 갖도록 최소 권한으로 발급한다.
        List<String> scopes = List.of(
                MissionTerminalScopes.IDENTITY_CHECK,
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        );
        String terminalToken = jwtTokenProvider.createMissionTerminalToken(
                mission.getPublicId(),
                mission.getCareCase().getPublicId(),
                scopes
        );

        auditLogService.log(
                "MISSION_TERMINAL_TOKEN_ISSUED",
                "MISSION",
                mission.getPublicId(),
                "corr_mis_" + mission.getPublicId(),
                actorId,
                actorRole,
                Map.of(
                        "caseId", mission.getCareCase().getPublicId(),
                        "expiresInSeconds", jwtTokenProvider.getMissionTerminalTokenExpiry(),
                        "scopes", scopes
                )
        );

        return IssueMissionTerminalTokenResponse.of(
                mission,
                terminalToken,
                jwtTokenProvider.getMissionTerminalTokenExpiry(),
                scopes
        );
    }
}
