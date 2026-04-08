package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.robot.dto.RobotSnapshotDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotSnapshotAssemblerTest {

    private final RobotSnapshotAssembler assembler = new RobotSnapshotAssembler(new ObjectMapper());

    @Test
    void assemble_prefersOdomForTelemetryAndMinimapForNavigation() {
        RobotSnapshotDto snapshot = assembler.assemble(
                """
                {
                  "goal_waypoint_id": "Waypoint_142",
                  "target_waypoint_value": 142,
                  "battery_soc": 87.5,
                  "current_pose": { "x": 690.0, "z": 1778.7, "yaw": 0.9 },
                  "path_waypoints": [{ "id": "Waypoint_141", "x": 689.0, "z": 1777.0 }],
                  "trajectory": [{ "x": 689.5, "z": 1777.5 }],
                  "full_path_waypoints": [{ "id": "Waypoint_140", "x": 688.0, "z": 1776.0 }],
                  "full_trajectory": [{ "x": 688.5, "z": 1776.5 }],
                  "bounds": { "min_x": 688.0, "max_x": 690.0, "min_z": 1776.0, "max_z": 1778.7, "width": 2.0, "height": 2.7 },
                  "generated_at": "2026-03-27T04:41:05.363472+00:00"
                }
                """,
                """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "missionId": "ms_demo_001",
                  "speed_ms": 1.5,
                  "speed_kmh": 5.4,
                  "latitude": 36.1115,
                  "longitude": 128.0708,
                  "minimap_pose": { "x": 690.0011, "z": 1778.6996, "yaw": 0.99865 }
                }
                """,
                """
                {
                  "state": "주행 중",
                  "updated_at": "2026-03-27T04:41:05.100000+00:00"
                }
                """,
                """
                {
                  "online": true,
                  "updated_at": "2026-03-27T04:41:05.200000+00:00"
                }
                """
        );

        assertThat(snapshot.telemetry().vehicleId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(snapshot.telemetry().missionId()).isEqualTo("ms_demo_001");
        assertThat(snapshot.telemetry().state()).isEqualTo("주행 중");
        assertThat(snapshot.telemetry().online()).isTrue();
        assertThat(snapshot.telemetry().batterySoc()).isEqualTo(87.5);
        assertThat(snapshot.telemetry().speedMs()).isEqualTo(1.5);
        assertThat(snapshot.telemetry().speedKmh()).isEqualTo(5.4);
        assertThat(snapshot.telemetry().pose().x()).isEqualTo(690.0011);
        assertThat(snapshot.telemetry().location().lat()).isEqualTo(36.1115);
        assertThat(snapshot.telemetry().updatedAt()).isEqualTo("2026-03-27T04:41:05.363472Z");
        assertThat(snapshot.navigation().goalWaypointId()).isEqualTo("Waypoint_142");
        assertThat(snapshot.navigation().targetWaypointValue()).isEqualTo(142);
        assertThat(snapshot.navigation().pathWaypoints()).hasSize(1);
        assertThat(snapshot.navigation().trajectory()).hasSize(1);
    }

    @Test
    void assemble_fallsBackToMinimapWhenOdomIsMissing() {
        RobotSnapshotDto snapshot = assembler.assemble(
                """
                {
                  "vehicleId": "veh_GIMCHEON_01",
                  "current_pose": { "x": 690.0, "z": 1778.7, "yaw": 0.9 },
                  "speedMs": 0.3,
                  "speedKmh": 1.08,
                  "battery_soc": 91.0
                }
                """,
                "{}",
                "{}",
                "{}"
        );

        assertThat(snapshot.telemetry().vehicleId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(snapshot.telemetry().pose().x()).isEqualTo(690.0);
        assertThat(snapshot.telemetry().speedKmh()).isEqualTo(1.08);
        assertThat(snapshot.telemetry().batterySoc()).isEqualTo(91.0);
    }

    @Test
    void assemble_usesStateTopicOverTelemetryStateFields() {
        RobotSnapshotDto snapshot = assembler.assemble(
                """
                {
                  "state": "대기"
                }
                """,
                """
                {
                  "state": "주행 중"
                }
                """,
                """
                {
                  "state": "도착"
                }
                """,
                "{}"
        );

        assertThat(snapshot.telemetry().state()).isEqualTo("도착");
    }

    @Test
    void assemble_preservesWaitingGoalNavigationState() {
        RobotSnapshotDto snapshot = assembler.assemble(
                """
                {
                  "goal_waypoint_id": null,
                  "target_waypoint_value": 0,
                  "cleared": true,
                  "clear_reason": "waiting_goal"
                }
                """,
                "{}",
                "{}",
                "{}"
        );

        assertThat(snapshot.navigation().goalWaypointId()).isNull();
        assertThat(snapshot.navigation().targetWaypointValue()).isEqualTo(0);
        assertThat(snapshot.navigation().cleared()).isTrue();
        assertThat(snapshot.navigation().clearReason()).isEqualTo("waiting_goal");
    }
}
