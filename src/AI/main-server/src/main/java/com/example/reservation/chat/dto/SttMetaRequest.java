package com.example.reservation.chat.dto;

public record SttMetaRequest(
        String engine,
        String language,
        long durationMs
) {
}

