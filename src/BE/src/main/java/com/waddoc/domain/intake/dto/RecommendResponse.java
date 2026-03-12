package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.Recommendation;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendResponse {

    private String recommendationId;
    private String symptomCategory;
    private String department;
    private String departmentName;
    private ConfidenceLevel confidenceLevel;
    private boolean isEmergency;
    private String reason;
    private List<AvailableSlotResponse> availableSlots;
    private String ttsMessage;

    public static RecommendResponse of(Recommendation rec, List<AvailableSlotResponse> slots, String ttsMessage) {
        return RecommendResponse.builder()
                .recommendationId(rec.getPublicId())
                .symptomCategory(rec.getSymptomIntake() != null
                        ? rec.getSymptomIntake().getSymptomCategory() : null)
                .department(rec.getDepartment())
                .departmentName(rec.getDepartmentName())
                .confidenceLevel(rec.getConfidenceLevel())
                .isEmergency(rec.isEmergency())
                .reason(rec.getReason())
                .availableSlots(slots)
                .ttsMessage(ttsMessage)
                .build();
    }
}
