package com.waddoc.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminBookingListResponse {

    private List<AdminBookingSummaryResponse> bookings;
    private long totalCount;
    private int page;
    private int size;

    public static AdminBookingListResponse of(List<AdminBookingSummaryResponse> bookings, Page<?> page) {
        return AdminBookingListResponse.builder()
                .bookings(bookings)
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}
