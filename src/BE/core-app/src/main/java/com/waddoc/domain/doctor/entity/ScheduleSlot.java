package com.waddoc.domain.doctor.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 의사별 예약 가능 슬롯. 예약 시 is_booked = true 전환.
 */
@Entity
@Table(name = "schedule_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSlot extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_booked", nullable = false)
    private boolean booked = false;

    @Builder
    public ScheduleSlot(DoctorProfile doctor, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.publicId = PublicIdGenerator.generate("slot_");
        this.doctor = doctor;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** 예약 확정 시 슬롯 점유 */
    public void markBooked() {
        this.booked = true;
    }

    /** 예약 취소 시 슬롯 해제 */
    public void markAvailable() {
        this.booked = false;
    }
}
