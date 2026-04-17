package com.waddoc.domain.admin.service;

import com.waddoc.domain.user.entity.Role;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 관제용 쿠키 발급과 검증 규칙을 한곳에서 관리한다.
 */
@Service
public class AdminMonitoringService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final String cookieName;
    private final String cookiePath;
    private final long cookieMaxAge;

    public AdminMonitoringService(
            @Value("${monitoring.access.cookie-secret}") String cookieSecret,
            @Value("${monitoring.access.cookie-name}") String cookieName,
            @Value("${monitoring.access.cookie-path}") String cookiePath,
            @Value("${monitoring.access.cookie-max-age}") long cookieMaxAge
    ) {
        this.secretKey = Keys.hmacShaKeyFor(cookieSecret.getBytes(StandardCharsets.UTF_8));
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieMaxAge = cookieMaxAge;
    }

    public void issueSession(AuthenticatedUser authenticatedUser, HttpServletResponse response) {
        if (authenticatedUser == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (authenticatedUser.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(cookieMaxAge);
        String token = Jwts.builder()
                .subject(authenticatedUser.userId())
                .claim(ROLE_CLAIM, authenticatedUser.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, cookieMaxAge).toString());
    }

    public void revokeSession(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    public void authorize(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role = claims.get(ROLE_CLAIM, String.class);
            if (userId == null || userId.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
            }
            if (!Role.ADMIN.name().equals(role)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(true)
                .path(cookiePath)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }
}
