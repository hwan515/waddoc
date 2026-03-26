package com.waddoc.domain.guardian.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuardianConsultationSummariesResponse {

    private String patientName;
    private List<GuardianConsultationSummaryResponse> summaries;
    private int totalCount;

    public static GuardianConsultationSummariesResponse of(
            String patientName,
            List<GuardianConsultationSummaryResponse> summaries
    ) {
        return GuardianConsultationSummariesResponse.builder()
                .patientName(patientName)
                .summaries(summaries)
                .totalCount(summaries.size())
                .build();
    }
}
