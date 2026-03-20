package com.example.reservation.chat.dto;

import com.example.reservation.triage.dto.TriageRecommendationResponse;

public record SubmitMessageResponse(
        String sessionId,
        ChatMessageView userMessage,
        ChatMessageView assistantMessage,
        TriageRecommendationResponse recommendation,
        String ttsText
) {
}

