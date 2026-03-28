package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DispatchRetryConsumer {

    private static final String CONSUMER_GROUP = "dispatch-retry-group";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;

    @KafkaListener(topics = KafkaTopics.DISPATCH_RETRY_TOPIC, groupId = CONSUMER_GROUP)
    public void consume(
            DispatchRequestMessage message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String regionCode
    ) {
        kafkaMonitoringMetrics.recordConsumerProcessing(KafkaTopics.DISPATCH_RETRY_TOPIC, CONSUMER_GROUP, () -> {
            // retry 토픽은 별도 처리 큐가 아니라, 원래 배차 토픽으로 다시 넣는 재평가 트리거다.
            kafkaTemplate.send(
                    KafkaTopics.DISPATCH_REQUESTS_TOPIC,
                    regionCode != null ? regionCode : message.regionCode(),
                    message
            );
        });
    }
}
