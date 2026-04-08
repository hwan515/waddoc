package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionIdentityCheckCacheService;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.MissionTerminalPrincipal;
import com.waddoc.global.security.authorization.AccessActor;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationPatientTokenServiceTest {

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private MissionIdentityCheckCacheService missionIdentityCheckCacheService;

    @Mock
    private ConsultationLiveKitService consultationLiveKitService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConsultationPatientTokenService consultationPatientTokenService;

    @Test
    void issuePatientToken_issuesTokenForMissionTerminalWhenRecentIdentityCheckExists() {
        MissionTerminalPrincipal missionTerminalPrincipal = new MissionTerminalPrincipal(
                "terminal:ms_test123",
                "ms_test123",
                "case_test123",
                List.of(MissionTerminalScopes.ISSUE_PATIENT_TOKEN)
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                missionTerminalPrincipal,
                "terminal-token",
                missionTerminalPrincipal.getAuthorities()
        );
        ConsultationSession session = buildSession("pat_test123");
        Mission mission = buildMission(session);
        mission.updatePhase(MissionPhase.VERIFYING);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        )).thenReturn(new AccessActor("terminal:ms_test123", "MISSION_TERMINAL"));
        when(missionIdentityCheckCacheService.findVerified("ms_test123", "pat_test123"))
                .thenReturn(Optional.of(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                )));
        when(consultationLiveKitService.issuePatientToken(session, session.getCareCase().getPatient())).thenReturn("patient-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        IssuePatientTokenResponse response = consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                authentication
        );

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getPatientToken()).isEqualTo("patient-token");
        assertThat(response.getExpiresIn()).isEqualTo(7200);
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        );
        verify(missionIdentityCheckCacheService).findVerified("ms_test123", "pat_test123");
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void issuePatientToken_rejectsWhenMissionNotReady() {
        Authentication authentication = adminAuthentication();
        ConsultationSession session = buildSession("pat_test123");
        Mission mission = buildMission(session);
        mission.updatePhase(MissionPhase.ARRIVED);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                authentication
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSION_NOT_READY);
    }

    @Test
    void issuePatientToken_rejectsWhenRecentIdentityCheckMissing() {
        Authentication authentication = adminAuthentication();
        ConsultationSession session = buildSession("pat_test123");
        Mission mission = buildMission(session);
        mission.updatePhase(MissionPhase.VERIFYING);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionIdentityCheckCacheService.findVerified("ms_test123", "pat_test123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                authentication
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDENTITY_CHECK_NOT_CONFIRMED);
    }

    @Test
    void issuePatientTokenByMission_resolvesSessionFromMission() {
        Authentication authentication = adminAuthentication();
        ConsultationSession session = buildSession("pat_test123");
        Mission mission = buildMission(session);
        mission.updatePhase(MissionPhase.VERIFYING);

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(consultationSessionRepository.findByCareCase(mission.getCareCase())).thenReturn(Optional.of(session));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionIdentityCheckCacheService.findVerified("ms_test123", "pat_test123"))
                .thenReturn(Optional.of(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                )));
        when(consultationLiveKitService.issuePatientToken(session, session.getCareCase().getPatient())).thenReturn("patient-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        IssuePatientTokenResponse response = consultationPatientTokenService.issuePatientTokenByMission(
                "ms_test123",
                authentication
        );

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getPatientToken()).isEqualTo("patient-token");
        verify(missionRepository).findWithDetailsByPublicId("ms_test123");
        verify(consultationSessionRepository).findByCareCase(mission.getCareCase());
    }

    @Test
    void issuePatientToken_directWebRtcMode_allowsEnRouteMissionWithoutIdentityCheck() {
        setField(consultationPatientTokenService, "directWebrtcEnabled", true);

        Authentication authentication = adminAuthentication();
        ConsultationSession session = buildSession("pat_test123");
        Mission mission = buildMission(session);
        mission.updatePhase(MissionPhase.EN_ROUTE);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionIdentityCheckCacheService.findVerified("ms_test123", "pat_test123")).thenReturn(Optional.empty());
        when(consultationLiveKitService.issuePatientToken(session, session.getCareCase().getPatient())).thenReturn("patient-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        IssuePatientTokenResponse response = consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                authentication
        );

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getPatientToken()).isEqualTo("patient-token");
        assertThat(mission.getPhase()).isEqualTo(MissionPhase.VERIFYING);
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
        verify(missionRepository).save(mission);
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

    private Authentication adminAuthentication() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("usr_admin", Role.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                "access-token",
                authenticatedUser.getAuthorities()
        );
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
