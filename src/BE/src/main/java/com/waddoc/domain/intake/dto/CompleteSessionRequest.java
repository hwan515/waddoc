package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.CompletionReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CompleteSessionRequest {

    @NotNull
    private CompletionReason completionReason;
}
