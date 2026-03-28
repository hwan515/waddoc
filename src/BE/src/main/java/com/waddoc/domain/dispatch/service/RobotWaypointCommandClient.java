package com.waddoc.domain.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.config.MqttTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotWaypointCommandClient {

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

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

    public void dispatchMission(String missionId, String vehicleId, int targetWaypointNumber, String destination) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "missionId", missionId,
                    "vehicleId", vehicleId,
                    "waypoint", targetWaypointNumber,
                    "destination", destination != null ? destination : ""
            ));
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader(MqttHeaders.TOPIC, MqttTopics.CMD_DISPATCH)
                            .setHeader(MqttHeaders.QOS, 1)
                            .build()
            );
            log.info("Mission dispatch sent via MQTT. missionId={}, vehicleId={}, waypoint={}",
                    missionId, vehicleId, targetWaypointNumber);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize dispatch command. missionId={}", missionId, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        } catch (Exception e) {
            log.error("Mission dispatch failed. missionId={}", missionId, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }
}
