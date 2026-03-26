package com.waddoc.global.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class LiveKitMonitoringMetrics {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAIL = "fail";

    private final MeterRegistry meterRegistry;

    public void recordRoomOperation(String operation, Runnable action) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            action.run();
            recordRoomOperation(operation, RESULT_SUCCESS);
            recordApiDuration(operation, RESULT_SUCCESS, sample);
        } catch (RuntimeException e) {
            recordRoomOperation(operation, RESULT_FAIL);
            recordApiDuration(operation, RESULT_FAIL, sample);
            throw e;
        }
    }

    public String recordTokenIssuance(String participantType, Supplier<String> supplier) {
        try {
            String token = supplier.get();
            recordTokenIssuance(participantType, RESULT_SUCCESS);
            return token;
        } catch (RuntimeException e) {
            recordTokenIssuance(participantType, RESULT_FAIL);
            throw e;
        }
    }

    public void recordWebhookEvent(String eventName, Runnable action) {
        try {
            action.run();
            recordWebhookEvent(eventName, RESULT_SUCCESS);
        } catch (RuntimeException e) {
            recordWebhookEvent(eventName, RESULT_FAIL);
            throw e;
        }
    }

    public void recordWebhookFailure(String eventName) {
        recordWebhookEvent(eventName, RESULT_FAIL);
    }

    private void recordRoomOperation(String operation, String result) {
        meterRegistry.counter(
                "waddoc.livekit.room.operations",
                "operation", normalize(operation),
                "result", result
        ).increment();
    }

    private void recordTokenIssuance(String participantType, String result) {
        meterRegistry.counter(
                "waddoc.livekit.token.issued",
                "participant_type", normalize(participantType),
                "result", result
        ).increment();
    }

    private void recordWebhookEvent(String eventName, String result) {
        meterRegistry.counter(
                "waddoc.livekit.webhook.events",
                "event", normalize(eventName),
                "result", result
        ).increment();
    }

    private void recordApiDuration(String operation, String result, Timer.Sample sample) {
        sample.stop(Timer.builder("waddoc.livekit.api.duration")
                .tags(
                        "operation", normalize(operation),
                        "result", result
                )
                .register(meterRegistry));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
