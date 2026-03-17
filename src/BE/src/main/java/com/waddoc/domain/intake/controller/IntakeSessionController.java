package com.waddoc.domain.intake.controller;

import com.waddoc.domain.intake.dto.*;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
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

    @PatchMapping("/{intakeSessionId}")
    public ResponseEntity<?> updateSession(
            @PathVariable String intakeSessionId,
            @RequestBody UpdateIntakeSessionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_PATCH_REQUEST);
        }

        if (request.isBindPatientRequest()) {
            BindPatientResponse response = intakeSessionService.bindPatient(
                    intakeSessionId,
                    new BindPatientRequest(request.getPatientId())
            );
            return ResponseEntity.ok(response);
        }

        if (request.isCompleteSessionRequest()) {
            CompleteSessionResponse response = intakeSessionService.completeSession(
                    intakeSessionId,
                    new CompleteSessionRequest(request.getCompletionReason())
            );
            return ResponseEntity.ok(response);
        }

        throw new BusinessException(ErrorCode.INVALID_PATCH_REQUEST);
    }

    @GetMapping("/{intakeSessionId}")
    public ResponseEntity<IntakeSessionDetailResponse> getSession(
            @PathVariable String intakeSessionId) {
        IntakeSessionDetailResponse response = intakeSessionService.getSession(intakeSessionId);
        return ResponseEntity.ok(response);
    }
}
