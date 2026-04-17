package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.shared.event.EventEnvelope;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.RobotCommandDispatchRequestedPayload;
import com.waddoc.shared.event.payload.RobotCommandEstopRequestedPayload;
import com.waddoc.shared.event.payload.RobotCommandWaypointRequestedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * core-app 또는 HTTP API가 발행한 로봇 명령 이벤트를 받아 실제 MQTT publish로 연결한다.
 */
@Service
@RequiredArgsConstructor
public class RobotCommandEventConsumer {

    private static final String CONSUMER_GROUP = "robot-command-group";

    private final ObjectMapper objectMapper;
    private final RobotCommandPublisher robotCommandPublisher;

    @KafkaListener(topics = EventTypes.ROBOT_COMMAND_WAYPOINT_REQUESTED_V1, groupId = CONSUMER_GROUP)
    public void consumeWaypoint(EventEnvelope envelope) {
        RobotCommandWaypointRequestedPayload payload =
                objectMapper.convertValue(envelope.payload(), RobotCommandWaypointRequestedPayload.class);
        robotCommandPublisher.publishWaypoint(payload.waypoint());
    }

    @KafkaListener(topics = EventTypes.ROBOT_COMMAND_ESTOP_REQUESTED_V1, groupId = CONSUMER_GROUP)
    public void consumeEstop(EventEnvelope envelope) {
        RobotCommandEstopRequestedPayload payload =
                objectMapper.convertValue(envelope.payload(), RobotCommandEstopRequestedPayload.class);
        robotCommandPublisher.publishEstop(payload.state());
    }

    @KafkaListener(topics = EventTypes.ROBOT_COMMAND_DISPATCH_REQUESTED_V1, groupId = CONSUMER_GROUP)
    public void consumeDispatch(EventEnvelope envelope) {
        RobotCommandDispatchRequestedPayload payload =
                objectMapper.convertValue(envelope.payload(), RobotCommandDispatchRequestedPayload.class);
        if (payload.targetWaypointNumber() != null) {
            robotCommandPublisher.publishDispatch(payload);
        }
    }
}
