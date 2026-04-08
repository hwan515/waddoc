package com.waddoc.bff.security;

import com.waddoc.shared.security.JwtPrincipal;
import com.waddoc.shared.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * BFF가 직접 소유한 읽기 엔드포인트에서만 사용하는 JWT 검증기다.
 */
@Component
public class BffJwtAuthService {

    private final JwtVerifier jwtVerifier;

    public BffJwtAuthService(@Value("${jwt.secret}") String secret) {
        this.jwtVerifier = new JwtVerifier(secret);
    }

    public void requireRole(String authorizationHeader, String role) {
        JwtPrincipal principal = requireAuthenticated(authorizationHeader);
        if (!principal.hasRole(role)) {
            throw new ResponseStatusException(FORBIDDEN, role + " role required");
        }
    }

    private JwtPrincipal requireAuthenticated(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null || !jwtVerifier.isValid(token)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return jwtVerifier.verify(token);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank() || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }
}
