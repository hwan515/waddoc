package com.waddoc.domain.vehicle.repository;

import com.waddoc.domain.vehicle.entity.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPublicId(String publicId);

    Optional<Vehicle> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Vehicle> findFirstByRegionCodeOrderByCreatedAtAsc(String regionCode);

    Optional<Vehicle> findByRegionCodeAndIsActiveTrue(String regionCode);

    List<Vehicle> findAllByIsActiveTrueOrderByCreatedAtAsc();
}
