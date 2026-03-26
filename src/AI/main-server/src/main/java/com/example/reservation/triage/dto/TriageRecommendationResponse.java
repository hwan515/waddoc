package com.example.reservation.triage.dto;

public record TriageRecommendationResponse(
        String departmentCode,
        String departmentName,
        String assistantMessage,
        String ttsText,
        double confidence,
        String reason
) {
}

