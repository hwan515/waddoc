package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttMissionPhaseUpdaterTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MqttMissionResolver mqttMissionResolver;

    private MqttMissionPhaseUpdater mqttMissionPhaseUpdater;

    @BeforeEach
    void setUp() {
        mqttMissionPhaseUpdater = new MqttMissionPhaseUpdater(
                new ObjectMapper(),
                missionRepository,
                mqttMissionResolver
        );
    }

    @Test
    void updateFromState_promotesDispatchedMissionToEnRoute() {
        Mission mission = buildMission(MissionPhase.DISPATCHED);
        when(mqttMissionResolver.resolveMission(any(), any(), any(), any()))
                .thenReturn(Optional.of(mission));

        mqttMissionPhaseUpdater.updateFromState("""
                {
                  "missionId": "ms_test",
                  "vehicleId": "veh_GIMCHEON_01",
                  "state": "주행 중"
                }
                """);

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        verify(missionRepository).save(mission);
    }

    @Test
    void updateFromMinimap_promotesEnRouteMissionToArrivedWhenRouteClears() {
        Mission mission = buildMission(MissionPhase.EN_ROUTE);
        when(mqttMissionResolver.resolveMission(any(), any(), any(), any()))
                .thenReturn(Optional.of(mission));

        mqttMissionPhaseUpdater.updateFromMinimap("""
                {
                  "missionId": "ms_test",
                  "vehicleId": "veh_GIMCHEON_01",
                  "goal_waypoint_id": "221",
                  "speed_ms": 0.0,
                  "clear_reason": "goal_reset"
                }
                """, """
                {
                  "state": "도착"
                }
                """);

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.ARRIVED);
        verify(missionRepository).save(mission);
    }

    @Test
    void updateFromState_doesNotDowngradeConsultingMission() {
        Mission mission = buildMission(MissionPhase.CONSULTING);
        when(mqttMissionResolver.resolveMission(any(), any(), any(), any()))
                .thenReturn(Optional.of(mission));

        mqttMissionPhaseUpdater.updateFromState("""
                {
                  "missionId": "ms_test",
                  "vehicleId": "veh_GIMCHEON_01",
                  "state": "주행 중"
                }
                """);

        assertThat(mission.getPhase()).isEqualTo(MissionPhase.CONSULTING);
        verify(missionRepository, never()).save(any(Mission.class));
    }

    private Mission buildMission(MissionPhase phase) {
        com.waddoc.domain.patient.entity.Patient patient = com.waddoc.domain.patient.entity.Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON")
                .address("경북 김천시")
                .phone("01012345678")
                .build();
        com.waddoc.domain.user.entity.User doctorUser = com.waddoc.domain.user.entity.User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("김의사")
                .role(com.waddoc.domain.user.entity.Role.DOCTOR)
                .build();
        com.waddoc.domain.doctor.entity.DoctorProfile doctor = com.waddoc.domain.doctor.entity.DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        com.waddoc.domain.booking.entity.Booking booking = com.waddoc.domain.booking.entity.Booking.builder()
                .patient(patient)
                .intakeSession(null)
                .slot(null)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 27))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        com.waddoc.domain.carecase.entity.CareCase careCase = com.waddoc.domain.carecase.entity.CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctor)
                .intakeSession(null)
                .build();
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("veh_GIMCHEON_01")
                .destination("경북 김천시")
                .targetWaypointNumber(221)
                .build();
        setField(mission, "publicId", "ms_test");
        setField(mission, "phase", phase);
        return mission;
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
