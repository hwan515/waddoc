package com.waddoc.domain.mission.service;

import com.waddoc.domain.mission.dto.MissionTelemetryRequest;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 차량 telemetry의 인증, 최신성 판단, 위치/단계 반영을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MissionTelemetryService {

    private final MissionRepository missionRepository;
    private final Clock clock;

    @Value("${telemetry.api-key}")
    private String telemetryApiKey;

    public void receiveTelemetry(String missionId, String apiKey, MissionTelemetryRequest request) {
        validateApiKey(apiKey);
        processTelemetry(missionId, request);
    }

    public void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !telemetryApiKey.equals(apiKey)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    /**
     * 이미 처리한 이벤트나 더 오래된 데이터는 버리고, 최신 상태만 미션에 반영한다.
     */
    public void processTelemetry(String missionId, MissionTelemetryRequest request) {
        Mission mission = missionRepository.findByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        if (mission.isTelemetryDuplicate(request.getSourceEventId())) {
            return;
        }

        LocalDateTime telemetryTimestamp = request.getTimestamp().atZoneSameInstant(KstTime.ZONE).toLocalDateTime();
        if (isOutdated(mission, request.getSeqNo(), telemetryTimestamp)) {
            return;
        }

        mission.updateLocation(request.getLatitude(), request.getLongitude());

        if (shouldUpdatePhase(mission, request.getPhase())) {
            mission.updatePhase(request.getPhase(), LocalDateTime.now(KstTime.resolve(clock)));
        }

        mission.recordTelemetry(request.getSourceEventId(), request.getSeqNo(), telemetryTimestamp);
        missionRepository.save(mission);
    }

    private boolean isOutdated(Mission mission, Long seqNo, LocalDateTime timestamp) {
        if (mission.getLastTelemetrySeqNo() != null && seqNo != null) {
            return seqNo < mission.getLastTelemetrySeqNo();
        }

        if (mission.getLastTelemetryAt() != null && timestamp != null) {
            return timestamp.isBefore(mission.getLastTelemetryAt());
        }

        return false;
    }

    /**
     * 미션 단계가 뒤로 가지 않도록 막고, INCIDENT 같은 예외 상태만 별도 규칙을 적용한다.
     */
    private boolean shouldUpdatePhase(Mission mission, MissionPhase incomingPhase) {
        MissionPhase currentPhase = mission.getPhase();
        if (currentPhase == incomingPhase) {
            return false;
        }

        if (currentPhase == MissionPhase.COMPLETED || currentPhase == MissionPhase.FAILED) {
            return false;
        }

        if (currentPhase == MissionPhase.INCIDENT) {
            return mission.getPreviousPhase() == incomingPhase;
        }

        if (incomingPhase == MissionPhase.INCIDENT) {
            return true;
        }

        Integer currentRank = phaseRank(currentPhase);
        Integer incomingRank = phaseRank(incomingPhase);

        if (currentRank == null || incomingRank == null) {
            return false;
        }

        return incomingRank >= currentRank;
    }

    private Integer phaseRank(MissionPhase phase) {
        return switch (phase) {
            case CREATED -> 0;
            case DISPATCHED -> 1;
            case EN_ROUTE -> 2;
            case ARRIVED -> 3;
            case VERIFYING -> 4;
            case CONSULTING -> 5;
            case RETURNING -> 6;
            case COMPLETED -> 7;
            case FAILED -> 8;
            case INCIDENT -> null;
        };
    }
}
