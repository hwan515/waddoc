package com.waddoc.domain.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BookingListResponse {

    private List<BookingSummaryResponse> bookings;
    private int totalCount;

    public static BookingListResponse of(List<BookingSummaryResponse> bookings) {
        return BookingListResponse.builder()
                .bookings(bookings)
                .totalCount(bookings.size())
                .build();
    }
}
