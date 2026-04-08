package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaMonitoringMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics = new KafkaMonitoringMetrics(meterRegistry);

    @Test
    void recordConsumerProcessing_recordsSuccessCounterAndTimer() {
        kafkaMonitoringMetrics.recordConsumerProcessing("mission.telemetry", "telemetry-group", () -> {
        });

        assertThat(meterRegistry.get("waddoc.kafka.consumer.processed")
                .tag("topic", "mission.telemetry")
                .tag("consumer_group", "telemetry-group")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.kafka.consumer.duration")
                .tag("topic", "mission.telemetry")
                .tag("consumer_group", "telemetry-group")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordConsumerProcessing_recordsFailureCounterAndTimer() {
        assertThatThrownBy(() -> kafkaMonitoringMetrics.recordConsumerProcessing(
                "dispatch.requests",
                "dispatch-group",
                () -> {
                    throw new IllegalStateException("boom");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("waddoc.kafka.consumer.processed")
                .tag("topic", "dispatch.requests")
                .tag("consumer_group", "dispatch-group")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.kafka.consumer.duration")
                .tag("topic", "dispatch.requests")
                .tag("consumer_group", "dispatch-group")
                .tag("result", "fail")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordProducerResult_recordsSuccessCounterAndTimer() {
        var sample = kafkaMonitoringMetrics.startProducerSend();

        kafkaMonitoringMetrics.recordProducerResult("robot.telemetry.v1", "robot-mqtt-subscriber", sample, null);

        assertThat(meterRegistry.get("waddoc.kafka.producer.sent")
                .tag("topic", "robot.telemetry.v1")
                .tag("producer", "robot-mqtt-subscriber")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.kafka.producer.duration")
                .tag("topic", "robot.telemetry.v1")
                .tag("producer", "robot-mqtt-subscriber")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordProducerResult_recordsFailureCounterAndTimer() {
        var sample = kafkaMonitoringMetrics.startProducerSend();

        kafkaMonitoringMetrics.recordProducerResult(
                "mission.telemetry",
                "mission-telemetry-controller",
                sample,
                new IllegalStateException("boom")
        );

        assertThat(meterRegistry.get("waddoc.kafka.producer.sent")
                .tag("topic", "mission.telemetry")
                .tag("producer", "mission-telemetry-controller")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("waddoc.kafka.producer.duration")
                .tag("topic", "mission.telemetry")
                .tag("producer", "mission-telemetry-controller")
                .tag("result", "fail")
                .timer()
                .count()).isEqualTo(1L);
    }
}
