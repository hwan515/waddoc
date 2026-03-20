package com.waddoc.domain.vehicle.repository;

import com.waddoc.domain.vehicle.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPublicId(String publicId);

    Optional<Vehicle> findByRegionCodeAndIsActiveTrue(String regionCode);
}
