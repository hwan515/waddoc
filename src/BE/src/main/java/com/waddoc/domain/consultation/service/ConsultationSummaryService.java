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
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 진료 요약의 조회와 저장, 세션 종료 시점 정리를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSummaryService {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;
    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ConsultationSummaryResponse getSummary(String sessionId, AuthenticatedUser authenticatedUser) {
        // 세션 조회 API는 세션-케이스-담당 의사 기준으로 접근 권한을 확인한다.
        ConsultationSession session = consultationSessionRepository.findWithDoctorAndCaseByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        accessControlService.assertAssignedDoctorOrAdmin(authenticatedUser, session.getCareCase());

        // 요약은 session과 1:1이므로 세션 기준으로 단건 조회한다.
        ConsultationSummary summary = consultationSummaryRepository.findBySession(session)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_SUMMARY_NOT_FOUND));

        return ConsultationSummaryResponse.from(session, summary);
    }

    @Transactional
    public ConsultationSummaryResponse saveSummary(
            String sessionId,
            PutConsultationSummaryRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        // 저장 API는 의사 전용이므로 관리자 허용 메서드가 아니라 의사 프로필을 직접 확인한다.
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

        boolean shouldCloseRoom = false;
        if (session.getStatus() == ConsultationSessionStatus.IN_PROGRESS) {
            // 진료 요약 저장을 세션 종료 시점으로 간주하고 연관된 케이스/예약 상태도 함께 마감한다.
            session.complete(calculateDurationMinutes(session, LocalDateTime.now()));
            session.getCareCase().complete();
            session.getCareCase().getBooking().complete();
            syncMissionAfterConsultationCompletion(session);
            shouldCloseRoom = true;
        }

        // 요약 내용 전문은 로그에 남기지 않고 운영 추적에 필요한 최소 정보만 남긴다.
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

        if (shouldCloseRoom) {
            closeLiveKitRoom(session);
        }

        return ConsultationSummaryResponse.from(session, summary);
    }

    private int calculateDurationMinutes(ConsultationSession session, LocalDateTime endedAt) {
        // 시작 시각이 아직 없다면 webhook/세션 생성 구현 전 단계로 보고 0분으로 처리한다.
        if (session.getStartedAt() == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(session.getStartedAt(), endedAt).toMinutes());
    }

    private void syncMissionAfterConsultationCompletion(ConsultationSession session) {
        missionRepository.findByCareCase(session.getCareCase())
                .ifPresent(mission -> {
                    if (mission.getPhase() == MissionPhase.CONSULTING
                            || mission.getPhase() == MissionPhase.VERIFYING) {
                        mission.updatePhase(MissionPhase.RETURNING);
                        missionRepository.save(mission);
                        return;
                    }

                    if (mission.getPhase() != MissionPhase.RETURNING
                            && mission.getPhase() != MissionPhase.COMPLETED
                            && mission.getPhase() != MissionPhase.FAILED) {
                        log.warn(
                                "Consultation completed with unexpected mission phase. sessionId={}, missionId={}, missionPhase={}",
                                session.getPublicId(),
                                mission.getPublicId(),
                                mission.getPhase()
                        );
                    }
                });
    }

    private void closeLiveKitRoom(ConsultationSession session) {
        try {
            consultationLiveKitService.deleteRoom(session.getRoomId());
        } catch (BusinessException e) {
            log.warn(
                    "Failed to close LiveKit room after consultation completion. sessionId={}, roomId={}",
                    session.getPublicId(),
                    session.getRoomId(),
                    e
            );
        }
    }
}
