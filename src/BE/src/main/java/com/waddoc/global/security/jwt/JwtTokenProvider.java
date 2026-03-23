package com.waddoc.global.security.jwt;

import com.waddoc.domain.user.entity.Role;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.DeviceTerminalPrincipal;
import com.waddoc.global.security.MissionTerminalPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
@Getter
public class JwtTokenProvider {

    private static final long DEVICE_TERMINAL_TOKEN_EXPIRY_SECONDS = 1800L;
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String MISSION_ID_CLAIM = "missionId";
    private static final String CASE_ID_CLAIM = "caseId";
    private static final String TERMINAL_ID_CLAIM = "terminalId";
    private static final String VEHICLE_ID_CLAIM = "vehicleId";
    private static final String REGION_CODE_CLAIM = "regionCode";
    private static final String SCOPES_CLAIM = "scopes";
    private static final String MISSION_TERMINAL_TOKEN_TYPE = "MISSION_TERMINAL";
    private static final String DEVICE_TERMINAL_TOKEN_TYPE = "DEVICE_TERMINAL";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.mission-terminal-token-expiry:1800}")
    private long missionTerminalTokenExpiry;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String userId, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenExpiry);

        return Jwts.builder()
                .subject(userId)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(String userId, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(refreshTokenExpiry);

        return Jwts.builder()
                .subject(userId)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String createMissionTerminalToken(String missionId, String caseId, List<String> scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(missionTerminalTokenExpiry);

        return Jwts.builder()
                .subject("terminal:" + missionId)
                .claim(TOKEN_TYPE_CLAIM, MISSION_TERMINAL_TOKEN_TYPE)
                .claim(MISSION_ID_CLAIM, missionId)
                .claim(CASE_ID_CLAIM, caseId)
                .claim(SCOPES_CLAIM, scopes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String createDeviceTerminalToken(String terminalId, String vehicleId, String regionCode, List<String> scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(DEVICE_TERMINAL_TOKEN_EXPIRY_SECONDS);

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject("device-terminal:" + terminalId)
                .claim(TOKEN_TYPE_CLAIM, DEVICE_TERMINAL_TOKEN_TYPE)
                .claim(TERMINAL_ID_CLAIM, terminalId)
                .claim(SCOPES_CLAIM, scopes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey);
        if (vehicleId != null && !vehicleId.isBlank()) {
            builder.claim(VEHICLE_ID_CLAIM, vehicleId);
        }
        if (regionCode != null && !regionCode.isBlank()) {
            builder.claim(REGION_CODE_CLAIM, regionCode);
        }
        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public Role getRole(String token) {
        String roleValue = parseClaims(token).get(ROLE_CLAIM, String.class);
        return Role.valueOf(roleValue);
    }

    public AuthenticatedUser getAuthenticatedUser(String token) {
        return new AuthenticatedUser(getUserId(token), getRole(token));
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Object principal = buildPrincipal(claims);
        if (principal instanceof MissionTerminalPrincipal missionTerminalPrincipal) {
            return new UsernamePasswordAuthenticationToken(
                    missionTerminalPrincipal,
                    token,
                    missionTerminalPrincipal.getAuthorities()
            );
        }
        if (principal instanceof DeviceTerminalPrincipal deviceTerminalPrincipal) {
            return new UsernamePasswordAuthenticationToken(
                    deviceTerminalPrincipal,
                    token,
                    deviceTerminalPrincipal.getAuthorities()
            );
        }

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                token,
                authenticatedUser.getAuthorities()
        );
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    public long getMissionTerminalTokenExpiry() {
        return missionTerminalTokenExpiry;
    }

    public long getDeviceTerminalTokenExpiry() {
        return DEVICE_TERMINAL_TOKEN_EXPIRY_SECONDS;
    }

    private Object buildPrincipal(Claims claims) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (MISSION_TERMINAL_TOKEN_TYPE.equals(tokenType)) {
            return new MissionTerminalPrincipal(
                    claims.getSubject(),
                    claims.get(MISSION_ID_CLAIM, String.class),
                    claims.get(CASE_ID_CLAIM, String.class),
                    getScopes(claims)
            );
        }
        if (DEVICE_TERMINAL_TOKEN_TYPE.equals(tokenType)) {
            return new DeviceTerminalPrincipal(
                    claims.getSubject(),
                    claims.get(TERMINAL_ID_CLAIM, String.class),
                    claims.get(VEHICLE_ID_CLAIM, String.class),
                    claims.get(REGION_CODE_CLAIM, String.class),
                    getScopes(claims)
            );
        }

        return new AuthenticatedUser(claims.getSubject(), Role.valueOf(claims.get(ROLE_CLAIM, String.class)));
    }

    private List<String> getScopes(Claims claims) {
        List<?> rawScopes = claims.get(SCOPES_CLAIM, List.class);
        if (rawScopes == null) {
            return List.of();
        }

        List<String> scopes = new ArrayList<>();
        for (Object rawScope : rawScopes) {
            if (rawScope != null) {
                scopes.add(rawScope.toString());
            }
        }
        return List.copyOf(scopes);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
