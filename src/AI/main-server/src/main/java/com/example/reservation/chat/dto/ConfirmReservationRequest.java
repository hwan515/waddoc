package com.example.reservation.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmReservationRequest(
        @NotBlank String departmentCode
) {
}

