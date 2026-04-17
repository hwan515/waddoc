package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.lock.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * dispatch_outbox를 Kafka 배차 토픽으로 내보내는 단일 relay 역할을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOutboxRelay {

    private static final String LOCK_KEY = "lock:outbox-relay";
    private static final long LOCK_TTL_SECONDS = 30;

    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisDistributedLock redisDistributedLock;
    private final DemoModePolicy demoModePolicy;

    @Scheduled(fixedDelayString = "${dispatch.outbox-relay-interval-ms:1000}")
    public void relay() {
        if (demoModePolicy.isOperatorDispatchOnly()) {
            return;
        }
        String lockValue = redisDistributedLock.tryLock(LOCK_KEY, LOCK_TTL_SECONDS);
        if (lockValue == null) {
            return;
        }
        try {
            relayPendingOutboxes();
            relayRetryPendingOutboxes();
        } finally {
            redisDistributedLock.unlock(LOCK_KEY, lockValue);
        }
    }

    private void relayPendingOutboxes() {
        List<DispatchOutbox> pendingList = fetchByStatus(DispatchOutboxStatus.PENDING);
        for (DispatchOutbox outbox : pendingList) {
            try {
                kafkaTemplate.send(
                        KafkaTopics.DISPATCH_REQUESTS_TOPIC,
                        outbox.getRegionCode(),
                        DispatchRequestMessage.from(outbox)
                ).get();
                // 브로커 ack를 받은 뒤에만 outbox를 PUBLISHED로 바꾼다.
                markPublished(outbox.getId());
            } catch (Exception e) {
                log.warn("Failed to relay dispatch outbox id={}: {}", outbox.getId(), e.getMessage());
            }
        }
    }

    private void relayRetryPendingOutboxes() {
        List<DispatchOutbox> retryList = fetchByStatus(DispatchOutboxStatus.RETRY_PENDING);
        // 같은 권역은 한 번만 다시 깨워도 대기 중인 outbox가 순서대로 재평가된다.
        Set<String> relayedRegions = new HashSet<>();
        for (DispatchOutbox outbox : retryList) {
            if (!relayedRegions.add(outbox.getRegionCode())) {
                continue;
            }
            try {
                kafkaTemplate.send(
                        KafkaTopics.DISPATCH_RETRY_TOPIC,
                        outbox.getRegionCode(),
                        DispatchRequestMessage.from(outbox)
                ).get();
            } catch (Exception e) {
                log.warn("Failed to relay retry outbox id={}: {}", outbox.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<DispatchOutbox> fetchByStatus(DispatchOutboxStatus status) {
        return dispatchOutboxRepository.findAllByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional
    public void markPublished(Long outboxId) {
        dispatchOutboxRepository.findById(outboxId).ifPresent(DispatchOutbox::markPublished);
    }
}
