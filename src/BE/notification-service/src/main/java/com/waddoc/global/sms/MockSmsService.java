package com.waddoc.global.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 로컬 개발이나 테스트에서 외부 SMS 벤더 호출 없이 로그로 대체하는 구현체다.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsService implements SmsService {

    private final String contactNumber;

    public MockSmsService(
            @Value("${sms.contact-number:}") String contactNumber,
            @Value("${sms.sender-number:01000000000}") String senderNumber
    ) {
        this.contactNumber = normalizePhoneNumber(contactNumber.isBlank() ? senderNumber : contactNumber);
    }

    @Override
    public void send(String to, String message) {
        log.info("[SMS Mock] to={}, message={}", to, message);
    }

    @Override
    public String getContactNumber() {
        return contactNumber;
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}
