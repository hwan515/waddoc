package com.waddoc.domain.consultation.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.dto.ConsultationSessionStatusResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConnectionState;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.MissionTerminalPrincipal;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSessionQueryServiceTest {

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private ConsultationSessionQueryService consultationSessionQueryService;

    @Test
    void getSessionStatus_returnsSessionStatusForAssignedDoctor() {
        AuthenticatedUser doctorUser = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        ConsultationSession session = buildSession();

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));

        ConsultationSessionStatusResponse response = consultationSessionQueryService.getSessionStatus("ses_test123", doctorUser);

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getCaseId()).isEqualTo("case_test123");
        assertThat(response.getDoctor().getConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(response.getPatient().getConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(response.getReconnectCount()).isZero();
        verify(accessControlService).assertAssignedDoctorOrAdmin(doctorUser, session.getCareCase());
    }

    @Test
    void getSessionStatus_throwsWhenSessionMissing() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationSessionQueryService.getSessionStatus("ses_missing", admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void getSessionStatusByMission_returnsSessionStatusForMissionTerminal() {
        ConsultationSession session = buildSession();
        Mission mission = buildMission(session);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new MissionTerminalPrincipal(
                        "mission-terminal:ms_test123",
                        "ms_test123",
                        "case_test123",
                        java.util.List.of(MissionTerminalScopes.SESSION_STATUS_READ)
                ),
                null
        );

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(consultationSessionRepository.findWithParticipantsByCareCase(session.getCareCase())).thenReturn(Optional.of(session));

        ConsultationSessionStatusResponse response = consultationSessionQueryService.getSessionStatusByMission(
                "ms_test123",
                authentication
        );

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getStatus().name()).isEqualTo("IN_PROGRESS");
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.SESSION_STATUS_READ
        );
    }

    private ConsultationSession buildSession() {
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
        setField(patient, "publicId", "pat_test123");

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
        setField(careCase, "publicId", "case_test123");

        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room_ses_test123")
                .livekitUrl("wss://livekit.test")
                .build();
        setField(session, "publicId", "ses_test123");
        setField(session, "status", com.waddoc.domain.consultation.entity.ConsultationSessionStatus.IN_PROGRESS);
        setField(session, "doctorConnectionState", ConnectionState.CONNECTED);
        setField(session, "patientConnectionState", ConnectionState.CONNECTED);
        setField(session, "doctorJoinedAt", LocalDateTime.of(2026, 3, 18, 10, 0, 30));
        setField(session, "patientJoinedAt", LocalDateTime.of(2026, 3, 18, 10, 1, 0));
        setField(session, "startedAt", LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        return session;
    }

    private Mission buildMission(ConsultationSession session) {
        Mission mission = Mission.builder()
                .careCase(session.getCareCase())
                .vehicleId("VEH-01")
                .destination(session.getCareCase().getPatient().getAddress())
                .build();
        setField(mission, "publicId", "ms_test123");
        return mission;
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
