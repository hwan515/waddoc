package com.waddoc.domain.intake.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class IntakeSessionDetailResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String intakeSessionId;
    private String patientId;
    private String callerNumber;
    private IntakeChannel channel;
    private IntakeStatus status;
    private String completionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime endedAt;
    private OffsetDateTime lastActivityAt;

    public static IntakeSessionDetailResponse from(IntakeSession session) {
        return IntakeSessionDetailResponse.builder()
                .intakeSessionId(session.getPublicId())
                .patientId(session.getPatient() != null ? session.getPatient().getPublicId() : null)
                .callerNumber(session.getCallerNumber())
                .channel(session.getChannel())
                .status(session.getStatus())
                .completionReason(session.getCompletionReason())
                .createdAt(session.getCreatedAt().atZone(KST).toOffsetDateTime())
                .endedAt(session.getEndedAt() != null
                        ? session.getEndedAt().atZone(KST).toOffsetDateTime() : null)
                .lastActivityAt(session.getLastActivityAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
