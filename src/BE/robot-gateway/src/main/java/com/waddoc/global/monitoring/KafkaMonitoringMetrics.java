package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * robot-gateway의 Kafka 발행 결과를 Micrometer 메트릭으로 남긴다.
 */
@Component
@RequiredArgsConstructor
public class KafkaMonitoringMetrics {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAIL = "fail";

    private final MeterRegistry meterRegistry;

    public Timer.Sample startProducerSend() {
        return Timer.start(meterRegistry);
    }

    public void recordProducerResult(String topic, String producerId, Timer.Sample sample, Throwable error) {
        String result = error == null ? RESULT_SUCCESS : RESULT_FAIL;
        meterRegistry.counter(
                "waddoc.kafka.producer.sent",
                "topic", normalize(topic),
                "producer", normalize(producerId),
                "result", result
        ).increment();
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder("waddoc.kafka.producer.duration")
                .tags(
                        "topic", normalize(topic),
                        "producer", normalize(producerId),
                        "result", result
                )
                .register(meterRegistry));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
