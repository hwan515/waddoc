package com.waddoc.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sms_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sms_delivery_id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    public SmsDelivery(String eventId, String recipientPhone, String message, String status, OffsetDateTime sentAt) {
        this.eventId = eventId;
        this.recipientPhone = recipientPhone;
        this.message = message;
        this.status = status;
        this.sentAt = sentAt;
    }
}
