package com.waddoc.domain.mission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceTerminalBootstrapRequest {

    @NotBlank(message = "terminalId is required")
    private String terminalId;

    @NotBlank(message = "terminalKey is required")
    private String terminalKey;
}
