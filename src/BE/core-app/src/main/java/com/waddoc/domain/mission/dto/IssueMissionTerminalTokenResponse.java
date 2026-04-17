package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IssueMissionTerminalTokenResponse {

    private String missionId;
    private String caseId;
    private String patientName;
    private String terminalToken;
    private long expiresIn;
    private List<String> scopes;

    public static IssueMissionTerminalTokenResponse of(
            Mission mission,
            String terminalToken,
            long expiresIn,
            List<String> scopes
    ) {
        return IssueMissionTerminalTokenResponse.builder()
                .missionId(mission.getPublicId())
                .caseId(mission.getCareCase().getPublicId())
                .patientName(mission.getCareCase().getPatient().getName())
                .terminalToken(terminalToken)
                .expiresIn(expiresIn)
                .scopes(List.copyOf(scopes))
                .build();
    }
}
