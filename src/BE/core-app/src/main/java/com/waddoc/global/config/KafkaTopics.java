package com.waddoc.global.config;

/**
 * core-app 내부에서 직접 사용하는 Kafka 토픽 이름을 모아 둔다.
 */
public final class KafkaTopics {

    public static final String DISPATCH_REQUESTS_TOPIC = "dispatch.requests";
    public static final String DISPATCH_RETRY_TOPIC = "dispatch.retry";
    public static final String MISSION_TELEMETRY_TOPIC = "mission.telemetry";

    private KafkaTopics() {
    }
}
