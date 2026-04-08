package com.waddoc.domain.shared.event;

import com.waddoc.global.audit.BaseCreatedEntity;
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

import java.time.OffsetDateTime;

/**
 * 외부 부작용을 트랜잭션 밖에서 재전송 가능하게 처리하기 위한 business outbox 엔티티다.
 */
@Entity
@Table(name = "business_event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessEventOutbox extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_event_outbox_id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "producer", nullable = false, length = 60)
    private String producer;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessEventOutboxStatus status;

    @Builder
    public BusinessEventOutbox(
            String eventId,
            String eventType,
            String aggregateId,
            String correlationId,
            String producer,
            OffsetDateTime occurredAt,
            String payloadJson
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId;
        this.producer = producer;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.status = BusinessEventOutboxStatus.PENDING;
    }

    public void markPublished() {
        this.status = BusinessEventOutboxStatus.PUBLISHED;
    }
}
