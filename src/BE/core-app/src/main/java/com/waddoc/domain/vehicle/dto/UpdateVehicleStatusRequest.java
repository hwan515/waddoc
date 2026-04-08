package com.waddoc.domain.vehicle.dto;

import com.waddoc.domain.vehicle.entity.OperationalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVehicleStatusRequest {

    @NotNull(message = "operationalStatus is required")
    private OperationalStatus operationalStatus;

    private String statusReason;
}
