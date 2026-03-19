package com.waddoc.domain.booking.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.dto.BookingDetailResponse;
import com.waddoc.domain.booking.dto.CreateBookingRequest;
import com.waddoc.domain.booking.dto.CreateBookingResponse;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.sms.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private IntakeSessionRepository intakeSessionRepository;

    @Mock
    private ScheduleSlotRepository scheduleSlotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SmsService smsService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_publishesBookingCreatedEvents() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 21))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 18, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));

        CreateBookingResponse response = bookingService.createBooking(session.getPublicId(), request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        assertThat(response.getBookingId()).isNotBlank();
        assertThat(response.getCaseId()).isNotBlank();
        assertThat(response.getTtsMessage()).isNotBlank();
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        BookingCreatedSmsEvent smsEvent = eventCaptor.getAllValues().stream()
                .filter(BookingCreatedSmsEvent.class::isInstance)
                .map(BookingCreatedSmsEvent.class::cast)
                .findFirst()
                .orElseThrow();
        BookingCreatedDoctorNotificationEvent notificationEvent = eventCaptor.getAllValues().stream()
                .filter(BookingCreatedDoctorNotificationEvent.class::isInstance)
                .map(BookingCreatedDoctorNotificationEvent.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(smsEvent.bookingId()).isEqualTo(response.getBookingId());
        assertThat(smsEvent.recipientPhone()).isEqualTo("01012345678");
        assertThat(smsEvent.message()).contains("2026-03-21, 10:00");
        assertThat(smsEvent.message()).contains("Doctor Kim");

        NewBookingNotificationPayload payload = notificationEvent.payload();
        assertThat(notificationEvent.doctorId()).isEqualTo(response.getDoctor().getDoctorId());
        assertThat(payload.getType()).isEqualTo("NEW_BOOKING");
        assertThat(payload.getBookingId()).isEqualTo(response.getBookingId());
        assertThat(payload.getCaseId()).isEqualTo(response.getCaseId());
        assertThat(payload.getDoctorId()).isEqualTo(response.getDoctor().getDoctorId());
        assertThat(payload.getDoctorName()).isEqualTo(response.getDoctor().getName());
        assertThat(payload.getDepartmentName()).isEqualTo(response.getDoctor().getDepartmentName());
        assertThat(payload.getPatientName()).isEqualTo(response.getPatient().getName());
        assertThat(payload.getAppointmentDate()).isEqualTo(response.getAppointmentDate());
        assertThat(payload.getStartTime()).isEqualTo(response.getStartTime());
        assertThat(payload.getLocation()).isEqualTo("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69");
        assertThat(payload.getCreatedAt()).isEqualTo(response.getCreatedAt());
    }

    @Test
    void getBookingDetail_includesCaseIdAndRegionCode() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(session)
                .slot(slot)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctor)
                .intakeSession(session)
                .build();

        when(bookingRepository.findByPublicId(booking.getPublicId())).thenReturn(Optional.of(booking));
        when(careCaseRepository.findByBooking(booking)).thenReturn(Optional.of(careCase));

        BookingDetailResponse response = bookingService.getBookingDetail(booking.getPublicId(), "usr_admin", "ADMIN");

        assertThat(response.getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getIntakeSessionId()).isEqualTo(session.getPublicId());
        assertThat(response.getPatient().getRegionCode()).isEqualTo("ULLEUNG");
    }
}
