package com.example.reservation.triage.dto;

import com.example.reservation.chat.dto.ChatMessageView;

import java.util.List;

public record TriageRecommendationRequest(
        String sessionId,
        String turnId,
        String transcript,
        List<ChatMessageView> history
) {
}

