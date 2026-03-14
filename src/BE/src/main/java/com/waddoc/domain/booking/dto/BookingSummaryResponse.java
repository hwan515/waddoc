package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class BookingSummaryResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String bookingId;
    private String status;
    private String doctorName;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private OffsetDateTime createdAt;

    public static BookingSummaryResponse from(Booking booking) {
        return BookingSummaryResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .doctorName(booking.getDoctor().getUser().getName())
                .departmentName(booking.getDoctor().getDepartmentName())
                .appointmentDate(booking.getAppointmentDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .createdAt(booking.getCreatedAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
