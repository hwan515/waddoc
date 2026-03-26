package com.waddoc.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class GuardianConsultationSummaryResponse {

    private String caseId;
    private LocalDate consultationDate;
    private String departmentName;
    private String doctorName;
    private String summaryNote;

    @JsonProperty("isPrescriptionIssued")
    private boolean prescriptionIssued;

    private String prescriptionNote;
    private boolean needsFollowUp;

    public static GuardianConsultationSummaryResponse from(ConsultationSummary summary) {
        LocalDate consultationDate = summary.getSession().getEndedAt() != null
                ? summary.getSession().getEndedAt().toLocalDate()
                : summary.getSession().getCareCase().getBooking().getAppointmentDate();

        return GuardianConsultationSummaryResponse.builder()
                .caseId(summary.getSession().getCareCase().getPublicId())
                .consultationDate(consultationDate)
                .departmentName(summary.getSession().getCareCase().getDoctor().getDepartmentName())
                .doctorName(summary.getSession().getCareCase().getDoctor().getUser().getName())
                .summaryNote(summary.getSummaryNote())
                .prescriptionIssued(summary.isPrescriptionIssued())
                .prescriptionNote(summary.getPrescriptionNote())
                .needsFollowUp(summary.isNeedsFollowUp())
                .build();
    }
}
