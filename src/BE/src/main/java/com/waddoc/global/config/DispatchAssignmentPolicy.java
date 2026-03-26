package com.waddoc.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DispatchAssignmentPolicy {

    private final String defaultVehicleId;

    public DispatchAssignmentPolicy(
            @Value("${dispatch.default-vehicle-id:veh_GIMCHEON_01}") String defaultVehicleId
    ) {
        this.defaultVehicleId = defaultVehicleId;
    }

    public String getDefaultVehicleId() {
        return defaultVehicleId;
    }
}
