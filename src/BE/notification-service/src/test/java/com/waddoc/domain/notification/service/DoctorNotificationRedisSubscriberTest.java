package com.waddoc.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.notification.dto.DoctorNotificationBroadcastMessage;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationRedisSubscriberTest {

    @Mock
    private DoctorNotificationSseService doctorNotificationSseService;

    private DoctorNotificationRedisSubscriber subscriber;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        subscriber = new DoctorNotificationRedisSubscriber(doctorNotificationSseService, objectMapper);
    }

    @Test
    void onMessage_deliversBroadcastUsingDoctorUserId() throws Exception {
        DoctorNotificationBroadcastMessage broadcast = new DoctorNotificationBroadcastMessage(
                "usr_doctor",
                "notification",
                NewBookingNotificationPayload.builder().type("NEW_BOOKING").build()
        );
        when(doctorNotificationSseService.hasConnections("usr_doctor")).thenReturn(true);

        Message message = new DefaultMessage(new byte[0], objectMapper.writeValueAsBytes(broadcast));
        subscriber.onMessage(message, null);

        verify(doctorNotificationSseService).hasConnections("usr_doctor");
        verify(doctorNotificationSseService)
                .sendToDoctorUser(
                        org.mockito.ArgumentMatchers.eq("usr_doctor"),
                        org.mockito.ArgumentMatchers.eq("notification"),
                        argThat((NewBookingNotificationPayload payload) ->
                                "NEW_BOOKING".equals(payload.getType()))
                );
    }

    @Test
    void onMessage_acceptsLegacyDoctorIdField() throws Exception {
        String legacyJson = """
                {
                  "doctorId": "usr_legacy",
                  "eventName": "notification",
                  "payload": {
                    "type": "NEW_BOOKING"
                  }
                }
                """;
        when(doctorNotificationSseService.hasConnections("usr_legacy")).thenReturn(false);

        Message message = new DefaultMessage(new byte[0], legacyJson.getBytes());
        subscriber.onMessage(message, null);

        verify(doctorNotificationSseService).hasConnections("usr_legacy");
        verify(doctorNotificationSseService, never()).sendToDoctorUser(anyString(), anyString(), any());
    }
}
