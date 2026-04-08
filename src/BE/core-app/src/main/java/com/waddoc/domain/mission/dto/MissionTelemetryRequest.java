package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.MissionPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionTelemetryRequest {

    @NotBlank(message = "source is required")
    private String source;

    @NotBlank(message = "sourceEventId is required")
    private String sourceEventId;

    @NotNull(message = "seqNo is required")
    private Long seqNo;

    @NotBlank(message = "vehicleId is required")
    private String vehicleId;

    @NotNull(message = "phase is required")
    private MissionPhase phase;

    @NotNull(message = "latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    private BigDecimal longitude;

    @NotNull(message = "speed is required")
    private BigDecimal speed;

    @NotNull(message = "heading is required")
    private Integer heading;

    @NotNull(message = "timestamp is required")
    private OffsetDateTime timestamp;

    private Map<String, Object> metadata;
}
