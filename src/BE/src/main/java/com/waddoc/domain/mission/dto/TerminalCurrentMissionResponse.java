package com.waddoc.domain.mission.dto;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.mission.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class TerminalCurrentMissionResponse {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private boolean hasMission;
    private String missionId;
    private String patientName;
    private String appointmentDate;
    private String appointmentTime;
    private String phase;
    private String vehicleId;
    private Integer targetWaypointNumber;

    public static TerminalCurrentMissionResponse empty() {
        return TerminalCurrentMissionResponse.builder()
                .hasMission(false)
                .build();
    }

    public static TerminalCurrentMissionResponse from(Mission mission) {
        Booking booking = mission.getCareCase().getBooking();

        return TerminalCurrentMissionResponse.builder()
                .hasMission(true)
                .missionId(mission.getPublicId())
                .patientName(mission.getCareCase().getPatient().getName())
                .appointmentDate(booking.getAppointmentDate().format(DATE_FORMATTER))
                .appointmentTime(booking.getStartTime().format(TIME_FORMATTER))
                .phase(mission.getPhase().name())
                .vehicleId(mission.getVehicleId())
                .targetWaypointNumber(mission.getTargetWaypointNumber())
                .build();
    }
}
