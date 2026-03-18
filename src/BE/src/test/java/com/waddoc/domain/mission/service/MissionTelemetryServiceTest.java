package com.waddoc.domain.mission.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.dto.MissionTelemetryRequest;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionTelemetryServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @InjectMocks
    private MissionTelemetryService missionTelemetryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(missionTelemetryService, "telemetryApiKey", "telemetry-dev-key");
    }

    @Test
    void receiveTelemetryUpdatesMissionStateWhenEventIsFresh() {
        Mission mission = buildMission();
        setField(mission, "publicId", "ms_F2gHn6");

        when(missionRepository.findByPublicId("ms_F2gHn6")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        missionTelemetryService.receiveTelemetry(
                "ms_F2gHn6",
                "telemetry-dev-key",
                telemetryRequest("ros2_msg_abc123", 42L, MissionPhase.EN_ROUTE, "37.4845", "130.9057")
        );

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        assertThat(mission.getLatitude()).isEqualByComparingTo("37.4845");
        assertThat(mission.getLongitude()).isEqualByComparingTo("130.9057");
        assertThat(mission.getLastTelemetrySourceEventId()).isEqualTo("ros2_msg_abc123");
        assertThat(mission.getLastTelemetrySeqNo()).isEqualTo(42L);
        assertThat(mission.getLastTelemetryAt()).isEqualTo(LocalDateTime.of(2026, 3, 11, 9, 15));
        verify(missionRepository).save(mission);
    }

    @Test
    void receiveTelemetryIgnoresDuplicateSourceEventId() {
        Mission mission = buildMission();
        setField(mission, "publicId", "ms_F2gHn6");
        mission.recordTelemetry("ros2_msg_abc123", 42L, LocalDateTime.of(2026, 3, 11, 9, 15));

        when(missionRepository.findByPublicId("ms_F2gHn6")).thenReturn(Optional.of(mission));

        missionTelemetryService.receiveTelemetry(
                "ms_F2gHn6",
                "telemetry-dev-key",
                telemetryRequest("ros2_msg_abc123", 43L, MissionPhase.EN_ROUTE, "37.5000", "130.9000")
        );

        assertThat(mission.getLatitude()).isNull();
        assertThat(mission.getLongitude()).isNull();
        assertThat(mission.getLastTelemetrySeqNo()).isEqualTo(42L);
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    void receiveTelemetryIgnoresOlderSeqNo() {
        Mission mission = buildMission();
        setField(mission, "publicId", "ms_F2gHn6");
        mission.recordTelemetry("ros2_msg_old", 42L, LocalDateTime.of(2026, 3, 11, 9, 15));

        when(missionRepository.findByPublicId("ms_F2gHn6")).thenReturn(Optional.of(mission));

        missionTelemetryService.receiveTelemetry(
                "ms_F2gHn6",
                "telemetry-dev-key",
                telemetryRequest("ros2_msg_new", 41L, MissionPhase.EN_ROUTE, "37.5000", "130.9000")
        );

        assertThat(mission.getLatitude()).isNull();
        assertThat(mission.getLongitude()).isNull();
        assertThat(mission.getLastTelemetrySourceEventId()).isEqualTo("ros2_msg_old");
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    void receiveTelemetryDoesNotDowngradePhaseButStillUpdatesLocation() {
        Mission mission = buildMission();
        setField(mission, "publicId", "ms_F2gHn6");
        mission.updatePhase(MissionPhase.DISPATCHED);
        mission.updatePhase(MissionPhase.EN_ROUTE);
        mission.updatePhase(MissionPhase.ARRIVED);

        when(missionRepository.findByPublicId("ms_F2gHn6")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        missionTelemetryService.receiveTelemetry(
                "ms_F2gHn6",
                "telemetry-dev-key",
                telemetryRequest("ros2_msg_abc124", 44L, MissionPhase.EN_ROUTE, "37.6000", "130.9500")
        );

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.ARRIVED);
        assertThat(mission.getLatitude()).isEqualByComparingTo("37.6000");
        assertThat(mission.getLongitude()).isEqualByComparingTo("130.9500");
        assertThat(mission.getLastTelemetrySeqNo()).isEqualTo(44L);
        verify(missionRepository).save(mission);
    }

    @Test
    void receiveTelemetryRejectsInvalidApiKey() {
        assertThatThrownBy(() -> missionTelemetryService.receiveTelemetry(
                "ms_F2gHn6",
                "wrong-key",
                telemetryRequest("ros2_msg_abc123", 42L, MissionPhase.EN_ROUTE, "37.4845", "130.9057")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED));
    }

    private MissionTelemetryRequest telemetryRequest(
            String sourceEventId,
            Long seqNo,
            MissionPhase phase,
            String latitude,
            String longitude
    ) {
        return MissionTelemetryRequest.builder()
                .source("ROS2")
                .sourceEventId(sourceEventId)
                .seqNo(seqNo)
                .vehicleId("v-001")
                .phase(phase)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .speed(new BigDecimal("30.5"))
                .heading(180)
                .timestamp(OffsetDateTime.parse("2026-03-11T09:15:00+09:00"))
                .metadata(Map.of())
                .build();
    }

    private Mission buildMission() {
        Patient patient = Patient.builder()
                .name("Hong Gil-dong")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung")
                .phone("01012345678")
                .build();
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded-password")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();
        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(null)
                .slot(null)
                .doctor(doctorProfile)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(10, 30))
                .build();
        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(null)
                .build();

        return Mission.builder()
                .careCase(careCase)
                .vehicleId("v-001")
                .destination("Ulleung")
                .dispatchedAt(LocalDateTime.of(2026, 3, 11, 8, 30))
                .estimatedArrivalTime(LocalDateTime.of(2026, 3, 11, 9, 45))
                .build();
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
