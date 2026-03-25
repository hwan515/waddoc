package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.config.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @Mock
    private DemoModePolicy demoModePolicy;

    @InjectMocks
    private DispatchConsumer dispatchConsumer;

    @Test
    void consume_marksRetryPendingAndSendsDelaySmsWhenVehicleIsUnavailable() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(false);

        DispatchOutbox outbox = buildOutbox();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));
        when(missionRepository.findByCareCase(outbox.getCareCase())).thenReturn(Optional.empty());
        when(vehicleRepository.findByRegionCodeAndIsActiveTrue("GIMCHEON_JEUNGSAN")).thenReturn(Optional.empty());

        dispatchConsumer.consume(message);

        assertThat(outbox.isRetryPending()).isTrue();
        verify(kafkaTemplate).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), any());
        verify(missionCommandService, never()).createMissionForDispatch(any(), any(), any(), any(), any());
    }

    @Test
    void consume_createsMissionAndCompletesOutboxWhenVehicleIsOperational() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(false);

        DispatchOutbox outbox = buildOutbox();
        outbox.markRetryPending();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        Vehicle vehicle = Vehicle.builder()
                .code("GIMCHEON-01")
                .regionCode("GIMCHEON_JEUNGSAN")
                .displayName("Gimcheon vehicle 1")
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
                eq(outbox.getDestination()),
                any(),
                eq(null)
        );
        verify(kafkaTemplate).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), any());
    }

    @Test
    void consume_reusesCreatedMissionAndCompletesOutboxWhenVehicleIsOperational() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(false);

        DispatchOutbox outbox = buildOutbox();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        Vehicle vehicle = Vehicle.builder()
                .code("GIMCHEON-01")
                .regionCode("GIMCHEON_JEUNGSAN")
                .displayName("Gimcheon vehicle 1")
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build();
        ReflectionTestUtils.setField(vehicle, "publicId", "veh_GIMCHEON_01");

        Mission mission = Mission.builder()
                .careCase(outbox.getCareCase())
                .vehicleId("veh_GIMCHEON_01")
                .destination(outbox.getDestination())
                .targetWaypointNumber(59)
                .build();

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));
        when(missionRepository.findByCareCase(outbox.getCareCase())).thenReturn(Optional.of(mission));
        when(vehicleRepository.findByRegionCodeAndIsActiveTrue("GIMCHEON_JEUNGSAN"))
                .thenReturn(Optional.of(vehicle));
        when(missionRepository.existsByVehicleIdAndPhaseIn(eq("veh_GIMCHEON_01"), any()))
                .thenReturn(false);

        dispatchConsumer.consume(message);

        assertThat(outbox.isCompleted()).isTrue();
        verify(missionCommandService).createMissionForDispatch(
                eq(outbox.getCareCase()),
                eq("veh_GIMCHEON_01"),
                eq(outbox.getDestination()),
                any(),
                eq(59)
        );
    }

    @Test
    void consume_completesOutboxWhenMissionAlreadyAdvanced() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(false);

        DispatchOutbox outbox = buildOutbox();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        Mission mission = Mission.builder()
                .careCase(outbox.getCareCase())
                .vehicleId("veh_GIMCHEON_01")
                .destination(outbox.getDestination())
                .build();
        mission.updatePhase(MissionPhase.DISPATCHED);

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));
        when(missionRepository.findByCareCase(outbox.getCareCase())).thenReturn(Optional.of(mission));

        dispatchConsumer.consume(message);

        assertThat(outbox.isCompleted()).isTrue();
        verify(missionCommandService, never()).createMissionForDispatch(any(), any(), any(), any(), any());
        verify(vehicleRepository, never()).findByRegionCodeAndIsActiveTrue(any());
    }

    @Test
    void consume_keepsOutboxPendingForOperatorDispatchWhenDemoModeIsEnabled() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);

        DispatchOutbox outbox = buildOutbox();
        DispatchRequestMessage message = DispatchRequestMessage.from(outbox);

        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId("case_test123"))
                .thenReturn(Optional.of(outbox));

        dispatchConsumer.consume(message);

        assertThat(outbox.isRetryPending()).isTrue();
        verify(missionCommandService, never()).createMissionForDispatch(any(), any(), any(), any(), any());
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), any());
    }

    private DispatchOutbox buildOutbox() {
        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON_JEUNGSAN")
                .address("Demo Address 1")
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
                .destination("Demo Address 1")
                .build();
    }
}
