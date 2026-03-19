package com.waddoc.domain.notification.dto;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class NewBookingNotificationPayload {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String type;
    private String bookingId;
    private String caseId;
    private String doctorId;
    private String doctorName;
    private String departmentName;
    private String patientName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private String location;
    private OffsetDateTime createdAt;

    public static NewBookingNotificationPayload from(Booking booking, CareCase careCase) {
        return NewBookingNotificationPayload.builder()
                .type("NEW_BOOKING")
                .bookingId(booking.getPublicId())
                .caseId(careCase.getPublicId())
                .doctorId(booking.getDoctor().getPublicId())
                .doctorName(booking.getDoctor().getUser().getName())
                .departmentName(booking.getDoctor().getDepartmentName())
                .patientName(booking.getPatient().getName())
                .appointmentDate(booking.getAppointmentDate())
                .startTime(booking.getStartTime())
                .location(booking.getPatient().getAddress())
                .createdAt(booking.getCreatedAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
