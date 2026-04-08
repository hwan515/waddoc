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
import com.waddoc.shared.event.payload.DispatchAssignedEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsConsumerTest {

    @Mock
    private SmsService smsService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private SmsDeliveryRepository smsDeliveryRepository;

    private SmsConsumer smsConsumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        smsConsumer = new SmsConsumer(
                smsService,
                processedEventRepository,
                notificationLogRepository,
                smsDeliveryRepository,
                objectMapper
        );
    }

    @Test
    void consumeDispatchAssigned_sendsSmsAndPersistsDelivery() {
        EventEnvelope envelope = new EventEnvelope(
                "evt-dispatch-1",
                EventTypes.DISPATCH_ASSIGNED_V1,
                OffsetDateTime.parse("2026-04-07T11:00:00+09:00"),
                "core-app",
                "mission_1",
                "corr_dispatch_1",
                objectMapper.valueToTree(new DispatchAssignedEventPayload(
                        "mission_1",
                        "case_1",
                        "veh_1",
                        "01012345678",
                        "Gimcheon",
                        OffsetDateTime.parse("2026-04-07T11:00:00+09:00")
                ))
        );
        when(processedEventRepository.existsByEventId("evt-dispatch-1")).thenReturn(false);

        smsConsumer.consumeDispatchAssigned(envelope);

        ArgumentCaptor<SmsDelivery> smsCaptor = ArgumentCaptor.forClass(SmsDelivery.class);
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);

        verify(smsService).send(eq("01012345678"), contains("veh_1 차량이 배정되어 순차적으로 출동을 준비하고 있습니다."));
        verify(smsDeliveryRepository).save(smsCaptor.capture());
        verify(notificationLogRepository).save(logCaptor.capture());
        verify(processedEventRepository).save(processedCaptor.capture());

        assertThat(smsCaptor.getValue().getEventId()).isEqualTo("evt-dispatch-1");
        assertThat(logCaptor.getValue().getRecipientType()).isEqualTo("PATIENT");
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo("evt-dispatch-1");
    }
}
