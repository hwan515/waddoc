package com.waddoc.global.sms;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "solapi")
public class SolapiSmsService implements SmsService {

    private final DefaultMessageService messageService;
    private final String senderNumber;
    private final String contactNumber;

    public SolapiSmsService(
            @Value("${sms.api-key}") String apiKey,
            @Value("${sms.api-secret}") String apiSecret,
            @Value("${sms.sender-number}") String senderNumber,
            @Value("${sms.contact-number:}") String contactNumber
    ) {
        this.messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
        this.senderNumber = normalizePhoneNumber(senderNumber);
        this.contactNumber = normalizePhoneNumber(contactNumber.isBlank() ? senderNumber : contactNumber);
    }

    @Override
    public void send(String to, String messageBody) {
        String recipient = normalizePhoneNumber(to);
        if (recipient.isBlank()) {
            throw new IllegalArgumentException("SMS recipient phone number is blank.");
        }
        if (senderNumber.isBlank()) {
            throw new IllegalStateException("Configured SMS sender number is blank.");
        }

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(recipient);
        message.setText(messageBody);
        message.setAutoTypeDetect(true);

        try {
            MultipleDetailMessageSentResponse response = messageService.send(message);
            String groupId = response.getGroupInfo() != null ? response.getGroupInfo().getGroupId() : null;
            log.info("SOLAPI SMS sent successfully. to={}, groupId={}", recipient, groupId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send SMS via SOLAPI.", e);
        }
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
