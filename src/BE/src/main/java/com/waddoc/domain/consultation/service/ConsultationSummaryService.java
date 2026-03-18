package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.PutConsultationSummaryRequest;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsultationSummaryService {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;

    @Transactional
    public ConsultationSummaryResponse saveSummary(
            String sessionId,
            PutConsultationSummaryRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        DoctorProfile doctorProfile = accessControlService.getDoctorProfileOrThrow(authenticatedUser);
        ConsultationSession session = consultationSessionRepository.findWithDoctorAndCaseByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (!doctorProfile.getId().equals(session.getCareCase().getDoctor().getId())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        // 완료 직후 요약 수정도 허용하기 위해 COMPLETED 상태까지 저장 가능하게 둔다.
        if (session.getStatus() != ConsultationSessionStatus.IN_PROGRESS
                && session.getStatus() != ConsultationSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        // PUT 성격에 맞춰 기존 요약이 있으면 갱신하고, 없으면 새로 생성한다.
        ConsultationSummary summary = consultationSummaryRepository.findBySession(session)
                .map(existing -> {
                    existing.update(
                            request.getSummaryNote(),
                            request.isPrescriptionIssued(),
                            request.getPrescriptionNote(),
                            request.isNeedsFollowUp()
                    );
                    return existing;
                })
                .orElseGet(() -> ConsultationSummary.builder()
                        .session(session)
                        .summaryNote(request.getSummaryNote())
                        .prescriptionIssued(request.isPrescriptionIssued())
                        .prescriptionNote(request.getPrescriptionNote())
                        .needsFollowUp(request.isNeedsFollowUp())
                        .build());

        consultationSummaryRepository.save(summary);

        if (session.getStatus() == ConsultationSessionStatus.IN_PROGRESS) {
            // 진료 요약 저장을 세션 종료 시점으로 간주하고 연관된 케이스/예약 상태도 함께 마감한다.
            session.complete(calculateDurationMinutes(session, LocalDateTime.now()));
            session.getCareCase().complete();
            session.getCareCase().getBooking().complete();
        }

        String correlationId = "corr_ses_" + session.getPublicId();
        auditLogService.log(
                "CONSULTATION_SUMMARY_SAVED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                correlationId,
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
                Map.of(
                        "caseId", session.getCareCase().getPublicId(),
                        "prescriptionIssued", request.isPrescriptionIssued(),
                        "needsFollowUp", request.isNeedsFollowUp()
                )
        );

        return ConsultationSummaryResponse.from(session, summary);
    }

    private int calculateDurationMinutes(ConsultationSession session, LocalDateTime endedAt) {
        // 시작 시각이 아직 없다면 webhook/세션 생성 구현 전 단계로 보고 0분으로 처리한다.
        if (session.getStartedAt() == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(session.getStartedAt(), endedAt).toMinutes());
    }
}
