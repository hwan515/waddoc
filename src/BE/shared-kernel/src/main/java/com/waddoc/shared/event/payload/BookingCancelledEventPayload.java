package com.waddoc.shared.event.payload;

import java.time.OffsetDateTime;

public record BookingCancelledEventPayload(
        String bookingId,
        String careCaseId,
        String patientId,
        String doctorId,
        String doctorUserId,
        String recipientPhone,
        String cancelReason,
        OffsetDateTime cancelledAt
) {
}
