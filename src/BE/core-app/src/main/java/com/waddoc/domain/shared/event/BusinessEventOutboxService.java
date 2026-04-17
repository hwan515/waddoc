package com.waddoc.domain.shared.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * core-app이 외부 부작용을 직접 실행하지 않고 business event outbox에 적재하도록 돕는다.
 */
@Service
@RequiredArgsConstructor
public class BusinessEventOutboxService {

    private static final String PRODUCER = "core-app";

    private final BusinessEventOutboxRepository businessEventOutboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void enqueue(String eventType, String aggregateId, String correlationId, Object payload) {
        businessEventOutboxRepository.save(BusinessEventOutbox.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .correlationId(correlationId)
                .producer(PRODUCER)
                .occurredAt(OffsetDateTime.now(KstTime.resolve(clock)))
                .payloadJson(toJson(payload))
                .build());
    }

    @Transactional(readOnly = true)
    public java.util.List<BusinessEventOutbox> findPending() {
        return businessEventOutboxRepository.findAllByStatusOrderByCreatedAtAsc(BusinessEventOutboxStatus.PENDING);
    }

    @Transactional
    public void markPublished(Long outboxId) {
        businessEventOutboxRepository.findById(outboxId).ifPresent(BusinessEventOutbox::markPublished);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize business event payload", exception);
        }
    }
}
