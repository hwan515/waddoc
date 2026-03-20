package com.waddoc.global.config;

public final class KafkaTopics {

    // Producer/Consumer가 같은 문자열을 공유하도록 토픽 이름을 한 곳에서 관리한다.
    public static final String DISPATCH_REQUESTS_TOPIC = "dispatch.requests";
    public static final String DISPATCH_RETRY_TOPIC = "dispatch.retry";
    public static final String SMS_REQUESTS_TOPIC = "sms.requests";
    public static final String SMS_REQUESTS_DLT_TOPIC = "sms.requests.DLT";
    public static final String DOCTOR_NOTIFICATIONS_TOPIC = "doctor.notifications";
    public static final String MISSION_TELEMETRY_TOPIC = "mission.telemetry";

    private KafkaTopics() {
    }
}
