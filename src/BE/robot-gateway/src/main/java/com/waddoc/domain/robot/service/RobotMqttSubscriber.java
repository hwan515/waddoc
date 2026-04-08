package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.RobotTopics;
import com.waddoc.shared.event.payload.RobotTelemetryEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MQTT에서 들어온 로봇 상태를 Redis/SSE에 반영하고 Kafka 텔레메트리 이벤트로 정규화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotMqttSubscriber {

    private static final double POSE_JUMP_WARNING_THRESHOLD = 25.0;
    private static final String PRODUCER_ID = "robot-mqtt-subscriber";

    private final RobotStateCache stateCache;
    private final RobotSseService sseService;
    private final RobotSnapshotAssembler robotSnapshotAssembler;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleMessage(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        String topic = (String) headers.get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        if (topic == null) {
            return;
        }

        boolean shouldBroadcastSnapshot = switch (topic) {
            case RobotTopics.ROBOT_ODOM -> {
                logPoseCacheDiagnostics(topic, stateCache.getLastOdomJson(), payload, true);
                stateCache.setLastOdomJson(payload);
                sseService.broadcast("odom", payload);
                yield true;
            }
            case RobotTopics.ROBOT_MINIMAP -> {
                logPoseCacheDiagnostics(topic, stateCache.getLastMinimapJson(), payload, false);
                stateCache.setLastMinimapJson(payload);
                sseService.broadcast("minimap", payload);
                yield true;
            }
            case RobotTopics.ROBOT_STATE -> {
                stateCache.setLastStateJson(payload);
                sseService.broadcast("state", payload);
                yield true;
            }
            case RobotTopics.ROBOT_STATUS -> {
                stateCache.setLastStatusJson(payload);
                sseService.broadcast("status", payload);
                log.info("Robot status update: {}", payload);
                yield true;
            }
            default -> {
                log.warn("Unhandled MQTT topic: {}", topic);
                yield false;
            }
        };

        if (shouldBroadcastSnapshot) {
            // MQTT transport는 gateway만 소유하고, core는 Kafka telemetry contract만 보게 한다.
            publishTelemetry(topic, payload);
            sseService.broadcast("snapshot", robotSnapshotAssembler.fromCache(stateCache));
        }
    }

    private void publishTelemetry(String topic, String rawPayload) {
        try {
            JsonNode snapshot = objectMapper.readTree(rawPayload);
            String sourceEventId = UUID.randomUUID().toString();
            RobotTelemetryEventPayload payload = new RobotTelemetryEventPayload(
                    topic,
                    sourceEventId,
                    OffsetDateTime.now(),
                    readText(snapshot, "missionId"),
                    readText(snapshot, "vehicleId"),
                    snapshot
            );
            String aggregateId = payload.vehicleId() == null || payload.vehicleId().isBlank()
                    ? topic
                    : payload.vehicleId();
            var sendSample = kafkaMonitoringMetrics.startProducerSend();
            try {
                kafkaTemplate.send(
                        EventTypes.ROBOT_TELEMETRY_V1,
                        aggregateId,
                        new EventEnvelope(
                                UUID.randomUUID().toString(),
                                EventTypes.ROBOT_TELEMETRY_V1,
                                payload.occurredAt(),
                                "robot-gateway",
                                aggregateId,
                                "corr_robot_telemetry_" + sourceEventId,
                                objectMapper.valueToTree(payload)
                        )
                ).whenComplete((result, exception) -> {
                    kafkaMonitoringMetrics.recordProducerResult(EventTypes.ROBOT_TELEMETRY_V1, PRODUCER_ID, sendSample, exception);
                    if (exception != null) {
                        log.warn(
                                "Failed to publish robot telemetry event. topic={}, aggregateId={}, sourceEventId={}",
                                topic,
                                aggregateId,
                                sourceEventId,
                                exception
                        );
                    }
                });
            } catch (RuntimeException exception) {
                kafkaMonitoringMetrics.recordProducerResult(EventTypes.ROBOT_TELEMETRY_V1, PRODUCER_ID, sendSample, exception);
                log.warn(
                        "Failed to publish robot telemetry event. topic={}, aggregateId={}, sourceEventId={}",
                        topic,
                        aggregateId,
                        sourceEventId,
                        exception
                );
            }
        } catch (Exception exception) {
            log.warn("Failed to normalize robot telemetry payload for Kafka publication. topic={}", topic, exception);
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
