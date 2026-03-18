package com.waddoc.domain.consultation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 기반 disconnect 타이머 관리.
 * 다중 인스턴스 환경에서도 안전하게 동작한다.
 *
 * 키 형식: disconnect:{sessionId}:{role}
 * 값: 만료 시각 (epoch millis)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectTimerService {

    private static final String KEY_PREFIX = "disconnect:";
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public void schedule(String sessionId, String role) {
        String key = buildKey(sessionId, role);
        String value = String.valueOf(Instant.now().plusSeconds(RECONNECT_TIMEOUT.getSeconds()).toEpochMilli());
        redisTemplate.opsForValue().set(key, value, RECONNECT_TIMEOUT);
        log.debug("Scheduled disconnect timer: key={}, ttl={}s", key, RECONNECT_TIMEOUT.getSeconds());
    }

    public void cancel(String sessionId, String role) {
        String key = buildKey(sessionId, role);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Cancelled disconnect timer: key={}", key);
        }
    }

    public boolean exists(String sessionId, String role) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(sessionId, role)));
    }

    static String buildKey(String sessionId, String role) {
        return KEY_PREFIX + sessionId + ":" + role;
    }

    static String parseSessionId(String expiredKey) {
        String stripped = expiredKey.substring(KEY_PREFIX.length());
        int lastColon = stripped.lastIndexOf(':');
        return lastColon > 0 ? stripped.substring(0, lastColon) : stripped;
    }

    static String parseRole(String expiredKey) {
        int lastColon = expiredKey.lastIndexOf(':');
        return lastColon > 0 ? expiredKey.substring(lastColon + 1) : "";
    }

    static boolean isDisconnectKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }
}
