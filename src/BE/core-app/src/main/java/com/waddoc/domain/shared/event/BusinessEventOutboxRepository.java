package com.waddoc.domain.shared.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * business event outbox 적재분을 생성 순서대로 조회하기 위한 저장소다.
 */
public interface BusinessEventOutboxRepository extends JpaRepository<BusinessEventOutbox, Long> {

    List<BusinessEventOutbox> findAllByStatusOrderByCreatedAtAsc(BusinessEventOutboxStatus status);
}
