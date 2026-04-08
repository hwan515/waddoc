package com.waddoc.shared.event.payload;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RobotTelemetryEventPayload(
        String sourceTopic,
        String sourceEventId,
        OffsetDateTime occurredAt,
        String missionId,
        String vehicleId,
        JsonNode snapshot
) {
}
