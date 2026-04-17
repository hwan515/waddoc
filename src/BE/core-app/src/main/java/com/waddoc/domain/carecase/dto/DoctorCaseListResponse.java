package com.waddoc.domain.carecase.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DoctorCaseListResponse {

    private List<DoctorCaseSummaryResponse> cases;
    private int totalCount;

    public static DoctorCaseListResponse of(List<DoctorCaseSummaryResponse> cases) {
        return DoctorCaseListResponse.builder()
                .cases(cases)
                .totalCount(cases.size())
                .build();
    }
}
