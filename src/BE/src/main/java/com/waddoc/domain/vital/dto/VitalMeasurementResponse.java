package com.waddoc.domain.vital.dto;

import com.waddoc.domain.vital.entity.VitalMeasurement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class VitalMeasurementResponse {

    private String caseId;
    private BigDecimal temperature;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer heartRate;
    private Integer spO2;
    private List<BigDecimal> ecgWaveform;
    private Integer ecgSamplingHz;
    private Integer ecgDurationSeconds;
    private LocalDateTime measuredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VitalMeasurementResponse from(VitalMeasurement vitalMeasurement) {
        return VitalMeasurementResponse.builder()
                .caseId(vitalMeasurement.getCareCase().getPublicId())
                .temperature(vitalMeasurement.getTemperature())
                .bloodPressureSys(vitalMeasurement.getBloodPressureSys())
                .bloodPressureDia(vitalMeasurement.getBloodPressureDia())
                .heartRate(vitalMeasurement.getHeartRate())
                .spO2(vitalMeasurement.getSpO2())
                .ecgWaveform(
                        vitalMeasurement.getEcgWaveform() == null
                                ? null
                                : List.copyOf(vitalMeasurement.getEcgWaveform())
                )
                .ecgSamplingHz(vitalMeasurement.getEcgSamplingHz())
                .ecgDurationSeconds(vitalMeasurement.getEcgDurationSeconds())
                .measuredAt(vitalMeasurement.getMeasuredAt())
                .createdAt(vitalMeasurement.getCreatedAt())
                .updatedAt(vitalMeasurement.getUpdatedAt())
                .build();
    }
}
