package com.waddoc.domain.booking.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelBookingRequest {

    private String cancelReason;
}
