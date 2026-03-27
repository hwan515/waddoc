package com.waddoc.domain.mission.dto;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class MissionSummaryResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private String missionId;
    private String caseId;
    private String patientName;
    private MissionPhase phase;
    private String vehicleId;
    private String destination;
    private LocalDate appointmentDate;
    private String appointmentTime;
    private Integer targetWaypointNumber;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime estimatedArrivalTime;

    public static MissionSummaryResponse from(Mission mission) {
        return MissionSummaryResponse.builder()
                .missionId(mission.getPublicId())
                .caseId(mission.getCareCase().getPublicId())
                .patientName(mission.getCareCase().getPatient().getName())
                .phase(mission.getPhase())
                .vehicleId(mission.getVehicleId())
                .destination(mission.getDestination())
                .appointmentDate(mission.getCareCase().getBooking().getAppointmentDate())
                .appointmentTime(mission.getCareCase().getBooking().getStartTime().format(TIME_FORMATTER))
                .targetWaypointNumber(mission.getTargetWaypointNumber())
                .dispatchedAt(toOffsetDateTime(
                        mission.getDispatchedAt() != null ? mission.getDispatchedAt() : mission.getCreatedAt()
                ))
                .estimatedArrivalTime(toOffsetDateTime(mission.getEstimatedArrivalTime()))
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(KST).toOffsetDateTime();
    }
}
