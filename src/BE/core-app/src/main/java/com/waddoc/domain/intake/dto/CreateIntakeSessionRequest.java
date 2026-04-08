package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateIntakeSessionRequest {

    @NotBlank(message = "발신번호는 필수입니다.")
    @Size(max = 20, message = "발신번호는 20자 이하여야 합니다.")
    @Pattern(regexp = "^[0-9]{9,20}$", message = "발신번호는 9~20자리 숫자여야 합니다.")
    private String callerNumber;

    private IntakeChannel channel;
}
