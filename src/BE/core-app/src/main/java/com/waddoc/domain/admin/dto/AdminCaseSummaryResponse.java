package com.waddoc.domain.admin.dto;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AdminCaseSummaryResponse {

    private String caseId;
    private CaseStatus status;
    private String patientName;
    private String doctorName;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private MissionPhase missionPhase;
    private ConsultationSessionStatus sessionStatus;

    public static AdminCaseSummaryResponse from(
            CareCase careCase,
            MissionPhase missionPhase,
            ConsultationSessionStatus sessionStatus
    ) {
        return AdminCaseSummaryResponse.builder()
                .caseId(careCase.getPublicId())
                .status(careCase.getStatus())
                .patientName(careCase.getPatient().getName())
                .doctorName(careCase.getDoctor().getUser().getName())
                .departmentName(careCase.getDoctor().getDepartmentName())
                .appointmentDate(careCase.getBooking().getAppointmentDate())
                .startTime(careCase.getBooking().getStartTime())
                .missionPhase(missionPhase)
                .sessionStatus(sessionStatus)
                .build();
    }
}
