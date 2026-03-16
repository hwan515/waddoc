package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.TokenRefreshResponse;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenService);
    }

    @Test
    void refreshRotatesTokenWhenRefreshTokenIsValid() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "old-refresh-token";
        String userId = "usr_test01";
        long accessTokenExpiry = 900L;
        long refreshTokenExpiry = 604800L;

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.of(userId));
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(Role.DOCTOR);
        when(jwtTokenProvider.createAccessToken(userId, Role.DOCTOR)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(userId, Role.DOCTOR)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(accessTokenExpiry);
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(refreshTokenExpiry);

        TokenRefreshResponse refreshResponse = authService.refresh(refreshToken, response);

        assertThat(refreshResponse.getAccessToken()).isEqualTo("new-access-token");
        assertThat(refreshResponse.getExpiresIn()).isEqualTo((int) accessTokenExpiry);
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=new-refresh-token");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1/auth");

        verify(refreshTokenService).delete(refreshToken);
        verify(refreshTokenService).save("new-refresh-token", userId, refreshTokenExpiry);
    }

    @Test
    void refreshThrowsWhenCookieIsMissing() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.refresh(null, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_EXPIRED);

        verifyNoInteractions(refreshTokenService, jwtTokenProvider);
    }

    @Test
    void refreshThrowsWhenRefreshTokenIsNotStored() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "missing-refresh-token";

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(refreshToken, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_EXPIRED);

        verify(refreshTokenService).findUserIdByRefreshToken(refreshToken);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void refreshDeletesStoredTokenWhenJwtValidationFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "expired-refresh-token";

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.of("usr_test01"));
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(refreshToken, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_EXPIRED);

        verify(refreshTokenService).delete(refreshToken);
    }

    @Test
    void logoutDeletesStoredTokenAndExpiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "stored-refresh-token";

        authService.logout(refreshToken, response);

        verify(refreshTokenService).delete(refreshToken);
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=");
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1/auth");
    }
}
