package com.waddoc.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.waddoc.shared.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationConsumerTest {

    @Mock
    private DoctorNotificationRedisPublisher doctorNotificationRedisPublisher;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private DoctorNotificationProjectionRepository doctorNotificationProjectionRepository;

    @Mock
    private SmsDeliveryRepository smsDeliveryRepository;

    @Mock
    private SmsService smsService;

    private DoctorNotificationConsumer doctorNotificationConsumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        doctorNotificationConsumer = new DoctorNotificationConsumer(
                doctorNotificationRedisPublisher,
                processedEventRepository,
                notificationLogRepository,
                doctorNotificationProjectionRepository,
                smsDeliveryRepository,
                smsService,
                objectMapper
        );
    }

    @Test
    void consume_skipsWhenDoctorUserIdMissing() {
        EventEnvelope envelope = bookingConfirmedEnvelope(samplePayload(null));
        when(processedEventRepository.existsByEventId("evt-booking-1")).thenReturn(false);

        doctorNotificationConsumer.consume(envelope);

        verify(doctorNotificationRedisPublisher, never()).publish(any(), any(), any());
        verify(notificationLogRepository, never()).save(any(NotificationLog.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void consume_publishesProjectionRedisAndSms() {
        EventEnvelope envelope = bookingConfirmedEnvelope(samplePayload("usr_doctor"));
        when(processedEventRepository.existsByEventId("evt-booking-1")).thenReturn(false);

        doctorNotificationConsumer.consume(envelope);

        ArgumentCaptor<DoctorNotificationProjection> projectionCaptor =
                ArgumentCaptor.forClass(DoctorNotificationProjection.class);
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        ArgumentCaptor<SmsDelivery> smsCaptor = ArgumentCaptor.forClass(SmsDelivery.class);
        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);

        verify(doctorNotificationRedisPublisher).publish(
                org.mockito.ArgumentMatchers.eq("usr_doctor"),
                org.mockito.ArgumentMatchers.eq("notification"),
                any()
        );
        verify(smsService).send(org.mockito.ArgumentMatchers.eq("01012345678"), org.mockito.ArgumentMatchers.contains("예약 확인"));
        verify(doctorNotificationProjectionRepository).save(projectionCaptor.capture());
        verify(notificationLogRepository).save(logCaptor.capture());
        verify(smsDeliveryRepository).save(smsCaptor.capture());
        verify(processedEventRepository).save(processedCaptor.capture());

        assertThat(projectionCaptor.getValue().getDoctorUserId()).isEqualTo("usr_doctor");
        assertThat(projectionCaptor.getValue().getBookingId()).isEqualTo("bk_test123");
        assertThat(logCaptor.getValue().getEventType()).isEqualTo(EventTypes.BOOKING_CONFIRMED_V1);
        assertThat(logCaptor.getValue().getRecipientId()).isEqualTo("usr_doctor");
        assertThat(smsCaptor.getValue().getRecipientPhone()).isEqualTo("01012345678");
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo("evt-booking-1");
    }

    private EventEnvelope bookingConfirmedEnvelope(BookingConfirmedEventPayload payload) {
        return new EventEnvelope(
                "evt-booking-1",
                EventTypes.BOOKING_CONFIRMED_V1,
                OffsetDateTime.parse("2026-04-07T10:15:30+09:00"),
                "core-app",
                payload.bookingId(),
                "corr_bk_test123",
                objectMapper.valueToTree(payload)
        );
    }

    private BookingConfirmedEventPayload samplePayload(String doctorUserId) {
        return new BookingConfirmedEventPayload(
                NotificationType.NEW_BOOKING,
                "bk_test123",
                "case_test123",
                "pt_test123",
                "doc_test123",
                doctorUserId,
                "Doctor Kim",
                "Internal Medicine",
                "Patient Park",
                "FEMALE",
                "1958-03-15",
                "01012345678",
                "01012345678",
                LocalDate.of(2026, 4, 10),
                LocalTime.of(10, 30),
                "2026-04-10, 10:30",
                "WEB_SIMULATOR",
                "Gimcheon",
                OffsetDateTime.parse("2026-04-07T10:15:30+09:00")
        );
    }
}
