package com.waddoc.shared.security;

public record JwtPrincipal(
        String userId,
        String role
) {
    public boolean hasRole(String expectedRole) {
        return role != null && role.equalsIgnoreCase(expectedRole);
    }
}
