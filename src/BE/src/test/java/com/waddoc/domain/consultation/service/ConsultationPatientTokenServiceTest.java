package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ConsultationIdentityVerificationClient consultationIdentityVerificationClient;

    @Mock
    private ConsultationLiveKitService consultationLiveKitService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConsultationPatientTokenService consultationPatientTokenService;

    @TempDir
    Path tempDir;

    @Test
    void issuePatientToken_issuesTokenWhenIdentityCheckSucceeds() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123", "patients/pat_test123/reference.jpg");
        Mission mission = Mission.builder()
                .careCase(session.getCareCase())
                .vehicleId("VEH-01")
                .destination(session.getCareCase().getPatient().getAddress())
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(consultationPatientTokenService, "fileStorageRoot", tempDir.toString());

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), any(), eq("reference.jpg"), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(successResult());
        when(consultationLiveKitService.issuePatientToken(session, session.getCareCase().getPatient())).thenReturn("patient-token");
        when(consultationLiveKitService.getParticipantTokenExpiresInSeconds()).thenReturn(7200);

        IssuePatientTokenResponse response = consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                "pat_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                admin
        );

        assertThat(response.getSessionId()).isEqualTo("ses_test123");
        assertThat(response.getPatientToken()).isEqualTo("patient-token");
        assertThat(response.getExpiresIn()).isEqualTo(7200);
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        verify(accessControlService).assertAdmin(admin);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void issuePatientToken_rejectsPatientMismatch() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_session", "patients/pat_session/reference.jpg");

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                "pat_other",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{2}),
                admin
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PATIENT_MISMATCH);
    }

    @Test
    void issuePatientToken_rejectsWhenMissionNotReady() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123", "patients/pat_test123/reference.jpg");
        Mission mission = Mission.builder()
                .careCase(session.getCareCase())
                .vehicleId("VEH-01")
                .destination(session.getCareCase().getPatient().getAddress())
                .build();
        mission.updatePhase(MissionPhase.ARRIVED);

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                "pat_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{2}),
                admin
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSION_NOT_READY);
    }

    @Test
    void issuePatientToken_rejectsWhenReferenceImageMissing() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123", "");
        Mission mission = Mission.builder()
                .careCase(session.getCareCase())
                .vehicleId("VEH-01")
                .destination(session.getCareCase().getPatient().getAddress())
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);
        setField(consultationPatientTokenService, "fileStorageRoot", tempDir.toString());

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                "pat_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{2}),
                admin
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFERENCE_IMAGE_MISSING);
    }

    @Test
    void issuePatientToken_rejectsWhenIdentityCheckFails() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        ConsultationSession session = buildSession("pat_test123", "patients/pat_test123/reference.jpg");
        Mission mission = Mission.builder()
                .careCase(session.getCareCase())
                .vehicleId("VEH-01")
                .destination(session.getCareCase().getPatient().getAddress())
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(consultationPatientTokenService, "fileStorageRoot", tempDir.toString());

        when(consultationSessionRepository.findWithParticipantsByPublicId("ses_test123")).thenReturn(Optional.of(session));
        when(missionRepository.findByCareCase(session.getCareCase())).thenReturn(Optional.of(mission));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), any(), eq("reference.jpg"), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(IdentityVerificationResult.builder()
                        .matched(false)
                        .faceSimilarityScore(0.42)
                        .idCardFaceSimilarityScore(0.38)
                        .reasonCodes(List.of("LOW_SIMILARITY"))
                        .ocr(IdentityVerificationResult.OcrData.builder()
                                .name("홍길동")
                                .rrnMasked("580315-1******")
                                .birthDate6("580315")
                                .address("김천시 증산면 장전1길 69")
                                .build())
                        .build());

        assertThatThrownBy(() -> consultationPatientTokenService.issuePatientToken(
                "ses_test123",
                "pat_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                admin
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDENTITY_CHECK_FAILED);
    }

    private IdentityVerificationResult successResult() {
        return IdentityVerificationResult.builder()
                .matched(true)
                .faceSimilarityScore(0.94)
                .idCardFaceSimilarityScore(0.91)
                .reasonCodes(List.of())
                .ocr(IdentityVerificationResult.OcrData.builder()
                        .name("홍길동")
                        .rrnMasked("580315-1******")
                        .birthDate6("580315")
                        .address("김천시증산면장전1길69")
                        .build())
                .build();
    }

    private ConsultationSession buildSession(String patientPublicId, String referenceImagePath) {
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
        setField(patient, "referenceImagePath", referenceImagePath);

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
