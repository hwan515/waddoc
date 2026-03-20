package com.waddoc.domain.carecase.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareCaseQueryServiceTest {

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private CareCaseQueryService careCaseQueryService;

    @Test
    void getCaseDetailReturnsMissionAndSessionMetadata() {
        CareCase careCase = buildCareCase();
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("울릉군")
                .build();
        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-1")
                .livekitUrl("wss://livekit.test")
                .build();
        setField(careCase, "createdAt", LocalDateTime.of(2026, 3, 10, 10, 5));

        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(missionRepository.findByCareCase(careCase)).thenReturn(Optional.of(mission));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.of(session));

        CaseDetailResponse response = careCaseQueryService.getCaseDetail(
                careCase.getPublicId(),
                new AuthenticatedUser("usr_doctor", Role.DOCTOR)
        );

        assertThat(response.getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getMissionId()).isEqualTo(mission.getPublicId());
        assertThat(response.getSessionId()).isEqualTo(session.getPublicId());
        assertThat(response.getPatient().getName()).isEqualTo("홍길동");
        assertThat(response.getIntakeSummary().getDepartmentName()).isEqualTo("내과");
        verify(accessControlService).assertAssignedDoctorOrAdmin(
                new AuthenticatedUser("usr_doctor", Role.DOCTOR),
                careCase
        );
    }

    @Test
    void getAssignedCasesReturnsMissionPhaseInSummary() {
        CareCase careCase = buildCareCase();
        setField(careCase, "id", 100L);
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("울릉군")
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);

        when(careCaseRepository.findAllAssignedToDoctor("usr_doctor", null, null)).thenReturn(List.of(careCase));
        when(missionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(mission));

        DoctorCaseListResponse response = careCaseQueryService.getAssignedCases(
                new AuthenticatedUser("usr_doctor", Role.DOCTOR),
                null,
                null
        );

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getMissionPhase()).isEqualTo(MissionPhase.VERIFYING);
        assertThat(response.getCases().get(0).getDepartmentName()).isEqualTo("내과");
        verify(accessControlService).getDoctorProfileOrThrow(new AuthenticatedUser("usr_doctor", Role.DOCTOR));
    }

    private CareCase buildCareCase() {
        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();
        IntakeSession intakeSession = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        intakeSession.recordSelection(
                "INTERNAL_MEDICINE",
                "내과",
                ConfidenceLevel.HIGH,
                false,
                "환자가 내과를 직접 선택했습니다.",
                List.of("slot_001")
        );
        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(intakeSession)
                .slot(null)
                .doctor(doctorProfile)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        return CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(intakeSession)
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
