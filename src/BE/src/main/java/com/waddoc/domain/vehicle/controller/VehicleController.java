package com.waddoc.domain.vehicle.controller;

import com.waddoc.domain.vehicle.dto.UpdateVehicleStatusRequest;
import com.waddoc.domain.vehicle.dto.VehicleResponse;
import com.waddoc.domain.vehicle.service.VehicleStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 화면에서 차량 상태를 조회하고 운영 상태를 변경하는 API다.
 */
@RestController
@RequestMapping("/api/v1/admin/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class VehicleController {

    private final VehicleStatusService vehicleStatusService;

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getVehicles() {
        return ResponseEntity.ok(vehicleStatusService.getVehicles());
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable String vehicleId) {
        return ResponseEntity.ok(vehicleStatusService.getVehicle(vehicleId));
    }

    @PatchMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> updateVehicleStatus(
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleStatusRequest request
    ) {
        return ResponseEntity.ok(vehicleStatusService.updateVehicleStatus(vehicleId, request));
    }
}
