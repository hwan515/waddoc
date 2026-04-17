package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CancelBookingResponse {

    private String bookingId;
    private String status;
    private OffsetDateTime cancelledAt;
    private String ttsMessage;

    public static CancelBookingResponse from(Booking booking, String ttsMessage) {
        return CancelBookingResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .cancelledAt(booking.getCancelledAt() != null
                        ? booking.getCancelledAt().atZone(KstTime.ZONE).toOffsetDateTime()
                        : null)
                .ttsMessage(ttsMessage)
                .build();
    }
}
