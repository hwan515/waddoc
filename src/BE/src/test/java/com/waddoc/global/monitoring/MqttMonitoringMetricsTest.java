package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttMonitoringMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MqttMonitoringMetrics mqttMonitoringMetrics = new MqttMonitoringMetrics(meterRegistry);

    @Test
    void recordInboundProcessing_recordsSuccessCounterTimerAndLastReceivedGauge() {
        boolean handled = mqttMonitoringMetrics.recordInboundProcessing("robot/odom", () -> true);

        assertThat(handled).isTrue();
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.processed")
                .tag("topic", "robot/odom")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.duration")
                .tag("topic", "robot/odom")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.last.received.epoch")
                .tag("topic", "robot/odom")
                .gauge()
                .value()).isPositive();
    }

    @Test
    void recordInboundProcessing_recordsIgnoredCounterAndTimerForUnhandledTopic() {
        boolean handled = mqttMonitoringMetrics.recordInboundProcessing("robot/unknown", () -> false);

        assertThat(handled).isFalse();
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.processed")
                .tag("topic", "robot/unknown")
                .tag("result", "ignored")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.duration")
                .tag("topic", "robot/unknown")
                .tag("result", "ignored")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordInboundProcessing_recordsFailureCounterAndTimer() {
        assertThatThrownBy(() -> mqttMonitoringMetrics.recordInboundProcessing("robot/state", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("waddoc.mqtt.inbound.processed")
                .tag("topic", "robot/state")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.duration")
                .tag("topic", "robot/state")
                .tag("result", "fail")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get("waddoc.mqtt.inbound.last.received.epoch")
                .tag("topic", "robot/state")
                .gauge()
                .value()).isPositive();
    }
}
