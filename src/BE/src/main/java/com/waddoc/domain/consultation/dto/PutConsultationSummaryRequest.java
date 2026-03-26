package com.waddoc.domain.consultation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PutConsultationSummaryRequest {

    @NotBlank(message = "summaryNote는 필수입니다.")
    private String summaryNote;

    @JsonProperty("isPrescriptionIssued")
    private boolean prescriptionIssued;

    private String prescriptionNote;

    private boolean needsFollowUp;
}
