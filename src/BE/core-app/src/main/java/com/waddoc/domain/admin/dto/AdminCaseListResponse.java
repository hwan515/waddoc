package com.waddoc.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminCaseListResponse {

    private List<AdminCaseSummaryResponse> cases;
    private long totalCount;
    private int page;
    private int size;

    public static AdminCaseListResponse of(List<AdminCaseSummaryResponse> cases, Page<?> page) {
        return AdminCaseListResponse.builder()
                .cases(cases)
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}
