package com.waddoc.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "doctor_notification_projection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorNotificationProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_notification_projection_id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "doctor_user_id", nullable = false, length = 64)
    private String doctorUserId;

    @Column(name = "doctor_id", nullable = false, length = 64)
    private String doctorId;

    @Column(name = "booking_id", nullable = false, length = 64)
    private String bookingId;

    @Column(name = "care_case_id", nullable = false, length = 64)
    private String careCaseId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public DoctorNotificationProjection(
            String eventId,
            String doctorUserId,
            String doctorId,
            String bookingId,
            String careCaseId,
            String payloadJson,
            OffsetDateTime createdAt
    ) {
        this.eventId = eventId;
        this.doctorUserId = doctorUserId;
        this.doctorId = doctorId;
        this.bookingId = bookingId;
        this.careCaseId = careCaseId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }
}
