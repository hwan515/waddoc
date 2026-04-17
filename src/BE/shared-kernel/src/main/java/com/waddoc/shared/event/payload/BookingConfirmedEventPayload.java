package com.waddoc.shared.event.payload;

import com.waddoc.shared.notification.NotificationType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record BookingConfirmedEventPayload(
        NotificationType type,
        String bookingId,
        String careCaseId,
        String patientId,
        String doctorId,
        String doctorUserId,
        String doctorName,
        String departmentName,
        String patientName,
        String patientGender,
        String patientBirthDate,
        String patientPhone,
        String recipientPhone,
        LocalDate appointmentDate,
        LocalTime startTime,
        String appointmentDateTime,
        String bookingChannel,
        String location,
        OffsetDateTime createdAt
) {
}
