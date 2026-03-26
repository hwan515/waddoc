package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.sms.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsConsumer {

    private final SmsService smsService;

    @KafkaListener(
            topics = KafkaTopics.SMS_REQUESTS_TOPIC,
            groupId = "sms-group",
            containerFactory = "smsKafkaListenerContainerFactory"
    )
    public void consume(SmsRequestMessage message) {
        // 실제 SMS 발송은 외부 I/O라서 본 업무 트랜잭션과 분리해 비동기로 처리한다.
        smsService.send(message.recipientPhone(), message.message());
    }
}
