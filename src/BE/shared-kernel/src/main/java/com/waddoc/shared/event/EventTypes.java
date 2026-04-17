package com.waddoc.shared.event;

/**
 * 분리 서비스 간에 합의된 Kafka 이벤트 이름을 모아 둔 상수 집합이다.
 */
public final class EventTypes {

    public static final String BOOKING_CONFIRMED_V1 = "booking.confirmed.v1";
    public static final String BOOKING_CANCELLED_V1 = "booking.cancelled.v1";
    public static final String DISPATCH_ASSIGNED_V1 = "dispatch.assigned.v1";
    public static final String DISPATCH_DELAYED_V1 = "dispatch.delayed.v1";
    public static final String ROBOT_COMMAND_WAYPOINT_REQUESTED_V1 = "robot.command.waypoint.requested.v1";
    public static final String ROBOT_COMMAND_ESTOP_REQUESTED_V1 = "robot.command.estop.requested.v1";
    public static final String ROBOT_COMMAND_DISPATCH_REQUESTED_V1 = "robot.command.dispatch-requested.v1";
    public static final String ROBOT_TELEMETRY_V1 = "robot.telemetry.v1";

    private EventTypes() {
    }
}
