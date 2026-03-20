package com.waddoc.domain.mission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionIdentityCheckCacheService {

    private static final String IDENTITY_CHECK_KEY_PREFIX = "identity-check:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${consultation.identity-check-cache-ttl-seconds:600}")
    private long identityCheckCacheTtlSeconds;

    public VerifiedIdentityCheck saveVerified(String missionId, String patientId) {
        OffsetDateTime verifiedAt = OffsetDateTime.now(KST);
        stringRedisTemplate.opsForValue().set(
                generateKey(missionId, patientId),
                verifiedAt.toString(),
                Duration.ofSeconds(identityCheckCacheTtlSeconds)
        );
        return new VerifiedIdentityCheck(verifiedAt, identityCheckCacheTtlSeconds);
    }

    public Optional<VerifiedIdentityCheck> findVerified(String missionId, String patientId) {
        String key = generateKey(missionId, patientId);
        String verifiedAtRaw = stringRedisTemplate.opsForValue().get(key);
        if (verifiedAtRaw == null || verifiedAtRaw.isBlank()) {
            return Optional.empty();
        }

        Long expiresInSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expiresInSeconds == null || expiresInSeconds <= 0) {
            stringRedisTemplate.delete(key);
            return Optional.empty();
        }

        try {
            return Optional.of(new VerifiedIdentityCheck(OffsetDateTime.parse(verifiedAtRaw), expiresInSeconds));
        } catch (RuntimeException e) {
            log.warn("Invalid cached identity-check timestamp. missionId={}, patientId={}", missionId, patientId, e);
            stringRedisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private String generateKey(String missionId, String patientId) {
        return IDENTITY_CHECK_KEY_PREFIX + missionId + ":" + patientId;
    }

    public record VerifiedIdentityCheck(OffsetDateTime verifiedAt, long expiresInSeconds) {
    }
}
