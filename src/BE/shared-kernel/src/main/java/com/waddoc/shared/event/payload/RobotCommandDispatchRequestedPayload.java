package com.waddoc.shared.event.payload;

public record RobotCommandDispatchRequestedPayload(
        String missionId,
        String vehicleId,
        Integer targetWaypointNumber,
        String destination
) {
}
