package com.waddoc.domain.consultation.controller;

import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.ConsultationSessionStatusResponse;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.dto.PostConsultationTokenRequest;
import com.waddoc.domain.consultation.dto.PutConsultationSummaryRequest;
import com.waddoc.domain.consultation.dto.ReissueConsultationTokenResponse;
import com.waddoc.domain.consultation.service.ConsultationPatientTokenService;
import com.waddoc.domain.consultation.service.ConsultationSessionQueryService;
import com.waddoc.domain.consultation.service.ConsultationSessionTokenService;
import com.waddoc.domain.consultation.service.ConsultationWebhookService;
import com.waddoc.domain.consultation.service.ConsultationSummaryService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ConsultationSessionController {

    private final ConsultationSummaryService consultationSummaryService;
    private final ConsultationWebhookService consultationWebhookService;
    private final ConsultationPatientTokenService consultationPatientTokenService;
    private final ConsultationSessionQueryService consultationSessionQueryService;
    private final ConsultationSessionTokenService consultationSessionTokenService;

    // 10.6: LiveKit 서버가 보내는 webhook 이벤트를 수신해 연결 상태를 반영한다.
    @PostMapping("/webhook/livekit")
    public ResponseEntity<Void> handleLiveKitWebhook(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        consultationWebhookService.handleWebhook(body, authorizationHeader);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/participants/patient/token")
    @PreAuthorize("hasAnyRole('ADMIN', 'MISSION_TERMINAL')")
    public ResponseEntity<IssuePatientTokenResponse> issuePatientToken(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                consultationPatientTokenService.issuePatientToken(sessionId, authentication)
        );
    }

    @PostMapping("/{sessionId}/token")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ReissueConsultationTokenResponse> reissueToken(
            @PathVariable String sessionId,
            @Valid @RequestBody PostConsultationTokenRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        // 의사/환자 재참여 시 만료된 LiveKit 토큰만 다시 발급한다.
        return ResponseEntity.ok(consultationSessionTokenService.reissueToken(sessionId, request, authenticatedUser));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ConsultationSessionStatusResponse> getSessionStatus(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        // 프런트가 현재 room 상태와 참가자 연결 상태를 복구할 수 있도록 세션 스냅샷을 내려준다.
        return ResponseEntity.ok(consultationSessionQueryService.getSessionStatus(sessionId, authenticatedUser));
    }

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
