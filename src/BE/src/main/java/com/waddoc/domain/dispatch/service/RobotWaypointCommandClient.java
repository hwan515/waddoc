package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.robot.config.MqttTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotWaypointCommandClient {

    private final MessageChannel mqttOutboundChannel;

    public void dispatchToWaypoint(int targetWaypointNumber) {
        try {
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(String.valueOf(targetWaypointNumber))
                            .setHeader(MqttHeaders.TOPIC, MqttTopics.CMD_WAYPOINT)
                            .setHeader(MqttHeaders.QOS, 1)
                            .build()
            );
            log.info("Robot waypoint dispatch requested via MQTT. targetWaypointNumber={}", targetWaypointNumber);
        } catch (Exception e) {
            log.error("Robot waypoint dispatch failed. targetWaypointNumber={}", targetWaypointNumber, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }
}
