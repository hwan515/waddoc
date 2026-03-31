package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class BookingSummaryResponse {

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
                .createdAt(booking.getCreatedAt().atZone(KstTime.ZONE).toOffsetDateTime())
                .build();
    }
}
