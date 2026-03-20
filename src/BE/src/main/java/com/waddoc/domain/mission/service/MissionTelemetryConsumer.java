package com.waddoc.domain.mission.service;

import com.waddoc.domain.mission.event.TelemetryMessage;
import com.waddoc.global.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionTelemetryConsumer {

    private final MissionTelemetryService missionTelemetryService;

    @KafkaListener(topics = KafkaTopics.MISSION_TELEMETRY_TOPIC, groupId = "telemetry-group")
    public void consume(TelemetryMessage message) {
        // 텔레메트리 burst를 Kafka에서 완충한 뒤 서비스 계층에서 순차 반영한다.
        missionTelemetryService.processTelemetry(message.missionId(), message.request());
    }
}
