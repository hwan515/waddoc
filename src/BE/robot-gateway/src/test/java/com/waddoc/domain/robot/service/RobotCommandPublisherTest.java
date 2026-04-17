package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.shared.event.RobotTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobotCommandPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MessageChannel mqttOutboundChannel;

    private RobotCommandPublisher publisher;

    @BeforeEach
    void setUp() {
        RobotMqttPayloadService payloadService = new RobotMqttPayloadService(objectMapper);
        publisher = new RobotCommandPublisher(mqttOutboundChannel, payloadService, objectMapper);
    }

    @Test
    void publishWaypoint_sendsJsonPayloadToMqttChannel() throws Exception {
        when(mqttOutboundChannel.send(any())).thenReturn(true);

        publisher.publishWaypoint(92);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(captor.capture());

        Message<?> message = captor.getValue();
        assertThat(message.getHeaders().get(MqttHeaders.TOPIC)).isEqualTo(RobotTopics.CMD_WAYPOINT);
        assertThat(message.getHeaders().get(MqttHeaders.QOS)).isEqualTo(1);

        JsonNode payload = objectMapper.readTree(message.getPayload().toString());
        assertThat(payload.path("command").asText()).isEqualTo("waypoint");
        assertThat(payload.path("waypoint").asInt()).isEqualTo(92);
    }

    @Test
    void publishEstop_whenChannelThrows_throwsIllegalStateException() {
        when(mqttOutboundChannel.send(any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> publisher.publishEstop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Robot MQTT publish failed");
    }
}
