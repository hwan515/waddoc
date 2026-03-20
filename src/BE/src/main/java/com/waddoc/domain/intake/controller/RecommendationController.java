package com.waddoc.domain.intake.controller;

import com.waddoc.domain.intake.dto.RecommendRequest;
import com.waddoc.domain.intake.dto.RecommendResponse;
import com.waddoc.domain.intake.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intake/sessions/{intakeSessionId}")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/recommend")
    public ResponseEntity<RecommendResponse> recommend(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody RecommendRequest request) {
        RecommendResponse response = recommendationService.recommend(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }
}
