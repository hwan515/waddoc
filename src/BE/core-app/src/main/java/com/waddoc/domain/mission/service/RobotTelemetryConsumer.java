package com.waddoc.domain.mission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.domain.robot.service.MqttMissionLocationUpdater;
import com.waddoc.domain.robot.service.MqttMissionPhaseUpdater;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.RobotTopics;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * robot-gateway가 정규화한 텔레메트리 이벤트만 받아 미션 위치와 단계를 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotTelemetryConsumer {

    private static final String CONSUMER_GROUP = "robot-telemetry-group";

    private final MqttMissionLocationUpdater missionLocationUpdater;
    private final MqttMissionPhaseUpdater missionPhaseUpdater;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;
    private final RobotTelemetryProcessingGuard robotTelemetryProcessingGuard;

    @KafkaListener(topics = EventTypes.ROBOT_TELEMETRY_V1, groupId = CONSUMER_GROUP)
    public void consume(EventEnvelope envelope) {
        kafkaMonitoringMetrics.recordConsumerProcessing(EventTypes.ROBOT_TELEMETRY_V1, CONSUMER_GROUP, () -> {
            JsonNode snapshot = envelope.payload();
            if (snapshot == null) {
                return;
            }

            String sourceTopic = snapshot.path("sourceTopic").asText(null);
            JsonNode sourceSnapshot = snapshot.path("snapshot");
            if (sourceTopic == null || sourceSnapshot.isMissingNode() || sourceSnapshot.isNull()) {
                return;
            }
            String sourceEventId = snapshot.path("sourceEventId").asText(null);
            RobotTelemetryProcessingGuard.Decision decision = robotTelemetryProcessingGuard.evaluate(
                    envelope.aggregateId(),
                    sourceTopic,
                    sourceEventId,
                    envelope.occurredAt()
            );
            if (!decision.accepted()) {
                log.debug(
                        "Skipped robot telemetry event. reason={}, aggregateId={}, sourceTopic={}, sourceEventId={}",
                        decision.reason(),
                        envelope.aggregateId(),
                        sourceTopic,
                        sourceEventId
                );
                return;
            }

            // mission 모듈은 더 이상 MQTT를 직접 모르고, gateway가 정규화한 telemetry contract만 해석한다.
            String rawPayload = sourceSnapshot.toString();
            if (RobotTopics.ROBOT_ODOM.equals(sourceTopic)) {
                missionLocationUpdater.updateFromOdom(rawPayload);
                return;
            }
            if (RobotTopics.ROBOT_MINIMAP.equals(sourceTopic)) {
                missionPhaseUpdater.updateFromMinimap(rawPayload, null);
                return;
            }
            if (RobotTopics.ROBOT_STATE.equals(sourceTopic)) {
                missionPhaseUpdater.updateFromState(rawPayload);
            }
        });
    }
}
