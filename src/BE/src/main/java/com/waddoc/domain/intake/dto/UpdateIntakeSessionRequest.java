package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.CompletionReason;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@NoArgsConstructor
public class UpdateIntakeSessionRequest {

    private String patientId;
    private IntakeStatus status;
    private CompletionReason completionReason;

    public boolean isBindPatientRequest() {
        return StringUtils.hasText(patientId)
                && status == null
                && completionReason == null;
    }

    public boolean isCompleteSessionRequest() {
        return !StringUtils.hasText(patientId)
                && status == IntakeStatus.COMPLETED
                && completionReason != null;
    }
}
