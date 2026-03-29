package com.waddoc.global.seed;

import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.type.ApprovalStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDummyDataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private ScheduleSlotRepository scheduleSlotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private PatientGuardianLinkRepository patientGuardianLinkRepository;

    @Mock
    private IntakeSessionRepository intakeSessionRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EntityManager entityManager;

    private LocalDummyDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new LocalDummyDataSeeder(
                userRepository,
                patientRepository,
                doctorProfileRepository,
                scheduleSlotRepository,
                bookingRepository,
                careCaseRepository,
                patientGuardianLinkRepository,
                intakeSessionRepository,
                missionRepository,
                consultationSessionRepository,
                dispatchOutboxRepository,
                vehicleRepository,
                passwordEncoder,
                entityManager
        );
        ReflectionTestUtils.setField(seeder, "defaultPassword", "Passw0rd!");
    }

    @Test
    void ensureUserSyncsExistingSeedUserPasswordFromEnvWhenHashDoesNotMatch() {
        User existingUser = User.builder()
                .username("seed_prod_admin")
                .passwordHash("legacy-hash")
                .name("운영 더미 관리자")
                .role(Role.ADMIN)
                .build();

        when(userRepository.findByUsername("seed_prod_admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Passw0rd!", "legacy-hash")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-default");

        User result = ReflectionTestUtils.invokeMethod(
                seeder,
                "ensureUser",
                "seed_prod_admin",
                "운영 더미 관리자",
                Role.ADMIN,
                null,
                ApprovalStatus.APPROVED
        );

        assertThat(result.getPasswordHash()).isEqualTo("encoded-default");
        verify(passwordEncoder).encode("Passw0rd!");
        verify(entityManager).flush();
    }

    @Test
    void ensureUserKeepsExistingSeedUserPasswordWhenItAlreadyMatchesEnvPassword() {
        User existingUser = User.builder()
                .username("seed_prod_admin")
                .passwordHash("matching-hash")
                .name("운영 더미 관리자")
                .role(Role.ADMIN)
                .build();

        when(userRepository.findByUsername("seed_prod_admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Passw0rd!", "matching-hash")).thenReturn(true);

        User result = ReflectionTestUtils.invokeMethod(
                seeder,
                "ensureUser",
                "seed_prod_admin",
                "운영 더미 관리자",
                Role.ADMIN,
                null,
                ApprovalStatus.APPROVED
        );

        assertThat(result.getPasswordHash()).isEqualTo("matching-hash");
        verify(passwordEncoder, never()).encode("Passw0rd!");
        verify(entityManager, never()).flush();
    }

    @Test
    void assertSeedDefaultPasswordConfiguredRejectsBlankPassword() {
        ReflectionTestUtils.setField(seeder, "defaultPassword", " ");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(seeder, "assertSeedDefaultPasswordConfigured"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_SEED_DEFAULT_PASSWORD must be set when app.seed.enabled=true");
    }

    @Test
    void resolveUpcomingActiveBookingEndDateKeepsUpcomingSeedToSameDay() {
        LocalDate today = LocalDate.of(2026, 3, 27);

        LocalDate result = LocalDummyDataSeeder.resolveUpcomingActiveBookingEndDate(today);

        assertThat(result).isEqualTo(today);
    }

    @Test
    void resolveUpcomingActiveBookingEndDateDoesNotExceedFutureSlotEndDate() {
        LocalDate today = LocalDate.of(2026, 4, 13);

        LocalDate result = LocalDummyDataSeeder.resolveUpcomingActiveBookingEndDate(today);

        assertThat(result).isEqualTo(today);
    }
}
