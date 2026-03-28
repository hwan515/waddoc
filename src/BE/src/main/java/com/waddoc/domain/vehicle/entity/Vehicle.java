package com.waddoc.domain.vehicle.entity;

import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "vehicle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "region_code", nullable = false, length = 30)
    private String regionCode;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 30)
    private OperationalStatus operationalStatus;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Builder
    public Vehicle(
            String code,
            String regionCode,
            String displayName,
            Boolean isActive,
            OperationalStatus operationalStatus,
            LocalDateTime statusChangedAt,
            String statusReason
    ) {
        this.publicId = PublicIdGenerator.generate("veh_");
        this.code = code;
        this.regionCode = regionCode;
        this.displayName = displayName;
        this.isActive = isActive == null || isActive;
        this.operationalStatus = operationalStatus == null ? OperationalStatus.OPERATIONAL : operationalStatus;
        this.statusChangedAt = statusChangedAt;
        this.statusReason = statusReason;
    }

    public void updateOperationalStatus(OperationalStatus nextStatus, String reason) {
        String normalizedReason = normalizeReason(reason);
        if (this.operationalStatus == nextStatus && Objects.equals(this.statusReason, normalizedReason)) {
            return;
        }

        if (this.operationalStatus != nextStatus) {
            this.statusChangedAt = LocalDateTime.now();
        }
        this.operationalStatus = nextStatus;
        this.statusReason = nextStatus == OperationalStatus.OPERATIONAL ? null : normalizedReason;
    }

    public boolean isOperational() {
        return isActive && operationalStatus == OperationalStatus.OPERATIONAL;
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
