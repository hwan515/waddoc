package com.waddoc.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 분리 서비스가 같은 JWT 서명을 검증할 수 있도록 제공하는 최소 검증 유틸이다.
 */
public class JwtVerifier {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;

    public JwtVerifier(String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtPrincipal verify(String token) {
        Claims claims = parseClaims(token);
        return new JwtPrincipal(claims.getSubject(), claims.get(ROLE_CLAIM, String.class));
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
