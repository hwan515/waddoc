package com.waddoc.domain.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecordDtmfTurnRequest {

    @NotBlank
    @Size(max = 10)
    @Pattern(regexp = "^[0-9*#]+$", message = "DTMF 입력은 숫자, *, # 만 가능합니다.")
    private String dtmfInput;

    private String prompt;
    private String nextAction;
    private String ttsMessage;
}
