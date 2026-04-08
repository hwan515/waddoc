package com.waddoc.domain.admin.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDemoMissionActionResponse {

    private String missionId;
    private MissionPhase phase;
    private MissionPhase previousPhase;
    private String vehicleId;
    private Integer targetWaypointNumber;
    private boolean waypointCommandSent;
    private boolean dummyCompleted;

    public static AdminDemoMissionActionResponse of(
            Mission mission,
            MissionPhase previousPhase,
            boolean waypointCommandSent,
            boolean dummyCompleted
    ) {
        return AdminDemoMissionActionResponse.builder()
                .missionId(mission.getPublicId())
                .phase(mission.getPhase())
                .previousPhase(previousPhase)
                .vehicleId(mission.getVehicleId())
                .targetWaypointNumber(mission.getTargetWaypointNumber())
                .waypointCommandSent(waypointCommandSent)
                .dummyCompleted(dummyCompleted)
                .build();
    }
}
