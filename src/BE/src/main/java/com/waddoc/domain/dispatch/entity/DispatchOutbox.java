package com.waddoc.domain.dispatch.entity;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.global.audit.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dispatch_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispatchOutbox extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CareCase careCase;

    @Column(name = "region_code", nullable = false, length = 30)
    private String regionCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchOutboxStatus status;

    @Builder
    public DispatchOutbox(CareCase careCase, String regionCode, String destination) {
        this.careCase = careCase;
        this.regionCode = regionCode;
        this.destination = destination;
        this.status = DispatchOutboxStatus.PENDING;
    }

    public void markPublished() {
        this.status = DispatchOutboxStatus.PUBLISHED;
    }

    public void markRetryPending() {
        this.status = DispatchOutboxStatus.RETRY_PENDING;
    }

    public void markCompleted() {
        this.status = DispatchOutboxStatus.COMPLETED;
    }

    public boolean isRetryPending() {
        return status == DispatchOutboxStatus.RETRY_PENDING;
    }

    public boolean isCompleted() {
        return status == DispatchOutboxStatus.COMPLETED;
    }
}
