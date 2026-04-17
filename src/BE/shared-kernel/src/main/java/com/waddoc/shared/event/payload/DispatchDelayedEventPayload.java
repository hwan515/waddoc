package com.waddoc.shared.event.payload;

import java.time.OffsetDateTime;

public record DispatchDelayedEventPayload(
        String careCaseId,
        String regionCode,
        String recipientPhone,
        String reason,
        OffsetDateTime delayedAt
) {
}
