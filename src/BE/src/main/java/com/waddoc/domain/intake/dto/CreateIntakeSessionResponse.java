package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CreateIntakeSessionResponse {

    private String intakeSessionId;
    private IntakeStatus status;
    private IntakeChannel channel;
    private OffsetDateTime createdAt;

    public static CreateIntakeSessionResponse from(IntakeSession session) {
        return CreateIntakeSessionResponse.builder()
                .intakeSessionId(session.getPublicId())
                .status(session.getStatus())
                .channel(session.getChannel())
                .createdAt(session.getCreatedAt().atZone(KstTime.ZONE).toOffsetDateTime())
                .build();
    }
}
