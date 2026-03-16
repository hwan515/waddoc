package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.dto.LoginRequest;
import com.waddoc.domain.auth.dto.LoginResponse;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getPublicId(), user.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(900)
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getPublicId())
                        .name(user.getName())
                        .role(user.getRole())
                        .build())
                .build();
    }
}