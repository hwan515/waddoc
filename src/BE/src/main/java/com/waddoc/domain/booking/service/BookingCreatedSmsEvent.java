package com.waddoc.domain.booking.service;

public record BookingCreatedSmsEvent(
        String bookingId,
        String recipientPhone,
        String message
) {
}
