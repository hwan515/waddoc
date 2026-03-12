package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class CreateBookingResponse {

    private String bookingId;
    private String status;
    private String caseId;
    private PatientInfo patient;
    private DoctorInfo doctor;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime createdAt;
    private String ttsMessage;

    @Getter
    @Builder
    public static class PatientInfo {
        private String patientId;
        private String name;
    }

    @Getter
    @Builder
    public static class DoctorInfo {
        private String doctorId;
        private String name;
        private String department;
        private String departmentName;
    }

    public static CreateBookingResponse of(Booking booking, CareCase careCase, String ttsMessage) {
        return CreateBookingResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .caseId(careCase.getPublicId())
                .patient(PatientInfo.builder()
                        .patientId(booking.getPatient().getPublicId())
                        .name(booking.getPatient().getName())
                        .build())
                .doctor(DoctorInfo.builder()
                        .doctorId(booking.getDoctor().getPublicId())
                        .name(booking.getDoctor().getUser().getName())
                        .department(booking.getDoctor().getDepartment())
                        .departmentName(booking.getDoctor().getDepartmentName())
                        .build())
                .appointmentDate(booking.getAppointmentDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .createdAt(booking.getCreatedAt())
                .ttsMessage(ttsMessage)
                .build();
    }
}
