package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.shared.event.RobotTopics;
import com.waddoc.shared.event.payload.RobotCommandDispatchRequestedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

/**
 * Kafka command 이벤트를 ROS2가 이해하는 MQTT 명령 payload로 바꿔 전송한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotCommandPublisher {

    private final MessageChannel mqttOutboundChannel;
    private final RobotMqttPayloadService payloadService;
    private final ObjectMapper objectMapper;

    public void publishWaypoint(int waypointNumber) {
        publish(
                RobotTopics.CMD_WAYPOINT,
                payloadService.buildWaypointCommandPayload(waypointNumber)
        );
        log.info("Waypoint 명령 전송: {}", waypointNumber);
    }

    public void publishEstop(boolean enabled) {
        publish(
                RobotTopics.CMD_ESTOP,
                payloadService.buildEstopCommandPayload(enabled)
        );
        log.info("E-Stop 명령 전송: {}", enabled);
    }

    public void publishDispatch(RobotCommandDispatchRequestedPayload payload) {
        try {
            publish(
                    RobotTopics.CMD_DISPATCH,
                    objectMapper.writeValueAsString(java.util.Map.of(
                            "missionId", payload.missionId(),
                            "vehicleId", payload.vehicleId(),
                            "waypoint", payload.targetWaypointNumber(),
                            "destination", payload.destination() == null ? "" : payload.destination()
                    ))
            );
            log.info("Dispatch 명령 전송: missionId={}, vehicleId={}, waypoint={}",
                    payload.missionId(), payload.vehicleId(), payload.targetWaypointNumber());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize dispatch command payload", exception);
        }
    }

    private void publish(String topic, String payload) {
        try {
            boolean sent = mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader(MqttHeaders.TOPIC, topic)
                            .setHeader(MqttHeaders.QOS, 1)
                            .build()
            );
            if (!sent) {
                throw new IllegalStateException("MQTT outbound channel rejected the message");
            }
        } catch (Exception e) {
            log.error("Robot MQTT publish failed. topic={}", topic, e);
            throw new IllegalStateException("Robot MQTT publish failed", e);
        }
    }
}
