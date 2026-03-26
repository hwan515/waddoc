package com.waddoc.domain.vehicle.dto;

import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VehicleResponse {

    private String vehicleId;
    private String code;
    private String regionCode;
    private String displayName;
    private boolean active;
    private OperationalStatus operationalStatus;
    private LocalDateTime statusChangedAt;
    private String statusReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleResponse from(Vehicle vehicle) {
        return VehicleResponse.builder()
                .vehicleId(vehicle.getPublicId())
                .code(vehicle.getCode())
                .regionCode(vehicle.getRegionCode())
                .displayName(vehicle.getDisplayName())
                .active(vehicle.isActive())
                .operationalStatus(vehicle.getOperationalStatus())
                .statusChangedAt(vehicle.getStatusChangedAt())
                .statusReason(vehicle.getStatusReason())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
