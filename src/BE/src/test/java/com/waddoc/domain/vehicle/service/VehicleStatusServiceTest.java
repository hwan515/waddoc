package com.waddoc.domain.vehicle.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.vehicle.dto.UpdateVehicleStatusRequest;
import com.waddoc.domain.vehicle.dto.VehicleResponse;
import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleStatusServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private VehicleStatusService vehicleStatusService;

    @Test
    void updateVehicleStatus_publishesRetryWhenVehicleRecovers() {
        Vehicle vehicle = Vehicle.builder()
                .code("GIMCHEON-01")
                .regionCode("GIMCHEON_JEUNGSAN")
                .displayName("김천증산 1호차")
                .operationalStatus(OperationalStatus.OUT_OF_SERVICE)
                .build();
        ReflectionTestUtils.setField(vehicle, "publicId", "veh_00000001");

        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(buildCareCase())
                .regionCode("GIMCHEON_JEUNGSAN")
                .destination("김천시 증산면 1길 69")
                .build();
        outbox.markRetryPending();

        when(vehicleRepository.findByPublicId("veh_00000001")).thenReturn(Optional.of(vehicle));
        when(dispatchOutboxRepository.findAllByRegionCodeAndStatusOrderByCreatedAtAsc(
                "GIMCHEON_JEUNGSAN",
                DispatchOutboxStatus.RETRY_PENDING
        )).thenReturn(List.of(outbox));

        VehicleResponse response = vehicleStatusService.updateVehicleStatus(
                "veh_00000001",
                new UpdateVehicleStatusRequest(OperationalStatus.OPERATIONAL, null)
        );

        assertThat(response.getOperationalStatus()).isEqualTo(OperationalStatus.OPERATIONAL);
        verify(kafkaTemplate).send(
                eq(KafkaTopics.DISPATCH_RETRY_TOPIC),
                eq("GIMCHEON_JEUNGSAN"),
                any(DispatchRequestMessage.class)
        );
    }

    private CareCase buildCareCase() {
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
        return careCase;
    }
}
