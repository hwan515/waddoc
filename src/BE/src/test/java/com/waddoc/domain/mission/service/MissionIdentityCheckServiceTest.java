package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.domain.consultation.service.ConsultationIdentityVerificationClient;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.dto.MissionIdentityCheckResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionIdentityCheckServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private ConsultationIdentityVerificationClient consultationIdentityVerificationClient;

    @Mock
    private MissionIdentityCheckCacheService missionIdentityCheckCacheService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private MissionIdentityCheckService missionIdentityCheckService;

    @TempDir
    Path tempDir;

    @Test
    void verify_succeedsAndCachesRecentIdentityCheck() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.ARRIVED);
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(missionIdentityCheckService, "fileStorageRoot", tempDir.toString());

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), any(), eq("reference.jpg"), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(successResult());
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        );

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.VERIFYING);
        assertThat(response.getMissionId()).isEqualTo("ms_test123");
        assertThat(response.getPatientId()).isEqualTo("pat_test123");
        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getNextStep()).isEqualTo("VITALS");
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        verify(missionRepository).save(mission);
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        );
    }

    @Test
    void verify_succeedsWhenRrnFrontAndNormalizedAddressMatchPatient() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.ARRIVED);
        setField(mission.getCareCase().getPatient(), "address", "경북 김천시 증산면 장전1길 69");
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(missionIdentityCheckService, "fileStorageRoot", tempDir.toString());

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), any(), eq("reference.jpg"), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(IdentityVerificationResult.builder()
                        .matched(true)
                        .faceSimilarityScore(0.94)
                        .idCardFaceSimilarityScore(0.91)
                        .reasonCodes(List.of())
                        .ocr(IdentityVerificationResult.OcrData.builder()
                                .name("홍길동")
                                .rrnMasked("580315-1******")
                                .birthDate6(null)
                                .address("경상북도 김천시 증산면 장전1길 69")
                                .build())
                        .build());
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        );

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
    }

    @Test
    void verify_succeedsWhenOnlyOneOcrFieldMatchesPatient() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.ARRIVED);
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(missionIdentityCheckService, "fileStorageRoot", tempDir.toString());

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), any(), eq("reference.jpg"), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(IdentityVerificationResult.builder()
                        .matched(true)
                        .faceSimilarityScore(0.94)
                        .idCardFaceSimilarityScore(0.91)
                        .reasonCodes(List.of())
                        .ocr(IdentityVerificationResult.OcrData.builder()
                                .name("홍길동")
                                .rrnMasked("700101-1******")
                                .birthDate6(null)
                                .address("서울특별시 마포구 성암로 330")
                                .build())
                        .build());
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        );

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
    }

    @Test
    void verify_rejectsWhenMissionNotReady() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.DISPATCHED);

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{2}),
                authentication
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSION_NOT_READY);
    }

    @Test
    void verify_succeedsWhenReferenceImageMissing() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "");
        mission.updatePhase(MissionPhase.VERIFYING);
        setField(missionIdentityCheckService, "fileStorageRoot", tempDir.toString());

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(consultationIdentityVerificationClient.verify(eq("pat_test123"), isNull(), isNull(), any(), eq("face.jpg"), any(), eq("id-card.jpg")))
                .thenReturn(successResultWithoutReferenceImage());
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{2}),
                authentication
        );

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
    }

    @Test
    void verify_rejectsWhenIdentityCheckFails() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.VERIFYING);
        Files.createDirectories(tempDir.resolve("patients/pat_test123"));
        Files.write(tempDir.resolve("patients/pat_test123/reference.jpg"), new byte[]{10, 20, 30});
        setField(missionIdentityCheckService, "fileStorageRoot", tempDir.toString());

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
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

        assertThatThrownBy(() -> missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDENTITY_CHECK_FAILED);
    }

    @Test
    void verify_bypassesWhenFlagEnabled() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Authentication authentication = adminAuthentication(admin);
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.ARRIVED);
        setField(missionIdentityCheckService, "identityCheckBypassEnabled", true);

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("usr_admin", "ADMIN"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        );

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.VERIFYING);
        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getIdentityCheck().isMatched()).isTrue();
        assertThat(response.getIdentityCheck().getReasonCodes()).containsExactly("BYPASSED");
        verify(missionRepository).save(mission);
        verify(missionIdentityCheckCacheService).saveVerified("ms_test123", "pat_test123");
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        );
        verifyNoInteractions(consultationIdentityVerificationClient);
    }

    @Test
    void verify_acceptsMissionTerminalAuthentication() {
        Mission mission = buildMission("pat_test123", "patients/pat_test123/reference.jpg");
        mission.updatePhase(MissionPhase.ARRIVED);
        setField(missionIdentityCheckService, "identityCheckBypassEnabled", true);

        MissionTerminalPrincipal terminalPrincipal = new MissionTerminalPrincipal(
                "terminal:ms_test123",
                "ms_test123",
                "case_test123",
                List.of(MissionTerminalScopes.IDENTITY_CHECK)
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                terminalPrincipal,
                "terminal-token",
                terminalPrincipal.getAuthorities()
        );

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        )).thenReturn(new AccessActor("terminal:ms_test123", "MISSION_TERMINAL"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(missionIdentityCheckCacheService.saveVerified("ms_test123", "pat_test123"))
                .thenReturn(new MissionIdentityCheckCacheService.VerifiedIdentityCheck(
                        OffsetDateTime.parse("2026-03-18T10:05:00+09:00"),
                        600
                ));

        MissionIdentityCheckResponse response = missionIdentityCheckService.verify(
                "ms_test123",
                new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("idCardImage", "id-card.jpg", "image/jpeg", new byte[]{4, 5, 6}),
                authentication
        );

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.IDENTITY_CHECK
        );
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

    private IdentityVerificationResult successResultWithoutReferenceImage() {
        return IdentityVerificationResult.builder()
                .matched(true)
                .faceSimilarityScore(null)
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

    private Mission buildMission(String patientPublicId, String referenceImagePath) {
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

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination(patient.getAddress())
                .build();
        setField(mission, "publicId", "ms_test123");
        return mission;
    }

    private Authentication adminAuthentication(AuthenticatedUser authenticatedUser) {
        return new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                "access-token",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
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
