package com.waddoc.domain.auth.controller;

import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.LoginResponse;
import com.waddoc.domain.auth.dto.GuardianSignupRequest;
import com.waddoc.domain.auth.dto.GuardianSignupResponse;
import com.waddoc.domain.auth.dto.TokenRefreshResponse;
import com.waddoc.domain.auth.service.AuthService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인, 로그아웃, 토큰 재발급, 보호자 회원가입 요청을 받는 인증 진입점이다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/guardians/signup")
    public ResponseEntity<GuardianSignupResponse> signupGuardian(
            @Valid @RequestBody GuardianSignupRequest request
    ) {
        GuardianSignupResponse response = authService.signupGuardian(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(request, response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        TokenRefreshResponse refreshResponse = authService.refresh(refreshToken, response);
        return ResponseEntity.ok(refreshResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(authenticatedUser, refreshToken, response);
        return ResponseEntity.noContent().build();
    }
}
