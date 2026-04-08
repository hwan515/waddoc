package com.waddoc.domain.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMissionRequest {

    @NotBlank(message = "caseId is required")
    private String caseId;

    @NotBlank(message = "vehicleId is required")
    private String vehicleId;

    @NotBlank(message = "destination is required")
    private String destination;

    @NotNull(message = "scheduledTime is required")
    private OffsetDateTime scheduledTime;
}
