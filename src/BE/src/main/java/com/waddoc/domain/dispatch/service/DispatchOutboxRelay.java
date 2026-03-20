package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.global.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOutboxRelay {

    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${dispatch.outbox-relay-interval-ms:1000}")
    public void relay() {
        relayPendingOutboxes();
        relayRetryPendingOutboxes();
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
