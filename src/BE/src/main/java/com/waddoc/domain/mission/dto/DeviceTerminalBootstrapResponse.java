package com.waddoc.domain.mission.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DeviceTerminalBootstrapResponse {

    private String terminalId;
    private String deviceTerminalToken;
    private long expiresIn;
    private List<String> scopes;

    public static DeviceTerminalBootstrapResponse of(
            String terminalId,
            String deviceTerminalToken,
            long expiresIn,
            List<String> scopes
    ) {
        return DeviceTerminalBootstrapResponse.builder()
                .terminalId(terminalId)
                .deviceTerminalToken(deviceTerminalToken)
                .expiresIn(expiresIn)
                .scopes(List.copyOf(scopes))
                .build();
    }
}
