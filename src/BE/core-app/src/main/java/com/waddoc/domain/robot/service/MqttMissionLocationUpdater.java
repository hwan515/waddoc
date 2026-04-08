package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로봇 텔레메트리에서 위치 정보를 해석해 mission 위치 필드를 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMissionLocationUpdater {

    private static final Pattern TRAILING_INTEGER_PATTERN = Pattern.compile("(\\d+)\\s*$");
    private static final EnumSet<MissionPhase> LOCATION_PHASES =
            EnumSet.of(MissionPhase.DISPATCHED, MissionPhase.EN_ROUTE, MissionPhase.ARRIVED, MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private final ObjectMapper objectMapper;
    private final MqttMissionResolver mqttMissionResolver;

    @Transactional
    public void updateFromOdom(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);

            JsonNode latNode = node.get("latitude");
            JsonNode lngNode = node.get("longitude");
            if (latNode == null || lngNode == null) {
                return;
            }

            BigDecimal latitude = latNode.decimalValue();
            BigDecimal longitude = lngNode.decimalValue();

            Optional<Mission> missionOptional = mqttMissionResolver.resolveMission(
                    readText(node, "missionId"),
                    readText(node, "vehicleId"),
                    readInteger(node, "goalWaypointId", "goal_waypoint_id"),
                    LOCATION_PHASES
            );

            missionOptional.ifPresent(mission -> {
                mission.updateLocation(latitude, longitude);
            });
        } catch (Exception e) {
            log.debug("Odom mission telemetry processing skipped: {}", e.getMessage());
        }
    }

    private String readText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.get(fieldName);
            if (candidate == null || candidate.isNull()) {
                continue;
            }
            String value = candidate.asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Integer readInteger(JsonNode node, String... fieldNames) {
        String textValue = readText(node, fieldNames);
        if (textValue == null) {
            return null;
        }
        Matcher matcher = TRAILING_INTEGER_PATTERN.matcher(textValue);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
