package com.waddoc.domain.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobotTelemetryProcessingGuardTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RobotTelemetryProcessingGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RobotTelemetryProcessingGuard(stringRedisTemplate);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void evaluate_rejectsDuplicateSourceEventId() {
        when(hashOperations.entries("robot:telemetry:last:veh_GIMCHEON_01:robot/odom"))
                .thenReturn(Map.of("sourceEventId", "evt-1", "occurredAt", "2026-04-09T10:00:00Z"));

        RobotTelemetryProcessingGuard.Decision decision = guard.evaluate(
                "veh_GIMCHEON_01",
                "robot/odom",
                "evt-1",
                OffsetDateTime.parse("2026-04-09T10:01:00Z")
        );

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).isEqualTo("duplicate");
        verify(hashOperations, never()).putAll(any(), any());
    }

    @Test
    void evaluate_rejectsOutdatedEvent() {
        when(hashOperations.entries("robot:telemetry:last:veh_GIMCHEON_01:robot/state"))
                .thenReturn(Map.of("sourceEventId", "evt-2", "occurredAt", "2026-04-09T10:05:00Z"));

        RobotTelemetryProcessingGuard.Decision decision = guard.evaluate(
                "veh_GIMCHEON_01",
                "robot/state",
                "evt-3",
                OffsetDateTime.parse("2026-04-09T10:04:59Z")
        );

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).isEqualTo("outdated");
        verify(hashOperations, never()).putAll(any(), any());
    }

    @Test
    void evaluate_acceptsAndStoresFreshEvent() {
        when(hashOperations.entries("robot:telemetry:last:veh_GIMCHEON_01:robot/minimap"))
                .thenReturn(Map.of("sourceEventId", "evt-1", "occurredAt", "2026-04-09T10:00:00Z"));

        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-04-09T10:00:01Z");
        RobotTelemetryProcessingGuard.Decision decision = guard.evaluate(
                "veh_GIMCHEON_01",
                "robot/minimap",
                "evt-2",
                occurredAt
        );

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.reason()).isEqualTo("accepted");
        verify(hashOperations).putAll(eq("robot:telemetry:last:veh_GIMCHEON_01:robot/minimap"), eq(Map.of(
                "sourceEventId", "evt-2",
                "occurredAt", occurredAt.toString()
        )));
        verify(stringRedisTemplate).expire("robot:telemetry:last:veh_GIMCHEON_01:robot/minimap", Duration.ofDays(7));
    }
}
