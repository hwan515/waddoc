package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CompleteSessionResponse {

    private String intakeSessionId;
    private IntakeStatus status;
    private OffsetDateTime endedAt;

    public static CompleteSessionResponse from(IntakeSession session) {
        return CompleteSessionResponse.builder()
                .intakeSessionId(session.getPublicId())
                .status(session.getStatus())
                .endedAt(session.getEndedAt() != null
                        ? session.getEndedAt().atZone(KstTime.ZONE).toOffsetDateTime() : null)
                .build();
    }
}
