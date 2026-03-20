package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.global.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DispatchRetryConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.DISPATCH_RETRY_TOPIC, groupId = "dispatch-retry-group")
    public void consume(
            DispatchRequestMessage message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String regionCode
    ) {
        // retry 토픽은 별도 처리 큐가 아니라, 원래 배차 토픽으로 다시 넣는 재평가 트리거다.
        kafkaTemplate.send(
                KafkaTopics.DISPATCH_REQUESTS_TOPIC,
                regionCode != null ? regionCode : message.regionCode(),
                message
        );
    }
}
