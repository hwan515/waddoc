package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.GuardianSignupRequest;
import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.TokenRefreshResponse;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientGuardianLinkRepository patientGuardianLinkRepository;

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
                patientRepository,
                patientGuardianLinkRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService,
                loginEligibilityService,
                true
        );
    }

    @Test
    void signupGuardianCreatesPendingGuardianAndLink() {
        Patient patient = buildPatient();
        GuardianSignupRequest request = buildGuardianSignupRequest(
                "guardian_lee",
                "Passw0rd!",
                "Guardian Lee",
                "01012345678",
                "daughter"
        );

        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.of(patient));
        when(userRepository.findByUsername("guardian_lee")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-password");

        var response = authService.signupGuardian(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<PatientGuardianLink> linkCaptor = ArgumentCaptor.forClass(PatientGuardianLink.class);
        verify(userRepository).save(userCaptor.capture());
        verify(patientGuardianLinkRepository).save(linkCaptor.capture());

        User savedGuardian = userCaptor.getValue();
        PatientGuardianLink savedLink = linkCaptor.getValue();

        assertThat(savedGuardian.getRole()).isEqualTo(Role.GUARDIAN);
        assertThat(savedGuardian.isPendingApproval()).isTrue();
        assertThat(savedGuardian.isActive()).isFalse();
        assertThat(savedLink.getPatient()).isEqualTo(patient);
        assertThat(savedLink.getGuardianUser()).isEqualTo(savedGuardian);
        assertThat(response.getUserId()).isEqualTo(savedGuardian.getPublicId());
        assertThat(response.getLinkId()).isEqualTo(savedLink.getPublicId());
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getPatient().getNameMasked()).isEqualTo("홍*동");
        assertThat(response.getPatient().getBirthDate6Masked()).isEqualTo("5803**");
        assertThat(response.getMessage()).isEqualTo("가입 요청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
    }

    @Test
    void signupGuardianThrowsWhenPatientPhoneIsMissing() {
        GuardianSignupRequest request = buildGuardianSignupRequest(
                "guardian_lee",
                "Passw0rd!",
                "Guardian Lee",
                "01012345678",
                "daughter"
        );

        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signupGuardian(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GUARDIAN_SIGNUP_FAILED);

        verifyNoInteractions(userRepository, passwordEncoder, patientGuardianLinkRepository);
    }

    @Test
    void signupGuardianThrowsUsernameConflictWhenUsernameIsAlreadyUsed() {
        Patient patient = buildPatient();
        User existingUser = buildUser(Role.ADMIN, "guardian_lee", "Existing Admin");
        GuardianSignupRequest request = buildGuardianSignupRequest(
                "guardian_lee",
                "Passw0rd!",
                "Guardian Lee",
                "01012345678",
                "daughter"
        );

        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.of(patient));
        when(userRepository.findByUsername("guardian_lee")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.signupGuardian(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_USERNAME_CONFLICT);
    }

    @Test
    void signupGuardianThrowsLinkAlreadyExistsForSameGuardianAndPatient() {
        Patient patient = buildPatient();
        User existingGuardian = buildUser(Role.GUARDIAN, "guardian_lee", "Guardian Lee");
        GuardianSignupRequest request = buildGuardianSignupRequest(
                "guardian_lee",
                "Passw0rd!",
                "Guardian Lee",
                "01012345678",
                "daughter"
        );

        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.of(patient));
        when(userRepository.findByUsername("guardian_lee")).thenReturn(Optional.of(existingGuardian));
        when(patientGuardianLinkRepository.existsByPatientAndGuardianUser(patient, existingGuardian)).thenReturn(true);

        assertThatThrownBy(() -> authService.signupGuardian(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GUARDIAN_LINK_ALREADY_EXISTS);
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
        User pendingUser = buildUser(Role.DOCTOR, "doctor_kim", "Doctor Kim");

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
        User inactiveUser = buildUser(Role.DOCTOR, "doctor_kim", "Doctor Kim");
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
        User doctor = buildUser(Role.DOCTOR, "doctor_kim", "Doctor Kim");
        setField(doctor, "approvalStatus", ApprovalStatus.APPROVED);
        setField(doctor, "active", true);

        when(userRepository.findByUsername("doctor_kim")).thenReturn(Optional.of(doctor));
        when(passwordEncoder.matches("Passw0rd!", "encoded-password")).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED))
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
        User user = buildUser(Role.DOCTOR, "doctor_kim", "Doctor Kim");
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
        assertThat(refreshResponse.getUser().getName()).isEqualTo("Doctor Kim");
        assertThat(refreshResponse.getUser().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=new-refresh-token");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1/auth");
        assertThat(response.getHeader("Set-Cookie")).contains("Secure");

        verify(loginEligibilityService).validate(user);
        verify(refreshTokenService).delete(refreshToken);
        verify(refreshTokenService).save("new-refresh-token", userId, refreshTokenExpiry);
    }

    @Test
    void refreshRevokesAllUserTokensWhenGuardianEligibilityFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String refreshToken = "guardian-refresh-token";
        String userId = "usr_guardian01";
        User guardian = buildUser(Role.GUARDIAN, "guardian_lee", "Guardian Lee");
        setField(guardian, "publicId", userId);
        setField(guardian, "active", true);
        setField(guardian, "approvalStatus", ApprovalStatus.APPROVED);

        when(refreshTokenService.findUserIdByRefreshToken(refreshToken)).thenReturn(Optional.of(userId));
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(Role.GUARDIAN);
        when(userRepository.findByPublicId(userId)).thenReturn(Optional.of(guardian));
        doThrow(new BusinessException(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED))
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
        assertThat(response.getHeader("Set-Cookie")).contains("Secure");
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

    private Patient buildPatient() {
        return Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung")
                .phone("01012345678")
                .build();
    }

    private User buildUser(Role role, String username, String name) {
        return User.builder()
                .username(username)
                .passwordHash("encoded-password")
                .name(name)
                .role(role)
                .build();
    }

    private LoginRequest buildLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        setField(request, "username", username);
        setField(request, "password", password);
        return request;
    }

    private GuardianSignupRequest buildGuardianSignupRequest(
            String username,
            String password,
            String name,
            String patientPhone,
            String relation
    ) {
        GuardianSignupRequest request = new GuardianSignupRequest();
        setField(request, "username", username);
        setField(request, "password", password);
        setField(request, "name", name);
        setField(request, "patientPhone", patientPhone);
        setField(request, "relation", relation);
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
