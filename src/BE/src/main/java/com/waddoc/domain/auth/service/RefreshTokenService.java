package com.waddoc.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * 리프레시 토큰을 Redis에 저장하고 사용자별 인덱스를 함께 관리한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String USER_REFRESH_TOKEN_PREFIX = "refresh:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String refreshToken, String userId, long refreshTokenExpirySeconds) {
        String key = generateKey(refreshToken);
        stringRedisTemplate.opsForValue()
                .set(key, userId, Duration.ofSeconds(refreshTokenExpirySeconds));
        stringRedisTemplate.opsForSet()
                .add(generateUserIndexKey(userId), key);
        stringRedisTemplate.expire(generateUserIndexKey(userId), Duration.ofSeconds(refreshTokenExpirySeconds));
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
        String userId = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);
        if (userId != null) {
            stringRedisTemplate.opsForSet().remove(generateUserIndexKey(userId), key);
        }
    }

    public void deleteAllByUserId(String userId) {
        String indexKey = generateUserIndexKey(userId);
        Set<String> tokenKeys = stringRedisTemplate.opsForSet().members(indexKey);
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            stringRedisTemplate.delete(tokenKeys);
        }
        stringRedisTemplate.delete(indexKey);
    }

    private String generateKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + hashToken(refreshToken);
    }

    private String generateUserIndexKey(String userId) {
        return USER_REFRESH_TOKEN_PREFIX + userId;
    }

    private String hashToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
