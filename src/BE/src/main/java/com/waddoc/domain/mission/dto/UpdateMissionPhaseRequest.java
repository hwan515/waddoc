package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.MissionPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMissionPhaseRequest {

    @NotNull(message = "phase is required")
    private MissionPhase phase;

    @NotBlank(message = "reason is required")
    private String reason;
}
