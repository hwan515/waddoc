package com.waddoc.domain.robot.config;

public final class MqttTopics {
    public static final String ROBOT_ODOM    = "robot/odom";
    public static final String ROBOT_MINIMAP = "robot/minimap";
    public static final String ROBOT_STATE   = "robot/state";
    public static final String ROBOT_STATUS  = "robot/status";
    public static final String CMD_ESTOP     = "robot/cmd/estop";
    public static final String CMD_WAYPOINT  = "robot/cmd/waypoint";

    private MqttTopics() {}
}
