package com.waddoc.domain.booking.entity;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 정보. 생성 시 CONFIRMED, 취소 시 CANCELLED. CareCase와 1:1.
 */
@Entity
@Table(name = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_session_id")
    private IntakeSession intakeSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ScheduleSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    public Booking(Patient patient, IntakeSession intakeSession,
                   ScheduleSlot slot, DoctorProfile doctor, String channel,
                   LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {
        this.publicId = PublicIdGenerator.generate("bk_");
        this.patient = patient;
        this.intakeSession = intakeSession;
        this.slot = slot;
        this.doctor = doctor;
        this.channel = channel != null ? channel : "WEB_SIMULATOR";
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.CONFIRMED;
    }

    /** 예약 취소 처리 */
    public void cancel(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }

    /** CONFIRMED 상태만 취소 가능 */
    public boolean isCancellable() {
        return this.status == BookingStatus.CONFIRMED;
    }
}
