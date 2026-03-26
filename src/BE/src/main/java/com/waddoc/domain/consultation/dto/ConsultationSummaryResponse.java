package com.waddoc.domain.consultation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class ConsultationSummaryResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String sessionId;
    private String caseId;
    private ConsultationSessionStatus status;
    private SummaryDetail summary;
    private OffsetDateTime endedAt;
    private Integer durationMinutes;

    public static ConsultationSummaryResponse from(ConsultationSession session, ConsultationSummary summary) {
        return ConsultationSummaryResponse.builder()
                .sessionId(session.getPublicId())
                .caseId(session.getCareCase().getPublicId())
                .status(session.getStatus())
                .summary(SummaryDetail.from(summary))
                .endedAt(session.getEndedAt() != null ? session.getEndedAt().atZone(KST).toOffsetDateTime() : null)
                .durationMinutes(session.getDurationMinutes())
                .build();
    }

    @Getter
    @Builder
    public static class SummaryDetail {
        private String summaryNote;

        @JsonProperty("isPrescriptionIssued")
        private boolean prescriptionIssued;

        private String prescriptionNote;
        private boolean needsFollowUp;

        static SummaryDetail from(ConsultationSummary summary) {
            return SummaryDetail.builder()
                    .summaryNote(summary.getSummaryNote())
                    .prescriptionIssued(summary.isPrescriptionIssued())
                    .prescriptionNote(summary.getPrescriptionNote())
                    .needsFollowUp(summary.isNeedsFollowUp())
                    .build();
        }
    }
}
