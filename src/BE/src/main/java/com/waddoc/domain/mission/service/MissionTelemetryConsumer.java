package com.waddoc.domain.mission.service;

import com.waddoc.domain.mission.event.TelemetryMessage;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionTelemetryConsumer {

    private static final String CONSUMER_GROUP = "telemetry-group";

    private final MissionTelemetryService missionTelemetryService;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;

    @KafkaListener(topics = KafkaTopics.MISSION_TELEMETRY_TOPIC, groupId = CONSUMER_GROUP)
    public void consume(TelemetryMessage message) {
        kafkaMonitoringMetrics.recordConsumerProcessing(KafkaTopics.MISSION_TELEMETRY_TOPIC, CONSUMER_GROUP, () -> {
            // 텔레메트리 burst를 Kafka에서 완충한 뒤 서비스 계층에서 순차 반영한다.
            missionTelemetryService.processTelemetry(message.missionId(), message.request());
        });
    }
}
