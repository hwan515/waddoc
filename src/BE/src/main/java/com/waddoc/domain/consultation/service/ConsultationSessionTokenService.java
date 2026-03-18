package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.dto.PostConsultationTokenRequest;
import com.waddoc.domain.consultation.dto.ReissueConsultationTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSessionTokenService {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final AccessControlService accessControlService;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ReissueConsultationTokenResponse reissueToken(
            String sessionId,
            PostConsultationTokenRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        ConsultationSession session = consultationSessionRepository.findWithParticipantsByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        // 재발급은 실제 진료가 진행 중인 세션에서만 허용한다.
        if (session.getStatus() != ConsultationSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }

        String token = switch (request.getParticipantType()) {
            case DOCTOR -> reissueDoctorToken(session, authenticatedUser);
            case PATIENT -> reissuePatientToken(session, request.getPatientId(), authenticatedUser);
        };

        return ReissueConsultationTokenResponse.of(
                token,
                consultationLiveKitService.getParticipantTokenExpiresInSeconds()
        );
    }

    private String reissueDoctorToken(ConsultationSession session, AuthenticatedUser authenticatedUser) {
        DoctorProfile doctorProfile = accessControlService.getDoctorProfileOrThrow(authenticatedUser);
        // 토큰 재발급은 세션 담당 의사 본인만 가능하다.
        if (!doctorProfile.getPublicId().equals(session.getCareCase().getDoctor().getPublicId())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        String token = consultationLiveKitService.issueDoctorToken(session, doctorProfile);
        auditLogService.log(
                "CONSULTATION_DOCTOR_TOKEN_REISSUED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
                Map.of("doctorId", doctorProfile.getPublicId())
        );
        return token;
    }

    private String reissuePatientToken(ConsultationSession session, String patientId, AuthenticatedUser authenticatedUser) {
        accessControlService.assertAdmin(authenticatedUser);
        if (patientId == null || patientId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Patient patient = session.getCareCase().getPatient();
        // 차량 단말은 관리자 권한으로 요청하지만, 세션에 연결된 동일 환자인지는 다시 확인한다.
        if (!patient.getPublicId().equals(patientId)) {
            throw new BusinessException(ErrorCode.PATIENT_MISMATCH);
        }

        // 재발급은 이미 연결된 세션의 동일 환자만 허용한다.
        String token = consultationLiveKitService.issuePatientToken(session, patient);
        auditLogService.log(
                "CONSULTATION_PATIENT_TOKEN_REISSUED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
                Map.of("patientId", patient.getPublicId())
        );
        return token;
    }
}
