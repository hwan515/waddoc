package com.waddoc.domain.vital.dto;

import com.waddoc.domain.vital.entity.VitalMeasurement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Getter
@Builder
public class VitalMeasurementResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String caseId;
    private BigDecimal temperature;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer heartRate;
    private Integer spO2;
    private List<BigDecimal> ecgWaveform;
    private Integer ecgSamplingHz;
    private Integer ecgDurationSeconds;
    private OffsetDateTime measuredAt;
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
                .measuredAt(toOffsetDateTime(vitalMeasurement.getMeasuredAt()))
                .createdAt(vitalMeasurement.getCreatedAt())
                .updatedAt(vitalMeasurement.getUpdatedAt())
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value != null ? value.atZone(KST).toOffsetDateTime() : null;
    }
}
