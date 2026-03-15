package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeTurn;
import com.waddoc.domain.intake.entity.TurnType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class IntakeTurnResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String turnId;
    private int turnOrder;
    private String intakeSessionId;
    private TurnType turnType;
    private String sttText;
    private BigDecimal sttConfidence;
    private String exceptionCode;
    private String nextAction;
    private String ttsMessage;
    private OffsetDateTime createdAt;

    public static IntakeTurnResponse from(IntakeTurn turn) {
        return IntakeTurnResponse.builder()
                .turnId(turn.getPublicId())
                .turnOrder(turn.getTurnOrder())
                .intakeSessionId(turn.getIntakeSession().getPublicId())
                .turnType(turn.getTurnType())
                .sttText(turn.getSttText())
                .sttConfidence(turn.getSttConfidence())
                .exceptionCode(turn.getExceptionCode())
                .nextAction(turn.getNextAction())
                .ttsMessage(turn.getTtsMessage())
                .createdAt(turn.getCreatedAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
