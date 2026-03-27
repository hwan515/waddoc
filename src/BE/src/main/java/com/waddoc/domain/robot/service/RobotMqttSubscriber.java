package com.waddoc.domain.robot.service;

import com.waddoc.domain.robot.config.MqttTopics;
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

    private final RobotStateCache stateCache;
    private final RobotSseService sseService;
    private final MqttMissionLocationUpdater missionLocationUpdater;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleMessage(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        String topic   = (String) headers.get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        if (topic == null) {
            return;
        }

        switch (topic) {
            case MqttTopics.ROBOT_ODOM -> {
                stateCache.setLastOdomJson(payload);
                sseService.broadcast("odom", payload);
                missionLocationUpdater.updateFromOdom(payload);
            }
            case MqttTopics.ROBOT_MINIMAP -> {
                stateCache.setLastMinimapJson(payload);
                sseService.broadcast("minimap", payload);
            }
            case MqttTopics.ROBOT_STATE -> {
                stateCache.setLastStateJson(payload);
                sseService.broadcast("state", payload);
            }
            case MqttTopics.ROBOT_STATUS -> {
                sseService.broadcast("status", payload);
                log.info("Robot status update: {}", payload);
            }
            default -> log.warn("Unhandled MQTT topic: {}", topic);
        }
    }
}
