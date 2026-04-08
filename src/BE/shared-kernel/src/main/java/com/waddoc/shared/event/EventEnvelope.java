package com.waddoc.shared.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 서비스 간 비즈니스 이벤트를 공통 형식으로 전달하기 위한 envelope이다.
 * payload만 달라지고 메타데이터 구조는 모든 이벤트에서 동일하게 유지한다.
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        OffsetDateTime occurredAt,
        String producer,
        String aggregateId,
        String correlationId,
        JsonNode payload
) {
}
