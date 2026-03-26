package com.waddoc.domain.robot.controller;

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

@Slf4j
@RestController
@RequestMapping("/api/v1/robots/cmd")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
public class RobotCommandController {

    private final MessageChannel mqttOutboundChannel;

    @PostMapping("/waypoint/{n}")
    public ResponseEntity<Void> sendWaypoint(@PathVariable int n) {
        publish(MqttTopics.CMD_WAYPOINT, String.valueOf(n));
        log.info("Waypoint 명령 전송: {}", n);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/estop/{state}")
    public ResponseEntity<Void> sendEstop(@PathVariable int state) {
        publish(MqttTopics.CMD_ESTOP, String.valueOf(state));
        log.info("E-Stop 명령 전송: {}", state);
        return ResponseEntity.accepted().build();
    }

    private void publish(String topic, String payload) {
        mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .setHeader(MqttHeaders.QOS, 1)
                        .build()
        );
    }
}
