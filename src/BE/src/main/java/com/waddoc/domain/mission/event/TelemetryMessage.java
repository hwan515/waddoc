package com.waddoc.domain.mission.event;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;

public record TelemetryMessage(
        String missionId,
        MissionTelemetryRequest request
) {
}
