package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationConsumerTest {

    @Mock
    private DoctorNotificationRedisPublisher doctorNotificationRedisPublisher;

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
