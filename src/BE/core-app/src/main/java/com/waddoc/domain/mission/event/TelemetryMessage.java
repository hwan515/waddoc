package com.waddoc.domain.mission.event;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;

/**
 * 차량 텔레메트리 수집 API가 Kafka로 넘길 때 사용하는 내부 메시지다.
 */
public record TelemetryMessage(
        String missionId,
        MissionTelemetryRequest request
) {
}
