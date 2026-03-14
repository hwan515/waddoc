package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class CancelBookingResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String bookingId;
    private String status;
    private OffsetDateTime cancelledAt;
    private String ttsMessage;

    public static CancelBookingResponse from(Booking booking, String ttsMessage) {
        return CancelBookingResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .cancelledAt(booking.getCancelledAt() != null
                        ? booking.getCancelledAt().atZone(KST).toOffsetDateTime()
                        : null)
                .ttsMessage(ttsMessage)
                .build();
    }
}
