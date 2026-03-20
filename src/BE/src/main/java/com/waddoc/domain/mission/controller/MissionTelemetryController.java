package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;
import com.waddoc.domain.mission.event.TelemetryMessage;
import com.waddoc.domain.mission.service.MissionTelemetryService;
import com.waddoc.global.config.KafkaTopics;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionTelemetryController {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final MissionTelemetryService missionTelemetryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/{missionId}/telemetry")
    public ResponseEntity<Void> receiveTelemetry(
            @PathVariable String missionId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MissionTelemetryRequest request
    ) {
        missionTelemetryService.validateApiKey(apiKey);
        // 수집 API는 빠르게 202를 반환하고, 실제 반영은 consumer에서 비동기로 처리한다.
        kafkaTemplate.send(
                KafkaTopics.MISSION_TELEMETRY_TOPIC,
                missionId,
                new TelemetryMessage(missionId, request)
        );
        return ResponseEntity.accepted().build();
    }
}
