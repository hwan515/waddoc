package com.waddoc.domain.consultation.controller;

import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.PutConsultationSummaryRequest;
import com.waddoc.domain.consultation.service.ConsultationSummaryService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ConsultationSessionController {

    private final ConsultationSummaryService consultationSummaryService;

    // 10.5 조회: 담당 의사나 관리자가 저장된 진료 요약을 확인할 수 있다.
    @GetMapping("/{sessionId}/summary")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ConsultationSummaryResponse> getSummary(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(consultationSummaryService.getSummary(sessionId, authenticatedUser));
    }

    // 10.5: 의사만 자신의 진료 세션 요약을 저장하고 종료할 수 있다.
    @PutMapping("/{sessionId}/summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ConsultationSummaryResponse> saveSummary(
            @PathVariable String sessionId,
            @Valid @RequestBody PutConsultationSummaryRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(consultationSummaryService.saveSummary(sessionId, request, authenticatedUser));
    }
}
