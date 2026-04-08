package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveKitMonitoringMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LiveKitMonitoringMetrics liveKitMonitoringMetrics = new LiveKitMonitoringMetrics(meterRegistry);

    @Test
    void recordRoomOperation_recordsSuccessCounterAndTimer() {
        liveKitMonitoringMetrics.recordRoomOperation("create", () -> {
        });

        assertThat(meterRegistry.get("waddoc.livekit.room.operations")
                .tag("operation", "create")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.livekit.api.duration")
                .tag("operation", "create")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordTokenIssuanceAndWebhookFailure_recordCounters() {
        String token = liveKitMonitoringMetrics.recordTokenIssuance("doctor", () -> "doctor-token");
        liveKitMonitoringMetrics.recordWebhookFailure("invalid_signature");

        assertThat(token).isEqualTo("doctor-token");
        assertThat(meterRegistry.get("waddoc.livekit.token.issued")
                .tag("participant_type", "doctor")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.livekit.webhook.events")
                .tag("event", "invalid_signature")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordRoomOperation_recordsFailureCounterAndTimer() {
        assertThatThrownBy(() -> liveKitMonitoringMetrics.recordRoomOperation("delete", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("waddoc.livekit.room.operations")
                .tag("operation", "delete")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.livekit.api.duration")
                .tag("operation", "delete")
                .tag("result", "fail")
                .timer()
                .count()).isEqualTo(1L);
    }
}
