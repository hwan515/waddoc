package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.intake.dto.IdentifyByCallerNumberRequest;
import com.waddoc.domain.intake.dto.IdentifyByInfoRequest;
import com.waddoc.domain.intake.dto.IdentifyByPhoneRequest;
import com.waddoc.domain.intake.dto.IdentifyPatientResponse;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientIdentifyService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public IdentifyPatientResponse identifyByCallerNumber(String intakeSessionId,
                                                          IdentifyByCallerNumberRequest request) {
        return identifyByPhoneNumber(
                intakeSessionId,
                request.getCallerNumber(),
                "PATIENT_LOOKUP_BY_CALLER_NUMBER",
                "callerNumberMasked"
        );
    }

    @Transactional
    public IdentifyPatientResponse identifyByPhone(String intakeSessionId,
                                                   IdentifyByPhoneRequest request) {
        return identifyByPhoneNumber(
                intakeSessionId,
                request.getPhone(),
                "PATIENT_LOOKUP_BY_PHONE",
                "phoneMasked"
        );
    }

    @Transactional
    public IdentifyPatientResponse identifyByInfo(String intakeSessionId,
                                                  IdentifyByInfoRequest request) {
        IntakeSession session = findActiveSession(intakeSessionId);

        List<Patient> matchedPatients = patientRepository.findAllByNameAndBirthDate6(
                request.getName(),
                request.getBirthDate6()
        );
        Patient patient = matchedPatients.size() == 1 ? matchedPatients.get(0) : null;

        session.touch();

        Map<String, Object> detailJson = new LinkedHashMap<>();
        detailJson.put("identified", patient != null);
        detailJson.put("nameMasked", maskName(request.getName()));
        detailJson.put("birthDate6Masked", maskBirthDate6(request.getBirthDate6()));
        detailJson.put("matchedCount", matchedPatients.size());
        if (patient != null) {
            detailJson.put("patientId", patient.getPublicId());
        }

        logLookup(session, "PATIENT_LOOKUP_BY_INFO", detailJson);

        return patient != null
                ? IdentifyPatientResponse.identified(patient)
                : IdentifyPatientResponse.notIdentified();
    }

    private IdentifyPatientResponse identifyByPhoneNumber(String intakeSessionId,
                                                          String phoneNumber,
                                                          String action,
                                                          String phoneFieldName) {
        IntakeSession session = findActiveSession(intakeSessionId);

        Patient patient = patientRepository.findByPhone(phoneNumber).orElse(null);

        session.touch();

        Map<String, Object> detailJson = new LinkedHashMap<>();
        detailJson.put("identified", patient != null);
        detailJson.put(phoneFieldName, maskPhone(phoneNumber));
        if (patient != null) {
            detailJson.put("patientId", patient.getPublicId());
        }

        logLookup(session, action, detailJson);

        return patient != null
                ? IdentifyPatientResponse.identified(patient)
                : IdentifyPatientResponse.notIdentified();
    }

    private IntakeSession findActiveSession(String intakeSessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(intakeSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        return session;
    }

    private void logLookup(IntakeSession session, String action, Map<String, Object> detailJson) {
        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                action,
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                detailJson
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }

        int prefixLength = Math.min(3, phone.length() - 2);
        int suffixLength = Math.min(2, phone.length() - prefixLength);
        int maskedLength = Math.max(phone.length() - prefixLength - suffixLength, 1);

        return phone.substring(0, prefixLength)
                + "*".repeat(maskedLength)
                + phone.substring(phone.length() - suffixLength);
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        if (name.length() == 1) {
            return name;
        }

        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    private String maskBirthDate6(String birthDate6) {
        if (birthDate6 == null || birthDate6.length() != 6) {
            return "******";
        }

        return birthDate6.substring(0, 2) + "****";
    }
}
