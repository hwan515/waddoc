package com.waddoc.domain.carecase.dto;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class DoctorCaseSummaryResponse {

    private String caseId;
    private CaseStatus status;
    private String patientId;
    private String patientName;
    private String patientGender;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private MissionPhase missionPhase;
    private String sessionId;
    private ConsultationSessionStatus sessionStatus;

    public static DoctorCaseSummaryResponse from(
            CareCase careCase,
            MissionPhase missionPhase,
            ConsultationSession consultationSession
    ) {
        return DoctorCaseSummaryResponse.builder()
                .caseId(careCase.getPublicId())
                .status(careCase.getStatus())
                .patientId(careCase.getPatient().getPublicId())
                .patientName(careCase.getPatient().getName())
                .patientGender(careCase.getPatient().getGender().name())
                .departmentName(careCase.getDoctor().getDepartmentName())
                .appointmentDate(careCase.getBooking().getAppointmentDate())
                .startTime(careCase.getBooking().getStartTime())
                .missionPhase(missionPhase)
                .sessionId(consultationSession != null ? consultationSession.getPublicId() : null)
                .sessionStatus(consultationSession != null ? consultationSession.getStatus() : null)
                .build();
    }
}
