package com.waddoc.global.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockSmsService implements SmsService {

    @Override
    public void send(String to, String message) {
        log.info("[SMS Mock] to={}, message={}", to, message);
    }
}
