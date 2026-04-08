package com.waddoc.domain.robot.config;

/**
 * gateway 내부 구현에서 사용하는 MQTT 토픽 alias를 제공한다.
 */
public final class MqttTopics {
    public static final String ROBOT_ODOM    = "robot/odom";
    public static final String ROBOT_MINIMAP = "robot/minimap";
    public static final String ROBOT_STATE   = "robot/state";
    public static final String ROBOT_STATUS  = "robot/status";
    public static final String CMD_ESTOP     = "robot/cmd/estop";
    public static final String CMD_WAYPOINT  = "robot/cmd/waypoint";
    public static final String CMD_DISPATCH  = "robot/cmd/dispatch";

    private MqttTopics() {}
}
