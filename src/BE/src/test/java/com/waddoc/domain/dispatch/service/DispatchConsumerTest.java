package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchConsumerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionCommandService missionCommandService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private KafkaMonitoringMetrics kafkaMonitoringMetrics = new KafkaMonitoringMetrics(meterRegistry);

    @InjectMocks
    private DispatchConsumer dispatchConsumer;

    @Test
    void consume_marksRetryPendingAndSendsDelaySmsWhenVehicleIsUnavailable() {
        DispatchOutbox outbox = buildOutbox();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));
        when(missionRepository.findByCareCase(outbox.getCareCase())).thenReturn(Optional.empty());
        when(vehicleRepository.findByRegionCodeAndIsActiveTrue("GIMCHEON_JEUNGSAN")).thenReturn(Optional.empty());

        dispatchConsumer.consume(message);

        assertThat(outbox.isRetryPending()).isTrue();
        assertThat(meterRegistry.get("waddoc.kafka.consumer.processed")
                .tag("topic", KafkaTopics.DISPATCH_REQUESTS_TOPIC)
                .tag("consumer_group", "dispatch-group")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        verify(kafkaTemplate).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), any());
        verify(missionCommandService, never()).createMissionForDispatch(any(), any(), any(), any());
    }

    @Test
    void consume_createsMissionAndCompletesOutboxWhenVehicleIsOperational() {
        DispatchOutbox outbox = buildOutbox();
        outbox.markRetryPending();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        Vehicle vehicle = Vehicle.builder()
                .code("GIMCHEON-01")
                .regionCode("GIMCHEON_JEUNGSAN")
                .displayName("김천증산 1호차")
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build();
        ReflectionTestUtils.setField(vehicle, "publicId", "veh_00000001");

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));
        when(missionRepository.findByCareCase(outbox.getCareCase())).thenReturn(Optional.empty());
        when(vehicleRepository.findByRegionCodeAndIsActiveTrue("GIMCHEON_JEUNGSAN"))
                .thenReturn(Optional.of(vehicle));
        when(missionRepository.existsByVehicleIdAndPhaseIn(eq("veh_00000001"), any()))
                .thenReturn(false);

        dispatchConsumer.consume(message);

        assertThat(outbox.isCompleted()).isTrue();
        verify(missionCommandService).createMissionForDispatch(
                eq(outbox.getCareCase()),
                eq("veh_00000001"),
                eq("김천시 증산면 1길 69"),
                any()
        );
        verify(kafkaTemplate).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), any());
    }

    private DispatchOutbox buildOutbox() {
        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON_JEUNGSAN")
                .address("김천시 증산면 1길 69")
                .phone("01012345678")
                .build();
        Booking booking = Booking.builder()
                .patient(patient)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 21))
                .build();
        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(null)
                .intakeSession(null)
                .build();
        ReflectionTestUtils.setField(careCase, "publicId", "case_test123");
        return DispatchOutbox.builder()
                .careCase(careCase)
                .regionCode("GIMCHEON_JEUNGSAN")
                .destination("김천시 증산면 1길 69")
                .build();
    }
}
