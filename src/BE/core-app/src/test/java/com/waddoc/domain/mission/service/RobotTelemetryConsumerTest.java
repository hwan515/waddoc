package com.waddoc.domain.mission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.service.MqttMissionLocationUpdater;
import com.waddoc.domain.robot.service.MqttMissionPhaseUpdater;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.RobotTopics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobotTelemetryConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private MqttMissionLocationUpdater missionLocationUpdater;

    @Mock
    private MqttMissionPhaseUpdater missionPhaseUpdater;

    @Mock
    private RobotTelemetryProcessingGuard robotTelemetryProcessingGuard;

    private RobotTelemetryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RobotTelemetryConsumer(
                missionLocationUpdater,
                missionPhaseUpdater,
                new KafkaMonitoringMetrics(meterRegistry),
                robotTelemetryProcessingGuard
        );
    }

    @Test
    void consume_delegatesOdomPayloadWhenGuardAccepts() throws Exception {
        EventEnvelope envelope = envelope(RobotTopics.ROBOT_ODOM, "evt-1", """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_test",
                  "latitude": 36.11,
                  "longitude": 128.12
                }
                """);
        when(robotTelemetryProcessingGuard.evaluate(
                "veh_GIMCHEON_01",
                RobotTopics.ROBOT_ODOM,
                "evt-1",
                envelope.occurredAt()
        )).thenReturn(RobotTelemetryProcessingGuard.Decision.accepted("accepted"));

        consumer.consume(envelope);

        verify(missionLocationUpdater).updateFromOdom(objectMapper.readTree("""
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_test",
                  "latitude": 36.11,
                  "longitude": 128.12
                }
                """).toString());
        verify(missionPhaseUpdater, never()).updateFromMinimap(anyString(), org.mockito.ArgumentMatchers.any());
        verify(missionPhaseUpdater, never()).updateFromState(anyString());
    }

    @Test
    void consume_skipsPayloadWhenGuardRejectsDuplicate() {
        EventEnvelope envelope = envelope(RobotTopics.ROBOT_STATE, "evt-2", """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_test",
                  "state": "도착"
                }
                """);
        when(robotTelemetryProcessingGuard.evaluate(
                "veh_GIMCHEON_01",
                RobotTopics.ROBOT_STATE,
                "evt-2",
                envelope.occurredAt()
        )).thenReturn(RobotTelemetryProcessingGuard.Decision.rejected("duplicate"));

        consumer.consume(envelope);

        verify(missionLocationUpdater, never()).updateFromOdom(anyString());
        verify(missionPhaseUpdater, never()).updateFromMinimap(anyString(), org.mockito.ArgumentMatchers.any());
        verify(missionPhaseUpdater, never()).updateFromState(anyString());
    }

    private EventEnvelope envelope(String sourceTopic, String sourceEventId, String sourceSnapshotJson) {
        try {
            OffsetDateTime occurredAt = OffsetDateTime.parse("2026-04-09T10:00:00Z");
            return new EventEnvelope(
                    "event-" + sourceEventId,
                    EventTypes.ROBOT_TELEMETRY_V1,
                    occurredAt,
                    "robot-gateway",
                    "veh_GIMCHEON_01",
                    "corr-" + sourceEventId,
                    objectMapper.readTree("""
                            {
                              "sourceTopic": "%s",
                              "sourceEventId": "%s",
                              "occurredAt": "%s",
                              "vehicleId": "veh_GIMCHEON_01",
                              "snapshot": %s
                            }
                            """.formatted(sourceTopic, sourceEventId, occurredAt, sourceSnapshotJson))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
