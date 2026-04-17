package com.waddoc.domain.shared.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.lock.RedisDistributedLock;
import com.waddoc.shared.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * business_event_outbox를 polling 하며 Kafka에 실제 이벤트를 발행하는 relay다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEventOutboxRelay {

    private static final String LOCK_KEY = "lock:business-event-outbox-relay";
    private static final long LOCK_TTL_SECONDS = 30;

    private final BusinessEventOutboxService businessEventOutboxService;
    private final RedisDistributedLock redisDistributedLock;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${business-event.outbox-relay-interval-ms:1000}")
    public void relay() {
        String lockValue = redisDistributedLock.tryLock(LOCK_KEY, LOCK_TTL_SECONDS);
        if (lockValue == null) {
            return;
        }

        try {
            for (BusinessEventOutbox outbox : businessEventOutboxService.findPending()) {
                try {
                    // DB 트랜잭션이 끝난 뒤에도 재전송 가능하도록, Kafka로는 envelope만 복원해서 내보낸다.
                    JsonNode payload = objectMapper.readTree(outbox.getPayloadJson());
                    kafkaTemplate.send(
                            outbox.getEventType(),
                            outbox.getAggregateId(),
                            new EventEnvelope(
                                    outbox.getEventId(),
                                    outbox.getEventType(),
                                    outbox.getOccurredAt(),
                                    outbox.getProducer(),
                                    outbox.getAggregateId(),
                                    outbox.getCorrelationId(),
                                    payload
                            )
                    ).get();
                    businessEventOutboxService.markPublished(outbox.getId());
                } catch (Exception exception) {
                    log.warn("Failed to relay business event outbox. id={}, eventType={}", outbox.getId(), outbox.getEventType(), exception);
                }
            }
        } finally {
            redisDistributedLock.unlock(LOCK_KEY, lockValue);
        }
    }
}
