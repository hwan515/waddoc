package com.waddoc.domain.intake.controller;

import com.waddoc.domain.intake.dto.*;
import com.waddoc.domain.intake.service.IntakeSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intake/sessions")
@RequiredArgsConstructor
public class IntakeSessionController {

    private final IntakeSessionService intakeSessionService;

    @PostMapping
    public ResponseEntity<CreateIntakeSessionResponse> createSession(
            @Valid @RequestBody CreateIntakeSessionRequest request) {
        CreateIntakeSessionResponse response = intakeSessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{intakeSessionId}/bind-patient")
    public ResponseEntity<BindPatientResponse> bindPatient(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody BindPatientRequest request) {
        BindPatientResponse response = intakeSessionService.bindPatient(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{intakeSessionId}")
    public ResponseEntity<IntakeSessionDetailResponse> getSession(
            @PathVariable String intakeSessionId) {
        IntakeSessionDetailResponse response = intakeSessionService.getSession(intakeSessionId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{intakeSessionId}/complete")
    public ResponseEntity<CompleteSessionResponse> completeSession(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody CompleteSessionRequest request) {
        CompleteSessionResponse response = intakeSessionService.completeSession(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }
}
