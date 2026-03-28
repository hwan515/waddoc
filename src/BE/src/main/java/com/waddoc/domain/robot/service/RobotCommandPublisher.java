package com.waddoc.domain.robot.service;

import com.waddoc.domain.robot.config.MqttTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotCommandPublisher {

    private final MessageChannel mqttOutboundChannel;
    private final RobotMqttPayloadService payloadService;

    public void publishWaypoint(int waypointNumber) {
        publish(
                MqttTopics.CMD_WAYPOINT,
                payloadService.buildWaypointCommandPayload(waypointNumber)
        );
        log.info("Waypoint 명령 전송: {}", waypointNumber);
    }

    public void publishEstop(boolean enabled) {
        publish(
                MqttTopics.CMD_ESTOP,
                payloadService.buildEstopCommandPayload(enabled)
        );
        log.info("E-Stop 명령 전송: {}", enabled);
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
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }
}
