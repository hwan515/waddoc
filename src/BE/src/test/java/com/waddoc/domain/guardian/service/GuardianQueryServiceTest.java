package com.waddoc.domain.guardian.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.guardian.dto.GuardianConsultationSummariesResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientsResponse;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardianQueryServiceTest {

    @Mock
    private PatientGuardianLinkRepository patientGuardianLinkRepository;

    @Mock
    private ConsultationSummaryRepository consultationSummaryRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private GuardianQueryService guardianQueryService;

    @Test
    void getLinkedPatientsReturnsApprovedLinkedPatients() {
        User guardianUser = User.builder()
                .username("guardian_lee")
                .passwordHash("encoded-password")
                .name("이보호자")
                .role(Role.GUARDIAN)
                .build();
        User adminUser = User.builder()
                .username("admin")
                .passwordHash("encoded-password")
                .name("관리자")
                .role(Role.ADMIN)
                .build();
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();
        PatientGuardianLink link = PatientGuardianLink.builder()
                .patient(patient)
                .guardianUser(guardianUser)
                .relation("자녀")
                .build();
        link.approve(adminUser);

        when(patientGuardianLinkRepository.findAllByGuardianUserPublicIdAndStatus(
                "usr_guardian",
                GuardianLinkStatus.APPROVED
        )).thenReturn(List.of(link));

        GuardianPatientsResponse response = guardianQueryService.getLinkedPatients(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN)
        );

        assertThat(response.getPatients()).hasSize(1);
        assertThat(response.getPatients().get(0).getName()).isEqualTo("홍길동");
        assertThat(response.getPatients().get(0).getRelation()).isEqualTo("자녀");
        verify(accessControlService).assertGuardian(new AuthenticatedUser("usr_guardian", Role.GUARDIAN));
    }

    @Test
    void getConsultationSummariesReturnsCompletedSummaries() {
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();
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
        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(null)
                .slot(null)
                .doctor(doctorProfile)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctorProfile)
                .intakeSession(null)
                .build();
        ConsultationSession session = ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-1")
                .livekitUrl("wss://livekit.test")
                .build();
        session.markReady();
        session.start();
        session.complete(15);

        ConsultationSummary summary = ConsultationSummary.builder()
                .session(session)
                .summaryNote("편두통 소견. 수분 섭취 권장.")
                .prescriptionIssued(true)
                .prescriptionNote("타이레놀 500mg")
                .needsFollowUp(true)
                .build();

        when(accessControlService.getGuardianLinkedPatientOrThrow(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN),
                patient.getPublicId()
        )).thenReturn(patient);
        when(consultationSummaryRepository.findAllByPatientPublicIdAndSessionStatus(
                patient.getPublicId(),
                com.waddoc.domain.consultation.entity.ConsultationSessionStatus.COMPLETED
        )).thenReturn(List.of(summary));

        GuardianConsultationSummariesResponse response = guardianQueryService.getConsultationSummaries(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN),
                patient.getPublicId()
        );

        assertThat(response.getPatientName()).isEqualTo("홍길동");
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSummaries().get(0).getDoctorName()).isEqualTo("김의사");
        assertThat(response.getSummaries().get(0).isPrescriptionIssued()).isTrue();
    }
}
