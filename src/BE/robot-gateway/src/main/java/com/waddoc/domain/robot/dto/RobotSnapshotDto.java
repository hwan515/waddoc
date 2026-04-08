package com.waddoc.domain.robot.dto;

import java.util.List;

public record RobotSnapshotDto(
        Telemetry telemetry,
        Navigation navigation
) {
    public record Telemetry(
            String vehicleId,
            String missionId,
            Boolean online,
            String state,
            Double batterySoc,
            Double speedMs,
            Double speedKmh,
            Pose pose,
            Location location,
            String updatedAt
    ) {
    }

    public record Pose(
            Double x,
            Double z,
            Double yaw
    ) {
    }

    public record Location(
            Double lat,
            Double lng
    ) {
    }

    public record Navigation(
            String goalWaypointId,
            Integer targetWaypointValue,
            List<Waypoint> pathWaypoints,
            List<Waypoint> fullPathWaypoints,
            List<Point> trajectory,
            List<Point> fullTrajectory,
            Bounds bounds,
            Boolean cleared,
            String clearReason
    ) {
    }

    public record Waypoint(
            String id,
            Double x,
            Double z
    ) {
    }

    public record Point(
            Double x,
            Double z
    ) {
    }

    public record Bounds(
            Double minX,
            Double maxX,
            Double minZ,
            Double maxZ,
            Double width,
            Double height
    ) {
    }
}
