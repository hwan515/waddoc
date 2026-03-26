package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationConsumerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private DoctorNotificationRedisPublisher doctorNotificationRedisPublisher;

    @Spy
    private KafkaMonitoringMetrics kafkaMonitoringMetrics = new KafkaMonitoringMetrics(meterRegistry);

    @InjectMocks
    private DoctorNotificationConsumer doctorNotificationConsumer;

    @Test
    void consume_skipsWhenDoctorIdMissing() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .bookingId("bk_test123")
                .build();

        doctorNotificationConsumer.consume(payload, null);

        verify(doctorNotificationRedisPublisher, never())
                .publish("doc_test123", "notification", payload);
    }

    @Test
    void consume_publishesToRedisWhenDoctorIdPresent() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .bookingId("bk_test123")
                .doctorId("doc_test123")
                .build();

        doctorNotificationConsumer.consume(payload, "doc_test123");

        assertThat(meterRegistry.get("waddoc.kafka.consumer.processed")
                .tag("topic", "doctor.notifications")
                .tag("consumer_group", "doctor-notification-group")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        verify(doctorNotificationRedisPublisher)
                .publish("doc_test123", "notification", payload);
    }

    @Test
    void consume_usesDoctorIdFromHeaderOverPayload() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .bookingId("bk_test123")
                .doctorId("doc_payload")
                .build();

        doctorNotificationConsumer.consume(payload, "doc_header");

        verify(doctorNotificationRedisPublisher)
                .publish("doc_header", "notification", payload);
    }
}
