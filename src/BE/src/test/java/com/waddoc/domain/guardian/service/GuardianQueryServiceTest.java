package com.waddoc.domain.guardian.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.guardian.dto.GuardianConsultationSummariesResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientsResponse;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGender;
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
                .name("Guardian Lee")
                .role(Role.GUARDIAN)
                .build();
        User adminUser = User.builder()
                .username("admin")
                .passwordHash("encoded-password")
                .name("Admin")
                .role(Role.ADMIN)
                .build();
        Patient patient = Patient.builder()
                .name("Kim Younghee")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung-eup, Ulleung-gun")
                .phone("01012345678")
                .gender(PatientGender.FEMALE)
                .build();
        PatientGuardianLink link = PatientGuardianLink.builder()
                .patient(patient)
                .guardianUser(guardianUser)
                .relation("DAUGHTER")
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
        assertThat(response.getPatients().get(0).getName()).isEqualTo("Kim Younghee");
        assertThat(response.getPatients().get(0).getPhone()).isEqualTo("01012345678");
        assertThat(response.getPatients().get(0).getRegionCode()).isEqualTo("ULLEUNG");
        assertThat(response.getPatients().get(0).getAddress()).isEqualTo("Ulleung-eup, Ulleung-gun");
        assertThat(response.getPatients().get(0).getGender()).isEqualTo(PatientGender.FEMALE);
        assertThat(response.getPatients().get(0).getRelation()).isEqualTo("DAUGHTER");
        verify(accessControlService).assertGuardian(new AuthenticatedUser("usr_guardian", Role.GUARDIAN));
    }

    @Test
    void getConsultationSummariesReturnsCompletedSummaries() {
        Patient patient = Patient.builder()
                .name("Kim Younghee")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("Ulleung-eup, Ulleung-gun")
                .phone("01012345678")
                .build();
        User doctorUser = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("Dr. Kim")
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
                .summaryNote("Headache and mild cough. Rest and hydration advised.")
                .prescriptionIssued(true)
                .prescriptionNote("Tylenol 500mg")
                .needsFollowUp(true)
                .build();

        when(accessControlService.getGuardianLinkedPatientOrThrow(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN),
                patient.getPublicId()
        )).thenReturn(patient);
        when(consultationSummaryRepository.findAllByPatientPublicIdAndSessionStatus(
                patient.getPublicId(),
                ConsultationSessionStatus.COMPLETED
        )).thenReturn(List.of(summary));

        GuardianConsultationSummariesResponse response = guardianQueryService.getConsultationSummaries(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN),
                patient.getPublicId()
        );

        assertThat(response.getPatientName()).isEqualTo("Kim Younghee");
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSummaries().get(0).getDoctorName()).isEqualTo("Dr. Kim");
        assertThat(response.getSummaries().get(0).isPrescriptionIssued()).isTrue();
    }
}
