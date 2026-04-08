package com.waddoc.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.notification.entity.NotificationLog;
import com.waddoc.domain.notification.entity.ProcessedEvent;
import com.waddoc.domain.notification.entity.SmsDelivery;
import com.waddoc.domain.notification.repository.NotificationLogRepository;
import com.waddoc.domain.notification.repository.ProcessedEventRepository;
import com.waddoc.domain.notification.repository.SmsDeliveryRepository;
import com.waddoc.global.sms.SmsService;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.BookingCancelledEventPayload;
import com.waddoc.shared.event.payload.DispatchAssignedEventPayload;
import com.waddoc.shared.event.payload.DispatchDelayedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 예약 취소, 배차 완료, 배차 지연 이벤트를 SMS 발송으로 변환하는 consumer다.
 */
@Service
@RequiredArgsConstructor
public class SmsConsumer {

    private static final String CONSUMER_GROUP = "sms-group";

    private final SmsService smsService;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final SmsDeliveryRepository smsDeliveryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = EventTypes.DISPATCH_ASSIGNED_V1, groupId = CONSUMER_GROUP)
    public void consumeDispatchAssigned(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            return;
        }
        DispatchAssignedEventPayload payload = objectMapper.convertValue(envelope.payload(), DispatchAssignedEventPayload.class);
        sendSms(envelope, payload.recipientPhone(), buildDispatchAssignedMessage(payload), "PATIENT", payload.recipientPhone());
    }

    @Transactional
    @KafkaListener(topics = EventTypes.DISPATCH_DELAYED_V1, groupId = CONSUMER_GROUP)
    public void consumeDispatchDelayed(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            return;
        }
        DispatchDelayedEventPayload payload = objectMapper.convertValue(envelope.payload(), DispatchDelayedEventPayload.class);
        sendSms(envelope, payload.recipientPhone(), payload.reason(), "PATIENT", payload.recipientPhone());
    }

    @Transactional
    @KafkaListener(topics = EventTypes.BOOKING_CANCELLED_V1, groupId = CONSUMER_GROUP)
    public void consumeBookingCancelled(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            return;
        }
        BookingCancelledEventPayload payload = objectMapper.convertValue(envelope.payload(), BookingCancelledEventPayload.class);
        String message = payload.cancelReason() == null || payload.cancelReason().isBlank()
                ? "[왔닥]%n예약이 취소되었습니다."
                : String.format("[왔닥]%n예약이 취소되었습니다.%n사유: %s", payload.cancelReason());
        sendSms(envelope, payload.recipientPhone(), message, "PATIENT", payload.recipientPhone());
    }

    private void sendSms(EventEnvelope envelope, String recipientPhone, String message, String recipientType, String recipientId) {
        OffsetDateTime now = envelope.occurredAt() != null ? envelope.occurredAt() : OffsetDateTime.now();
        if (recipientPhone != null && !recipientPhone.isBlank()) {
            smsService.send(recipientPhone, message);
            smsDeliveryRepository.save(new SmsDelivery(
                    envelope.eventId(),
                    recipientPhone,
                    message,
                    "SENT",
                    now
            ));
        }
        notificationLogRepository.save(new NotificationLog(
                envelope.eventId(),
                envelope.eventType(),
                recipientType,
                recipientId == null ? "UNKNOWN" : recipientId,
                "PUBLISHED",
                now
        ));
        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), now));
    }

    private String buildDispatchAssignedMessage(DispatchAssignedEventPayload payload) {
        return String.format(
                "[왔닥]%n배차가 완료되었습니다.%n%s 차량이 배정되어 순차적으로 출동을 준비하고 있습니다.",
                payload.vehicleId() == null || payload.vehicleId().isBlank() ? "배차" : payload.vehicleId()
        );
    }
}
