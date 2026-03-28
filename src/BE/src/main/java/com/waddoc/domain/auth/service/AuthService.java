package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.GuardianSignupRequest;
import com.waddoc.domain.auth.dto.GuardianSignupResponse;
import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.LoginResponse;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 회원가입과 로그인, 리프레시 토큰 수명주기를 한곳에서 관리한다.
 */
@Service
public class AuthService {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PatientGuardianLinkRepository patientGuardianLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginEligibilityService loginEligibilityService;
    private final boolean refreshCookieSecure;

    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            PatientGuardianLinkRepository patientGuardianLinkRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            LoginEligibilityService loginEligibilityService,
            @Value("${auth.refresh-cookie.secure:true}") boolean refreshCookieSecure
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.patientGuardianLinkRepository = patientGuardianLinkRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.loginEligibilityService = loginEligibilityService;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    /**
     * 보호자 계정을 만들고 환자와의 연결 요청을 함께 생성한다.
     */
    @Transactional
    public GuardianSignupResponse signupGuardian(GuardianSignupRequest request) {
        Patient patient = patientRepository.findByPhone(request.getPatientPhone())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_SIGNUP_FAILED));

        User existingUser = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (existingUser != null) {
            if (existingUser.getRole() == Role.GUARDIAN
                    && patientGuardianLinkRepository.existsByPatientAndGuardianUser(patient, existingUser)) {
                throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_EXISTS);
            }
            throw new BusinessException(ErrorCode.AUTH_USERNAME_CONFLICT);
        }

        User guardian = User.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName().trim())
                .role(Role.GUARDIAN)
                .build();
        userRepository.save(guardian);

        PatientGuardianLink link = PatientGuardianLink.builder()
                .patient(patient)
                .guardianUser(guardian)
                .relation(request.getRelation().trim())
                .build();
        patientGuardianLinkRepository.save(link);

        return GuardianSignupResponse.of(link);
    }

    /**
     * 비밀번호와 역할별 로그인 조건을 통과한 사용자에게 access/refresh 토큰을 발급한다.
     */
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

        loginEligibilityService.validate(user);

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

    /**
     * 전달된 refresh token이 아직 유효한지 검증한 뒤 새 토큰 쌍으로 교체한다.
     * 토큰 재사용이나 사용자 상태 변경이 감지되면 기존 토큰을 즉시 폐기한다.
     */
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
            refreshTokenService.deleteAllByUserId(userId);
            throw new BusinessException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }
        try {
            loginEligibilityService.validate(user);
        } catch (BusinessException e) {
            refreshTokenService.deleteAllByUserId(userId);
            throw e;
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

    /**
     * Redis에 저장된 refresh token과 브라우저 쿠키를 함께 정리한다.
     */
    public void logout(AuthenticatedUser authenticatedUser, String refreshToken, HttpServletResponse response) {
        if (authenticatedUser == null) {
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
                .secure(refreshCookieSecure)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .sameSite("Strict")
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
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

    private LoginResponse.UserInfo buildUserInfo(User user) {
        return LoginResponse.UserInfo.builder()
                .userId(user.getPublicId())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
