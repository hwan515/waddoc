package com.waddoc.domain.intake.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BindPatientRequest {

    @NotBlank(message = "환자 ID는 필수입니다.")
    private String patientId;
}
