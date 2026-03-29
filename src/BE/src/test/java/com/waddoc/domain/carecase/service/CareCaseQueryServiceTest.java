package com.waddoc.domain.carecase.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
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
import com.waddoc.domain.vital.entity.VitalMeasurement;
import com.waddoc.domain.vital.repository.VitalMeasurementRepository;
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
    private ConsultationSummaryRepository consultationSummaryRepository;

    @Mock
    private VitalMeasurementRepository vitalMeasurementRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private CareCaseQueryService careCaseQueryService;

    @Test
    void getCaseDetailReturnsMissionSessionAndVitalsMetadata() {
        CareCase careCase = buildCareCase();
        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("Ulleung")
                .build();
        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-1")
                .livekitUrl("wss://livekit.test")
                .build();
        ConsultationSession historySession = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-history")
                .livekitUrl("wss://livekit.history")
                .build();
        setField(historySession, "endedAt", LocalDateTime.of(2026, 3, 9, 14, 20));
        ConsultationSummary consultationSummary = ConsultationSummary.builder()
                .session(historySession)
                .summaryNote("Follow-up required")
                .prescriptionIssued(true)
                .prescriptionNote("[\"MED001\"]")
                .needsFollowUp(true)
                .build();
        VitalMeasurement vitalMeasurement = VitalMeasurement.create(careCase);
        vitalMeasurement.applyMeasurements(
                new BigDecimal("36.7"),
                128,
                82,
                72,
                98,
                List.of(new BigDecimal("0.12"), new BigDecimal("0.18")),
                25,
                8,
                LocalDateTime.of(2026, 3, 10, 10, 0)
        );
        setField(careCase, "createdAt", LocalDateTime.of(2026, 3, 10, 10, 5));

        when(careCaseRepository.findWithDetailsByPublicId(careCase.getPublicId())).thenReturn(Optional.of(careCase));
        when(missionRepository.findByCareCase(careCase)).thenReturn(Optional.of(mission));
        when(consultationSessionRepository.findByCareCase(careCase)).thenReturn(Optional.of(session));
        when(consultationSummaryRepository.findAllByPatientPublicIdAndSessionStatus(
                careCase.getPatient().getPublicId(),
                ConsultationSessionStatus.COMPLETED
        )).thenReturn(List.of(consultationSummary));
        when(vitalMeasurementRepository.findByCareCase(careCase)).thenReturn(Optional.of(vitalMeasurement));

        CaseDetailResponse response = careCaseQueryService.getCaseDetail(
                careCase.getPublicId(),
                new AuthenticatedUser("usr_doctor", Role.DOCTOR)
        );

        assertThat(response.getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getMissionId()).isEqualTo(mission.getPublicId());
        assertThat(response.getSessionId()).isEqualTo(session.getPublicId());
        assertThat(response.getPatient().getName()).isEqualTo("Hong Gil-dong");
        assertThat(response.getIntakeSummary().getDepartmentName()).isEqualTo("Internal Medicine");
        assertThat(response.getConsultationHistories()).hasSize(1);
        assertThat(response.getConsultationHistories().get(0).getDoctorName()).isEqualTo("Doctor Kim");
        assertThat(response.getConsultationHistories().get(0).getSymptom())
                .isEqualTo("Patient selected the department directly.");
        assertThat(response.getConsultationHistories().get(0).getSummaryNote()).isEqualTo("Follow-up required");
        assertThat(response.getConsultationHistories().get(0).isPrescriptionIssued()).isTrue();
        assertThat(response.getVitals()).isNotNull();
        assertThat(response.getVitals().getTemperature()).isEqualByComparingTo("36.7");
        assertThat(response.getVitals().getBloodPressureSys()).isEqualTo(128);
        assertThat(response.getVitals().getBloodPressureDia()).isEqualTo(82);
        assertThat(response.getVitals().getHeartRate()).isEqualTo(72);
        assertThat(response.getVitals().getSpO2()).isEqualTo(98);
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
                .destination("Ulleung")
                .build();
        mission.updatePhase(MissionPhase.VERIFYING);
        ConsultationSession consultationSession = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room_case_100")
                .livekitUrl("wss://livekit.example.com")
                .build();
        consultationSession.markReady();

        when(careCaseRepository.findAllAssignedToDoctor("usr_doctor", null, null)).thenReturn(List.of(careCase));
        when(missionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(mission));
        when(consultationSessionRepository.findAllByCareCaseIn(List.of(careCase))).thenReturn(List.of(consultationSession));

        DoctorCaseListResponse response = careCaseQueryService.getAssignedCases(
                new AuthenticatedUser("usr_doctor", Role.DOCTOR),
                null,
                null
        );

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getMissionPhase()).isEqualTo(MissionPhase.VERIFYING);
        assertThat(response.getCases().get(0).getSessionId()).isEqualTo(consultationSession.getPublicId());
        assertThat(response.getCases().get(0).getSessionStatus()).isEqualTo(ConsultationSessionStatus.READY);
        assertThat(response.getCases().get(0).getDepartmentName()).isEqualTo("Internal Medicine");
        verify(accessControlService).getDoctorProfileOrThrow(new AuthenticatedUser("usr_doctor", Role.DOCTOR));
    }

    private CareCase buildCareCase() {
        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();
        Patient patient = Patient.builder()
                .name("Hong Gil-dong")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung")
                .phone("01012345678")
                .build();
        IntakeSession intakeSession = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        intakeSession.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "Patient selected the department directly.",
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
