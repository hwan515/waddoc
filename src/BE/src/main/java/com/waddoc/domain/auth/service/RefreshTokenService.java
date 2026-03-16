package com.waddoc.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String refreshToken, String userId, long refreshTokenExpirySeconds) {
        String key = generateKey(refreshToken);
        stringRedisTemplate.opsForValue()
                .set(key, userId, Duration.ofSeconds(refreshTokenExpirySeconds));
    }

    public Optional<String> findUserIdByRefreshToken(String refreshToken) {
        String key = generateKey(refreshToken);
        String userId = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(userId);
    }

    public boolean exists(String refreshToken) {
        String key = generateKey(refreshToken);
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public void delete(String refreshToken) {
        String key = generateKey(refreshToken);
        stringRedisTemplate.delete(key);
    }

    private String generateKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + refreshToken;
    }
}