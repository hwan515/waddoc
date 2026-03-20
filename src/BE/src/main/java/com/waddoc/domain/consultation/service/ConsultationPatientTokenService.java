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
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            String patientId,
            AuthenticatedUser authenticatedUser
    ) {
        accessControlService.assertAdmin(authenticatedUser);

        ConsultationSession session = consultationSessionRepository.findWithParticipantsByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (isTerminal(session.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        Patient patient = session.getCareCase().getPatient();
        if (!patient.getPublicId().equals(patientId)) {
            throw new BusinessException(ErrorCode.PATIENT_MISMATCH);
        }

        Mission mission = missionRepository.findByCareCase(session.getCareCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_READY));
        if (!READY_MISSION_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }

        if (missionIdentityCheckCacheService.findVerified(mission.getPublicId(), patientId).isEmpty()) {
            throw new BusinessException(ErrorCode.IDENTITY_CHECK_NOT_CONFIRMED);
        }

        String patientToken = consultationLiveKitService.issuePatientToken(session, patient);
        auditLogService.log(
                "CONSULTATION_PATIENT_TOKEN_ISSUED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
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
