package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class BookingDetailResponse {

    private String bookingId;
    private String status;
    private PatientInfo patient;
    private DoctorInfo doctor;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String channel;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class PatientInfo {
        private String patientId;
        private String name;
        private String phone;
    }

    @Getter
    @Builder
    public static class DoctorInfo {
        private String doctorId;
        private String name;
        private String department;
        private String departmentName;
    }

    public static BookingDetailResponse from(Booking booking, String patientPhone) {
        return BookingDetailResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .patient(PatientInfo.builder()
                        .patientId(booking.getPatient().getPublicId())
                        .name(booking.getPatient().getName())
                        .phone(patientPhone)
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
                .channel(booking.getChannel())
                .cancelReason(booking.getCancelReason())
                .cancelledAt(booking.getCancelledAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
