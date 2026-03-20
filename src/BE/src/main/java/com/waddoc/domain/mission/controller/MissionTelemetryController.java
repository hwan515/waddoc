package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;
import com.waddoc.domain.mission.service.MissionTelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/{missionId}/telemetry")
    public ResponseEntity<Void> receiveTelemetry(
            @PathVariable String missionId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MissionTelemetryRequest request
    ) {
        missionTelemetryService.receiveTelemetry(missionId, apiKey, request);
        return ResponseEntity.accepted().build();
    }
}
