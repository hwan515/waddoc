package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.dto.RobotSnapshotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Redis에 쌓인 각종 로봇 상태 조각을 운영 화면용 단일 snapshot으로 합친다.
 */
@Service
@RequiredArgsConstructor
public class RobotSnapshotAssembler {

    private final ObjectMapper objectMapper;

    public RobotSnapshotDto fromCache(RobotStateCache stateCache) {
        return assemble(
                stateCache.getLastMinimapJson(),
                stateCache.getLastOdomJson(),
                stateCache.getLastStateJson(),
                stateCache.getLastStatusJson()
        );
    }

    public RobotSnapshotDto assemble(
            String minimapJson,
            String odomJson,
            String stateJson,
            String statusJson
    ) {
        JsonNode minimapNode = readJson(minimapJson);
        JsonNode odomNode = readJson(odomJson);
        JsonNode stateNode = readJson(stateJson);
        JsonNode statusNode = readJson(statusJson);

        return new RobotSnapshotDto(
                buildTelemetry(minimapNode, odomNode, stateNode, statusNode),
                buildNavigation(minimapNode)
        );
    }

    private RobotSnapshotDto.Telemetry buildTelemetry(
            JsonNode minimapNode,
            JsonNode odomNode,
            JsonNode stateNode,
            JsonNode statusNode
    ) {
        return new RobotSnapshotDto.Telemetry(
                firstText(
                        odomNode, "vehicleId",
                        minimapNode, "vehicleId",
                        stateNode, "vehicleId",
                        statusNode, "vehicleId"
                ),
                firstText(
                        odomNode, "missionId",
                        minimapNode, "missionId",
                        stateNode, "missionId"
                ),
                firstBoolean(statusNode, "online"),
                firstNonBlank(
                        readText(stateNode, "state"),
                        readTelemetryState(odomNode),
                        readTelemetryState(minimapNode)
                ),
                firstNumber(
                        minimapNode, "batterySoc",
                        minimapNode, "battery_soc",
                        odomNode, "batterySoc",
                        odomNode, "battery_soc"
                ),
                firstNumber(
                        odomNode, "speedMs",
                        odomNode, "speed_ms",
                        odomNode, "speed",
                        minimapNode, "speedMs",
                        minimapNode, "speed_ms",
                        minimapNode, "speed"
                ),
                firstNumber(
                        odomNode, "speedKmh",
                        odomNode, "speed_kmh",
                        minimapNode, "speedKmh",
                        minimapNode, "speed_kmh"
                ),
                extractPose(odomNode, minimapNode),
                extractLocation(odomNode, minimapNode),
                resolveUpdatedAt(odomNode, minimapNode, stateNode, statusNode)
        );
    }

    private RobotSnapshotDto.Navigation buildNavigation(JsonNode minimapNode) {
        return new RobotSnapshotDto.Navigation(
                firstText(
                        minimapNode, "goalWaypointId",
                        minimapNode, "goal_waypoint_id"
                ),
                firstInteger(
                        minimapNode, "targetWaypointValue",
                        minimapNode, "target_waypoint_value"
                ),
                readWaypoints(firstNode(minimapNode, "pathWaypoints", "path_waypoints")),
                readWaypoints(firstNode(minimapNode, "fullPathWaypoints", "full_path_waypoints")),
                readPoints(firstNode(minimapNode, "trajectory")),
                readPoints(firstNode(minimapNode, "fullTrajectory", "full_trajectory")),
                readBounds(firstNode(minimapNode, "bounds")),
                firstBoolean(minimapNode, "cleared"),
                firstText(
                        minimapNode, "clearReason",
                        minimapNode, "clear_reason"
                )
        );
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private RobotSnapshotDto.Pose extractPose(JsonNode odomNode, JsonNode minimapNode) {
        JsonNode odomPose = firstNode(odomNode, "minimap_pose", "minimapPose", "vehiclePose", "current_pose", "currentPose");
        JsonNode minimapPose = firstNode(minimapNode, "current_pose", "currentPose", "vehiclePose", "minimap_pose", "minimapPose");
        JsonNode poseNode = odomPose != null ? odomPose : minimapPose;
        if (poseNode == null) {
            return null;
        }

        Double x = readNumber(poseNode, "x");
        Double z = readNumber(poseNode, "z");
        if (x == null || z == null) {
            return null;
        }

        return new RobotSnapshotDto.Pose(
                x,
                z,
                readNumber(poseNode, "yaw")
        );
    }

    private RobotSnapshotDto.Location extractLocation(JsonNode odomNode, JsonNode minimapNode) {
        RobotSnapshotDto.Location odomLocation = readLocation(odomNode);
        if (odomLocation != null) {
            return odomLocation;
        }

        return readLocation(minimapNode);
    }

    private RobotSnapshotDto.Location readLocation(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode nestedLocation = firstNode(node, "currentLocation", "vehicleLocation", "location");
        Double nestedLat = readNumber(nestedLocation, "lat", "latitude");
        Double nestedLng = readNumber(nestedLocation, "lng", "longitude");
        if (nestedLat != null && nestedLng != null) {
            return new RobotSnapshotDto.Location(nestedLat, nestedLng);
        }

        Double lat = readNumber(node, "lat", "latitude");
        Double lng = readNumber(node, "lng", "longitude");
        if (lat == null || lng == null) {
            return null;
        }

        return new RobotSnapshotDto.Location(lat, lng);
    }

    private String readTelemetryState(JsonNode node) {
        return firstNonBlank(
                readText(node, "state"),
                readText(node, "vehicleState"),
                readText(node, "missionState"),
                readText(node, "status")
        );
    }

    private String resolveUpdatedAt(
            JsonNode odomNode,
            JsonNode minimapNode,
            JsonNode stateNode,
            JsonNode statusNode
    ) {
        return java.util.stream.Stream.of(
                        readTimestamp(odomNode, "updatedAt", "updated_at"),
                        readTimestamp(minimapNode, "updatedAt", "updated_at", "generated_at"),
                        readTimestamp(stateNode, "updatedAt", "updated_at"),
                        readTimestamp(statusNode, "updatedAt", "updated_at")
                )
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(OffsetDateTime::toString)
                .orElse(null);
    }

    private OffsetDateTime readTimestamp(JsonNode node, String... fieldNames) {
        String value = readText(node, fieldNames);
        if (value == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<RobotSnapshotDto.Waypoint> readWaypoints(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<RobotSnapshotDto.Waypoint> waypoints = new ArrayList<>();
        for (JsonNode item : node) {
            Double x = readNumber(item, "x");
            Double z = readNumber(item, "z");
            if (x == null || z == null) {
                continue;
            }

            waypoints.add(new RobotSnapshotDto.Waypoint(
                    readText(item, "id"),
                    x,
                    z
            ));
        }
        return waypoints;
    }

    private List<RobotSnapshotDto.Point> readPoints(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<RobotSnapshotDto.Point> points = new ArrayList<>();
        for (JsonNode item : node) {
            Double x = readNumber(item, "x");
            Double z = readNumber(item, "z");
            if (x == null || z == null) {
                continue;
            }
            points.add(new RobotSnapshotDto.Point(x, z));
        }
        return points;
    }

    private RobotSnapshotDto.Bounds readBounds(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        return new RobotSnapshotDto.Bounds(
                readNumber(node, "minX", "min_x"),
                readNumber(node, "maxX", "max_x"),
                readNumber(node, "minZ", "min_z"),
                readNumber(node, "maxZ", "max_z"),
                readNumber(node, "width"),
                readNumber(node, "height")
        );
    }

    private JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode candidate = node.get(fieldName);
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }

        return null;
    }

    private Double readNumber(JsonNode node, String... fieldNames) {
        JsonNode candidate = fieldNames.length == 0 ? node : firstNode(node, fieldNames);
        if (candidate == null || !candidate.isNumber()) {
            return null;
        }
        return candidate.doubleValue();
    }

    private Integer firstInteger(
            JsonNode firstNode, String firstField,
            JsonNode secondNode, String secondField
    ) {
        Double value = firstNumber(firstNode, firstField, secondNode, secondField);
        return value == null ? null : value.intValue();
    }

    private Double firstNumber(Object... nodeFieldPairs) {
        for (int index = 0; index + 1 < nodeFieldPairs.length; index += 2) {
            JsonNode node = (JsonNode) nodeFieldPairs[index];
            String fieldName = (String) nodeFieldPairs[index + 1];
            Double value = readNumber(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... nodeFieldPairs) {
        for (int index = 0; index + 1 < nodeFieldPairs.length; index += 2) {
            JsonNode node = (JsonNode) nodeFieldPairs[index];
            String fieldName = (String) nodeFieldPairs[index + 1];
            String value = readText(node, fieldName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode node, String fieldName) {
        JsonNode candidate = firstNode(node, fieldName);
        return candidate != null && candidate.isBoolean() ? candidate.booleanValue() : null;
    }

    private String readText(JsonNode node, String... fieldNames) {
        JsonNode candidate = fieldNames.length == 0 ? node : firstNode(node, fieldNames);
        if (candidate == null || !candidate.isTextual()) {
            return null;
        }
        String value = candidate.asText();
        return value == null || value.isBlank() ? null : value;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
