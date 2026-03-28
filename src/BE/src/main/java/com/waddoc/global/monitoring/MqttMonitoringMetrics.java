package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class MqttMonitoringMetrics {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAIL = "fail";
    private static final String RESULT_IGNORED = "ignored";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicLong> lastReceivedEpochSecondsByTopic = new ConcurrentHashMap<>();

    public boolean recordInboundProcessing(String topic, Supplier<Boolean> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        updateLastReceivedEpoch(topic);

        try {
            boolean handled = action.get();
            String result = handled ? RESULT_SUCCESS : RESULT_IGNORED;
            recordProcessed(topic, result);
            recordDuration(topic, result, sample);
            return handled;
        } catch (RuntimeException e) {
            recordProcessed(topic, RESULT_FAIL);
            recordDuration(topic, RESULT_FAIL, sample);
            throw e;
        }
    }

    private void updateLastReceivedEpoch(String topic) {
        AtomicLong gaugeValue = lastReceivedEpochSecondsByTopic.computeIfAbsent(
                normalize(topic),
                this::registerLastReceivedGauge
        );
        gaugeValue.set(Instant.now().getEpochSecond());
    }

    private AtomicLong registerLastReceivedGauge(String topic) {
        AtomicLong value = new AtomicLong(0);
        Gauge.builder("waddoc.mqtt.inbound.last.received.epoch", value, AtomicLong::doubleValue)
                .baseUnit("seconds")
                .tag("topic", topic)
                .register(meterRegistry);
        return value;
    }

    private void recordProcessed(String topic, String result) {
        meterRegistry.counter(
                "waddoc.mqtt.inbound.processed",
                "topic", normalize(topic),
                "result", result
        ).increment();
    }

    private void recordDuration(String topic, String result, Timer.Sample sample) {
        sample.stop(Timer.builder("waddoc.mqtt.inbound.duration")
                .tags(
                        "topic", normalize(topic),
                        "result", result
                )
                .register(meterRegistry));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
