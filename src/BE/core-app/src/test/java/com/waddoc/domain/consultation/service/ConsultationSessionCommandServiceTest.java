package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.dto.CreateConsultationSessionResponse;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSessionCommandServiceTest {

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private ConsultationLiveKitService consultationLiveKitService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConsultationSessionCommandService consultationSessionCommandService;

    @Test
    void createOrReuseSession_createsSessionAndIssuesDoctorToken() {
        DoctorProfile doctorProfile = buildDoctorProfile("usr_doctor", "doc_doctor");
        CareCase careCase = buildCareCase(doctorProfile);
        AuthenticatedUser actor = new AuthenticatedUser("usr_doctor", Role.DOCTOR);

        when(accessControlService.getDoctorProfileOrThrow(actor)).thenReturn(doctorProfile);
        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.empty());
        when(consultationLiveKitService.getLivekitUrl()).thenReturn("wss://livekit.example.com");
        when(consultationSessionRepository.saveAndFlush(any(ConsultationSession.class))).thenAnswer(invocation -> {
            ConsultationSession session = invocation.getArgument(0);
            setField(session, "createdAt", LocalDateTime.of(2026, 3, 18, 15, 0));
            return session;
        });
        when(consultationLiveKitService.issueDoctorToken(any(ConsultationSession.class), eq(doctorProfile)))
                .thenReturn("doctor-token");

        ConsultationSessionCommandService.CreateSessionResult result =
                consultationSessionCommandService.createOrReuseSession(careCase.getPublicId(), actor);

        CreateConsultationSessionResponse response = result.response();
        assertThat(result.created()).isTrue();
        assertThat(response.getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getDoctorToken()).isEqualTo("doctor-token");
        assertThat(response.getRoom().getRoomId()).startsWith("room_ses_");
        assertThat(response.getRoom().getLivekitUrl()).isEqualTo("wss://livekit.example.com");
        verify(consultationLiveKitService).createRoom(response.getRoom().getRoomId());
        verify(consultationSessionRepository).saveAndFlush(any(ConsultationSession.class));
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createOrReuseSession_reusesActiveSession() {
        DoctorProfile doctorProfile = buildDoctorProfile("usr_doctor", "doc_doctor");
        CareCase careCase = buildCareCase(doctorProfile);
        AuthenticatedUser actor = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        ConsultationSession existingSession = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room_ses_existing")
                .livekitUrl("wss://livekit.example.com")
                .build();
        setField(existingSession, "createdAt", LocalDateTime.of(2026, 3, 18, 14, 30));
        existingSession.markReady();

        when(accessControlService.getDoctorProfileOrThrow(actor)).thenReturn(doctorProfile);
        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.of(existingSession));
        when(consultationLiveKitService.issueDoctorToken(existingSession, doctorProfile)).thenReturn("doctor-token-2");

        ConsultationSessionCommandService.CreateSessionResult result =
                consultationSessionCommandService.createOrReuseSession(careCase.getPublicId(), actor);

        assertThat(result.created()).isFalse();
        assertThat(result.response().getSessionId()).isEqualTo(existingSession.getPublicId());
        assertThat(result.response().getStatus()).isEqualTo(ConsultationSessionStatus.READY);
        verify(consultationSessionRepository, never()).saveAndFlush(any());
        verify(consultationLiveKitService, never()).createRoom(any());
    }

    @Test
    void createOrReuseSession_deletesRoomWhenDownstreamStepFails() {
        DoctorProfile doctorProfile = buildDoctorProfile("usr_doctor", "doc_doctor");
        CareCase careCase = buildCareCase(doctorProfile);
        AuthenticatedUser actor = new AuthenticatedUser("usr_doctor", Role.DOCTOR);

        when(accessControlService.getDoctorProfileOrThrow(actor)).thenReturn(doctorProfile);
        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.empty());
        when(consultationLiveKitService.getLivekitUrl()).thenReturn("wss://livekit.example.com");
        when(consultationSessionRepository.saveAndFlush(any(ConsultationSession.class))).thenAnswer(invocation -> {
            ConsultationSession session = invocation.getArgument(0);
            setField(session, "createdAt", LocalDateTime.of(2026, 3, 18, 15, 0));
            return session;
        });
        when(consultationLiveKitService.issueDoctorToken(any(ConsultationSession.class), eq(doctorProfile)))
                .thenThrow(new IllegalStateException("token issuance failed"));

        assertThatThrownBy(() -> consultationSessionCommandService.createOrReuseSession(careCase.getPublicId(), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token issuance failed");

        verify(consultationLiveKitService).deleteRoom(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createOrReuseSession_rejectsOtherDoctorsCase() {
        DoctorProfile assignedDoctor = buildDoctorProfile("usr_assigned", "doc_assigned");
        DoctorProfile actorDoctor = buildDoctorProfile("usr_actor", "doc_actor");
        CareCase careCase = buildCareCase(assignedDoctor);
        AuthenticatedUser actor = new AuthenticatedUser("usr_actor", Role.DOCTOR);

        when(accessControlService.getDoctorProfileOrThrow(actor)).thenReturn(actorDoctor);
        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));

        assertThatThrownBy(() -> consultationSessionCommandService.createOrReuseSession(careCase.getPublicId(), actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CASE_NOT_ASSIGNED);
    }

    @Test
    void createOrReuseSession_rejectsTerminalSession() {
        DoctorProfile doctorProfile = buildDoctorProfile("usr_doctor", "doc_doctor");
        CareCase careCase = buildCareCase(doctorProfile);
        AuthenticatedUser actor = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        ConsultationSession existingSession = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room_ses_existing")
                .livekitUrl("wss://livekit.example.com")
                .build();
        existingSession.complete(15);

        when(accessControlService.getDoctorProfileOrThrow(actor)).thenReturn(doctorProfile);
        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.of(existingSession));

        assertThatThrownBy(() -> consultationSessionCommandService.createOrReuseSession(careCase.getPublicId(), actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_STATE_INVALID);
    }

    private CareCase buildCareCase(DoctorProfile doctorProfile) {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON")
                .address("김천시 증산면 장전1길 69")
                .phone("01049163720")
                .build();
        IntakeSession intakeSession = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01049163720")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(intakeSession)
                .slot(null)
                .doctor(doctorProfile)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 18))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(16, 30))
                .build();
        return CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(intakeSession)
                .build();
    }

    private DoctorProfile buildDoctorProfile(String userPublicId, String doctorPublicId) {
        User user = User.builder()
                .username("doctor_" + userPublicId)
                .passwordHash("encoded-password")
                .name("이국종")
                .role(Role.DOCTOR)
                .build();
        setField(user, "publicId", userPublicId);
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(user)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(doctorProfile, "id", Math.abs((long) doctorPublicId.hashCode()));
        setField(doctorProfile, "publicId", doctorPublicId);
        return doctorProfile;
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
