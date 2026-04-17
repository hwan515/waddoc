package com.waddoc.domain.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyByInfoRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "생년월일 6자리는 필수입니다.")
    @Pattern(regexp = "^[0-9]{6}$", message = "생년월일은 6자리 숫자여야 합니다.")
    private String birthDate6;
}
