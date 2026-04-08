package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.dto.RecommendRequest;
import com.waddoc.domain.intake.dto.RecommendResponse;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.util.KstTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private IntakeSessionRepository intakeSessionRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private ScheduleSlotRepository scheduleSlotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AuditLogService auditLogService;

    private RecommendationService recommendationService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-01T03:00:00Z"), KstTime.ZONE);

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                intakeSessionRepository,
                doctorProfileRepository,
                scheduleSlotRepository,
                bookingRepository,
                auditLogService,
                clock
        );
    }

    @Test
    void recommend_prioritizesLastDoctorWithinSelectedDepartment() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        User preferredUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        User fallbackUser = User.builder()
                .username("doctor_park")
                .passwordHash("encoded")
                .name("박의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile preferredDoctor = DoctorProfile.builder()
                .user(preferredUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")

                .build();

        DoctorProfile fallbackDoctor = DoctorProfile.builder()
                .user(fallbackUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")

                .build();

        ScheduleSlot fallbackSlot = ScheduleSlot.builder()
                .doctor(fallbackDoctor)
                .slotDate(today().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        ScheduleSlot preferredSlot = ScheduleSlot.builder()
                .doctor(preferredDoctor)
                .slotDate(today().plusDays(1))
                .startTime(LocalTime.of(10, 30))
                .endTime(LocalTime.of(11, 0))
                .build();

        Booking lastBooking = Booking.builder()
                .patient(patient)
                .slot(preferredSlot)
                .doctor(preferredDoctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(today().minusDays(30))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        ReflectionTestUtils.setField(lastBooking, "status", BookingStatus.COMPLETED);

        RecommendRequest request = new RecommendRequest();
        ReflectionTestUtils.setField(request, "departmentCode", "INTERNAL_MEDICINE");

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(doctorProfileRepository.findByDepartment("INTERNAL_MEDICINE"))
                .thenReturn(List.of(preferredDoctor, fallbackDoctor));
        when(scheduleSlotRepository.findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                List.of(preferredDoctor, fallbackDoctor), today()))
                .thenReturn(List.of(fallbackSlot, preferredSlot));
        when(bookingRepository.findRecentPastDepartmentBookings(
                eq(patient),
                eq("INTERNAL_MEDICINE"),
                eq(BookingStatus.CANCELLED),
                any(LocalDate.class),
                any(LocalTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(lastBooking));
        when(bookingRepository.findActiveRegionBookingsFromDate("ULLEUNG", today(), BookingStatus.CANCELLED))
                .thenReturn(List.of());

        RecommendResponse response = recommendationService.recommend(session.getPublicId(), request);

        assertThat(response.getDepartment()).isEqualTo("INTERNAL_MEDICINE");
        assertThat(response.getDepartmentName()).isEqualTo("내과");
        assertThat(response.getSymptomCategory()).isEqualTo("DTMF_SELECTION");
        assertThat(response.getAvailableSlots()).hasSize(2);
        assertThat(response.getAvailableSlots().get(0).getDoctorName()).isEqualTo("김의사");
        assertThat(response.getTtsMessage()).contains("오전 10시 30분");
        assertThat(response.getTtsMessage()).contains("예약은 1번");

        // 세션에 선택 결과가 저장되었는지 확인
        assertThat(session.hasRecommendation()).isTrue();
        assertThat(session.getSelectedDepartment()).isEqualTo("INTERNAL_MEDICINE");
        assertThat(session.getOfferedSlotIds()).hasSize(2);
    }

    @Test
    void isSlotBookable_allowsOnlyFutureTimeTodayOrFutureDate() {
        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        ScheduleSlot startedTodaySlot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 31))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();
        ScheduleSlot futureTodaySlot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 31))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(15, 30))
                .build();
        ScheduleSlot tomorrowSlot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 4, 1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        assertThat(RecommendationService.isSlotBookable(
                startedTodaySlot,
                LocalDate.of(2026, 3, 31),
                LocalTime.of(10, 0)
        )).isFalse();
        assertThat(RecommendationService.isSlotBookable(
                futureTodaySlot,
                LocalDate.of(2026, 3, 31),
                LocalTime.of(10, 0)
        )).isTrue();
        assertThat(RecommendationService.isSlotBookable(
                tomorrowSlot,
                LocalDate.of(2026, 3, 31),
                LocalTime.of(10, 0)
        )).isTrue();
    }

    @Test
    void recommend_excludesStartedTodaySlotsFromAvailableSlots() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        ScheduleSlot startedTodaySlot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(today())
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(0, 30))
                .build();
        ScheduleSlot tomorrowSlot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(today().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        RecommendRequest request = new RecommendRequest();
        ReflectionTestUtils.setField(request, "departmentCode", "INTERNAL_MEDICINE");

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(doctorProfileRepository.findByDepartment("INTERNAL_MEDICINE")).thenReturn(List.of(doctor));
        when(scheduleSlotRepository.findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                List.of(doctor), today()))
                .thenReturn(List.of(startedTodaySlot, tomorrowSlot));
        when(bookingRepository.findRecentPastDepartmentBookings(
                eq(patient),
                eq("INTERNAL_MEDICINE"),
                eq(BookingStatus.CANCELLED),
                any(LocalDate.class),
                any(LocalTime.class),
                any(Pageable.class)))
                .thenReturn(List.of());
        when(bookingRepository.findActiveRegionBookingsFromDate("ULLEUNG", today(), BookingStatus.CANCELLED))
                .thenReturn(List.of());

        RecommendResponse response = recommendationService.recommend(session.getPublicId(), request);

        assertThat(response.getAvailableSlots()).hasSize(1);
        assertThat(response.getAvailableSlots().get(0).getSlotId()).isEqualTo(tomorrowSlot.getPublicId());
        assertThat(session.getOfferedSlotIds()).containsExactly(tomorrowSlot.getPublicId());
    }

    @Test
    void recommend_filtersConflictingRegionSlotsAndKeepsFilteredOfferedSlotIds() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        User preferredUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        User fallbackUser = User.builder()
                .username("doctor_park")
                .passwordHash("encoded")
                .name("박의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile preferredDoctor = DoctorProfile.builder()
                .user(preferredUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        DoctorProfile fallbackDoctor = DoctorProfile.builder()
                .user(fallbackUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        LocalDate targetDate = today().plusDays(1);
        ScheduleSlot conflictingPreferredSlot = ScheduleSlot.builder()
                .doctor(preferredDoctor)
                .slotDate(targetDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();
        ScheduleSlot sameTimeFallbackSlot = ScheduleSlot.builder()
                .doctor(fallbackDoctor)
                .slotDate(targetDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();
        ScheduleSlot remainingSlot = ScheduleSlot.builder()
                .doctor(fallbackDoctor)
                .slotDate(targetDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Booking conflictingBooking = Booking.builder()
                .patient(patient)
                .slot(conflictingPreferredSlot)
                .doctor(preferredDoctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(targetDate)
                .regionCode("ULLEUNG")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        RecommendRequest request = new RecommendRequest();
        ReflectionTestUtils.setField(request, "departmentCode", "INTERNAL_MEDICINE");

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(doctorProfileRepository.findByDepartment("INTERNAL_MEDICINE"))
                .thenReturn(List.of(preferredDoctor, fallbackDoctor));
        when(scheduleSlotRepository.findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                List.of(preferredDoctor, fallbackDoctor), today()))
                .thenReturn(List.of(conflictingPreferredSlot, sameTimeFallbackSlot, remainingSlot));
        when(bookingRepository.findRecentPastDepartmentBookings(
                eq(patient),
                eq("INTERNAL_MEDICINE"),
                eq(BookingStatus.CANCELLED),
                any(LocalDate.class),
                any(LocalTime.class),
                any(Pageable.class)))
                .thenReturn(List.of());
        when(bookingRepository.findActiveRegionBookingsFromDate("ULLEUNG", today(), BookingStatus.CANCELLED))
                .thenReturn(List.of(conflictingBooking));

        RecommendResponse response = recommendationService.recommend(session.getPublicId(), request);

        assertThat(response.getAvailableSlots()).hasSize(1);
        assertThat(response.getAvailableSlots().get(0).getSlotId()).isEqualTo(remainingSlot.getPublicId());
        assertThat(session.getOfferedSlotIds()).containsExactly(remainingSlot.getPublicId());
    }

    @Test
    void recommend_ignoresCancelledBookingWhenFilteringRegionCapacity() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        LocalDate targetDate = today().plusDays(1);
        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(targetDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        Booking cancelledBooking = Booking.builder()
                .patient(patient)
                .slot(slot)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(targetDate)
                .regionCode("ULLEUNG")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();
        ReflectionTestUtils.setField(cancelledBooking, "status", BookingStatus.CANCELLED);

        RecommendRequest request = new RecommendRequest();
        ReflectionTestUtils.setField(request, "departmentCode", "INTERNAL_MEDICINE");

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(doctorProfileRepository.findByDepartment("INTERNAL_MEDICINE")).thenReturn(List.of(doctor));
        when(scheduleSlotRepository.findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                List.of(doctor), today()))
                .thenReturn(List.of(slot));
        when(bookingRepository.findRecentPastDepartmentBookings(
                eq(patient),
                eq("INTERNAL_MEDICINE"),
                eq(BookingStatus.CANCELLED),
                any(LocalDate.class),
                any(LocalTime.class),
                any(Pageable.class)))
                .thenReturn(List.of());
        when(bookingRepository.findActiveRegionBookingsFromDate("ULLEUNG", today(), BookingStatus.CANCELLED))
                .thenReturn(List.of(cancelledBooking));

        RecommendResponse response = recommendationService.recommend(session.getPublicId(), request);

        assertThat(response.getAvailableSlots()).hasSize(1);
        assertThat(response.getAvailableSlots().get(0).getSlotId()).isEqualTo(slot.getPublicId());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
