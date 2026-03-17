package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.ConfidenceLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendResponse {

    private String symptomCategory;
    private String department;
    private String departmentName;
    private ConfidenceLevel confidenceLevel;
    private boolean isEmergency;
    private String reason;
    private List<AvailableSlotResponse> availableSlots;
    private String ttsMessage;

    public static RecommendResponse of(String symptomCategory, String department, String departmentName,
                                       ConfidenceLevel confidenceLevel, boolean isEmergency, String reason,
                                       List<AvailableSlotResponse> slots, String ttsMessage) {
        return RecommendResponse.builder()
                .symptomCategory(symptomCategory)
                .department(department)
                .departmentName(departmentName)
                .confidenceLevel(confidenceLevel)
                .isEmergency(isEmergency)
                .reason(reason)
                .availableSlots(slots)
                .ttsMessage(ttsMessage)
                .build();
    }
}
