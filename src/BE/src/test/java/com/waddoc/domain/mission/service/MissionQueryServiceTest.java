package com.waddoc.domain.mission.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.dto.MissionDetailResponse;
import com.waddoc.domain.mission.dto.MissionListResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionQueryServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private MissionQueryService missionQueryService;

    @Test
    void getMissionsReturnsMissionSummariesForAdminDashboard() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        LocalDate date = LocalDate.of(2026, 3, 11);

        Mission mission = buildMission();
        setField(mission.getCareCase(), "publicId", "case_T7nLp4");
        setField(mission, "publicId", "ms_F2gHn6");

        when(missionRepository.findAllForAdminDashboardByPhaseAndAppointmentDate(MissionPhase.DISPATCHED, date))
                .thenReturn(List.of(mission));

        MissionListResponse response = missionQueryService.getMissions(admin, date, MissionPhase.DISPATCHED);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getMissions()).hasSize(1);
        assertThat(response.getMissions().get(0).getMissionId()).isEqualTo("ms_F2gHn6");
        assertThat(response.getMissions().get(0).getCaseId()).isEqualTo("case_T7nLp4");
        assertThat(response.getMissions().get(0).getPatientName()).isEqualTo("홍길동");
        assertThat(response.getMissions().get(0).getPhase()).isEqualTo(MissionPhase.DISPATCHED);
        assertThat(response.getMissions().get(0).getAppointmentDate()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(response.getMissions().get(0).getAppointmentTime()).isEqualTo("10:00");
        assertThat(response.getMissions().get(0).getTargetWaypointNumber()).isEqualTo(59);
        assertThat(response.getMissions().get(0).getDispatchedAt())
                .isEqualTo(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"));
        assertThat(response.getMissions().get(0).getEstimatedArrivalTime())
                .isEqualTo(OffsetDateTime.parse("2026-03-11T09:45:00+09:00"));
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void getMissionsWithoutFiltersUsesUnfilteredRepositoryQuery() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission();
        setField(mission.getCareCase(), "publicId", "case_T7nLp4");
        setField(mission, "publicId", "ms_F2gHn6");

        when(missionRepository.findAllForAdminDashboard()).thenReturn(List.of(mission));

        MissionListResponse response = missionQueryService.getMissions(admin, null, null);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getMissions()).hasSize(1);
        assertThat(response.getMissions().get(0).getMissionId()).isEqualTo("ms_F2gHn6");
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void getMissionDetailReturnsMissionDetail() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        Mission mission = buildMission();
        setField(mission.getCareCase(), "publicId", "case_T7nLp4");
        setField(mission, "publicId", "ms_F2gHn6");
        setField(mission, "createdAt", LocalDateTime.of(2026, 3, 11, 8, 30));
        setField(mission, "updatedAt", LocalDateTime.of(2026, 3, 11, 9, 15));
        mission.updatePhase(MissionPhase.EN_ROUTE);
        mission.updateLocation(new BigDecimal("37.4845000"), new BigDecimal("130.9057000"));

        when(missionRepository.findWithDetailsByPublicId("ms_F2gHn6"))
                .thenReturn(Optional.of(mission));

        MissionDetailResponse response = missionQueryService.getMissionDetail(admin, "ms_F2gHn6");

        assertThat(response.getMissionId()).isEqualTo("ms_F2gHn6");
        assertThat(response.getCaseId()).isEqualTo("case_T7nLp4");
        assertThat(response.getPhase()).isEqualTo(MissionPhase.EN_ROUTE);
        assertThat(response.getCurrentLocation()).isNotNull();
        assertThat(response.getCurrentLocation().getLatitude()).isEqualByComparingTo("37.4845000");
        assertThat(response.getCurrentLocation().getLongitude()).isEqualByComparingTo("130.9057000");
        assertThat(response.getCurrentLocation().getTimestamp())
                .isEqualTo(OffsetDateTime.parse("2026-03-11T09:15:00+09:00"));
        assertThat(response.getUpdatedAt())
                .isEqualTo(OffsetDateTime.parse("2026-03-11T09:15:00+09:00"));
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void getMissionDetailThrowsWhenMissionDoesNotExist() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);

        when(missionRepository.findWithDetailsByPublicId("ms_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> missionQueryService.getMissionDetail(admin, "ms_missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MISSION_NOT_FOUND));

        verify(accessControlService).assertAdmin(admin);
    }

    private Mission buildMission() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("경북 울릉군 울릉읍...")
                .phone("01012345678")
                .build();
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
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

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("v-001")
                .destination("경북 울릉군 울릉읍...")
                .dispatchedAt(LocalDateTime.of(2026, 3, 11, 8, 30))
                .estimatedArrivalTime(LocalDateTime.of(2026, 3, 11, 9, 45))
                .targetWaypointNumber(59)
                .build();
        mission.updatePhase(MissionPhase.DISPATCHED);
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
