package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.intake.dto.IdentifyByCallerNumberRequest;
import com.waddoc.domain.intake.dto.IdentifyByInfoRequest;
import com.waddoc.domain.intake.dto.IdentifyByPhoneRequest;
import com.waddoc.domain.intake.dto.IdentifyPatientResponse;
import com.waddoc.domain.intake.entity.CompletionReason;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientPhoneBinding;
import com.waddoc.domain.patient.repository.PatientPhoneBindingRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientIdentifyServiceTest {

    @Mock
    private IntakeSessionRepository intakeSessionRepository;

    @Mock
    private PatientPhoneBindingRepository patientPhoneBindingRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PatientIdentifyService patientIdentifyService;

    @Test
    void identifyByCallerNumber_returnsPatient_whenPhoneBindingExists() {
        IntakeSession session = IntakeSession.builder()
                .callerNumber("01012345678")
                .build();
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();
        PatientPhoneBinding binding = PatientPhoneBinding.builder()
                .patient(patient)
                .phone("01012345678")
                .primary(true)
                .build();

        when(intakeSessionRepository.findByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(session));
        when(patientPhoneBindingRepository.findByPhone("01012345678"))
                .thenReturn(Optional.of(binding));

        IdentifyPatientResponse response = patientIdentifyService.identifyByCallerNumber(
                session.getPublicId(),
                new IdentifyByCallerNumberRequest("01012345678")
        );

        assertThat(response.isIdentified()).isTrue();
        assertThat(response.getPatient()).isNotNull();
        assertThat(response.getPatient().getPatientId()).isEqualTo(patient.getPublicId());
        assertThat(response.getPatient().getBirthDate6()).isEqualTo("580315");
        assertThat(response.getPatient().getRegionCode()).isEqualTo("ULLEUNG");

        verify(auditLogService).log(
                eq("PATIENT_LOOKUP_BY_CALLER_NUMBER"),
                eq("INTAKE_SESSION"),
                eq(session.getPublicId()),
                eq("corr_ints_" + session.getPublicId()),
                argThat(detail -> hasBoolean(detail, "identified", true)
                        && patient.getPublicId().equals(detail.get("patientId")))
        );
    }

    @Test
    void identifyByPhone_returnsNotIdentified_whenPhoneBindingDoesNotExist() {
        IntakeSession session = IntakeSession.builder()
                .callerNumber("01012345678")
                .build();

        when(intakeSessionRepository.findByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(session));
        when(patientPhoneBindingRepository.findByPhone("01099998888"))
                .thenReturn(Optional.empty());

        IdentifyPatientResponse response = patientIdentifyService.identifyByPhone(
                session.getPublicId(),
                new IdentifyByPhoneRequest("01099998888")
        );

        assertThat(response.isIdentified()).isFalse();
        assertThat(response.getPatient()).isNull();

        verify(auditLogService).log(
                eq("PATIENT_LOOKUP_BY_PHONE"),
                eq("INTAKE_SESSION"),
                eq(session.getPublicId()),
                eq("corr_ints_" + session.getPublicId()),
                argThat(detail -> hasBoolean(detail, "identified", false))
        );
    }

    @Test
    void identifyByInfo_returnsNotIdentified_whenPatientDoesNotExist() {
        IntakeSession session = IntakeSession.builder()
                .callerNumber("01012345678")
                .build();

        when(intakeSessionRepository.findByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(session));
        when(patientRepository.findAllByNameAndBirthDate6("없는사람", "900101"))
                .thenReturn(List.of());

        IdentifyPatientResponse response = patientIdentifyService.identifyByInfo(
                session.getPublicId(),
                new IdentifyByInfoRequest("없는사람", "900101")
        );

        assertThat(response.isIdentified()).isFalse();
        assertThat(response.getPatient()).isNull();

        verify(auditLogService).log(
                eq("PATIENT_LOOKUP_BY_INFO"),
                eq("INTAKE_SESSION"),
                eq(session.getPublicId()),
                eq("corr_ints_" + session.getPublicId()),
                argThat(detail -> hasBoolean(detail, "identified", false)
                        && Integer.valueOf(0).equals(detail.get("matchedCount")))
        );
    }

    @Test
    void identifyByInfo_returnsNotIdentified_whenMultiplePatientsMatch() {
        IntakeSession session = IntakeSession.builder()
                .callerNumber("01012345678")
                .build();
        Patient first = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();
        Patient second = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("JEJU")
                .address("제주시")
                .build();

        when(intakeSessionRepository.findByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(session));
        when(patientRepository.findAllByNameAndBirthDate6("홍길동", "580315"))
                .thenReturn(List.of(first, second));

        IdentifyPatientResponse response = patientIdentifyService.identifyByInfo(
                session.getPublicId(),
                new IdentifyByInfoRequest("홍길동", "580315")
        );

        assertThat(response.isIdentified()).isFalse();
        assertThat(response.getPatient()).isNull();

        verify(auditLogService).log(
                eq("PATIENT_LOOKUP_BY_INFO"),
                eq("INTAKE_SESSION"),
                eq(session.getPublicId()),
                eq("corr_ints_" + session.getPublicId()),
                argThat(detail -> hasBoolean(detail, "identified", false)
                        && Integer.valueOf(2).equals(detail.get("matchedCount")))
        );
    }

    @Test
    void identifyByInfo_throwsException_whenSessionIsInactive() {
        IntakeSession session = IntakeSession.builder()
                .callerNumber("01012345678")
                .build();
        session.complete(CompletionReason.USER_HANGUP);

        when(intakeSessionRepository.findByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> patientIdentifyService.identifyByInfo(
                session.getPublicId(),
                new IdentifyByInfoRequest("홍길동", "580315")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 세션 상태");
    }

    @Test
    void identifyByCallerNumber_throwsException_whenSessionDoesNotExist() {
        when(intakeSessionRepository.findByPublicId("ints_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientIdentifyService.identifyByCallerNumber(
                "ints_missing",
                new IdentifyByCallerNumberRequest("01012345678")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTAKE_SESSION_NOT_FOUND));
    }

    private boolean hasBoolean(Map<String, Object> detail, String key, boolean expected) {
        Object value = detail.get(key);
        return value instanceof Boolean && ((Boolean) value) == expected;
    }
}
