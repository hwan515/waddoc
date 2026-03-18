package com.waddoc.domain.mission.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.dto.CreateMissionRequest;
import com.waddoc.domain.mission.dto.CreateMissionResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionCommandServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private MissionCommandService missionCommandService;

    @Test
    void createMissionCreatesMissionForCase() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        CareCase careCase = buildCareCase();
        setField(careCase, "publicId", "case_T7nLp4");

        CreateMissionRequest request = CreateMissionRequest.builder()
                .caseId("case_T7nLp4")
                .vehicleId("v-001")
                .destination("Ulleung")
                .scheduledTime(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"))
                .build();

        when(careCaseRepository.findByPublicId("case_T7nLp4")).thenReturn(Optional.of(careCase));
        when(missionRepository.findByCareCase(careCase)).thenReturn(Optional.empty());
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> {
            Mission mission = invocation.getArgument(0);
            setField(mission, "publicId", "ms_F2gHn6");
            setField(mission, "createdAt", LocalDateTime.of(2026, 3, 10, 14, 0));
            return mission;
        });

        CreateMissionResponse response = missionCommandService.createMission(admin, request);

        ArgumentCaptor<Mission> missionCaptor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(missionCaptor.capture());

        Mission savedMission = missionCaptor.getValue();
        assertThat(savedMission.getCareCase()).isSameAs(careCase);
        assertThat(savedMission.getVehicleId()).isEqualTo("v-001");
        assertThat(savedMission.getDestination()).isEqualTo("Ulleung");
        assertThat(savedMission.getDispatchedAt()).isEqualTo(LocalDateTime.of(2026, 3, 11, 8, 30));
        assertThat(savedMission.getPhase()).isEqualTo(MissionPhase.CREATED);

        assertThat(response.getMissionId()).isEqualTo("ms_F2gHn6");
        assertThat(response.getCaseId()).isEqualTo("case_T7nLp4");
        assertThat(response.getPhase()).isEqualTo(MissionPhase.CREATED);
        assertThat(response.getVehicleId()).isEqualTo("v-001");
        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T14:00:00+09:00"));
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void createMissionThrowsWhenCaseDoesNotExist() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        CreateMissionRequest request = CreateMissionRequest.builder()
                .caseId("case_missing")
                .vehicleId("v-001")
                .destination("Ulleung")
                .scheduledTime(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"))
                .build();

        when(careCaseRepository.findByPublicId("case_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> missionCommandService.createMission(admin, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CASE_NOT_FOUND));

        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void createMissionThrowsWhenMissionAlreadyExists() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        CareCase careCase = buildCareCase();
        setField(careCase, "publicId", "case_T7nLp4");
        CreateMissionRequest request = CreateMissionRequest.builder()
                .caseId("case_T7nLp4")
                .vehicleId("v-001")
                .destination("Ulleung")
                .scheduledTime(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"))
                .build();

        Mission existingMission = Mission.builder()
                .careCase(careCase)
                .vehicleId("v-999")
                .destination("Existing")
                .build();

        when(careCaseRepository.findByPublicId("case_T7nLp4")).thenReturn(Optional.of(careCase));
        when(missionRepository.findByCareCase(careCase)).thenReturn(Optional.of(existingMission));

        assertThatThrownBy(() -> missionCommandService.createMission(admin, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MISSION_ALREADY_EXISTS));

        verify(accessControlService).assertAdmin(admin);
    }

    private CareCase buildCareCase() {
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

        return CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(null)
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
