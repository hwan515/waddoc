package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.dto.PostConsultationTokenRequest;
import com.waddoc.domain.consultation.dto.ReissueConsultationTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSessionTokenServiceTest {

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private ConsultationLiveKitService consultationLiveKitService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConsultationSessionTokenService consultationSessionTokenService;

    @Test
    void reissueToken_reissuesDoctorTokenWhenSessionIsInProgress() {
        AuthenticatedUser doctorUser = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        ConsultationSession session = buildSession("pat_test123");
        session.start();
        DoctorProfile doctorProfile = session.getCareCase().getDoctor();
        PostConsultationTokenRequest request = request(PostConsultationTokenRequest.ParticipantType.DOCTOR, null);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(accessControlService.getDoctorProfileOrThrow(doctorUser)).thenReturn(doctorProfile);
        when(consultationLiveKitService.issueDoctorToken(session, doctorProfile)).thenReturn("doctor-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        ReissueConsultationTokenResponse response = consultationSessionTokenService.reissueToken("ses_test123", request, doctorUser);

        assertThat(response.getToken()).isEqualTo("doctor-token");
        assertThat(response.getExpiresIn()).isEqualTo(7200);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reissueToken_reissuesPatientTokenForAdmin() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123");
        session.start();
        PostConsultationTokenRequest request = request(PostConsultationTokenRequest.ParticipantType.PATIENT, "pat_test123");

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(consultationLiveKitService.issuePatientToken(session, session.getCareCase().getPatient())).thenReturn("patient-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        ReissueConsultationTokenResponse response = consultationSessionTokenService.reissueToken("ses_test123", request, admin);

        assertThat(response.getToken()).isEqualTo("patient-token");
        assertThat(response.getExpiresIn()).isEqualTo(7200);
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void reissueToken_rejectsWhenSessionIsNotInProgress() {
        AuthenticatedUser doctorUser = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        ConsultationSession session = buildSession("pat_test123");
        PostConsultationTokenRequest request = request(PostConsultationTokenRequest.ParticipantType.DOCTOR, null);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> consultationSessionTokenService.reissueToken("ses_test123", request, doctorUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_NOT_IN_PROGRESS);
    }

    @Test
    void reissueToken_rejectsPatientMismatch() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123");
        session.start();
        PostConsultationTokenRequest request = request(PostConsultationTokenRequest.ParticipantType.PATIENT, "pat_other");

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> consultationSessionTokenService.reissueToken("ses_test123", request, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PATIENT_MISMATCH);
    }

    @Test
    void reissueToken_rejectsBlankPatientIdForPatientReissue() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123");
        session.start();
        PostConsultationTokenRequest request = request(PostConsultationTokenRequest.ParticipantType.PATIENT, " ");

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> consultationSessionTokenService.reissueToken("ses_test123", request, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private PostConsultationTokenRequest request(PostConsultationTokenRequest.ParticipantType participantType, String patientId) {
        PostConsultationTokenRequest request = new PostConsultationTokenRequest();
        setField(request, "participantType", participantType);
        setField(request, "patientId", patientId);
        return request;
    }

    private ConsultationSession buildSession(String patientPublicId) {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded-password")
                .name("이국종")
                .role(Role.DOCTOR)
                .build();
        setField(doctorUser, "publicId", "usr_doctor");

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(doctor, "publicId", "doc_test123");

        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON")
                .address("김천시 증산면 장전1길 69")
                .phone("01049163720")
                .build();
        setField(patient, "publicId", patientPublicId);

        IntakeSession intakeSession = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01049163720")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(intakeSession)
                .slot(null)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 18))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(16, 30))
                .build();

        com.waddoc.domain.carecase.entity.CareCase careCase = com.waddoc.domain.carecase.entity.CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctor)
                .intakeSession(intakeSession)
                .build();

        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room_ses_test123")
                .livekitUrl("wss://livekit.test")
                .build();
        setField(session, "publicId", "ses_test123");
        return session;
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Field not found: " + fieldName);
    }
}
