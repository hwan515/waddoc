package com.waddoc.domain.notification.event;

public record SmsRequestMessage(
        String recipientPhone,
        String message,
        String correlationId
) {
}
