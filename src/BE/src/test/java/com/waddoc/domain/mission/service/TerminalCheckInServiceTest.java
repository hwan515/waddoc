package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.dto.ClaimMissionTerminalRequest;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesRequest;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.security.DeviceTerminalPrincipal;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.DeviceTerminalScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalCheckInServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private MissionTerminalTokenService missionTerminalTokenService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TerminalCheckInService terminalCheckInService;

    @Test
    void lookupCandidates_filtersCandidatesByTerminalRegionAndVehicle() {
        Authentication authentication = mock(Authentication.class);
        DeviceTerminalPrincipal principal = new DeviceTerminalPrincipal(
                "device-terminal:robot-terminal-01",
                "robot-terminal-01",
                "veh_GIMCHEON_01",
                "GIMCHEON",
                List.of(DeviceTerminalScopes.CHECK_IN_CANDIDATES)
        );
        when(accessControlService.assertDeviceTerminalPrincipal(
                authentication,
                DeviceTerminalScopes.CHECK_IN_CANDIDATES
        )).thenReturn(principal);

        TerminalCheckInCandidatesRequest request = new TerminalCheckInCandidatesRequest();
        setField(request, "phoneLast4", "3720");
        setField(request, "birthDate6", "580315");

        Mission accessibleMission = createLookupMission(
                "ms_accessible",
                "veh_GIMCHEON_01",
                "GIMCHEON",
                "홍길동"
        );
        Mission differentVehicleMission = createFilteredLookupMission(
                "ms_other_vehicle",
                "veh_GIMCHEON_02",
                "GIMCHEON"
        );
        Mission differentRegionMission = createFilteredLookupMission(
                "ms_other_region",
                null,
                "ANDONG"
        );

        when(missionRepository.findTerminalCandidates(
                eq("3720"),
                eq("580315"),
                eq(BookingStatus.CONFIRMED),
                any()
        )).thenReturn(List.of(accessibleMission, differentVehicleMission, differentRegionMission));

        TerminalCheckInCandidatesResponse response = terminalCheckInService.lookupCandidates(request, authentication);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().get(0).getMissionId()).isEqualTo("ms_accessible");
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void claimMission_bindsUnassignedMissionToClaimingVehicle() {
        Authentication authentication = mock(Authentication.class);
        DeviceTerminalPrincipal principal = new DeviceTerminalPrincipal(
                "device-terminal:robot-terminal-01",
                "robot-terminal-01",
                "veh_GIMCHEON_01",
                "GIMCHEON",
                List.of(DeviceTerminalScopes.CLAIM_MISSION)
        );
        when(accessControlService.assertDeviceTerminalPrincipal(
                authentication,
                DeviceTerminalScopes.CLAIM_MISSION
        )).thenReturn(principal);

        ClaimMissionTerminalRequest request = new ClaimMissionTerminalRequest();
        setField(request, "phoneLast4", "3720");
        setField(request, "birthDate6", "580315");

        Mission mission = createClaimMission(
                "ms_claimable",
                null,
                "GIMCHEON",
                "01012343720",
                "580315"
        );
        when(missionRepository.findWithDetailsByPublicId("ms_claimable")).thenReturn(Optional.of(mission));
        when(missionTerminalTokenService.issueTokenForDeviceClaim("ms_claimable", "robot-terminal-01"))
                .thenReturn(IssueMissionTerminalTokenResponse.builder()
                        .missionId("ms_claimable")
                        .caseId("case_test123")
                        .terminalToken("mission-terminal-token")
                        .expiresIn(1800L)
                        .scopes(List.of("mission:identity-check", "session:issue-patient-token"))
                        .build());

        IssueMissionTerminalTokenResponse response =
                terminalCheckInService.claimMission("ms_claimable", request, authentication);

        assertThat(mission.getVehicleId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(response.getMissionId()).isEqualTo("ms_claimable");
        verify(missionTerminalTokenService).issueTokenForDeviceClaim("ms_claimable", "robot-terminal-01");
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    private Mission createLookupMission(
            String missionId,
            String vehicleId,
            String regionCode,
            String patientName
    ) {
        Patient patient = mock(Patient.class);
        when(patient.getRegionCode()).thenReturn(regionCode);
        when(patient.getName()).thenReturn(patientName);

        User user = mock(User.class);
        when(user.getName()).thenReturn("이종");

        DoctorProfile doctor = mock(DoctorProfile.class);
        when(doctor.getUser()).thenReturn(user);

        Booking booking = mock(Booking.class);
        when(booking.getAppointmentDate()).thenReturn(LocalDate.of(2026, 3, 20));
        when(booking.getStartTime()).thenReturn(LocalTime.of(14, 30));

        com.waddoc.domain.carecase.entity.CareCase careCase = mock(com.waddoc.domain.carecase.entity.CareCase.class);
        when(careCase.getPatient()).thenReturn(patient);
        when(careCase.getDoctor()).thenReturn(doctor);
        when(careCase.getBooking()).thenReturn(booking);

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId(vehicleId)
                .destination("경북 김천시")
                .build();
        setField(mission, "publicId", missionId);
        setField(mission, "phase", MissionPhase.ARRIVED);
        return mission;
    }

    private Mission createFilteredLookupMission(
            String missionId,
            String vehicleId,
            String regionCode
    ) {
        Patient patient = mock(Patient.class);
        when(patient.getRegionCode()).thenReturn(regionCode);

        com.waddoc.domain.carecase.entity.CareCase careCase = mock(com.waddoc.domain.carecase.entity.CareCase.class);
        when(careCase.getPatient()).thenReturn(patient);

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId(vehicleId)
                .destination("경북 김천시")
                .build();
        setField(mission, "publicId", missionId);
        setField(mission, "phase", MissionPhase.ARRIVED);
        return mission;
    }

    private Mission createClaimMission(
            String missionId,
            String vehicleId,
            String regionCode,
            String patientPhone,
            String birthDate6
    ) {
        Patient patient = mock(Patient.class);
        when(patient.getPhone()).thenReturn(patientPhone);
        when(patient.getBirthDate6()).thenReturn(birthDate6);
        when(patient.getRegionCode()).thenReturn(regionCode);

        Booking booking = mock(Booking.class);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);

        com.waddoc.domain.carecase.entity.CareCase careCase = mock(com.waddoc.domain.carecase.entity.CareCase.class);
        when(careCase.getPatient()).thenReturn(patient);
        when(careCase.getBooking()).thenReturn(booking);

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId(vehicleId)
                .destination("경북 김천시")
                .build();
        setField(mission, "publicId", missionId);
        setField(mission, "phase", MissionPhase.ARRIVED);
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
