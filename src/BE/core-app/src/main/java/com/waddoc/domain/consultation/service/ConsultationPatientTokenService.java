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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

/**
 * 본인 확인이 끝난 환자에게만 진료방 입장 토큰을 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationPatientTokenService {

    private static final EnumSet<MissionPhase> READY_MISSION_PHASES =
            EnumSet.of(MissionPhase.VERIFYING, MissionPhase.CONSULTING);
    private static final EnumSet<MissionPhase> DIRECT_READY_MISSION_PHASES =
            EnumSet.of(MissionPhase.DISPATCHED, MissionPhase.EN_ROUTE, MissionPhase.ARRIVED, MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private final ConsultationSessionRepository consultationSessionRepository;
    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final MissionIdentityCheckCacheService missionIdentityCheckCacheService;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    @Value("${consultation.direct-webrtc-enabled:false}")
    private boolean directWebrtcEnabled;

    @Transactional
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

    @Transactional
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
        if (!resolveReadyMissionPhases().contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }
        AccessActor actor = accessControlService.assertAdminOrMissionTerminal(
                authentication,
                mission.getPublicId(),
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        );

        prepareMissionForDirectWebRtc(mission);
        boolean identityCheckBypassed = false;

        // 얼굴/신분증 확인 성공 캐시가 없으면 진료방 입장을 막는다.
        Optional<MissionIdentityCheckCacheService.VerifiedIdentityCheck> verifiedIdentityCheck =
                missionIdentityCheckCacheService.findVerified(mission.getPublicId(), patient.getPublicId());
        if (verifiedIdentityCheck.isEmpty()) {
            if (!directWebrtcEnabled) {
                throw new BusinessException(ErrorCode.IDENTITY_CHECK_NOT_CONFIRMED);
            }
            // 즉시 진료 모드에서는 차량 도착 전 진입을 허용하므로,
            // 현장 본인확인 캐시가 아직 없더라도 환자 토큰 발급을 진행한다.
            missionIdentityCheckCacheService.saveVerified(mission.getPublicId(), patient.getPublicId());
            identityCheckBypassed = true;
            log.warn(
                    "Bypassing identity-check gate for direct robot WebRTC. missionId={}, patientId={}, missionPhase={}",
                    mission.getPublicId(),
                    patient.getPublicId(),
                    mission.getPhase()
            );
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
                        "missionPhase", mission.getPhase().name(),
                        "identityCheckBypassed", identityCheckBypassed
                )
        );

        return IssuePatientTokenResponse.of(
                session,
                patientToken,
                consultationLiveKitService.getParticipantTokenExpiresInSeconds()
        );
    }

    private EnumSet<MissionPhase> resolveReadyMissionPhases() {
        // 즉시 진료 모드에서는 ARRIVED 이전 단계도 진료방 입장 준비 상태로 본다.
        return directWebrtcEnabled ? DIRECT_READY_MISSION_PHASES : READY_MISSION_PHASES;
    }

    private void prepareMissionForDirectWebRtc(Mission mission) {
        if (!directWebrtcEnabled) {
            return;
        }

        if (mission.getPhase() == MissionPhase.DISPATCHED
                || mission.getPhase() == MissionPhase.EN_ROUTE
                || mission.getPhase() == MissionPhase.ARRIVED) {
            // LiveKit 입장 직전에는 mission을 VERIFYING으로 올려
            // 이후 webhook/상담 흐름이 기존 phase 전이를 그대로 재사용하게 한다.
            mission.updatePhase(MissionPhase.VERIFYING);
            missionRepository.save(mission);
        }
    }

    private boolean isTerminal(ConsultationSessionStatus status) {
        return status == ConsultationSessionStatus.COMPLETED
                || status == ConsultationSessionStatus.FAILED
                || status == ConsultationSessionStatus.ABANDONED;
    }
}
