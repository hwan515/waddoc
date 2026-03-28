package com.waddoc.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public record DeviceTerminalPrincipal(
        String subject,
        String terminalId,
        String vehicleId,
        String regionCode,
        List<String> scopes
) {

    private static final String ROLE_NAME = "DEVICE_TERMINAL";

    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_NAME));
    }

    public boolean hasScope(String requiredScope) {
        return scopes != null && scopes.contains(requiredScope);
    }

    public boolean hasVehicleBinding() {
        return vehicleId != null && !vehicleId.isBlank();
    }

    public boolean hasRegionBinding() {
        return regionCode != null && !regionCode.isBlank();
    }

    public String actorRole() {
        return ROLE_NAME;
    }
}
