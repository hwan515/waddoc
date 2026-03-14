package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingResponseDtoTest {

    @Test
    void createBookingResponse_usesKstOffsetForCreatedAt() {
        Booking booking = createBooking();
        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(booking.getPatient())
                .doctor(booking.getDoctor())
                .intakeSession(booking.getIntakeSession())
                .build();

        CreateBookingResponse response = CreateBookingResponse.of(booking, careCase, "예약이 완료되었습니다.");

        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T10:05:00+09:00"));
    }

    @Test
    void bookingSummaryResponse_includesCreatedAtWithOffset() {
        Booking booking = createBooking();

        BookingSummaryResponse response = BookingSummaryResponse.from(booking);

        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T10:05:00+09:00"));
    }

    @Test
    void cancelBookingResponse_usesKstOffsetForCancelledAt() {
        Booking booking = createBooking();
        ReflectionTestUtils.setField(booking, "cancelledAt", LocalDateTime.of(2026, 3, 10, 11, 0));

        CancelBookingResponse response = CancelBookingResponse.from(booking, "예약이 취소되었습니다.");

        assertThat(response.getCancelledAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T11:00:00+09:00"));
    }

    @Test
    void bookingDetailResponse_includesDocumentedFields() {
        Booking booking = createBooking();
        ReflectionTestUtils.setField(booking, "cancelledAt", LocalDateTime.of(2026, 3, 10, 11, 0));

        BookingDetailResponse response = BookingDetailResponse.from(booking, "01012345678", "case_abc123");

        assertThat(response.getPatient().getRegionCode()).isEqualTo("ULLEUNG");
        assertThat(response.getCaseId()).isEqualTo("case_abc123");
        assertThat(response.getIntakeSessionId()).isEqualTo(booking.getIntakeSession().getPublicId());
        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T10:05:00+09:00"));
        assertThat(response.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T10:06:00+09:00"));
        assertThat(response.getCancelledAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T11:00:00+09:00"));
    }

    private Booking createBooking() {
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
                .specialty("가정의학")
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

        ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 10, 10, 5));
        ReflectionTestUtils.setField(booking, "updatedAt", LocalDateTime.of(2026, 3, 10, 10, 6));

        return booking;
    }
}
