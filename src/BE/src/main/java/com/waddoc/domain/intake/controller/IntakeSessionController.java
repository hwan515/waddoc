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

/**
 * 접수 세션의 생성, 환자 연결, 종료, 상세 조회를 담당한다.
 */
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

    /**
     * PATCH 요청 하나로 환자 연결과 세션 종료를 처리한다.
     * 요청 본문에 어떤 필드가 들어왔는지 보고 실제 동작을 분기한다.
     */
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
