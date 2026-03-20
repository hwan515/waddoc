package com.waddoc.domain.carecase.dto;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
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
    private String patientName;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private MissionPhase missionPhase;

    public static DoctorCaseSummaryResponse from(CareCase careCase, MissionPhase missionPhase) {
        return DoctorCaseSummaryResponse.builder()
                .caseId(careCase.getPublicId())
                .status(careCase.getStatus())
                .patientName(careCase.getPatient().getName())
                .departmentName(careCase.getDoctor().getDepartmentName())
                .appointmentDate(careCase.getBooking().getAppointmentDate())
                .startTime(careCase.getBooking().getStartTime())
                .missionPhase(missionPhase)
                .build();
    }
}
