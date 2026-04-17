package com.waddoc.domain.vital.entity;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "vital_measurement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalMeasurement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vital_measurement_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private CareCase careCase;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "blood_pressure_sys")
    private Integer bloodPressureSys;

    @Column(name = "blood_pressure_dia")
    private Integer bloodPressureDia;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "spo2")
    private Integer spO2;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ecg_waveform_json", columnDefinition = "jsonb")
    private List<BigDecimal> ecgWaveform;

    @Column(name = "ecg_sampling_hz")
    private Integer ecgSamplingHz;

    @Column(name = "ecg_duration_seconds")
    private Integer ecgDurationSeconds;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Builder
    public VitalMeasurement(CareCase careCase) {
        this.careCase = careCase;
    }

    public static VitalMeasurement create(CareCase careCase) {
        return VitalMeasurement.builder()
                .careCase(careCase)
                .build();
    }

    public void applyMeasurements(
            BigDecimal temperature,
            Integer bloodPressureSys,
            Integer bloodPressureDia,
            Integer heartRate,
            Integer spO2,
            List<BigDecimal> ecgWaveform,
            Integer ecgSamplingHz,
            Integer ecgDurationSeconds,
            LocalDateTime measuredAt
    ) {
        boolean changed = false;

        if (temperature != null) {
            this.temperature = temperature;
            changed = true;
        }
        if (bloodPressureSys != null) {
            this.bloodPressureSys = bloodPressureSys;
            changed = true;
        }
        if (bloodPressureDia != null) {
            this.bloodPressureDia = bloodPressureDia;
            changed = true;
        }
        if (heartRate != null) {
            this.heartRate = heartRate;
            changed = true;
        }
        if (spO2 != null) {
            this.spO2 = spO2;
            changed = true;
        }
        if (ecgWaveform != null) {
            this.ecgWaveform = List.copyOf(ecgWaveform);
            changed = true;
        }
        if (ecgSamplingHz != null) {
            this.ecgSamplingHz = ecgSamplingHz;
            changed = true;
        }
        if (ecgDurationSeconds != null) {
            this.ecgDurationSeconds = ecgDurationSeconds;
            changed = true;
        }

        if (changed && measuredAt != null) {
            this.measuredAt = measuredAt;
        }
    }
}
