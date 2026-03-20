package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.global.sms.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmsConsumerTest {

    @Mock
    private SmsService smsService;

    @InjectMocks
    private SmsConsumer smsConsumer;

    @Test
    void consume_sendsSms() {
        SmsRequestMessage message = new SmsRequestMessage("01012345678", "booking confirmed", "corr-bk-1");

        smsConsumer.consume(message);

        verify(smsService).send("01012345678", "booking confirmed");
    }
}
