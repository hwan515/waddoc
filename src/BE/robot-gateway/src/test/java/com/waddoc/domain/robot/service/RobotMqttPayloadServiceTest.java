package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.config.MqttTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RobotMqttPayloadServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RobotMqttPayloadService payloadService;

    @BeforeEach
    void setUp() {
        payloadService = new RobotMqttPayloadService(objectMapper);
    }

    @Test
    void buildWaypointCommandPayload_returnsJsonCommand() throws Exception {
        JsonNode payload = objectMapper.readTree(payloadService.buildWaypointCommandPayload(59));

        assertThat(payload.path("command").asText()).isEqualTo("waypoint");
        assertThat(payload.path("waypoint").asInt()).isEqualTo(59);
        assertThat(payload.path("target_waypoint").asInt()).isEqualTo(59);
        assertThat(payload.path("requested_at").asText()).isNotBlank();
    }

    @Test
    void buildEstopCommandPayload_returnsJsonCommand() throws Exception {
        JsonNode payload = objectMapper.readTree(payloadService.buildEstopCommandPayload(true));

        assertThat(payload.path("command").asText()).isEqualTo("estop");
        assertThat(payload.path("enabled").asBoolean()).isTrue();
        assertThat(payload.path("state").asInt()).isEqualTo(1);
        assertThat(payload.path("requested_at").asText()).isNotBlank();
    }

    @Test
    void normalizeInboundPayload_wrapsRawStateStringAsJson() throws Exception {
        Optional<String> normalized = payloadService.normalizeInboundPayload(
                MqttTopics.ROBOT_STATE,
                "  주행 중  "
        );

        assertThat(normalized).isPresent();
        JsonNode payload = objectMapper.readTree(normalized.orElseThrow());
        assertThat(payload.path("state").asText()).isEqualTo("주행 중");
        assertThat(payload.path("updated_at").asText()).isNotBlank();
    }

    @Test
    void normalizeInboundPayload_rejectsInvalidMinimapJson() {
        Optional<String> normalized = payloadService.normalizeInboundPayload(
                MqttTopics.ROBOT_MINIMAP,
                "{invalid"
        );

        assertThat(normalized).isEmpty();
    }

    @Test
    void normalizeInboundPayload_sanitizesStructuredTelemetry() throws Exception {
        Optional<String> normalized = payloadService.normalizeInboundPayload(
                MqttTopics.ROBOT_ODOM,
                """
                {
                  "x": 1.25,
                  "minimap_pose": {
                    "x": 3.5,
                    "z": 7.25
                  },
                  "label": "  demo  "
                }
                """
        );

        assertThat(normalized).isPresent();
        JsonNode payload = objectMapper.readTree(normalized.orElseThrow());
        assertThat(payload.path("x").asDouble()).isEqualTo(1.25d);
        assertThat(payload.path("minimap_pose").path("x").asDouble()).isEqualTo(3.5d);
        assertThat(payload.path("label").asText()).isEqualTo("demo");
    }

    @Test
    void normalizeInboundPayload_rejectsStatusWithoutOnlineBoolean() {
        Optional<String> normalized = payloadService.normalizeInboundPayload(
                MqttTopics.ROBOT_STATUS,
                "{\"status\":\"up\"}"
        );

        assertThat(normalized).isEmpty();
    }
}
