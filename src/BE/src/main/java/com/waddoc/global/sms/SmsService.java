package com.waddoc.global.sms;

public interface SmsService {
    void send(String to, String message);

    String getContactNumber();
}
