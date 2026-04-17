package com.waddoc.shared.event;

/**
 * robot-gateway와 ROS2가 MQTT로 주고받는 토픽 이름을 정의한다.
 */
public final class RobotTopics {

    public static final String ROBOT_ODOM = "robot/odom";
    public static final String ROBOT_MINIMAP = "robot/minimap";
    public static final String ROBOT_STATE = "robot/state";
    public static final String ROBOT_STATUS = "robot/status";
    public static final String CMD_ESTOP = "robot/cmd/estop";
    public static final String CMD_WAYPOINT = "robot/cmd/waypoint";
    public static final String CMD_DISPATCH = "robot/cmd/dispatch";

    private RobotTopics() {
    }
}
