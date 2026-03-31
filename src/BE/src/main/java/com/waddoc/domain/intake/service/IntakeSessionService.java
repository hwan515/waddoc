package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.intake.dto.*;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 접수 세션의 생성, 환자 연결, 종료, 상세 조회를 처리하는 기본 서비스다.
 */
@Service
@RequiredArgsConstructor
public class IntakeSessionService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * 문진/전화 접수의 시작점을 만들고 감사 로그를 남긴다.
     */
    @Transactional
    public CreateIntakeSessionResponse createSession(CreateIntakeSessionRequest request) {
        IntakeSession session = IntakeSession.builder()
                .callerNumber(request.getCallerNumber())
                .channel(request.getChannel())
                .build();

        intakeSessionRepository.save(session);

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_SESSION_CREATED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("callerNumber", request.getCallerNumber(),
                       "channel", session.getChannel().name())
        );

        return CreateIntakeSessionResponse.from(session);
    }

    /**
     * 활성 세션에 환자를 연결한다.
     * 이미 같은 환자가 연결된 경우에는 멱등하게 기존 결과를 돌려준다.
     */
    @Transactional
    public BindPatientResponse bindPatient(String intakeSessionId, BindPatientRequest request) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        Patient patient = patientRepository.findByPublicId(request.getPatientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));

        // 멱등성: 같은 환자가 이미 바인딩된 경우 그대로 반환
        if (session.getPatient() != null) {
            if (session.getPatient().getId().equals(patient.getId())) {
                return BindPatientResponse.from(session);
            }
            throw new BusinessException(ErrorCode.PATIENT_ALREADY_BOUND);
        }

        session.bindPatient(patient, LocalDateTime.now(KstTime.resolve(clock)));

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_PATIENT_BOUND",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("patientId", patient.getPublicId())
        );

        return BindPatientResponse.from(session);
    }

    /**
     * 접수 흐름을 더 진행하지 않을 때 세션을 종료 상태로 바꾼다.
     */
    @Transactional
    public CompleteSessionResponse completeSession(String intakeSessionId, CompleteSessionRequest request) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        session.complete(request.getCompletionReason(), LocalDateTime.now(KstTime.resolve(clock)));

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_SESSION_COMPLETED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("completionReason", request.getCompletionReason().name())
        );

        return CompleteSessionResponse.from(session);
    }

    /**
     * 프론트가 이어서 사용할 수 있도록 현재 세션 스냅샷을 그대로 내려준다.
     */
    @Transactional(readOnly = true)
    public IntakeSessionDetailResponse getSession(String intakeSessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        return IntakeSessionDetailResponse.from(session);
    }
}
