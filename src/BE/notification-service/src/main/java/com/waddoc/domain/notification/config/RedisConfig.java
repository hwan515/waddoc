package com.waddoc.domain.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.notification.dto.DoctorNotificationBroadcastMessage;
import com.waddoc.domain.notification.service.DoctorNotificationRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * notification-service의 Redis Pub/Sub 채널과 직렬화 설정을 구성한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ChannelTopic doctorNotificationTopic() {
        return new ChannelTopic("doctor:notifications");
    }

    @Bean
    public RedisTemplate<String, DoctorNotificationBroadcastMessage> notificationRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        RedisTemplate<String, DoctorNotificationBroadcastMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, DoctorNotificationBroadcastMessage.class));
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DoctorNotificationRedisSubscriber subscriber,
            ChannelTopic doctorNotificationTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, doctorNotificationTopic);
        return container;
    }
}
