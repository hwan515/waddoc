package com.waddoc.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.domain.notification.entity.DoctorNotificationProjection;
import com.waddoc.domain.notification.entity.NotificationLog;
import com.waddoc.domain.notification.entity.ProcessedEvent;
import com.waddoc.domain.notification.entity.SmsDelivery;
import com.waddoc.domain.notification.repository.DoctorNotificationProjectionRepository;
import com.waddoc.domain.notification.repository.NotificationLogRepository;
import com.waddoc.domain.notification.repository.ProcessedEventRepository;
import com.waddoc.domain.notification.repository.SmsDeliveryRepository;
import com.waddoc.global.sms.SmsService;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.BookingConfirmedEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 예약 확정 이벤트를 받아 의사 알림 projection 저장, Redis fan-out, 환자 SMS 발송을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorNotificationConsumer {

    private static final String DOCTOR_NOTIFICATION_EVENT_NAME = "notification";
    private static final String CONSUMER_GROUP = "doctor-notification-group";

    private final DoctorNotificationRedisPublisher doctorNotificationRedisPublisher;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final DoctorNotificationProjectionRepository doctorNotificationProjectionRepository;
    private final SmsDeliveryRepository smsDeliveryRepository;
    private final SmsService smsService;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = EventTypes.BOOKING_CONFIRMED_V1, groupId = CONSUMER_GROUP)
    public void consume(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            return;
        }

        BookingConfirmedEventPayload payload = convert(envelope, BookingConfirmedEventPayload.class);
        if (payload.doctorUserId() == null || payload.doctorUserId().isBlank()) {
            log.warn("Doctor notification skipped. reason=missing-doctor-user-id, bookingId={}", payload.bookingId());
            return;
        }

        NewBookingNotificationPayload notificationPayload = NewBookingNotificationPayload.builder()
                .type(payload.type().name())
                .bookingId(payload.bookingId())
                .caseId(payload.careCaseId())
                .doctorId(payload.doctorId())
                .doctorUserId(payload.doctorUserId())
                .doctorName(payload.doctorName())
                .departmentName(payload.departmentName())
                .patientId(payload.patientId())
                .patientName(payload.patientName())
                .patientGender(payload.patientGender())
                .patientBirthDate(payload.patientBirthDate())
                .patientPhone(payload.patientPhone())
                .appointmentDate(payload.appointmentDate())
                .startTime(payload.startTime())
                .bookingChannel(payload.bookingChannel())
                .location(payload.location())
                .createdAt(payload.createdAt())
                .build();

        OffsetDateTime now = envelope.occurredAt() != null ? envelope.occurredAt() : OffsetDateTime.now();
        // SSE 재연결 시 최근 알림 목록을 복원할 수 있도록 doctor별 projection을 별도 저장한다.
        doctorNotificationProjectionRepository.save(new DoctorNotificationProjection(
                envelope.eventId(),
                payload.doctorUserId(),
                payload.doctorId(),
                payload.bookingId(),
                payload.careCaseId(),
                toJson(notificationPayload),
                now
        ));
        notificationLogRepository.save(new NotificationLog(
                envelope.eventId(),
                envelope.eventType(),
                "DOCTOR",
                payload.doctorUserId(),
                "PUBLISHED",
                now
        ));
        doctorNotificationRedisPublisher.publish(payload.doctorUserId(), DOCTOR_NOTIFICATION_EVENT_NAME, notificationPayload);

        if (payload.recipientPhone() != null && !payload.recipientPhone().isBlank()) {
            String smsMessage = buildBookingCreatedSms(payload);
            smsService.send(payload.recipientPhone(), smsMessage);
            smsDeliveryRepository.save(new SmsDelivery(
                    envelope.eventId(),
                    payload.recipientPhone(),
                    smsMessage,
                    "SENT",
                    now
            ));
        }

        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), now));
    }

    private String buildBookingCreatedSms(BookingConfirmedEventPayload payload) {
        return String.format(
                "예약 확인%n[왔닥]%n안녕하세요, %s님.%n진료 예약이 아래와 같이 확정되었습니다.%n%n일시: %s%n의사: %s (%s)%n※ 유의사항%n%n예약 시간 10분 전까지 준비 부탁드립니다.%n변경이나 취소를 원하실 경우 최소 하루 전까지 연락 주시기 바랍니다.%n%n☎ 문의: %s",
                payload.patientName(),
                payload.appointmentDateTime(),
                payload.doctorName(),
                payload.departmentName(),
                smsService.getContactNumber()
        );
    }

    private <T> T convert(EventEnvelope envelope, Class<T> targetType) {
        return objectMapper.convertValue(envelope.payload(), targetType);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification projection payload", exception);
        }
    }
}
