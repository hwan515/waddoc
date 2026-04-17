package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer 처리 결과와 시간을 Micrometer 메트릭으로 남긴다.
 */
@Component
@RequiredArgsConstructor
public class KafkaMonitoringMetrics {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAIL = "fail";

    private final MeterRegistry meterRegistry;

    public void recordConsumerProcessing(String topic, String consumerGroup, Runnable action) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            action.run();
            recordProcessed(topic, consumerGroup, RESULT_SUCCESS);
            recordDuration(topic, consumerGroup, RESULT_SUCCESS, sample);
        } catch (RuntimeException e) {
            recordProcessed(topic, consumerGroup, RESULT_FAIL);
            recordDuration(topic, consumerGroup, RESULT_FAIL, sample);
            throw e;
        }
    }

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

    private void recordProcessed(String topic, String consumerGroup, String result) {
        meterRegistry.counter(
                "waddoc.kafka.consumer.processed",
                "topic", normalize(topic),
                "consumer_group", normalize(consumerGroup),
                "result", result
        ).increment();
    }

    private void recordDuration(String topic, String consumerGroup, String result, Timer.Sample sample) {
        sample.stop(Timer.builder("waddoc.kafka.consumer.duration")
                .tags(
                        "topic", normalize(topic),
                        "consumer_group", normalize(consumerGroup),
                        "result", result
                )
                .register(meterRegistry));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
