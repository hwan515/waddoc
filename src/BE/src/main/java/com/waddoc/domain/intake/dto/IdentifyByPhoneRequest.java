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
public class IdentifyByPhoneRequest {

    @NotBlank(message = "전화번호는 필수입니다.")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    @Pattern(regexp = "^[0-9]{9,20}$", message = "전화번호는 9~20자리 숫자여야 합니다.")
    private String phone;
}
