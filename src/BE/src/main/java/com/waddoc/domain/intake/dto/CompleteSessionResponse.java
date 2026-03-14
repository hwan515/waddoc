package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class CompleteSessionResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String intakeSessionId;
    private IntakeStatus status;
    private OffsetDateTime endedAt;

    public static CompleteSessionResponse from(IntakeSession session) {
        return CompleteSessionResponse.builder()
                .intakeSessionId(session.getPublicId())
                .status(session.getStatus())
                .endedAt(session.getEndedAt() != null
                        ? session.getEndedAt().atZone(KST).toOffsetDateTime() : null)
                .build();
    }
}
