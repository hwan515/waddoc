package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.dto.RobotSnapshotDto;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.RobotTopics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobotMqttSubscriberTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private RobotStateCache stateCache;

    @Mock
    private RobotSseService sseService;

    @Mock
    private RobotSnapshotAssembler robotSnapshotAssembler;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private RobotMqttSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RobotMqttSubscriber(
                stateCache,
                sseService,
                robotSnapshotAssembler,
                kafkaTemplate,
                objectMapper,
                new KafkaMonitoringMetrics(meterRegistry)
        );
    }

    @Test
    void handleMessage_publishesRobotTelemetryEventAndSnapshot() {
        RobotSnapshotDto snapshot = new RobotSnapshotDto(null, null);
        String payload = """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_test",
                  "latitude": 36.1115,
                  "longitude": 128.1151
                }
        """;
        when(robotSnapshotAssembler.fromCache(stateCache)).thenReturn(snapshot);
        when(kafkaTemplate.send(eq(EventTypes.ROBOT_TELEMETRY_V1), any(), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        subscriber.handleMessage(MessageBuilder.withPayload(payload)
                .setHeader("mqtt_receivedTopic", RobotTopics.ROBOT_ODOM)
                .build());

        verify(stateCache).setLastOdomJson(payload);
        verify(sseService).broadcast("odom", payload);
        verify(sseService).broadcast("snapshot", snapshot);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaTemplate).send(eq(EventTypes.ROBOT_TELEMETRY_V1), keyCaptor.capture(), captor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("veh_GIMCHEON_01");
        EventEnvelope envelope = captor.getValue();
        assertThat(envelope.aggregateId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(envelope.eventType()).isEqualTo(EventTypes.ROBOT_TELEMETRY_V1);
        assertThat(envelope.payload().path("sourceTopic").asText()).isEqualTo(RobotTopics.ROBOT_ODOM);
        assertThat(envelope.payload().path("vehicleId").asText()).isEqualTo("veh_GIMCHEON_01");
        assertThat(envelope.payload().path("snapshot").path("missionId").asText()).isEqualTo("ms_test");
        assertThat(meterRegistry.get("waddoc.kafka.producer.sent")
                .tag("topic", EventTypes.ROBOT_TELEMETRY_V1)
                .tag("producer", "robot-mqtt-subscriber")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void handleMessage_recordsFailureMetricWhenKafkaSendFails() {
        String payload = """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_test",
                  "state": "주행 중"
                }
        """;
        when(robotSnapshotAssembler.fromCache(stateCache)).thenReturn(new RobotSnapshotDto(null, null));
        when(kafkaTemplate.send(eq(EventTypes.ROBOT_TELEMETRY_V1), any(), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        subscriber.handleMessage(MessageBuilder.withPayload(payload)
                .setHeader("mqtt_receivedTopic", RobotTopics.ROBOT_STATE)
                .build());

        assertThat(meterRegistry.get("waddoc.kafka.producer.sent")
                .tag("topic", EventTypes.ROBOT_TELEMETRY_V1)
                .tag("producer", "robot-mqtt-subscriber")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
