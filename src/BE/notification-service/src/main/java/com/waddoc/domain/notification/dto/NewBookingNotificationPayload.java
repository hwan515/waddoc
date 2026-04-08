package com.waddoc.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewBookingNotificationPayload {

    private String type;
    private String bookingId;
    private String caseId;
    private String doctorId;
    private String doctorName;
    private String departmentName;
    private String doctorUserId;
    private String patientId;
    private String patientName;
    private String patientGender;
    private String patientBirthDate;
    private String patientPhone;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private String bookingChannel;
    private String location;
    private OffsetDateTime createdAt;
}
