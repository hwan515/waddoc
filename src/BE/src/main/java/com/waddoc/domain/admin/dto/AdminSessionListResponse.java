package com.waddoc.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminSessionListResponse {

    private List<AdminSessionSummaryResponse> sessions;
    private long totalCount;
    private int page;
    private int size;

    public static AdminSessionListResponse of(List<AdminSessionSummaryResponse> sessions, Page<?> page) {
        return AdminSessionListResponse.builder()
                .sessions(sessions)
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}
