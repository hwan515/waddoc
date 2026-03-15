package com.waddoc.domain.intake.controller;

import com.waddoc.domain.intake.dto.*;
import com.waddoc.domain.intake.service.IntakeTurnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/intake/sessions/{intakeSessionId}")
@RequiredArgsConstructor
public class IntakeTurnController {

    private final IntakeTurnService intakeTurnService;

    @PostMapping(value = "/turns/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IntakeTurnResponse> recordVoiceTurn(
            @PathVariable String intakeSessionId,
            @RequestPart("audioFile") MultipartFile audioFile,
            @RequestPart(value = "prompt", required = false) String prompt,
            @RequestPart(value = "nextAction", required = false) String nextAction,
            @RequestPart(value = "ttsMessage", required = false) String ttsMessage) {
        IntakeTurnResponse response = intakeTurnService.recordVoiceTurn(
                intakeSessionId, audioFile, prompt, nextAction, ttsMessage);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/complete")
    public ResponseEntity<CompleteSessionResponse> completeSession(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody CompleteSessionRequest request) {
        CompleteSessionResponse response = intakeTurnService.completeSession(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }
}
