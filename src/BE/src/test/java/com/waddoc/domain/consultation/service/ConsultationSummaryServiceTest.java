package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.PutConsultationSummaryRequest;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSummaryServiceTest {

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private ConsultationSummaryRepository consultationSummaryRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConsultationSummaryService consultationSummaryService;

    @Test
    void saveSummary_createsSummaryAndCompletesSessionCaseAndBooking() {
        DoctorProfile doctor = buildDoctorProfile("usr_doctor");
        ConsultationSession session = buildSession(doctor);
        session.start();

        when(accessControlService.getDoctorProfileOrThrow(new AuthenticatedUser("usr_doctor", Role.DOCTOR))).thenReturn(doctor);
        when(consultationSessionRepository.findWithDoctorAndCaseByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(consultationSummaryRepository.findBySession(session)).thenReturn(Optional.empty());
        when(consultationSummaryRepository.save(any(ConsultationSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConsultationSummaryResponse response = consultationSummaryService.saveSummary(
                session.getPublicId(),
                request("편두통 소견", true, "타이레놀 500mg", true),
                new AuthenticatedUser("usr_doctor", Role.DOCTOR)
        );

        assertThat(response.getSessionId()).isEqualTo(session.getPublicId());
        assertThat(response.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.getSummary().getSummaryNote()).isEqualTo("편두통 소견");
        assertThat(response.getSummary().isPrescriptionIssued()).isTrue();
        assertThat(response.getEndedAt()).isNotNull();
        assertThat(response.getDurationMinutes()).isNotNegative();
        assertThat(session.getCareCase().getStatus().name()).isEqualTo("COMPLETED");
        assertThat(session.getCareCase().getBooking().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveSummary_updatesExistingSummaryForCompletedSession() {
        DoctorProfile doctor = buildDoctorProfile("usr_doctor");
        ConsultationSession session = buildSession(doctor);
        session.start();
        session.complete(20);
        LocalDateTime endedAt = session.getEndedAt();
        Integer durationMinutes = session.getDurationMinutes();

        ConsultationSummary existingSummary = ConsultationSummary.builder()
                .session(session)
                .summaryNote("기존 요약")
                .prescriptionIssued(false)
                .prescriptionNote(null)
                .needsFollowUp(false)
                .build();

        when(accessControlService.getDoctorProfileOrThrow(new AuthenticatedUser("usr_doctor", Role.DOCTOR))).thenReturn(doctor);
        when(consultationSessionRepository.findWithDoctorAndCaseByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(consultationSummaryRepository.findBySession(session)).thenReturn(Optional.of(existingSummary));
        when(consultationSummaryRepository.save(existingSummary)).thenReturn(existingSummary);

        ConsultationSummaryResponse response = consultationSummaryService.saveSummary(
                session.getPublicId(),
                request("수정된 요약", false, null, true),
                new AuthenticatedUser("usr_doctor", Role.DOCTOR)
        );

        assertThat(response.getSummary().getSummaryNote()).isEqualTo("수정된 요약");
        assertThat(response.getSummary().isNeedsFollowUp()).isTrue();
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(session.getDurationMinutes()).isEqualTo(durationMinutes);
    }

    @Test
    void saveSummary_rejectsOtherDoctorSession() {
        DoctorProfile assignedDoctor = buildDoctorProfile("usr_assigned");
        DoctorProfile actorDoctor = buildDoctorProfile("usr_actor");
        ConsultationSession session = buildSession(assignedDoctor);
        session.start();

        when(accessControlService.getDoctorProfileOrThrow(new AuthenticatedUser("usr_actor", Role.DOCTOR))).thenReturn(actorDoctor);
        when(consultationSessionRepository.findWithDoctorAndCaseByPublicId(session.getPublicId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> consultationSummaryService.saveSummary(
                session.getPublicId(),
                request("편두통 소견", false, null, false),
                new AuthenticatedUser("usr_actor", Role.DOCTOR)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    private PutConsultationSummaryRequest request(
            String summaryNote,
            boolean prescriptionIssued,
            String prescriptionNote,
            boolean needsFollowUp
    ) {
        PutConsultationSummaryRequest request = new PutConsultationSummaryRequest();
        setField(request, "summaryNote", summaryNote);
        setField(request, "prescriptionIssued", prescriptionIssued);
        setField(request, "prescriptionNote", prescriptionNote);
        setField(request, "needsFollowUp", needsFollowUp);
        return request;
    }

    private ConsultationSession buildSession(DoctorProfile doctor) {
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
        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(intakeSession)
                .slot(null)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        com.waddoc.domain.carecase.entity.CareCase careCase = com.waddoc.domain.carecase.entity.CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctor)
                .intakeSession(intakeSession)
                .build();
        return ConsultationSession.builder()
                .careCase(careCase)
                .roomId("room-1")
                .livekitUrl("wss://livekit.test")
                .build();
    }

    private DoctorProfile buildDoctorProfile(String userPublicId) {
        User user = User.builder()
                .username("doctor_" + userPublicId)
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(user, "publicId", userPublicId);
        DoctorProfile doctor = DoctorProfile.builder()
                .user(user)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(doctor, "id", Math.abs((long) userPublicId.hashCode()));
        return doctor;
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
