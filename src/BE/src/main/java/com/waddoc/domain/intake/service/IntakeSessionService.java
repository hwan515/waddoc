package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.intake.dto.*;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntakeSessionService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;

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

        session.bindPatient(patient);

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

    @Transactional(readOnly = true)
    public IntakeSessionDetailResponse getSession(String intakeSessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        return IntakeSessionDetailResponse.from(session);
    }

    @Transactional
    public CompleteSessionResponse completeSession(String intakeSessionId, CompleteSessionRequest request) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        session.complete(request.getCompletionReason());

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
}
