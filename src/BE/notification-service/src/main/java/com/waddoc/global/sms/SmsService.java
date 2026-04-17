package com.waddoc.global.sms;

/**
 * notification-service가 실제 발송 구현체를 교체 가능하게 쓰도록 정의한 SMS 포트다.
 */
public interface SmsService {
    void send(String to, String message);

    String getContactNumber();
}
