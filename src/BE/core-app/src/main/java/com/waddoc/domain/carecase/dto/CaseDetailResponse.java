package com.waddoc.domain.carecase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CaseDetailResponse {

    private String caseId;
    private CaseStatus status;
    private String bookingId;
    private PatientInfo patient;
    private DoctorInfo doctor;
    private IntakeSummary intakeSummary;
    private String missionId;
    private String sessionId;
    private List<ConsultationHistory> consultationHistories;
    private VitalMeasurementResponse vitals;
    private LocalDateTime createdAt;

    public static CaseDetailResponse of(
            CareCase careCase,
            Mission mission,
            ConsultationSession session,
            List<ConsultationSummary> consultationHistories,
            VitalMeasurementResponse vitals
    ) {
        return CaseDetailResponse.builder()
                .caseId(careCase.getPublicId())
                .status(careCase.getStatus())
                .bookingId(careCase.getBooking().getPublicId())
                .patient(PatientInfo.from(careCase.getPatient()))
                .doctor(DoctorInfo.from(careCase))
                .intakeSummary(IntakeSummary.from(careCase.getIntakeSession()))
                .missionId(mission != null ? mission.getPublicId() : null)
                .sessionId(session != null ? session.getPublicId() : null)
                .consultationHistories(consultationHistories.stream()
                        .map(ConsultationHistory::from)
                        .toList())
                .vitals(vitals)
                .createdAt(careCase.getCreatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class ConsultationHistory {
        private String caseId;
        private LocalDate consultationDate;
        private String departmentName;
        private String doctorName;
        private String symptom;
        private String summaryNote;

        @JsonProperty("isPrescriptionIssued")
        private boolean prescriptionIssued;

        private String prescriptionNote;
        private boolean needsFollowUp;

        static ConsultationHistory from(ConsultationSummary summary) {
            CareCase careCase = summary.getSession().getCareCase();
            IntakeSession intakeSession = careCase.getIntakeSession();
            LocalDate consultationDate = summary.getSession().getEndedAt() != null
                    ? summary.getSession().getEndedAt().toLocalDate()
                    : careCase.getBooking().getAppointmentDate();

            return ConsultationHistory.builder()
                    .caseId(careCase.getPublicId())
                    .consultationDate(consultationDate)
                    .departmentName(careCase.getDoctor().getDepartmentName())
                    .doctorName(careCase.getDoctor().getUser().getName())
                    .symptom(intakeSession != null ? intakeSession.getSelectionReason() : null)
                    .summaryNote(summary.getSummaryNote())
                    .prescriptionIssued(summary.isPrescriptionIssued())
                    .prescriptionNote(summary.getPrescriptionNote())
                    .needsFollowUp(summary.isNeedsFollowUp())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class PatientInfo {
        private String patientId;
        private String name;
        private String birthDate6;
        private String birthDate;
        private String gender;
        private String phone;
        private String address;

        static PatientInfo from(Patient patient) {
            return PatientInfo.builder()
                    .patientId(patient.getPublicId())
                    .name(patient.getName())
                    .birthDate6(patient.getBirthDate6())
                    .birthDate(patient.getBirthDate() != null ? patient.getBirthDate().toString() : null)
                    .gender(patient.getGender() != null ? patient.getGender().name() : null)
                    .phone(patient.getPhone())
                    .address(patient.getAddress())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class DoctorInfo {
        private String doctorId;
        private String name;

        static DoctorInfo from(CareCase careCase) {
            return DoctorInfo.builder()
                    .doctorId(careCase.getDoctor().getPublicId())
                    .name(careCase.getDoctor().getUser().getName())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class IntakeSummary {
        private String department;
        private String departmentName;
        private String selectionReason;
        private ConfidenceLevel selectionConfidenceLevel;
        private String intakeSessionId;

        static IntakeSummary from(IntakeSession intakeSession) {
            if (intakeSession == null || !intakeSession.hasRecommendation()) {
                return null;
            }
            return IntakeSummary.builder()
                    .department(intakeSession.getSelectedDepartment())
                    .departmentName(intakeSession.getSelectedDepartmentName())
                    .selectionReason(intakeSession.getSelectionReason())
                    .selectionConfidenceLevel(intakeSession.getSelectionConfidenceLevel())
                    .intakeSessionId(intakeSession.getPublicId())
                    .build();
        }
    }
}
