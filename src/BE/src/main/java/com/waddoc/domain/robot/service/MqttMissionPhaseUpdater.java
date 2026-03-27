package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMissionPhaseUpdater {

    private static final EnumSet<MissionPhase> AUTO_PHASE_ELIGIBLE =
            EnumSet.of(MissionPhase.DISPATCHED, MissionPhase.EN_ROUTE, MissionPhase.ARRIVED, MissionPhase.VERIFYING, MissionPhase.CONSULTING);
    private static final Set<String> MOVING_STATES = Set.of(
            "DISPATCHED", "EN_ROUTE", "START", "DEPARTURE", "DRIVING", "MOVING", "출발", "주행 중"
    );
    private static final Set<String> ARRIVED_STATES = Set.of("ARRIVED", "도착");
    private static final Set<String> ARRIVAL_CLEAR_REASONS = Set.of("goal_reset", "traj_end_reached");
    private static final BigDecimal MOVING_SPEED_THRESHOLD = new BigDecimal("0.1");
    private static final Pattern TRAILING_INTEGER_PATTERN = Pattern.compile("(\\d+)\\s*$");

    private final ObjectMapper objectMapper;
    private final MissionRepository missionRepository;
    private final MqttMissionResolver mqttMissionResolver;

    @Transactional
    public void updateFromMinimap(String minimapPayload, String statePayload) {
        try {
            JsonNode minimapNode = objectMapper.readTree(minimapPayload);
            JsonNode stateNode = readJson(statePayload);
            Optional<Mission> missionOptional = resolveMission(minimapNode);
            if (missionOptional.isEmpty()) {
                return;
            }

            Mission mission = missionOptional.get();
            if (shouldTransitionToArrived(minimapNode, stateNode, mission)) {
                transition(mission, MissionPhase.ARRIVED, "robot/minimap");
                return;
            }

            if (shouldTransitionToEnRoute(minimapNode, stateNode, mission)) {
                transition(mission, MissionPhase.EN_ROUTE, "robot/minimap");
            }
        } catch (Exception e) {
            log.debug("Minimap phase telemetry processing skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public void updateFromState(String statePayload) {
        try {
            JsonNode stateNode = objectMapper.readTree(statePayload);
            Optional<Mission> missionOptional = resolveMission(stateNode);
            if (missionOptional.isEmpty()) {
                return;
            }

            Mission mission = missionOptional.get();
            String normalizedState = normalizeState(readText(stateNode, "state", "status"));
            if (normalizedState == null) {
                return;
            }

            if (ARRIVED_STATES.contains(normalizedState)) {
                transition(mission, MissionPhase.ARRIVED, "robot/state");
                return;
            }

            if (MOVING_STATES.contains(normalizedState)) {
                transition(mission, MissionPhase.EN_ROUTE, "robot/state");
            }
        } catch (Exception e) {
            log.debug("State phase telemetry processing skipped: {}", e.getMessage());
        }
    }

    private Optional<Mission> resolveMission(JsonNode node) {
        return mqttMissionResolver.resolveMission(
                readText(node, "missionId"),
                readText(node, "vehicleId"),
                readInteger(node, "goalWaypointId", "goal_waypoint_id", "targetWaypointValue", "target_waypoint_value"),
                AUTO_PHASE_ELIGIBLE
        );
    }

    private boolean shouldTransitionToArrived(JsonNode minimapNode, JsonNode stateNode, Mission mission) {
        MissionPhase currentPhase = mission.getPhase();
        if (currentPhase != MissionPhase.DISPATCHED && currentPhase != MissionPhase.EN_ROUTE) {
            return false;
        }

        String telemetryState = normalizeState(firstNonBlank(
                readText(minimapNode, "state", "status"),
                readText(stateNode, "state", "status")
        ));
        if (telemetryState != null && ARRIVED_STATES.contains(telemetryState)) {
            return true;
        }

        String clearReason = normalizeReason(readText(minimapNode, "clearReason", "clear_reason"));
        BigDecimal speedMs = readDecimal(minimapNode, "speedMs", "speed_ms", "speed");
        return clearReason != null
                && ARRIVAL_CLEAR_REASONS.contains(clearReason)
                && speedMs != null
                && speedMs.compareTo(MOVING_SPEED_THRESHOLD) <= 0;
    }

    private boolean shouldTransitionToEnRoute(JsonNode minimapNode, JsonNode stateNode, Mission mission) {
        if (mission.getPhase() != MissionPhase.DISPATCHED) {
            return false;
        }

        String telemetryState = normalizeState(firstNonBlank(
                readText(stateNode, "state", "status"),
                readText(minimapNode, "state", "status")
        ));
        if (telemetryState != null && MOVING_STATES.contains(telemetryState)) {
            return true;
        }

        BigDecimal speedMs = readDecimal(minimapNode, "speedMs", "speed_ms", "speed");
        return speedMs != null && speedMs.compareTo(MOVING_SPEED_THRESHOLD) > 0;
    }

    private void transition(Mission mission, MissionPhase targetPhase, String sourceTopic) {
        MissionPhase currentPhase = mission.getPhase();
        if (targetPhase == MissionPhase.EN_ROUTE && currentPhase != MissionPhase.DISPATCHED) {
            return;
        }
        if (targetPhase == MissionPhase.ARRIVED && currentPhase != MissionPhase.DISPATCHED && currentPhase != MissionPhase.EN_ROUTE) {
            return;
        }

        mission.updatePhase(targetPhase);
        missionRepository.save(mission);
        log.info("Mission phase updated from MQTT. missionId={}, previousPhase={}, nextPhase={}, sourceTopic={}",
                mission.getPublicId(), currentPhase, targetPhase, sourceTopic);
    }

    private JsonNode readJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
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
        if (textValue != null) {
            Matcher matcher = TRAILING_INTEGER_PATTERN.matcher(textValue);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.get(fieldName);
            if (candidate != null && candidate.isInt()) {
                return candidate.intValue();
            }
        }
        return null;
    }

    private BigDecimal readDecimal(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.get(fieldName);
            if (candidate == null || candidate.isNull()) {
                continue;
            }
            if (candidate.isNumber()) {
                return candidate.decimalValue();
            }
            try {
                return new BigDecimal(candidate.asText());
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return null;
    }

    private String normalizeState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
