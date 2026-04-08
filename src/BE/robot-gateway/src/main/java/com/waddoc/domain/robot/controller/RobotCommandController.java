package com.waddoc.domain.robot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.security.RobotJwtAuthService;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.RobotCommandEstopRequestedPayload;
import com.waddoc.shared.event.payload.RobotCommandWaypointRequestedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 외부 로봇 명령 요청을 Kafka 이벤트로 변환해 robot-gateway 내부 소비 경로로 넘긴다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/robots/cmd")
@RequiredArgsConstructor
public class RobotCommandController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RobotJwtAuthService robotJwtAuthService;

    @PostMapping("/waypoint/{n}")
    public ResponseEntity<Void> sendWaypoint(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @PathVariable int n
    ) {
        robotJwtAuthService.requireAdminOrDoctor(authorizationHeader);
        publish(EventTypes.ROBOT_COMMAND_WAYPOINT_REQUESTED_V1, "waypoint-" + n, new RobotCommandWaypointRequestedPayload(n));
        log.info("Waypoint command accepted. waypoint={}", n);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/estop/{state}")
    public ResponseEntity<Void> sendEstop(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @PathVariable int state
    ) {
        robotJwtAuthService.requireAdminOrDoctor(authorizationHeader);
        boolean enabled = state == 1;
        publish(EventTypes.ROBOT_COMMAND_ESTOP_REQUESTED_V1, "estop-" + enabled, new RobotCommandEstopRequestedPayload(enabled));
        log.info("E-Stop command accepted. enabled={}", enabled);
        return ResponseEntity.accepted().build();
    }

    private void publish(String eventType, String aggregateId, Object payload) {
        // HTTP API 요청도 event contract로 감싸서 core가 발행한 command와 동일한 소비 경로를 사용한다.
        kafkaTemplate.send(
                eventType,
                aggregateId,
                new EventEnvelope(
                        UUID.randomUUID().toString(),
                        eventType,
                        OffsetDateTime.now(),
                        "robot-gateway",
                        aggregateId,
                        "corr_" + aggregateId,
                        objectMapper.valueToTree(payload)
                )
        );
    }
}
