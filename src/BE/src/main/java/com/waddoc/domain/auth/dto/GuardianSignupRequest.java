package com.waddoc.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GuardianSignupRequest {

    @NotBlank(message = "username은 필수입니다.")
    private String username;

    @NotBlank(message = "password는 필수입니다.")
    private String password;

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    @NotBlank(message = "patientPhone은 필수입니다.")
    private String patientPhone;

    @NotBlank(message = "relation은 필수입니다.")
    private String relation;
}
