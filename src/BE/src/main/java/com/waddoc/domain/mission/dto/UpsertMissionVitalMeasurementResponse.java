package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpsertMissionVitalMeasurementResponse {

    private String missionId;
    private String caseId;
    private VitalMeasurementResponse vitals;

    public static UpsertMissionVitalMeasurementResponse of(
            Mission mission,
            VitalMeasurementResponse vitals
    ) {
        return UpsertMissionVitalMeasurementResponse.builder()
                .missionId(mission.getPublicId())
                .caseId(mission.getCareCase().getPublicId())
                .vitals(vitals)
                .build();
    }
}
