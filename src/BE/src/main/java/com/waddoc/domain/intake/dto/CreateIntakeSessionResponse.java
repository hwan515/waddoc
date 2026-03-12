package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class CreateIntakeSessionResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String intakeSessionId;
    private IntakeStatus status;
    private IntakeChannel channel;
    private OffsetDateTime createdAt;

    public static CreateIntakeSessionResponse from(IntakeSession session) {
        return CreateIntakeSessionResponse.builder()
                .intakeSessionId(session.getPublicId())
                .status(session.getStatus())
                .channel(session.getChannel())
                .createdAt(session.getCreatedAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
