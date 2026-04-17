package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;
import com.waddoc.domain.mission.event.TelemetryMessage;
import com.waddoc.domain.mission.service.MissionTelemetryService;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 차량에서 올라오는 위치/상태 telemetry를 빠르게 수신해 비동기 처리로 넘긴다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionTelemetryController {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String PRODUCER_ID = "mission-telemetry-controller";

    private final MissionTelemetryService missionTelemetryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;

    /**
     * 단말 응답 지연을 줄이기 위해 검증만 한 뒤 Kafka로 넘기고 바로 202를 반환한다.
     */
    @PostMapping("/{missionId}/telemetry")
    public ResponseEntity<Void> receiveTelemetry(
            @PathVariable String missionId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MissionTelemetryRequest request
    ) {
        missionTelemetryService.validateApiKey(apiKey);
        // 수집 API는 빠르게 202를 반환하고, 실제 반영은 consumer에서 비동기로 처리한다.
        var sendSample = kafkaMonitoringMetrics.startProducerSend();
        try {
            kafkaTemplate.send(
                    KafkaTopics.MISSION_TELEMETRY_TOPIC,
                    missionId,
                    new TelemetryMessage(missionId, request)
            ).whenComplete((result, exception) -> {
                kafkaMonitoringMetrics.recordProducerResult(KafkaTopics.MISSION_TELEMETRY_TOPIC, PRODUCER_ID, sendSample, exception);
                if (exception != null) {
                    log.warn(
                            "Failed to publish mission telemetry event. missionId={}, sourceEventId={}, vehicleId={}",
                            missionId,
                            request.getSourceEventId(),
                            request.getVehicleId(),
                            exception
                    );
                }
            });
        } catch (RuntimeException exception) {
            kafkaMonitoringMetrics.recordProducerResult(KafkaTopics.MISSION_TELEMETRY_TOPIC, PRODUCER_ID, sendSample, exception);
            log.warn(
                    "Failed to publish mission telemetry event. missionId={}, sourceEventId={}, vehicleId={}",
                    missionId,
                    request.getSourceEventId(),
                    request.getVehicleId(),
                    exception
            );
            throw exception;
        }
        return ResponseEntity.accepted().build();
    }
}
