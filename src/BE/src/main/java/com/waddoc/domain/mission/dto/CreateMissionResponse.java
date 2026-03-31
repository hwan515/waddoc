package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class CreateMissionResponse {

    private String missionId;
    private String caseId;
    private MissionPhase phase;
    private String vehicleId;
    private OffsetDateTime createdAt;

    public static CreateMissionResponse from(Mission mission) {
        return CreateMissionResponse.builder()
                .missionId(mission.getPublicId())
                .caseId(mission.getCareCase().getPublicId())
                .phase(mission.getPhase())
                .vehicleId(mission.getVehicleId())
                .createdAt(toOffsetDateTime(mission.getCreatedAt()))
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(KstTime.ZONE).toOffsetDateTime();
    }
}
