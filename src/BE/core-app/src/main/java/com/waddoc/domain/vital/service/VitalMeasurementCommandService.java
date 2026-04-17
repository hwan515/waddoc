package com.waddoc.domain.vital.service;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.vital.dto.UpsertVitalMeasurementRequest;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import com.waddoc.domain.vital.entity.VitalMeasurement;
import com.waddoc.domain.vital.repository.VitalMeasurementRepository;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 케이스 단위 활력 징후를 생성하거나 최신 값으로 갱신한다.
 */
@Service
@RequiredArgsConstructor
public class VitalMeasurementCommandService {

    private final VitalMeasurementRepository vitalMeasurementRepository;
    private final Clock clock;

    @Transactional
    public VitalMeasurementResponse upsert(CareCase careCase, UpsertVitalMeasurementRequest request) {
        VitalMeasurement vitalMeasurement = vitalMeasurementRepository.findByCareCase(careCase)
                .orElseGet(() -> VitalMeasurement.create(careCase));

        if (!request.hasAnyMeasurementValue()) {
            if (vitalMeasurement.getId() == null) {
                return VitalMeasurementResponse.from(vitalMeasurement);
            }
            return VitalMeasurementResponse.from(vitalMeasurement);
        }

        vitalMeasurement.applyMeasurements(
                request.getTemperature(),
                request.getBloodPressureSys(),
                request.getBloodPressureDia(),
                request.getHeartRate(),
                request.getSpO2(),
                request.getEcgWaveform(),
                request.getEcgSamplingHz(),
                request.getEcgDurationSeconds(),
                request.resolveMeasuredAt(LocalDateTime.now(KstTime.resolve(clock)))
        );

        VitalMeasurement saved = vitalMeasurement.getId() == null
                ? vitalMeasurementRepository.save(vitalMeasurement)
                : vitalMeasurement;

        return VitalMeasurementResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<VitalMeasurementResponse> findByCaseId(String caseId) {
        return vitalMeasurementRepository.findByCareCase_PublicId(caseId)
                .map(VitalMeasurementResponse::from);
    }
}
