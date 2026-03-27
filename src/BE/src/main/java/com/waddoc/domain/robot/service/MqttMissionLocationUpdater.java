package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMissionLocationUpdater {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void updateFromOdom(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);

            JsonNode missionIdNode = node.get("missionId");
            if (missionIdNode == null || missionIdNode.isNull() || missionIdNode.asText().isBlank()) {
                return;
            }

            String missionId = missionIdNode.asText();
            JsonNode latNode = node.get("latitude");
            JsonNode lngNode = node.get("longitude");
            if (latNode == null || lngNode == null) {
                return;
            }

            BigDecimal latitude = latNode.decimalValue();
            BigDecimal longitude = lngNode.decimalValue();

            missionRepository.findByPublicId(missionId).ifPresent(mission -> {
                mission.updateLocation(latitude, longitude);
                missionRepository.save(mission);
            });
        } catch (Exception e) {
            log.debug("Odom mission telemetry processing skipped: {}", e.getMessage());
        }
    }
}
