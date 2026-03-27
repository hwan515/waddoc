package com.waddoc.domain.robot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.config.MqttTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/robots/cmd")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
public class RobotCommandController {

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    @PostMapping("/waypoint/{n}")
    public ResponseEntity<Void> sendWaypoint(@PathVariable int n) {
        publish(MqttTopics.CMD_WAYPOINT, Map.of("waypoint", n));
        log.info("Waypoint 명령 전송: {}", n);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/estop/{state}")
    public ResponseEntity<Void> sendEstop(@PathVariable int state) {
        boolean enabled = state == 1;
        publish(MqttTopics.CMD_ESTOP, Map.of("state", enabled));
        log.info("E-Stop 명령 전송: state={}, enabled={}", state, enabled);
        return ResponseEntity.accepted().build();
    }

    private void publish(String topic, Map<String, ?> payload) {
        final String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("MQTT 명령 직렬화 실패. topic={}, payload={}", topic, payload, e);
            throw new IllegalStateException("Failed to serialize MQTT command payload", e);
        }

        mqttOutboundChannel.send(
                MessageBuilder.withPayload(serializedPayload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .setHeader(MqttHeaders.QOS, 1)
                        .build()
        );
    }
}
