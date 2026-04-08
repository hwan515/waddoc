package com.waddoc.domain.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ClaimMissionTerminalRequest {

    @NotBlank(message = "phoneLast4 is required")
    @Pattern(regexp = "^[0-9]{4}$", message = "phoneLast4 must be 4 digits")
    private String phoneLast4;

    @NotBlank(message = "birthDate6 is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "birthDate6 must be 6 digits")
    private String birthDate6;
}
