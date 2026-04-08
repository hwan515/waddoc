package com.waddoc.domain.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateBookingRequest {

    @NotBlank(message = "슬롯 ID는 필수입니다.")
    private String slotId;
}
