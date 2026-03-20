package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationConsumerTest {

    @Mock
    private DoctorNotificationSseService doctorNotificationSseService;

    @InjectMocks
    private DoctorNotificationConsumer doctorNotificationConsumer;

    @Test
    void consume_logsAndSkipsWhenNoActiveConnection() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .bookingId("bk_test123")
                .doctorId("doc_test123")
                .build();
        when(doctorNotificationSseService.hasConnections("doc_test123")).thenReturn(false);

        doctorNotificationConsumer.consume(payload, "doc_test123");

        verify(doctorNotificationSseService, never()).sendToDoctor("doc_test123", "notification", payload);
    }

    @Test
    void consume_sendsNotificationWhenConnectionExists() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .bookingId("bk_test123")
                .doctorId("doc_test123")
                .build();
        when(doctorNotificationSseService.hasConnections("doc_test123")).thenReturn(true);

        doctorNotificationConsumer.consume(payload, "doc_test123");

        verify(doctorNotificationSseService).sendToDoctor("doc_test123", "notification", payload);
    }
}
