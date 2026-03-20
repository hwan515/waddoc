package com.example.reservation.chat.dto;

public record ConfirmReservationResponse(
        String sessionId,
        String status,
        String departmentCode
) {
}

