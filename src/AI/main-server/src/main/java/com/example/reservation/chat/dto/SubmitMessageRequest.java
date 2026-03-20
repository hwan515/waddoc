package com.example.reservation.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record SubmitMessageRequest(
        @NotBlank String turnId,
        @NotBlank String inputType,
        @NotBlank String text,
        @Valid SttMetaRequest sttMeta
) {
}

