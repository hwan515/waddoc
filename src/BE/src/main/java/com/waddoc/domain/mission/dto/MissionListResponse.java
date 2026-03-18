package com.waddoc.domain.mission.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MissionListResponse {

    private List<MissionSummaryResponse> missions;
    private long totalCount;

    public static MissionListResponse of(List<MissionSummaryResponse> missions) {
        return MissionListResponse.builder()
                .missions(missions)
                .totalCount(missions.size())
                .build();
    }
}
