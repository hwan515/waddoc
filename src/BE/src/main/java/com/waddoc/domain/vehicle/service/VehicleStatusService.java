package com.waddoc.domain.vehicle.service;

import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.vehicle.dto.UpdateVehicleStatusRequest;
import com.waddoc.domain.vehicle.dto.VehicleResponse;
import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 차량 운영 상태를 조회/변경하고 복구 시 대기 중인 재배차를 다시 깨운다.
 */
@Service
@RequiredArgsConstructor
public class VehicleStatusService {

    private final VehicleRepository vehicleRepository;
    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<VehicleResponse> getVehicles() {
        return vehicleRepository.findAllByIsActiveTrueOrderByCreatedAtAsc().stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(String vehicleId) {
        return VehicleResponse.from(findVehicle(vehicleId));
    }

    @Transactional
    public VehicleResponse updateVehicleStatus(String vehicleId, UpdateVehicleStatusRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        OperationalStatus previousStatus = vehicle.getOperationalStatus();

        vehicle.updateOperationalStatus(
                request.getOperationalStatus(),
                request.getStatusReason(),
                LocalDateTime.now(KstTime.resolve(clock))
        );

        if (previousStatus != OperationalStatus.OPERATIONAL
                && request.getOperationalStatus() == OperationalStatus.OPERATIONAL) {
            // 차량이 복구되면 같은 권역의 retry 배차를 다시 평가하도록 이벤트를 던진다.
            dispatchOutboxRepository
                    .findAllByRegionCodeAndStatusOrderByCreatedAtAsc(
                            vehicle.getRegionCode(),
                            DispatchOutboxStatus.RETRY_PENDING
                    )
                    .forEach(this::publishRetry);
        }

        return VehicleResponse.from(vehicle);
    }

    private Vehicle findVehicle(String vehicleId) {
        return vehicleRepository.findByPublicId(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
    }

    private void publishRetry(DispatchOutbox outbox) {
        kafkaTemplate.send(
                KafkaTopics.DISPATCH_RETRY_TOPIC,
                outbox.getRegionCode(),
                DispatchRequestMessage.from(outbox)
        );
    }
}
