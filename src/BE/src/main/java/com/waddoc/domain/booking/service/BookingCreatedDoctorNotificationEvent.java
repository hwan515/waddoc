package com.waddoc.domain.booking.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;

public record BookingCreatedDoctorNotificationEvent(
        String doctorId,
        NewBookingNotificationPayload payload
) {
}
