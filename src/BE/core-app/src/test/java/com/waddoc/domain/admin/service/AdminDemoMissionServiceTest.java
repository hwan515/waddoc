package com.waddoc.domain.admin.service;

import com.waddoc.domain.admin.dto.AdminDemoMissionActionResponse;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.dispatch.service.RobotWaypointCommandClient;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.global.config.DispatchAssignmentPolicy;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDemoMissionServiceTest {

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private RobotWaypointCommandClient robotWaypointCommandClient;

    @Mock
    private DemoModePolicy demoModePolicy;

    @Mock
    private DispatchAssignmentPolicy dispatchAssignmentPolicy;

    @InjectMocks
    private AdminDemoMissionService adminDemoMissionService;

    @Test
    void dispatchMission_callsWaypointApiForMappedMission() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(59);
        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(mission.getCareCase())
                .regionCode("GIMCHEON")
                .destination(mission.getDestination())
                .build();

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.findAllByVehicleIdAndPhaseIn(eq(mission.getVehicleId()), any())).thenReturn(java.util.List.of());
        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId(anyString())).thenReturn(Optional.of(outbox));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.dispatchMission(admin, mission.getPublicId());

        assertThat(response.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        assertThat(response.isWaypointCommandSent()).isTrue();
        assertThat(response.isDummyCompleted()).isFalse();
        assertThat(outbox.isCompleted()).isTrue();
        verify(robotWaypointCommandClient).dispatchMission(
                mission.getPublicId(),
                mission.getVehicleId(),
                59,
                mission.getDestination()
        );
    }

    @Test
    void dispatchMission_completesUnmappedMissionAsDummy() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(null);
        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(mission.getCareCase())
                .regionCode("GIMCHEON")
                .destination(mission.getDestination())
                .build();

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.findAllByVehicleIdAndPhaseIn(eq(mission.getVehicleId()), any())).thenReturn(java.util.List.of());
        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId(anyString())).thenReturn(Optional.of(outbox));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.dispatchMission(admin, mission.getPublicId());

        assertThat(response.getPhase()).isEqualTo(MissionPhase.COMPLETED);
        assertThat(response.isWaypointCommandSent()).isFalse();
        assertThat(response.isDummyCompleted()).isTrue();
        assertThat(outbox.isCompleted()).isTrue();
        verify(robotWaypointCommandClient, never()).dispatchMission(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void dispatchMission_keepsUnmappedMissionActiveWhenDirectWebrtcEnabled() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(null);
        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(mission.getCareCase())
                .regionCode("GIMCHEON")
                .destination(mission.getDestination())
                .build();

        ReflectionTestUtils.setField(adminDemoMissionService, "directWebrtcEnabled", true);

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.findAllByVehicleIdAndPhaseIn(eq(mission.getVehicleId()), any())).thenReturn(java.util.List.of());
        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId(anyString())).thenReturn(Optional.of(outbox));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.dispatchMission(admin, mission.getPublicId());

        assertThat(response.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        assertThat(response.isWaypointCommandSent()).isFalse();
        assertThat(response.isDummyCompleted()).isFalse();
        assertThat(outbox.isCompleted()).isTrue();
        verify(robotWaypointCommandClient, never()).dispatchMission(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void arriveMission_advancesToArrived() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(92);
        mission.updatePhase(MissionPhase.DISPATCHED);

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.arriveMission(admin, mission.getPublicId());

        assertThat(response.getPhase()).isEqualTo(MissionPhase.ARRIVED);
        assertThat(response.getPreviousPhase()).isEqualTo(MissionPhase.DISPATCHED);
    }

    @Test
    void dispatchMission_completesOtherActiveMissionsOnSameVehicle() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(59);
        Mission staleMission = buildMission(13);
        staleMission.updatePhase(MissionPhase.DISPATCHED);
        staleMission.updatePhase(MissionPhase.EN_ROUTE);
        staleMission.updatePhase(MissionPhase.ARRIVED);

        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(mission.getCareCase())
                .regionCode("GIMCHEON")
                .destination(mission.getDestination())
                .build();

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.findAllByVehicleIdAndPhaseIn(eq(mission.getVehicleId()), any()))
                .thenReturn(java.util.List.of(staleMission));
        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId(anyString())).thenReturn(Optional.of(outbox));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.dispatchMission(admin, mission.getPublicId());

        assertThat(response.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        assertThat(staleMission.getPhase()).isEqualTo(MissionPhase.COMPLETED);
    }

    @Test
    void dispatchMission_assignsDefaultVehicleWhenMissionVehicleIsMissing() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(59, null);
        DispatchOutbox outbox = DispatchOutbox.builder()
                .careCase(mission.getCareCase())
                .regionCode("GIMCHEON")
                .destination(mission.getDestination())
                .build();

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);
        when(dispatchAssignmentPolicy.getDefaultVehicleId()).thenReturn("veh_GIMCHEON_01");
        when(missionRepository.findWithDetailsByPublicId(mission.getPublicId())).thenReturn(Optional.of(mission));
        when(missionRepository.findAllByVehicleIdAndPhaseIn(eq("veh_GIMCHEON_01"), any())).thenReturn(java.util.List.of());
        when(dispatchOutboxRepository.findWithPatientByCareCasePublicId(anyString())).thenReturn(Optional.of(outbox));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDemoMissionActionResponse response =
                adminDemoMissionService.dispatchMission(admin, mission.getPublicId());

        assertThat(response.getVehicleId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(response.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        verify(robotWaypointCommandClient).dispatchMission(
                mission.getPublicId(),
                "veh_GIMCHEON_01",
                59,
                mission.getDestination()
        );
    }

    @Test
    void dispatchMission_rejectsWhenDemoModeIsDisabled() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission(59);

        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(false);

        assertThatThrownBy(() -> adminDemoMissionService.dispatchMission(admin, mission.getPublicId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DEMO_MODE_DISABLED);
    }

    private Mission buildMission(Integer targetWaypointNumber) {
        return buildMission(targetWaypointNumber, "veh_GIMCHEON_01");
    }

    private Mission buildMission(Integer targetWaypointNumber, String vehicleId) {
        Patient patient = Patient.builder()
                .name("Hong Gil-dong")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("GIMCHEON")
                .address("경상북도 김천시 증산면 장전4길 14")
                .phone("01012345678")
                .build();
        Booking booking = Booking.builder()
                .patient(patient)
                .slot(null)
                .doctor(null)
                .channel("PHONE")
                .appointmentDate(LocalDate.of(2026, 3, 25))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(null)
                .intakeSession(null)
                .build();
        return Mission.builder()
                .careCase(careCase)
                .vehicleId(vehicleId)
                .destination(patient.getAddress())
                .targetWaypointNumber(targetWaypointNumber)
                .build();
    }
}
