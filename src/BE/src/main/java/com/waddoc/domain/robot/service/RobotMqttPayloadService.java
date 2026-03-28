package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.waddoc.domain.robot.config.MqttTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotMqttPayloadService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;

    public String buildWaypointCommandPayload(int waypointNumber) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("command", "waypoint");
        payload.put("waypoint", waypointNumber);
        payload.put("target_waypoint", waypointNumber);
        payload.put("requested_at", OffsetDateTime.now(KST).toString());
        return write(payload);
    }

    public String buildEstopCommandPayload(boolean enabled) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("command", "estop");
        payload.put("enabled", enabled);
        payload.put("state", enabled ? 1 : 0);
        payload.put("requested_at", OffsetDateTime.now(KST).toString());
        return write(payload);
    }

    public Optional<String> normalizeInboundPayload(String topic, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Optional.empty();
        }

        return switch (topic) {
            case MqttTopics.ROBOT_ODOM -> normalizeStructuredPayload(topic, rawPayload, true);
            case MqttTopics.ROBOT_MINIMAP -> normalizeStructuredPayload(topic, rawPayload, true);
            case MqttTopics.ROBOT_STATE -> normalizeStatePayload(rawPayload);
            case MqttTopics.ROBOT_STATUS -> normalizeStatusPayload(rawPayload);
            default -> Optional.of(rawPayload.trim());
        };
    }

    private Optional<String> normalizeStructuredPayload(String topic, String rawPayload, boolean requireObject) {
        Optional<JsonNode> parsed = readJson(rawPayload);
        if (parsed.isEmpty()) {
            log.warn("Dropping invalid MQTT JSON payload. topic={}", topic);
            return Optional.empty();
        }

        JsonNode root = parsed.get();
        if (requireObject && !root.isObject()) {
            log.warn("Dropping non-object MQTT payload. topic={}", topic);
            return Optional.empty();
        }

        JsonNode sanitized = sanitizeNode(root);
        if (sanitized == null || sanitized.isNull() || (sanitized.isObject() && sanitized.isEmpty())) {
            log.warn("Dropping empty MQTT payload after normalization. topic={}", topic);
            return Optional.empty();
        }

        return Optional.of(write(sanitized));
    }

    private Optional<String> normalizeStatePayload(String rawPayload) {
        String trimmed = rawPayload.trim();
        Optional<JsonNode> parsed = readJson(trimmed);

        if (parsed.isPresent()) {
            JsonNode node = parsed.get();
            if (node.isTextual()) {
                trimmed = node.asText().trim();
            } else if (node.isObject()) {
                String state = textValue(node.get("state"));
                if (state == null || state.isBlank()) {
                    return Optional.empty();
                }

                ObjectNode normalized = objectMapper.createObjectNode();
                normalized.put("state", state);
                copyTextField(node, normalized, "updated_at");
                copyTextField(node, normalized, "source");
                return Optional.of(write(normalized));
            } else {
                return Optional.empty();
            }
        }

        if (trimmed.isBlank()) {
            return Optional.empty();
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("state", trimmed);
        normalized.put("updated_at", OffsetDateTime.now(KST).toString());
        return Optional.of(write(normalized));
    }

    private Optional<String> normalizeStatusPayload(String rawPayload) {
        Optional<JsonNode> parsed = readJson(rawPayload.trim());
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        JsonNode node = parsed.get();
        ObjectNode normalized = objectMapper.createObjectNode();

        if (node.isBoolean()) {
            normalized.put("online", node.booleanValue());
        } else if (node.isObject()) {
            JsonNode onlineNode = node.get("online");
            if (onlineNode == null || !onlineNode.isBoolean()) {
                return Optional.empty();
            }
            normalized.put("online", onlineNode.booleanValue());
            copyTextField(node, normalized, "transport");
            copyTextField(node, normalized, "updated_at");
        } else {
            return Optional.empty();
        }

        if (!normalized.has("updated_at")) {
            normalized.put("updated_at", OffsetDateTime.now(KST).toString());
        }

        return Optional.of(write(normalized));
    }

    private Optional<JsonNode> readJson(String rawPayload) {
        try {
            return Optional.of(objectMapper.readTree(rawPayload));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }

        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : iterable(node.fields())) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isEmpty()) {
                    continue;
                }
                sanitized.set(key, sanitizeNode(entry.getValue()));
            }
            return sanitized;
        }

        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                sanitized.add(sanitizeNode(child));
            }
            return sanitized;
        }

        if (node.isTextual()) {
            return TextNode.valueOf(node.asText().trim());
        }

        if (node.isFloatingPointNumber()) {
            double value = node.asDouble();
            if (!Double.isFinite(value)) {
                return NullNode.instance;
            }
            return DecimalNode.valueOf(BigDecimal.valueOf(value));
        }

        if (node.isNumber() || node.isBoolean()) {
            return node.deepCopy();
        }

        return JsonNodeFactory.instance.textNode(node.asText().trim());
    }

    private void copyTextField(JsonNode source, ObjectNode target, String fieldName) {
        String value = textValue(source.get(fieldName));
        if (value != null && !value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null ? null : value.trim();
    }

    private Iterable<Map.Entry<String, JsonNode>> iterable(java.util.Iterator<Map.Entry<String, JsonNode>> iterator) {
        return () -> iterator;
    }

    private String write(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MQTT payload", e);
        }
    }
}
