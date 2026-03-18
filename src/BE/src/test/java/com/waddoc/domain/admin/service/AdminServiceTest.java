package com.waddoc.domain.admin.service;

import com.waddoc.domain.admin.dto.AdminBookingListResponse;
import com.waddoc.domain.admin.dto.AdminCaseListResponse;
import com.waddoc.domain.admin.dto.AdminPatientListResponse;
import com.waddoc.domain.admin.dto.GuardianLinkApprovalResponse;
import com.waddoc.domain.admin.dto.GuardianLinkRejectionResponse;
import com.waddoc.domain.auth.service.RefreshTokenService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientGuardianLinkRepository patientGuardianLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getBookingsReturnsCaseAndMissionMetadata() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Booking booking = buildBooking();
        setField(booking, "id", 10L);
        CareCase careCase = buildCareCase(booking);
        setField(careCase, "id", 100L);
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("울릉군")
                .build();
        mission.updatePhase(MissionPhase.DISPATCHED);

        when(bookingRepository.searchAdminBookings(null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 20), 1));
        when(careCaseRepository.findAllByBookingIn(List.of(booking))).thenReturn(List.of(careCase));
        when(missionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(mission));

        AdminBookingListResponse response = adminService.getBookings(admin, null, null, 0, 20);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getBookings()).hasSize(1);
        assertThat(response.getBookings().get(0).getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getBookings().get(0).getMissionPhase()).isEqualTo(MissionPhase.DISPATCHED);
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void getCasesReturnsMissionAndSessionStates() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Booking booking = buildBooking();
        CareCase careCase = buildCareCase(booking);
        setField(careCase, "id", 100L);
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("울릉군")
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);
        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-1")
                .livekitUrl("wss://livekit.test")
                .build();
        session.markReady();

        when(careCaseRepository.searchAdminCases(null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(careCase), PageRequest.of(0, 20), 1));
        when(missionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(mission));
        when(consultationSessionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(session));

        AdminCaseListResponse response = adminService.getCases(admin, null, null, 0, 20);

        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getMissionPhase()).isEqualTo(MissionPhase.VERIFYING);
        assertThat(response.getCases().get(0).getSessionStatus()).isEqualTo(ConsultationSessionStatus.READY);
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void getPatientsWithoutFiltersReturnsPagedPatients() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Patient patient = Patient.builder()
                .name("Hong")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung")
                .phone("01012345678")
                .build();

        when(patientRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(patient), PageRequest.of(0, 20), 1));

        AdminPatientListResponse response = adminService.getPatients(admin, null, null, 0, 20);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getPatients()).hasSize(1);
        assertThat(response.getPatients().get(0).getName()).isEqualTo("Hong");
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void approveGuardianLinkRequestApprovesLinkAndGuardianUser() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        User adminUser = buildUser("admin", "관리자", Role.ADMIN);
        setField(adminUser, "publicId", "usr_admin");
        User guardianUser = buildUser("guardian", "이보호자", Role.GUARDIAN);
        PatientGuardianLink link = buildGuardianLink(guardianUser);

        when(userRepository.findByPublicId("usr_admin")).thenReturn(Optional.of(adminUser));
        when(patientGuardianLinkRepository.findDetailedByPublicId(link.getPublicId())).thenReturn(Optional.of(link));

        GuardianLinkApprovalResponse response = adminService.approveGuardianLinkRequest(admin, link.getPublicId());

        assertThat(response.getStatus()).isEqualTo(GuardianLinkStatus.APPROVED);
        assertThat(response.getApprovedByUserId()).isEqualTo("usr_admin");
        assertThat(link.getGuardianUser().isApproved()).isTrue();
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void rejectGuardianLinkRequestRejectsAccountAndRevokesTokensWhenNoApprovedLinksRemain() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        User adminUser = buildUser("admin", "관리자", Role.ADMIN);
        setField(adminUser, "publicId", "usr_admin");
        User guardianUser = buildUser("guardian", "이보호자", Role.GUARDIAN);
        setField(guardianUser, "publicId", "usr_guardian");
        setField(guardianUser, "id", 2L);
        PatientGuardianLink link = buildGuardianLink(guardianUser);

        when(userRepository.findByPublicId("usr_admin")).thenReturn(Optional.of(adminUser));
        when(patientGuardianLinkRepository.findDetailedByPublicId(link.getPublicId())).thenReturn(Optional.of(link));
        when(patientGuardianLinkRepository.existsByGuardianUserIdAndStatus(2L, GuardianLinkStatus.APPROVED))
                .thenReturn(false);

        GuardianLinkRejectionResponse response = adminService.rejectGuardianLinkRequest(admin, link.getPublicId());

        assertThat(response.getStatus()).isEqualTo(GuardianLinkStatus.REJECTED);
        assertThat(link.getGuardianUser().isApproved()).isFalse();
        verify(refreshTokenService).deleteAllByUserId("usr_guardian");
    }

    private Booking buildBooking() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();
        User doctorUser = buildUser("doctor_kim", "김의사", Role.DOCTOR);
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        return Booking.builder()
                .patient(patient)
                .intakeSession(null)
                .slot(null)
                .doctor(doctorProfile)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
    }

    private CareCase buildCareCase(Booking booking) {
        return CareCase.builder()
                .booking(booking)
                .patient(booking.getPatient())
                .doctor(booking.getDoctor())
                .intakeSession(null)
                .build();
    }

    private PatientGuardianLink buildGuardianLink(User guardianUser) {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01098765432")
                .build();
        return PatientGuardianLink.builder()
                .patient(patient)
                .guardianUser(guardianUser)
                .relation("자녀")
                .build();
    }

    private User buildUser(String username, String name, Role role) {
        return User.builder()
                .username(username)
                .passwordHash("encoded-password")
                .name(name)
                .role(role)
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
