package com.waddoc.domain.intake.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendRequest {

    private String symptomText;
    private String departmentCode;
}
