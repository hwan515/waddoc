package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.LoginResponse;
import com.waddoc.domain.auth.dto.TokenRefreshResponse;
import com.waddoc.domain.user.entity.ApprovalStatus;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (user.isPendingApproval()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_PENDING_APPROVAL);
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getPublicId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getPublicId(), user.getRole());

        refreshTokenService.save(
                refreshToken,
                user.getPublicId(),
                jwtTokenProvider.getRefreshTokenExpiry()
        );

        addRefreshTokenCookie(response, refreshToken, jwtTokenProvider.getRefreshTokenExpiry());

        return buildLoginResponse(user, accessToken);
    }

    public TokenRefreshResponse refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }

        String savedUserId = refreshTokenService.findUserIdByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_REUSE));

        String userId = jwtTokenProvider.getUserId(refreshToken);
        Role role = jwtTokenProvider.getRole(refreshToken);
        if (!savedUserId.equals(userId)) {
            refreshTokenService.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REUSE);
        }

        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_EXPIRED));
        if (user.getRole() != role || !user.isActive() || user.getApprovalStatus() != ApprovalStatus.APPROVED) {
            refreshTokenService.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, role);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, role);

        refreshTokenService.delete(refreshToken);
        refreshTokenService.save(
                newRefreshToken,
                userId,
                jwtTokenProvider.getRefreshTokenExpiry()
        );

        addRefreshTokenCookie(response, newRefreshToken, jwtTokenProvider.getRefreshTokenExpiry());

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn((int) jwtTokenProvider.getAccessTokenExpiry())
                .user(buildUserInfo(user))
                .build();
    }

    public void logout(String authorizationHeader, String refreshToken, HttpServletResponse response) {
        String accessToken = extractBearerToken(authorizationHeader);
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.delete(refreshToken);
        }

        deleteRefreshTokenCookie(response);
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false) // 로컬 개발 단계에서는 false, 운영에서는 true 고려
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .sameSite("Strict")
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .sameSite("Strict")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private LoginResponse buildLoginResponse(User user, String accessToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn((int) jwtTokenProvider.getAccessTokenExpiry())
                .user(buildUserInfo(user))
                .build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }

    private LoginResponse.UserInfo buildUserInfo(User user) {
        return LoginResponse.UserInfo.builder()
                .userId(user.getPublicId())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
