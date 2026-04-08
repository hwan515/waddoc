package com.waddoc.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.notification.dto.DoctorNotificationBroadcastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 메시지를 받아 현재 인스턴스에 연결된 SSE 세션에만 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorNotificationRedisSubscriber implements MessageListener {

    private final DoctorNotificationSseService doctorNotificationSseService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DoctorNotificationBroadcastMessage broadcast = objectMapper.readValue(
                    message.getBody(), DoctorNotificationBroadcastMessage.class
            );

            String doctorId = broadcast.getDoctorId();

            if (!doctorNotificationSseService.hasConnections(doctorId)) {
                return;
            }

            doctorNotificationSseService.sendToDoctor(
                    doctorId, broadcast.getEventName(), broadcast.getPayload()
            );

            log.debug("Delivered Redis broadcast to local SSE. doctorId={}", doctorId);
        } catch (Exception e) {
            log.warn("Failed to process doctor notification Redis message", e);
        }
    }
}
