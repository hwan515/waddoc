package com.waddoc.domain.notification.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.global.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorNotificationConsumer {

    private static final String DOCTOR_NOTIFICATION_EVENT_NAME = "notification";

    private final DoctorNotificationSseService doctorNotificationSseService;

    @KafkaListener(topics = KafkaTopics.DOCTOR_NOTIFICATIONS_TOPIC, groupId = "doctor-notification-group")
    public void consume(
            NewBookingNotificationPayload payload,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String doctorId
    ) {
        String targetDoctorId = doctorId != null ? doctorId : payload.getDoctorId();
        if (targetDoctorId == null || targetDoctorId.isBlank()) {
            log.warn("Doctor notification skipped. reason=missing-doctor-id, bookingId={}", payload.getBookingId());
            return;
        }

        // SSE 연결이 없는 의사는 실시간 휘발성 알림을 굳이 발행하지 않는다.
        if (!doctorNotificationSseService.hasConnections(targetDoctorId)) {
            log.info(
                    "Doctor notification skipped. reason=no-active-connection, doctorId={}, bookingId={}",
                    targetDoctorId,
                    payload.getBookingId()
            );
            return;
        }

        doctorNotificationSseService.sendToDoctor(targetDoctorId, DOCTOR_NOTIFICATION_EVENT_NAME, payload);
    }
}
