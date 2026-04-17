package com.waddoc.domain.vital.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertVitalMeasurementRequest {

    @DecimalMin(value = "0.0")
    private BigDecimal temperature;

    @Positive
    private Integer bloodPressureSys;

    @Positive
    private Integer bloodPressureDia;

    @Positive
    private Integer heartRate;

    @Min(0)
    @Max(100)
    private Integer spO2;

    private List<BigDecimal> ecgWaveform;

    @Positive
    private Integer ecgSamplingHz;

    @Positive
    private Integer ecgDurationSeconds;

    private LocalDateTime measuredAt;

    public boolean hasAnyMeasurementValue() {
        return temperature != null
                || bloodPressureSys != null
                || bloodPressureDia != null
                || heartRate != null
                || spO2 != null
                || ecgWaveform != null
                || ecgSamplingHz != null
                || ecgDurationSeconds != null;
    }

    public LocalDateTime resolveMeasuredAt(LocalDateTime fallback) {
        return measuredAt != null ? measuredAt : fallback;
    }
}
