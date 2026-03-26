package com.waddoc.domain.vital.service;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.vital.dto.UpsertVitalMeasurementRequest;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import com.waddoc.domain.vital.entity.VitalMeasurement;
import com.waddoc.domain.vital.repository.VitalMeasurementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VitalMeasurementCommandServiceTest {

    @Mock
    private VitalMeasurementRepository vitalMeasurementRepository;

    @InjectMocks
    private VitalMeasurementCommandService vitalMeasurementCommandService;

    @Test
    void upsert_createsMeasurementWhenMissing() {
        CareCase careCase = org.mockito.Mockito.mock(CareCase.class);
        when(careCase.getPublicId()).thenReturn("case_test123");
        when(vitalMeasurementRepository.findByCareCase(careCase)).thenReturn(Optional.empty());
        when(vitalMeasurementRepository.save(any(VitalMeasurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime measuredAt = LocalDateTime.of(2026, 3, 23, 14, 23, 10);
        UpsertVitalMeasurementRequest request = UpsertVitalMeasurementRequest.builder()
                .temperature(new BigDecimal("36.7"))
                .bloodPressureSys(128)
                .bloodPressureDia(82)
                .heartRate(72)
                .spO2(98)
                .ecgWaveform(List.of(new BigDecimal("0.12"), new BigDecimal("0.18")))
                .ecgSamplingHz(25)
                .ecgDurationSeconds(8)
                .measuredAt(measuredAt)
                .build();

        VitalMeasurementResponse response = vitalMeasurementCommandService.upsert(careCase, request);

        assertThat(response.getCaseId()).isEqualTo("case_test123");
        assertThat(response.getTemperature()).isEqualByComparingTo("36.7");
        assertThat(response.getBloodPressureSys()).isEqualTo(128);
        assertThat(response.getBloodPressureDia()).isEqualTo(82);
        assertThat(response.getHeartRate()).isEqualTo(72);
        assertThat(response.getSpO2()).isEqualTo(98);
        assertThat(response.getEcgWaveform()).containsExactly(
                new BigDecimal("0.12"),
                new BigDecimal("0.18")
        );
        assertThat(response.getMeasuredAt()).isEqualTo(OffsetDateTime.parse("2026-03-23T14:23:10+09:00"));
        verify(vitalMeasurementRepository).save(any(VitalMeasurement.class));
    }

    @Test
    void upsert_updatesOnlyProvidedFieldsOnExistingMeasurement() {
        CareCase careCase = org.mockito.Mockito.mock(CareCase.class);
        when(careCase.getPublicId()).thenReturn("case_test123");

        VitalMeasurement existing = VitalMeasurement.create(careCase);
        existing.applyMeasurements(
                new BigDecimal("36.5"),
                120,
                80,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 3, 23, 14, 0, 0)
        );
        setField(existing, "id", 1L);

        when(vitalMeasurementRepository.findByCareCase(careCase)).thenReturn(Optional.of(existing));

        VitalMeasurementResponse response = vitalMeasurementCommandService.upsert(
                careCase,
                UpsertVitalMeasurementRequest.builder()
                        .heartRate(74)
                        .spO2(97)
                        .measuredAt(LocalDateTime.of(2026, 3, 23, 14, 5, 0))
                        .build()
        );

        assertThat(response.getTemperature()).isEqualByComparingTo("36.5");
        assertThat(response.getBloodPressureSys()).isEqualTo(120);
        assertThat(response.getBloodPressureDia()).isEqualTo(80);
        assertThat(response.getHeartRate()).isEqualTo(74);
        assertThat(response.getSpO2()).isEqualTo(97);
        assertThat(response.getMeasuredAt()).isEqualTo(OffsetDateTime.parse("2026-03-23T14:05:00+09:00"));
        verify(vitalMeasurementRepository, never()).save(any(VitalMeasurement.class));
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Field not found: " + fieldName);
    }
}
