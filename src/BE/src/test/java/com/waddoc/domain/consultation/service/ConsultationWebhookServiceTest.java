package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.entity.ConnectionState;
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
import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationWebhookServiceTest {

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DisconnectTimerService disconnectTimerService;

    @Mock
    private WebhookReceiver webhookReceiver;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ConsultationWebhookService consultationWebhookService;

    @Test
    void handleWebhook_marksDoctorAndPatientConnectedAndStartsSession() {
        ConsultationSession session = buildSession();
        when(consultationSessionRepository.findWithParticipantsByRoomId("room_ses_test123")).thenReturn(Optional.of(session));
        stubIdempotencyCheck();

        String doctorJoinedBody = participantEventBody("participant_joined", "room_ses_test123", "doc_usr_doctor");
        String patientJoinedBody = participantEventBody("participant_joined", "room_ses_test123", "patient:pat_test123");
        when(webhookReceiver.receive(doctorJoinedBody, "signed-header"))
                .thenReturn(buildParticipantEvent("participant_joined", "room_ses_test123", "doc_usr_doctor"));
        when(webhookReceiver.receive(patientJoinedBody, "signed-header"))
                .thenReturn(buildParticipantEvent("participant_joined", "room_ses_test123", "patient:pat_test123"));

        consultationWebhookService.handleWebhook(
                doctorJoinedBody,
                "signed-header"
        );
        consultationWebhookService.handleWebhook(
                patientJoinedBody,
                "signed-header"
        );

        assertThat(session.getDoctorConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(session.getPatientConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(session.getStatus()).isEqualTo(ConsultationSessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isNotNull();
    }

    @Test
    void handleWebhook_marksParticipantDisconnectedAndSchedulesRedisTimer() {
        ConsultationSession session = buildSession();
        session.connectDoctor();

        when(consultationSessionRepository.findWithParticipantsByRoomId("room_ses_test123")).thenReturn(Optional.of(session));
        stubIdempotencyCheck();

        String body = participantEventBody("participant_left", "room_ses_test123", "doctor:doc_usr_doctor");
        when(webhookReceiver.receive(body, "signed-header"))
                .thenReturn(buildParticipantEvent("participant_left", "room_ses_test123", "doctor:doc_usr_doctor"));

        consultationWebhookService.handleWebhook(body, "signed-header");

        assertThat(session.getDoctorConnectionState()).isEqualTo(ConnectionState.DISCONNECTED);
        verify(disconnectTimerService).schedule("ses_test123", "DOCTOR");
    }

    @Test
    void handleWebhook_completesSessionWhenRoomFinished() {
        ConsultationSession session = buildSession();
        session.connectDoctor();
        session.connectPatient();

        when(consultationSessionRepository.findWithParticipantsByRoomId("room_ses_test123")).thenReturn(Optional.of(session));
        stubIdempotencyCheck();

        String body = """
                {
                  "event": "room_finished",
                  "room": {
                    "name": "room_ses_test123"
                  }
                }
                """;
        when(webhookReceiver.receive(body, "signed-header"))
                .thenReturn(buildRoomFinishedEvent("room_ses_test123"));

        consultationWebhookService.handleWebhook(body, "signed-header");

        assertThat(session.getStatus()).isEqualTo(ConsultationSessionStatus.COMPLETED);
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(session.getDurationMinutes()).isNotNull();
        verify(disconnectTimerService).cancel("ses_test123", "DOCTOR");
        verify(disconnectTimerService).cancel("ses_test123", "PATIENT");
        verify(disconnectTimerService, never()).schedule(anyString(), anyString());
    }

    @Test
    void handleWebhook_keepsReadySessionWhenRoomFinishedBeforeConsultationStarts() {
        ConsultationSession session = buildSession();
        session.connectDoctor();

        when(consultationSessionRepository.findWithParticipantsByRoomId("room_ses_test123")).thenReturn(Optional.of(session));
        stubIdempotencyCheck();

        String body = """
                {
                  "event": "room_finished",
                  "room": {
                    "name": "room_ses_test123"
                  }
                }
                """;
        when(webhookReceiver.receive(body, "signed-header"))
                .thenReturn(buildRoomFinishedEvent("room_ses_test123"));

        consultationWebhookService.handleWebhook(body, "signed-header");

        assertThat(session.getStatus()).isEqualTo(ConsultationSessionStatus.READY);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getDurationMinutes()).isNull();
        verify(disconnectTimerService).cancel("ses_test123", "DOCTOR");
        verify(disconnectTimerService).cancel("ses_test123", "PATIENT");
        verify(disconnectTimerService, never()).schedule(anyString(), anyString());
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        when(webhookReceiver.receive("{}", "invalid-token")).thenThrow(new IllegalArgumentException("invalid signature"));

        assertThatThrownBy(() -> consultationWebhookService.handleWebhook("{}", "invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LIVEKIT_WEBHOOK_INVALID_SIGNATURE);
    }

    private void stubIdempotencyCheck() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    }

    private ConsultationSession buildSession() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(doctorUser, "publicId", "usr_doctor");

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(doctor, "publicId", "doc_usr_doctor");

        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();
        setField(patient, "publicId", "pat_test123");

        IntakeSession intakeSession = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(intakeSession)
                .slot(null)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
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

    private String participantEventBody(String event, String roomName, String identity) {
        return """
                {
                  "event": "%s",
                  "room": {
                    "name": "%s"
                  },
                  "participant": {
                    "identity": "%s"
                  }
                }
                """.formatted(event, roomName, identity);
    }

    private LivekitWebhook.WebhookEvent buildParticipantEvent(String eventName, String roomName, String identity) {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setId("evt_" + eventName + "_" + identity.hashCode())
                .setEvent(eventName)
                .setRoom(LivekitModels.Room.newBuilder().setName(roomName).build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity(identity).build())
                .build();
    }

    private LivekitWebhook.WebhookEvent buildRoomFinishedEvent(String roomName) {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setId("evt_room_finished_" + roomName.hashCode())
                .setEvent("room_finished")
                .setRoom(LivekitModels.Room.newBuilder().setName(roomName).build())
                .build();
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
