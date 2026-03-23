package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.mission.dto.UpsertMissionVitalMeasurementResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.vital.dto.UpsertVitalMeasurementRequest;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import com.waddoc.domain.vital.service.VitalMeasurementCommandService;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.authorization.AccessActor;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionVitalMeasurementServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private VitalMeasurementCommandService vitalMeasurementCommandService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private MissionVitalMeasurementService missionVitalMeasurementService;

    @Test
    void upsert_savesMissionScopedVitals() {
        Authentication authentication = mock(Authentication.class);
        Mission mission = buildMission();
        mission.updatePhase(MissionPhase.ARRIVED);

        UpsertVitalMeasurementRequest request = UpsertVitalMeasurementRequest.builder()
                .temperature(new BigDecimal("36.7"))
                .heartRate(72)
                .build();
        VitalMeasurementResponse vitals = VitalMeasurementResponse.builder()
                .caseId("case_test123")
                .temperature(new BigDecimal("36.7"))
                .heartRate(72)
                .measuredAt(LocalDateTime.of(2026, 3, 23, 14, 23, 10))
                .build();

        when(accessControlService.assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.VITALS_WRITE
        )).thenReturn(new AccessActor("terminal:ms_test123", "MISSION_TERMINAL"));
        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(vitalMeasurementCommandService.upsert(mission.getCareCase(), request)).thenReturn(vitals);

        UpsertMissionVitalMeasurementResponse response = missionVitalMeasurementService.upsert(
                "ms_test123",
                request,
                authentication
        );

        assertThat(response.getMissionId()).isEqualTo("ms_test123");
        assertThat(response.getCaseId()).isEqualTo("case_test123");
        assertThat(response.getVitals().getTemperature()).isEqualByComparingTo("36.7");
        assertThat(response.getVitals().getHeartRate()).isEqualTo(72);
        verify(accessControlService).assertAdminOrMissionTerminal(
                authentication,
                "ms_test123",
                MissionTerminalScopes.VITALS_WRITE
        );
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void upsert_rejectsEmptyRequest() {
        assertThatThrownBy(() -> missionVitalMeasurementService.upsert(
                "ms_test123",
                UpsertVitalMeasurementRequest.builder().build(),
                mock(Authentication.class)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private Mission buildMission() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("경북 울릉군 ...")
                .phone("01012345678")
                .build();
        setField(patient, "publicId", "pat_test123");

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
                .appointmentDate(LocalDate.of(2026, 3, 23))
                .startTime(java.time.LocalTime.of(14, 0))
                .endTime(java.time.LocalTime.of(14, 30))
                .build();

        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(null)
                .build();
        setField(careCase, "publicId", "case_test123");

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("v-001")
                .destination("경북 울릉군 ...")
                .dispatchedAt(LocalDateTime.of(2026, 3, 23, 13, 20))
                .estimatedArrivalTime(LocalDateTime.of(2026, 3, 23, 13, 50))
                .build();
        setField(mission, "publicId", "ms_test123");
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
