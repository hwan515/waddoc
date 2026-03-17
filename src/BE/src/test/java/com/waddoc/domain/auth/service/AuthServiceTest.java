package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.TokenRefreshResponse;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import com.waddoc.global.type.ApprovalStatus;
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

    @Mock
    private LoginEligibilityService loginEligibilityService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService,
                loginEligibilityService
        );
    }

    @Test
    void loginThrowsInvalidCredentialsWhenUserIsMissing() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequest request = buildLoginRequest("doctor_kim", "Passw0rd!");

        when(userRepository.findByUsername("doctor_kim")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenService, loginEligibilityService);
    }

    @Test
    void loginThrowsPendingApprovalWhenUserIsPending() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequest request = buildLoginRequest("doctor_kim", "Passw0rd!");
        User pendingUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        when(userRepository.findByUsername("doctor_kim")).thenReturn(Optional.of(pendingUser));
        when(passwordEncoder.matches("Passw0rd!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_PENDING_APPROVAL);
    }

    @Test
    void loginThrowsAccountLockedWhenApprovedUserIsInactive() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequest request = buildLoginRequest("doctor_kim", "Passw0rd!");
        User inactiveUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(inactiveUser, "approvalStatus", ApprovalStatus.APPROVED);
        setField(inactiveUser, "active", false);

        when(userRepository.findByUsername("doctor_kim")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("Passw0rd!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void loginThrowsWhenDoctorEligibilityValidationFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequest request = buildLoginRequest("doctor_kim", "Passw0rd!");
        User doctor = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(doctor, "approvalStatus", ApprovalStatus.APPROVED);
        setField(doctor, "active", true);

        when(userRepository.findByUsername("doctor_kim")).thenReturn(Optional.of(doctor));
        when(passwordEncoder.matches("Passw0rd!", "encoded-password")).thenReturn(true);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED))
                .when(loginEligibilityService)
                .validate(doctor);

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED);

        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void refreshRotatesTokenWhenRefreshTokenIsValid() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "old-refresh-token";
        String userId = "usr_test01";
        long accessTokenExpiry = 900L;
        long refreshTokenExpiry = 604800L;
        User user = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(user, "publicId", userId);
        setField(user, "approvalStatus", ApprovalStatus.APPROVED);
        setField(user, "active", true);

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.of(userId));
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(Role.DOCTOR);
        when(jwtTokenProvider.createAccessToken(userId, Role.DOCTOR)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(userId, Role.DOCTOR)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(accessTokenExpiry);
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(refreshTokenExpiry);
        when(userRepository.findByPublicId(userId)).thenReturn(Optional.of(user));

        TokenRefreshResponse refreshResponse = authService.refresh(refreshToken, response);

        assertThat(refreshResponse.getAccessToken()).isEqualTo("new-access-token");
        assertThat(refreshResponse.getExpiresIn()).isEqualTo((int) accessTokenExpiry);
        assertThat(refreshResponse.getUser().getUserId()).isEqualTo(userId);
        assertThat(refreshResponse.getUser().getName()).isEqualTo("김의사");
        assertThat(refreshResponse.getUser().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=new-refresh-token");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1/auth");

        verify(loginEligibilityService).validate(user);
        verify(refreshTokenService).delete(refreshToken);
        verify(refreshTokenService).save("new-refresh-token", userId, refreshTokenExpiry);
    }

    @Test
    void refreshRevokesAllUserTokensWhenGuardianEligibilityFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "guardian-refresh-token";
        String userId = "usr_guardian01";
        User guardian = User.builder()
                .username("guardian_lee")
                .passwordHash("encoded-password")
                .name("이보호자")
                .role(Role.GUARDIAN)
                .build();
        setField(guardian, "publicId", userId);
        setField(guardian, "active", true);
        setField(guardian, "approvalStatus", ApprovalStatus.APPROVED);

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.of(userId));
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(Role.GUARDIAN);
        when(userRepository.findByPublicId(userId)).thenReturn(Optional.of(guardian));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED))
                .when(loginEligibilityService)
                .validate(guardian);

        assertThatThrownBy(() -> authService.refresh(refreshToken, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED);

        verify(refreshTokenService).deleteAllByUserId(userId);
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

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(refreshToken, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REUSE);

        verify(refreshTokenService).findUserIdByRefreshToken(refreshToken);
    }

    @Test
    void refreshDeletesStoredTokenWhenJwtValidationFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "expired-refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(refreshToken, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_EXPIRED);
    }

    @Test
    void logoutDeletesStoredTokenAndExpiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "stored-refresh-token";
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("usr_test01", Role.ADMIN);

        authService.logout(authenticatedUser, refreshToken, response);

        verify(refreshTokenService).delete(refreshToken);
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=");
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1/auth");
    }

    @Test
    void logoutThrowsUnauthorizedWhenBearerTokenIsMissing() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.logout(null, "stored-refresh-token", response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);

        verifyNoInteractions(refreshTokenService);
    }

    private LoginRequest buildLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        setField(request, "username", username);
        setField(request, "password", password);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
