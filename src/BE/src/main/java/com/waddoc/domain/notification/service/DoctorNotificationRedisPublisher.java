package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.dto.DoctorNotificationBroadcastMessage;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorNotificationRedisPublisher {

    private final RedisTemplate<String, DoctorNotificationBroadcastMessage> notificationRedisTemplate;
    private final ChannelTopic doctorNotificationTopic;

    public void publish(String doctorId, String eventName, NewBookingNotificationPayload payload) {
        DoctorNotificationBroadcastMessage message = new DoctorNotificationBroadcastMessage(
                doctorId, eventName, payload
        );
        notificationRedisTemplate.convertAndSend(doctorNotificationTopic.getTopic(), message);
        log.debug("Published doctor notification to Redis. doctorId={}, eventName={}", doctorId, eventName);
    }
}
