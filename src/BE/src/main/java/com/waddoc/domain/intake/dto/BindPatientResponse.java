package com.waddoc.domain.intake.dto;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class BindPatientResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String intakeSessionId;
    private String patientId;
    private IntakeStatus status;
    private OffsetDateTime lastActivityAt;

    public static BindPatientResponse from(IntakeSession session) {
        return BindPatientResponse.builder()
                .intakeSessionId(session.getPublicId())
                .patientId(session.getPatient() != null ? session.getPatient().getPublicId() : null)
                .status(session.getStatus())
                .lastActivityAt(session.getLastActivityAt().atZone(KST).toOffsetDateTime())
                .build();
    }
}
