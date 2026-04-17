package com.waddoc.domain.vital.repository;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.vital.entity.VitalMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VitalMeasurementRepository extends JpaRepository<VitalMeasurement, Long> {

    Optional<VitalMeasurement> findByCareCase(CareCase careCase);

    Optional<VitalMeasurement> findByCareCase_PublicId(String caseId);
}
