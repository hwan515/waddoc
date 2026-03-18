package com.waddoc.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class GuardianLinkRequestListResponse {

    private List<GuardianLinkRequestSummaryResponse> requests;
    private long totalCount;
    private int page;
    private int size;

    public static GuardianLinkRequestListResponse of(List<GuardianLinkRequestSummaryResponse> requests, Page<?> page) {
        return GuardianLinkRequestListResponse.builder()
                .requests(requests)
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}
