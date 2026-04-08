package com.waddoc.shared.event.payload;

import java.time.OffsetDateTime;

public record DispatchAssignedEventPayload(
        String missionId,
        String careCaseId,
        String vehicleId,
        String recipientPhone,
        String destination,
        OffsetDateTime assignedAt
) {
}
