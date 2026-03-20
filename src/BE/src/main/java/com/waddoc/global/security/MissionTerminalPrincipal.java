package com.waddoc.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public record MissionTerminalPrincipal(
        String subject,
        String missionId,
        String caseId,
        List<String> scopes
) {

    private static final String ROLE_NAME = "MISSION_TERMINAL";

    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_NAME));
    }

    public boolean hasScope(String requiredScope) {
        return scopes != null && scopes.contains(requiredScope);
    }

    public String actorRole() {
        return ROLE_NAME;
    }
}
