package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.waddoc.global.sms.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmsConsumerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private SmsService smsService;

    @Spy
    private KafkaMonitoringMetrics kafkaMonitoringMetrics = new KafkaMonitoringMetrics(meterRegistry);

    @InjectMocks
    private SmsConsumer smsConsumer;

    @Test
    void consume_sendsSms() {
        SmsRequestMessage message = new SmsRequestMessage("01012345678", "booking confirmed", "corr-bk-1");

        smsConsumer.consume(message);

        assertThat(meterRegistry.get("waddoc.kafka.consumer.processed")
                .tag("topic", "sms.requests")
                .tag("consumer_group", "sms-group")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        verify(smsService).send("01012345678", "booking confirmed");
    }
}
