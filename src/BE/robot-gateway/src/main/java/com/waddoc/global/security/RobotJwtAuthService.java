package com.waddoc.global.security;

import com.waddoc.shared.security.JwtPrincipal;
import com.waddoc.shared.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * robot-gateway의 명령/SSE 엔드포인트에서 관리자 또는 의사 권한을 검증한다.
 */
@Component
public class RobotJwtAuthService {

    private final JwtVerifier jwtVerifier;

    public RobotJwtAuthService(@Value("${jwt.secret}") String secret) {
        this.jwtVerifier = new JwtVerifier(secret);
    }

    public JwtPrincipal requireAdminOrDoctor(String authorizationHeader) {
        JwtPrincipal principal = requireAuthenticated(authorizationHeader);
        if (!principal.hasRole("ADMIN") && !principal.hasRole("DOCTOR")) {
            throw new ResponseStatusException(FORBIDDEN, "ADMIN or DOCTOR role required");
        }
        return principal;
    }

    public JwtPrincipal requireAuthenticated(String authorizationHeader) {
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
