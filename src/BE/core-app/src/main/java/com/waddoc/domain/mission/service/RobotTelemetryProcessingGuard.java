package com.waddoc.domain.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * robot-gateway가 보낸 telemetry 이벤트의 최신 처리 상태를 Redis에 기록해
 * Kafka 재전송이나 그룹 재합류 시 오래된 이벤트를 다시 반영하지 않게 막는다.
 */
@Service
@RequiredArgsConstructor
public class RobotTelemetryProcessingGuard {

    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "robot:telemetry:last:";
    private static final String FIELD_SOURCE_EVENT_ID = "sourceEventId";
    private static final String FIELD_OCCURRED_AT = "occurredAt";

    private final StringRedisTemplate stringRedisTemplate;

    public Decision evaluate(String aggregateId, String sourceTopic, String sourceEventId, OffsetDateTime occurredAt) {
        if (isBlank(aggregateId) || isBlank(sourceTopic) || isBlank(sourceEventId) || occurredAt == null) {
            return Decision.accepted("insufficient_metadata");
        }

        String key = buildKey(aggregateId, sourceTopic);
        HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
        Map<String, String> snapshot = hashOperations.entries(key);
        String lastSourceEventId = snapshot.get(FIELD_SOURCE_EVENT_ID);
        if (sourceEventId.equals(lastSourceEventId)) {
            return Decision.rejected("duplicate");
        }

        OffsetDateTime lastOccurredAt = parseOccurredAt(snapshot.get(FIELD_OCCURRED_AT));
        if (lastOccurredAt != null && occurredAt.isBefore(lastOccurredAt)) {
            return Decision.rejected("outdated");
        }

        hashOperations.putAll(key, Map.of(
                FIELD_SOURCE_EVENT_ID, sourceEventId,
                FIELD_OCCURRED_AT, occurredAt.toString()
        ));
        stringRedisTemplate.expire(key, CACHE_TTL);
        return Decision.accepted("accepted");
    }

    private String buildKey(String aggregateId, String sourceTopic) {
        return KEY_PREFIX + aggregateId + ":" + sourceTopic;
    }

    private OffsetDateTime parseOccurredAt(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Decision(boolean accepted, String reason) {
        public static Decision accepted(String reason) {
            return new Decision(true, reason);
        }

        public static Decision rejected(String reason) {
            return new Decision(false, reason);
        }
    }
}
