package com.example.reservation.chat.domain;

import com.example.reservation.chat.dto.ChatMessageView;

import java.util.List;

public record ChatSessionState(
        String sessionId,
        String locale,
        List<ChatMessageView> messages
) {
}

