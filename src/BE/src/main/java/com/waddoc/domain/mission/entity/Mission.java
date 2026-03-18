package com.waddoc.domain.mission.entity;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "mission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private CareCase careCase;

    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(columnDefinition = "TEXT")
    private String destination;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "estimated_arrival_time")
    private LocalDateTime estimatedArrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_phase", length = 20)
    private MissionPhase previousPhase;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "last_telemetry_source_event_id", length = 100)
    private String lastTelemetrySourceEventId;

    @Column(name = "last_telemetry_seq_no")
    private Long lastTelemetrySeqNo;

    @Column(name = "last_telemetry_at")
    private LocalDateTime lastTelemetryAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public Mission(
            CareCase careCase,
            String vehicleId,
            String destination,
            LocalDateTime dispatchedAt,
            LocalDateTime estimatedArrivalTime
    ) {
        this.publicId = PublicIdGenerator.generate("ms_");
        this.careCase = careCase;
        this.vehicleId = vehicleId;
        this.destination = destination;
        this.dispatchedAt = dispatchedAt;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.phase = MissionPhase.CREATED;
    }

    public void assignSchedule(LocalDateTime dispatchedAt, LocalDateTime estimatedArrivalTime) {
        this.dispatchedAt = dispatchedAt;
        this.estimatedArrivalTime = estimatedArrivalTime;
    }

    public void updatePhase(MissionPhase phase) {
        if (this.phase == phase) {
            return;
        }

        MissionPhase currentPhase = this.phase;
        if (phase == MissionPhase.INCIDENT) {
            this.previousPhase = currentPhase;
        } else if (currentPhase == MissionPhase.INCIDENT && phase == this.previousPhase) {
            this.previousPhase = null;
        } else {
            this.previousPhase = currentPhase;
        }

        this.phase = phase;
        if (phase == MissionPhase.DISPATCHED && this.dispatchedAt == null) {
            this.dispatchedAt = LocalDateTime.now();
        }
        if (phase == MissionPhase.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void recordTelemetry(String sourceEventId, Long seqNo, LocalDateTime timestamp) {
        this.lastTelemetrySourceEventId = sourceEventId;
        this.lastTelemetrySeqNo = seqNo;
        this.lastTelemetryAt = timestamp;
    }

    public boolean isTelemetryDuplicate(String sourceEventId) {
        return sourceEventId != null && Objects.equals(this.lastTelemetrySourceEventId, sourceEventId);
    }
}
