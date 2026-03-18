package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class UpdateMissionPhaseResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String missionId;
    private MissionPhase phase;
    private MissionPhase previousPhase;
    private OffsetDateTime updatedAt;

    public static UpdateMissionPhaseResponse of(Mission mission, MissionPhase previousPhase) {
        return UpdateMissionPhaseResponse.builder()
                .missionId(mission.getPublicId())
                .phase(mission.getPhase())
                .previousPhase(previousPhase)
                .updatedAt(toOffsetDateTime(mission.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(KST).toOffsetDateTime();
    }
}
