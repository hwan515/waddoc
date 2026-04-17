package com.waddoc.domain.mission.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TerminalCheckInCandidatesResponse {

    private List<Candidate> candidates;
    private int totalCount;

    public static TerminalCheckInCandidatesResponse of(List<Candidate> candidates) {
        return TerminalCheckInCandidatesResponse.builder()
                .candidates(List.copyOf(candidates))
                .totalCount(candidates.size())
                .build();
    }

    @Getter
    @Builder
    public static class Candidate {
        private String missionId;
        private String patientMaskedName;
        private String appointmentDate;
        private String appointmentTime;
        private String doctorMaskedName;
        private String missionPhase;
    }
}
