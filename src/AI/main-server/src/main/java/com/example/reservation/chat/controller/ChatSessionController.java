package com.example.reservation.chat.controller;

import com.example.reservation.chat.dto.ConfirmReservationRequest;
import com.example.reservation.chat.dto.ConfirmReservationResponse;
import com.example.reservation.chat.dto.CreateSessionRequest;
import com.example.reservation.chat.dto.CreateSessionResponse;
import com.example.reservation.chat.dto.MessageHistoryResponse;
import com.example.reservation.chat.dto.SubmitMessageRequest;
import com.example.reservation.chat.dto.SubmitMessageResponse;
import com.example.reservation.chat.service.ChatSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public CreateSessionResponse createSession(@RequestBody(required = false) CreateSessionRequest request) {
        return chatSessionService.createSession(request == null ? new CreateSessionRequest("ko-KR") : request);
    }

    @GetMapping("/{sessionId}/messages")
    public MessageHistoryResponse getMessages(@PathVariable String sessionId) {
        return chatSessionService.getMessages(sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public SubmitMessageResponse submitMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitMessageRequest request
    ) {
        return chatSessionService.submitMessage(sessionId, request);
    }

    @PostMapping("/{sessionId}/reservations/confirm")
    public ConfirmReservationResponse confirmReservation(
            @PathVariable String sessionId,
            @Valid @RequestBody ConfirmReservationRequest request
    ) {
        return chatSessionService.confirmReservation(sessionId, request);
    }
}
