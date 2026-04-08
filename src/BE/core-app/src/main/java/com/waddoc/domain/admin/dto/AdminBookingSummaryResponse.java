package com.waddoc.domain.admin.dto;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AdminBookingSummaryResponse {

    private String bookingId;
    private BookingStatus status;
    private String patientName;
    private String doctorName;
    private String departmentName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private String caseId;
    private MissionPhase missionPhase;

    public static AdminBookingSummaryResponse from(Booking booking, String caseId, MissionPhase missionPhase) {
        return AdminBookingSummaryResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus())
                .patientName(booking.getPatient().getName())
                .doctorName(booking.getDoctor().getUser().getName())
                .departmentName(booking.getDoctor().getDepartmentName())
                .appointmentDate(booking.getAppointmentDate())
                .startTime(booking.getStartTime())
                .caseId(caseId)
                .missionPhase(missionPhase)
                .build();
    }
}
