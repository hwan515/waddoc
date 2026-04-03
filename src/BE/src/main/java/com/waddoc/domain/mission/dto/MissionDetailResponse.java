package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class MissionDetailResponse {

    private String missionId;
    private String caseId;
    private MissionPhase phase;
    private String vehicleId;
    private String patientName;
    private String destination;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime estimatedArrivalTime;
    private CurrentLocation currentLocation;
    private OffsetDateTime updatedAt;

    public static MissionDetailResponse from(Mission mission) {
        return MissionDetailResponse.builder()
                .missionId(mission.getPublicId())
                .caseId(mission.getCareCase().getPublicId())
                .phase(mission.getPhase())
                .vehicleId(mission.getVehicleId())
                .patientName(mission.getCareCase().getPatient().getName())
                .destination(mission.getDestination())
                .dispatchedAt(toOffsetDateTime(
                        mission.getDispatchedAt() != null ? mission.getDispatchedAt() : mission.getCreatedAt()
                ))
                .estimatedArrivalTime(toOffsetDateTime(mission.getEstimatedArrivalTime()))
                .currentLocation(CurrentLocation.from(mission))
                .updatedAt(toOffsetDateTime(mission.getUpdatedAt()))
                .build();
    }

    @Getter
    @Builder
    public static class CurrentLocation {
        private BigDecimal latitude;
        private BigDecimal longitude;
        private OffsetDateTime timestamp;

        static CurrentLocation from(Mission mission) {
            if (mission.getLatitude() == null || mission.getLongitude() == null) {
                return null;
            }

            return CurrentLocation.builder()
                    .latitude(mission.getLatitude())
                    .longitude(mission.getLongitude())
                    .timestamp(toOffsetDateTime(mission.getUpdatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(KstTime.ZONE).toOffsetDateTime();
    }
}
