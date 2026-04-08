package com.waddoc.domain.carecase.entity;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진료 케이스. Booking과 1:1. 예약 생성 시 자동 생성.
 */
@Entity
@Table(name = "care_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_session_id")
    private IntakeSession intakeSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status;

    @Builder
    public CareCase(Booking booking, Patient patient, DoctorProfile doctor, IntakeSession intakeSession) {
        this.publicId = PublicIdGenerator.generate("case_");
        this.booking = booking;
        this.patient = patient;
        this.doctor = doctor;
        this.intakeSession = intakeSession;
        this.status = CaseStatus.CREATED;
    }

    public void cancel() {
        this.status = CaseStatus.CANCELLED;
    }

    public void complete() {
        this.status = CaseStatus.COMPLETED;
    }
}
