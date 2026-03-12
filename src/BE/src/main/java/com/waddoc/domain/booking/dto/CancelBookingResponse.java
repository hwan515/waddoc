package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CancelBookingResponse {

    private String bookingId;
    private String status;
    private LocalDateTime cancelledAt;
    private String ttsMessage;

    public static CancelBookingResponse from(Booking booking, String ttsMessage) {
        return CancelBookingResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .cancelledAt(booking.getCancelledAt())
                .ttsMessage(ttsMessage)
                .build();
    }
}
