package com.example.reservation.chat.dto;

public record SttConnectionInfo(
        String wsUrl,
        String token,
        int sampleRate,
        int chunkMs,
        String encoding
) {
}

