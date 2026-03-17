package com.waddoc.domain.carecase.dto;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.patient.entity.Patient;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    public static CaseDetailResponse of(CareCase careCase, Mission mission, ConsultationSession session) {
        return CaseDetailResponse.builder()
                .caseId(careCase.getPublicId())
                .status(careCase.getStatus())
                .bookingId(careCase.getBooking().getPublicId())
                .patient(PatientInfo.from(careCase.getPatient()))
                .doctor(DoctorInfo.from(careCase))
                .intakeSummary(IntakeSummary.from(careCase.getIntakeSession()))
                .missionId(mission != null ? mission.getPublicId() : null)
                .sessionId(session != null ? session.getPublicId() : null)
                .createdAt(careCase.getCreatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class PatientInfo {
        private String patientId;
        private String name;
        private String birthDate6;

        static PatientInfo from(Patient patient) {
            return PatientInfo.builder()
                    .patientId(patient.getPublicId())
                    .name(patient.getName())
                    .birthDate6(patient.getBirthDate6())
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
