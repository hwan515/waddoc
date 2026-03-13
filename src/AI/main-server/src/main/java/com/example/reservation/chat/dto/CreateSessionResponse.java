package com.example.reservation.chat.dto;

public record CreateSessionResponse(
        String sessionId,
        SttConnectionInfo stt
) {
}

