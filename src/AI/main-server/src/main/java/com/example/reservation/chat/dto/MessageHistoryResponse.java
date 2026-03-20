package com.example.reservation.chat.dto;

import java.util.List;

public record MessageHistoryResponse(
        String sessionId,
        List<ChatMessageView> messages
) {
}

