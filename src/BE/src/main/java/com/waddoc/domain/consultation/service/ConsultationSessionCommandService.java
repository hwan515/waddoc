package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.dto.CreateConsultationSessionResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 케이스 기준으로 진료 세션을 생성하거나 재사용하는 쓰기 작업을 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ConsultationSessionCommandService {

    private final CareCaseRepository careCaseRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final AccessControlService accessControlService;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    /**
     * 같은 케이스에 열린 세션이 있으면 재사용하고, 없으면 LiveKit 방까지 함께 준비한다.
     */
    @Transactional
    public CreateSessionResult createOrReuseSession(String caseId, AuthenticatedUser authenticatedUser) {
        DoctorProfile doctorProfile = accessControlService.getDoctorProfileOrThrow(authenticatedUser);
        CareCase careCase = careCaseRepository.findWithDetailsByPublicId(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        if (!doctorProfile.getId().equals(careCase.getDoctor().getId())) {
            throw new BusinessException(ErrorCode.CASE_NOT_ASSIGNED);
        }

        ConsultationSession existingSession = consultationSessionRepository.findByCareCase(careCase).orElse(null);
        if (existingSession != null) {
            if (isTerminal(existingSession.getStatus())) {
                throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
            }
            String doctorToken = consultationLiveKitService.issueDoctorToken(existingSession, doctorProfile);
            auditLogService.log(
                    "CONSULTATION_SESSION_REUSED",
                    "CONSULTATION_SESSION",
                    existingSession.getPublicId(),
                    "corr_ses_" + existingSession.getPublicId(),
                    authenticatedUser.userId(),
                    authenticatedUser.role().name(),
                    Map.of(
                            "caseId", careCase.getPublicId(),
                            "roomId", existingSession.getRoomId()
                    )
            );
            return new CreateSessionResult(false, CreateConsultationSessionResponse.of(existingSession, doctorToken));
        }

        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId(null)
                .livekitUrl(consultationLiveKitService.getLivekitUrl())
                .build();

        consultationLiveKitService.createRoom(session.getRoomId());
        ConsultationSession savedSession = consultationSessionRepository.save(session);
        String doctorToken = consultationLiveKitService.issueDoctorToken(savedSession, doctorProfile);

        auditLogService.log(
                "CONSULTATION_SESSION_CREATED",
                "CONSULTATION_SESSION",
                savedSession.getPublicId(),
                "corr_ses_" + savedSession.getPublicId(),
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
                Map.of(
                        "caseId", careCase.getPublicId(),
                        "roomId", savedSession.getRoomId()
                )
        );

        return new CreateSessionResult(true, CreateConsultationSessionResponse.of(savedSession, doctorToken));
    }

    private boolean isTerminal(ConsultationSessionStatus status) {
        return status == ConsultationSessionStatus.COMPLETED
                || status == ConsultationSessionStatus.FAILED
                || status == ConsultationSessionStatus.ABANDONED;
    }

    public record CreateSessionResult(boolean created, CreateConsultationSessionResponse response) {
    }
}
