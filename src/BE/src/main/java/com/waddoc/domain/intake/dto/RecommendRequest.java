package com.waddoc.domain.intake.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendRequest {

    @NotBlank
    private String symptomText;
}
