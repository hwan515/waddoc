package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.shared.event.BusinessEventOutboxService;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.RobotCommandDispatchRequestedPayload;
import com.waddoc.shared.event.payload.RobotCommandWaypointRequestedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * core-app에서 직접 MQTT를 알지 않고 로봇 명령을 business event로 요청하도록 감싼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RobotWaypointCommandClient {

    private final BusinessEventOutboxService businessEventOutboxService;

    public void dispatchToWaypoint(int targetWaypointNumber) {
        try {
            businessEventOutboxService.enqueue(
                    EventTypes.ROBOT_COMMAND_WAYPOINT_REQUESTED_V1,
                    "waypoint-" + targetWaypointNumber,
                    "corr_robot_waypoint_" + targetWaypointNumber,
                    new RobotCommandWaypointRequestedPayload(targetWaypointNumber)
            );
            log.info("Robot waypoint dispatch requested via business event. targetWaypointNumber={}", targetWaypointNumber);
        } catch (Exception e) {
            log.error("Robot waypoint dispatch failed. targetWaypointNumber={}", targetWaypointNumber, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }

    public void dispatchMission(String missionId, String vehicleId, int targetWaypointNumber, String destination) {
        try {
            businessEventOutboxService.enqueue(
                    EventTypes.ROBOT_COMMAND_DISPATCH_REQUESTED_V1,
                    missionId,
                    "corr_robot_dispatch_" + missionId,
                    new RobotCommandDispatchRequestedPayload(
                            missionId,
                            vehicleId,
                            targetWaypointNumber,
                            destination
                    )
            );
            log.info("Mission dispatch event queued. missionId={}, vehicleId={}, waypoint={}",
                    missionId, vehicleId, targetWaypointNumber);
        } catch (Exception e) {
            log.error("Mission dispatch failed. missionId={}", missionId, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }
}
