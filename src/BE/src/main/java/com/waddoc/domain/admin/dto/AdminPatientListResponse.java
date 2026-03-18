package com.waddoc.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminPatientListResponse {

    private List<AdminPatientSummaryResponse> patients;
    private long totalCount;
    private int page;
    private int size;

    public static AdminPatientListResponse of(List<AdminPatientSummaryResponse> patients, Page<?> page) {
        return AdminPatientListResponse.builder()
                .patients(patients)
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}
