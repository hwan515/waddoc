package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.config.MqttTopics;
import com.waddoc.global.monitoring.MqttMonitoringMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotMqttSubscriber {

    private static final double POSE_JUMP_WARNING_THRESHOLD = 25.0;

    private final RobotStateCache stateCache;
    private final RobotSseService sseService;
    private final MqttMissionLocationUpdater missionLocationUpdater;
    private final MqttMissionPhaseUpdater missionPhaseUpdater;
    private final RobotSnapshotAssembler robotSnapshotAssembler;
    private final MqttMonitoringMetrics mqttMonitoringMetrics;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleMessage(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        String topic   = (String) headers.get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        if (topic == null) {
            return;
        }

        boolean shouldBroadcastSnapshot = mqttMonitoringMetrics.recordInboundProcessing(topic, () -> switch (topic) {
            case MqttTopics.ROBOT_ODOM -> {
                logPoseCacheDiagnostics(topic, stateCache.getLastOdomJson(), payload, true);
                stateCache.setLastOdomJson(payload);
                sseService.broadcast("odom", payload);
                missionLocationUpdater.updateFromOdom(payload);
                yield true;
            }
            case MqttTopics.ROBOT_MINIMAP -> {
                logPoseCacheDiagnostics(topic, stateCache.getLastMinimapJson(), payload, false);
                stateCache.setLastMinimapJson(payload);
                sseService.broadcast("minimap", payload);
                missionPhaseUpdater.updateFromMinimap(payload, stateCache.getLastStateJson());
                yield true;
            }
            case MqttTopics.ROBOT_STATE -> {
                stateCache.setLastStateJson(payload);
                sseService.broadcast("state", payload);
                missionPhaseUpdater.updateFromState(payload);
                yield true;
            }
            case MqttTopics.ROBOT_STATUS -> {
                stateCache.setLastStatusJson(payload);
                sseService.broadcast("status", payload);
                log.info("Robot status update: {}", payload);
                yield true;
            }
            default -> {
                log.warn("Unhandled MQTT topic: {}", topic);
                yield false;
            }
        });

        if (shouldBroadcastSnapshot) {
            sseService.broadcast("snapshot", robotSnapshotAssembler.fromCache(stateCache));
        }
    }

    private void logPoseCacheDiagnostics(String topic, String previousPayload, String nextPayload, boolean preferOdomPose) {
        PayloadPoseSummary previousSummary = summarizePayload(previousPayload, preferOdomPose);
        PayloadPoseSummary nextSummary = summarizePayload(nextPayload, preferOdomPose);
        if (nextSummary == null) {
            return;
        }

        if (previousSummary == null) {
            log.info(
                    "Robot pose cache initialized. topic={}, vehicleId={}, missionId={}, goalWaypointId={}, updatedAt={}, clearReason={}, pose={}",
                    topic,
                    nextSummary.vehicleId(),
                    nextSummary.missionId(),
                    nextSummary.goalWaypointId(),
                    nextSummary.updatedAt(),
                    nextSummary.clearReason(),
                    nextSummary.poseString()
            );
            return;
        }

        double delta = nextSummary.distanceFrom(previousSummary);
        if (Double.isNaN(delta) || delta < POSE_JUMP_WARNING_THRESHOLD) {
            return;
        }

        log.warn(
                "Robot pose jump detected. topic={}, delta={}, previousUpdatedAt={}, nextUpdatedAt={}, previousPose={}, nextPose={}, vehicleId={}, missionId={}, goalWaypointId={}, clearReason={}",
                topic,
                String.format("%.3f", delta),
                previousSummary.updatedAt(),
                nextSummary.updatedAt(),
                previousSummary.poseString(),
                nextSummary.poseString(),
                nextSummary.vehicleId(),
                nextSummary.missionId(),
                nextSummary.goalWaypointId(),
                nextSummary.clearReason()
        );
    }

    private PayloadPoseSummary summarizePayload(String payload, boolean preferOdomPose) {
        if (payload == null || payload.isBlank() || "{}".equals(payload.trim())) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode poseNode = preferOdomPose
                    ? firstNode(node, "minimap_pose", "minimapPose", "vehiclePose", "current_pose", "currentPose")
                    : firstNode(node, "current_pose", "currentPose", "vehiclePose", "minimap_pose", "minimapPose");
            if (poseNode == null) {
                return null;
            }

            Double x = readNumber(poseNode, "x");
            Double z = readNumber(poseNode, "z");
            if (x == null || z == null) {
                return null;
            }

            return new PayloadPoseSummary(
                    readText(node, "vehicleId"),
                    readText(node, "missionId"),
                    firstNonBlank(
                            readText(node, "goalWaypointId"),
                            readText(node, "goal_waypoint_id")
                    ),
                    firstNonBlank(
                            readText(node, "updatedAt"),
                            readText(node, "updated_at"),
                            readText(node, "generated_at")
                    ),
                    firstNonBlank(
                            readText(node, "clearReason"),
                            readText(node, "clear_reason")
                    ),
                    x,
                    z,
                    readNumber(poseNode, "yaw")
            );
        } catch (Exception exception) {
            log.debug("Failed to summarize robot MQTT payload. topicPosePreference={}", preferOdomPose ? "odom" : "minimap", exception);
            return null;
        }
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

    private String readText(JsonNode node, String... fieldNames) {
        JsonNode candidate = fieldNames.length == 0 ? node : firstNode(node, fieldNames);
        if (candidate == null || !candidate.isTextual()) {
            return null;
        }

        String value = candidate.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private record PayloadPoseSummary(
            String vehicleId,
            String missionId,
            String goalWaypointId,
            String updatedAt,
            String clearReason,
            Double x,
            Double z,
            Double yaw
    ) {
        double distanceFrom(PayloadPoseSummary other) {
            double deltaX = x - other.x;
            double deltaZ = z - other.z;
            return Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        }

        String poseString() {
            return String.format("(x=%.3f,z=%.3f,yaw=%s)", x, z, yaw == null ? "-" : String.format("%.6f", yaw));
        }
    }
}
