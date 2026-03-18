package com.waddoc.domain.booking.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.dto.CreateBookingRequest;
import com.waddoc.domain.booking.dto.CreateBookingResponse;
import com.waddoc.domain.booking.dto.BookingDetailResponse;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_smsFailureDoesNotBreakBookingCreation() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 21))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "내과",
                ConfidenceLevel.HIGH,
                false,
                "기침",
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
        String smsMessage = """
                예약 확인
                [왔닥]
                안녕하세요, 홍길동님.
                진료 예약이 아래와 같이 확정되었습니다.

                일시: 2026-03-21, 10:00
                의사: 김의사 (내과)
                ※ 유의사항

                예약 시간 10분 전까지 준비 부탁드립니다.
                변경이나 취소를 원하실 경우 최소 하루 전까지 연락 주시기 바랍니다.

                ☎ 문의: 01049163720""";
        doThrow(new IllegalStateException("SMS gateway down"))
                .when(smsService).send(eq("01012345678"), anyString());

        CreateBookingResponse response = bookingService.createBooking(session.getPublicId(), request);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        assertThat(response.getBookingId()).isNotBlank();
        assertThat(response.getCaseId()).isNotBlank();
        assertThat(response.getTtsMessage()).contains("예약이 완료되었습니다.");
        verify(smsService).send(eq("01012345678"), messageCaptor.capture());
        assertThat(normalizeLineEndings(messageCaptor.getValue())).isEqualTo(normalizeLineEndings(smsMessage));
    }

    @Test
    void getBookingDetail_includesCaseIdAndRegionCode() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
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

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }
}
