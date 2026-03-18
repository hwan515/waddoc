package com.waddoc.domain.admin.dto;

import com.waddoc.domain.consultation.entity.ConnectionState;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminSessionSummaryResponse {

    private String sessionId;
    private String caseId;
    private ConsultationSessionStatus status;
    private String patientName;
    private String doctorName;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private ConnectionState doctorConnectionState;
    private ConnectionState patientConnectionState;

    public static AdminSessionSummaryResponse from(ConsultationSession session) {
        return AdminSessionSummaryResponse.builder()
                .sessionId(session.getPublicId())
                .caseId(session.getCareCase().getPublicId())
                .status(session.getStatus())
                .patientName(session.getCareCase().getPatient().getName())
                .doctorName(session.getCareCase().getDoctor().getUser().getName())
                .departmentName(session.getCareCase().getDoctor().getDepartmentName())
                .appointmentDate(session.getCareCase().getBooking().getAppointmentDate())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .doctorConnectionState(session.getDoctorConnectionState())
                .patientConnectionState(session.getPatientConnectionState())
                .build();
    }
}
