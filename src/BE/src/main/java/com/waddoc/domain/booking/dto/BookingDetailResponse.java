package com.waddoc.domain.booking.dto;

import com.waddoc.domain.booking.entity.Booking;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class BookingDetailResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String bookingId;
    private String status;
    private PatientInfo patient;
    private DoctorInfo doctor;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String caseId;
    private String intakeSessionId;
    private String channel;
    private String cancelReason;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Builder
    public static class PatientInfo {
        private String patientId;
        private String name;
        private String phone;
        private String regionCode;
    }

    @Getter
    @Builder
    public static class DoctorInfo {
        private String doctorId;
        private String name;
        private String department;
        private String departmentName;
    }

    public static BookingDetailResponse from(Booking booking, String patientPhone, String caseId) {
        return BookingDetailResponse.builder()
                .bookingId(booking.getPublicId())
                .status(booking.getStatus().name())
                .patient(PatientInfo.builder()
                        .patientId(booking.getPatient().getPublicId())
                        .name(booking.getPatient().getName())
                        .phone(patientPhone)
                        .regionCode(booking.getPatient().getRegionCode())
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
                .caseId(caseId)
                .intakeSessionId(booking.getIntakeSession() != null ? booking.getIntakeSession().getPublicId() : null)
                .channel(booking.getChannel())
                .cancelReason(booking.getCancelReason())
                .cancelledAt(booking.getCancelledAt() != null
                        ? booking.getCancelledAt().atZone(KST).toOffsetDateTime()
                        : null)
                .createdAt(booking.getCreatedAt() != null
                        ? booking.getCreatedAt().atZone(KST).toOffsetDateTime()
                        : null)
                .updatedAt(booking.getUpdatedAt() != null
                        ? booking.getUpdatedAt().atZone(KST).toOffsetDateTime()
                        : null)
                .build();
    }
}
