package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionIdentityCheckCacheService;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.authorization.AccessActor;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationPatientTokenService {

    private static final EnumSet<MissionPhase> READY_MISSION_PHASES =
            EnumSet.of(MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private final ConsultationSessionRepository consultationSessionRepository;
    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final MissionIdentityCheckCacheService missionIdentityCheckCacheService;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public IssuePatientTokenResponse issuePatientToken(
            String sessionId,
            Authentication authentication
    ) {
        ConsultationSession session = consultationSessionRepository.findWithParticipantsByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        Mission mission = missionRepository.findByCareCase(session.getCareCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_READY));

        return issuePatientToken(session, mission, authentication);
    }

    @Transactional(readOnly = true)
    public IssuePatientTokenResponse issuePatientTokenByMission(
            String missionId,
            Authentication authentication
    ) {
        // FE가 sessionId를 직접 들고 다니지 않도록 mission 기준으로 세션을 해석한다.
        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
        ConsultationSession session = consultationSessionRepository.findByCareCase(mission.getCareCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        return issuePatientToken(session, mission, authentication);
    }

    private IssuePatientTokenResponse issuePatientToken(
            ConsultationSession session,
            Mission mission,
            Authentication authentication
    ) {
        if (isTerminal(session.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        Patient patient = session.getCareCase().getPatient();
        if (!READY_MISSION_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }
        AccessActor actor = accessControlService.assertAdminOrMissionTerminal(
                authentication,
                mission.getPublicId(),
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        );

        // 얼굴/신분증 확인 성공 캐시가 없으면 진료방 입장을 막는다.
        if (missionIdentityCheckCacheService.findVerified(mission.getPublicId(), patient.getPublicId()).isEmpty()) {
            throw new BusinessException(ErrorCode.IDENTITY_CHECK_NOT_CONFIRMED);
        }

        String patientToken = consultationLiveKitService.issuePatientToken(session, patient);
        auditLogService.log(
                "CONSULTATION_PATIENT_TOKEN_ISSUED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                actor.actorId(),
                actor.actorRole(),
                Map.of(
                        "patientId", patient.getPublicId(),
                        "missionPhase", mission.getPhase().name()
                )
        );

        return IssuePatientTokenResponse.of(
                session,
                patientToken,
                consultationLiveKitService.getParticipantTokenExpiresInSeconds()
        );
    }

    private boolean isTerminal(ConsultationSessionStatus status) {
        return status == ConsultationSessionStatus.COMPLETED
                || status == ConsultationSessionStatus.FAILED
                || status == ConsultationSessionStatus.ABANDONED;
    }
}
