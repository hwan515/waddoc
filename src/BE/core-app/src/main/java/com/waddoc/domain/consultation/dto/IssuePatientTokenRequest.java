package com.waddoc.domain.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssuePatientTokenRequest {

    @NotBlank(message = "patientId는 필수입니다.")
    private String patientId;
}
