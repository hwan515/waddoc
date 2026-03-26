import json
import math
import time
from datetime import datetime, timezone
from pathlib import Path

from std_msgs.msg import String


class MinimapRouteExporter:
    def __init__(
        self,
        route_export_path,
        publisher,
        logger,
        source_name_resolver,
        goal_state_resolver,
        config,
    ):
        self.route_export_path = Path(route_export_path)
        self.publisher = publisher
        self.logger = logger
        self.source_name_resolver = source_name_resolver
        self.goal_state_resolver = goal_state_resolver
        self.config = config
        self.route_export_seq = 0
        self.last_minimap_publish_time = 0.0
        self.minimap_last_error_log_time = 0.0
        self.active_route_start_id = None
        self.active_route_state_value = None
        self.current_battery_soc = 100.0

    def sanitize_battery_soc(self, value, default=100.0):
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            parsed = float(default)

        if not math.isfinite(parsed):
            parsed = float(default)

        return max(0.0, min(100.0, parsed))

    def get_battery_soc(self):
        self.current_battery_soc = self.sanitize_battery_soc(self.current_battery_soc)
        return self.current_battery_soc

    def update_battery_soc(self, value):
        self.current_battery_soc = self.sanitize_battery_soc(value)
        return self.current_battery_soc

    def build_speed_snapshot(self, speed_state):
        return {
            'speed': float(speed_state.speed_ms),
            'speedMs': float(speed_state.speed_ms),
            'speedKmh': float(speed_state.speed_kmh),
        }

    def apply_runtime_payload_fields(self, payload):
        payload['battery_soc'] = self.get_battery_soc()
        return payload

    def publish_minimap_route_payload(self, payload):
        payload = self.apply_runtime_payload_fields(payload)
        message = String()
        message.data = json.dumps(payload, ensure_ascii=False)
        self.publisher.publish(message)

    def persist_minimap_route_payload(self, payload):
        payload = self.apply_runtime_payload_fields(payload)
        self.route_export_path.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = self.route_export_path.parent / f'{self.route_export_path.name}.tmp'
        tmp_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding='utf-8',
        )
        tmp_path.replace(self.route_export_path)

    def log_minimap_export_error(self, error):
        now = time.time()
        if now - self.minimap_last_error_log_time >= 5.0:
            self.logger.error(f'Minimap export failed: {error}')
            self.minimap_last_error_log_time = now

    def clear_active_route(self):
        self.active_route_start_id = None
        self.active_route_state_value = None

    def build_path_waypoints(self, path_ids, waypoints):
        path_waypoints = []
        for waypoint_id in path_ids:
            coords = waypoints.get(waypoint_id)
            if coords is None:
                continue

            path_waypoints.append(
                {
                    'id': waypoint_id,
                    'x': float(coords['x']),
                    'z': float(coords['z']),
                }
            )

        return path_waypoints

    def squared_distance(self, x1, z1, x2, z2):
        dx = float(x1) - float(x2)
        dz = float(z1) - float(z2)
        return (dx * dx) + (dz * dz)

    def find_nearest_path_waypoint_index(self, path_waypoints, current_pose):
        if not path_waypoints:
            return 0

        nearest_index = 0
        nearest_distance_sq = float('inf')
        current_x = float(current_pose.x)
        current_z = float(current_pose.z)

        for index, waypoint in enumerate(path_waypoints):
            distance_sq = self.squared_distance(
                current_x,
                current_z,
                waypoint['x'],
                waypoint['z'],
            )
            if distance_sq < nearest_distance_sq:
                nearest_index = index
                nearest_distance_sq = distance_sq

        return nearest_index

    def find_nearest_trajectory_index(self, trajectory, target_x, target_z):
        if not trajectory:
            return 0

        nearest_index = 0
        nearest_distance_sq = float('inf')

        for index, (x, z) in enumerate(trajectory):
            distance_sq = self.squared_distance(x, z, target_x, target_z)
            if distance_sq < nearest_distance_sq:
                nearest_index = index
                nearest_distance_sq = distance_sq

        return nearest_index

    def points_match(self, point_a, point_b, tolerance=1e-6):
        return (
            abs(float(point_a[0]) - float(point_b[0])) <= tolerance
            and abs(float(point_a[1]) - float(point_b[1])) <= tolerance
        )

    def serialize_trajectory_points(self, trajectory):
        return [
            {'index': index, 'x': float(x), 'z': float(z)}
            for index, (x, z) in enumerate(trajectory)
        ]

    def build_display_pose_and_path(self, *, path_ids, waypoints, trajectory, current_pose):
        full_path_waypoints = self.build_path_waypoints(path_ids, waypoints)
        display_path_waypoints = list(full_path_waypoints)
        full_trajectory = [(float(x), float(z)) for x, z in trajectory]
        display_trajectory = list(full_trajectory)

        if full_path_waypoints:
            nearest_index = self.find_nearest_path_waypoint_index(
                full_path_waypoints,
                current_pose,
            )
            display_path_waypoints = full_path_waypoints[nearest_index:]
            nearest_waypoint = display_path_waypoints[0]

            if display_trajectory:
                display_start_index = self.find_nearest_trajectory_index(
                    display_trajectory,
                    nearest_waypoint['x'],
                    nearest_waypoint['z'],
                )
                display_trajectory = display_trajectory[display_start_index:]

            nearest_waypoint_point = (
                float(nearest_waypoint['x']),
                float(nearest_waypoint['z']),
            )
            if not display_trajectory:
                display_trajectory = [nearest_waypoint_point]
            elif not self.points_match(display_trajectory[0], nearest_waypoint_point):
                display_trajectory.insert(0, nearest_waypoint_point)

        return {
            'full_path_waypoint_ids': [waypoint['id'] for waypoint in full_path_waypoints],
            'full_path_waypoints': full_path_waypoints,
            'full_trajectory': self.serialize_trajectory_points(full_trajectory),
            'path_waypoint_ids': [waypoint['id'] for waypoint in display_path_waypoints],
            'path_waypoints': display_path_waypoints,
            'trajectory': self.serialize_trajectory_points(display_trajectory),
        }

    def build_empty_route_snapshot(
        self,
        *,
        goal_wp_id,
        waypoints,
        current_pose,
        speed_state,
        reason='navigation_cleared',
    ):
        goal_wp = waypoints.get(goal_wp_id)

        return {
            'route_version': self.route_export_seq,
            'generated_at': datetime.now(timezone.utc).isoformat(),
            'source_node': self.source_name_resolver(),
            'planner_type': 'vision+spline',
            'start_waypoint_id': None,
            'goal_waypoint_id': goal_wp_id,
            'target_waypoint_value': 0,
            'current_pose': {
                'x': float(current_pose.x),
                'z': float(current_pose.z),
                'yaw': float(current_pose.yaw),
                'speed_ms': float(speed_state.speed_ms),
                'speed_kmh': float(speed_state.speed_kmh),
            },
            **self.build_speed_snapshot(speed_state),
            'start_waypoint': None,
            'goal_waypoint': None if goal_wp is None else {
                'id': goal_wp_id,
                'x': float(goal_wp['x']),
                'z': float(goal_wp['z']),
            },
            'battery_soc': self.get_battery_soc(),
            'full_path_waypoint_ids': [],
            'full_path_waypoints': [],
            'full_trajectory_point_count': 0,
            'full_trajectory': [],
            'path_waypoint_ids': [],
            'path_waypoints': [],
            'trajectory_point_count': 0,
            'trajectory': [],
            'bounds': {
                'min_x': float(current_pose.x),
                'max_x': float(current_pose.x),
                'min_z': float(current_pose.z),
                'max_z': float(current_pose.z),
                'width': 0.0,
                'height': 0.0,
            },
            'cleared': True,
            'clear_reason': reason,
        }

    def publish_cleared_route(
        self,
        *,
        goal_wp_id,
        waypoints,
        current_pose,
        speed_state,
        reason='navigation_cleared',
    ):
        try:
            self.route_export_seq += 1
            self.clear_active_route()
            payload = self.build_empty_route_snapshot(
                goal_wp_id=goal_wp_id,
                waypoints=waypoints,
                current_pose=current_pose,
                speed_state=speed_state,
                reason=reason,
            )
            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = time.time()
        except Exception as error:
            self.log_minimap_export_error(error)

    def build_route_snapshot(
        self,
        *,
        start_id,
        goal_id,
        state_value,
        path_ids,
        trajectory,
        display_path_ids,
        display_trajectory,
        waypoints,
        current_pose,
        speed_state,
    ):
        display_data = self.build_display_pose_and_path(
            path_ids=display_path_ids if display_path_ids else path_ids,
            waypoints=waypoints,
            trajectory=display_trajectory if display_trajectory else trajectory,
            current_pose=current_pose,
        )
        full_path_waypoints = display_data['full_path_waypoints']
        full_trajectory_points = display_data['full_trajectory']
        path_waypoints = display_data['path_waypoints']
        trajectory_points = display_data['trajectory']

        start_wp = waypoints.get(start_id)
        goal_wp = waypoints.get(goal_id)

        bound_points = [(current_pose.x, current_pose.z)]
        if start_wp is not None:
            bound_points.append((float(start_wp['x']), float(start_wp['z'])))
        if goal_wp is not None:
            bound_points.append((float(goal_wp['x']), float(goal_wp['z'])))
        bound_points.extend((float(point['x']), float(point['z'])) for point in trajectory_points)

        xs = [point[0] for point in bound_points]
        zs = [point[1] for point in bound_points]

        return {
            'route_version': self.route_export_seq,
            'generated_at': datetime.now(timezone.utc).isoformat(),
            'source_node': self.source_name_resolver(),
            'planner_type': 'vision+spline',
            'start_waypoint_id': start_id,
            'goal_waypoint_id': goal_id,
            'target_waypoint_value': int(state_value),
            'current_pose': {
                'x': float(current_pose.x),
                'z': float(current_pose.z),
                'yaw': float(current_pose.yaw),
                'speed_ms': float(speed_state.speed_ms),
                'speed_kmh': float(speed_state.speed_kmh),
            },
            **self.build_speed_snapshot(speed_state),
            'start_waypoint': None if start_wp is None else {
                'id': start_id,
                'x': float(start_wp['x']),
                'z': float(start_wp['z']),
            },
            'goal_waypoint': None if goal_wp is None else {
                'id': goal_id,
                'x': float(goal_wp['x']),
                'z': float(goal_wp['z']),
            },
            'battery_soc': self.get_battery_soc(),
            'full_path_waypoint_ids': display_data['full_path_waypoint_ids'],
            'full_path_waypoints': full_path_waypoints,
            'full_trajectory_point_count': len(full_trajectory_points),
            'full_trajectory': full_trajectory_points,
            'path_waypoint_ids': display_data['path_waypoint_ids'],
            'path_waypoints': path_waypoints,
            'trajectory_point_count': len(trajectory_points),
            'trajectory': trajectory_points,
            'bounds': {
                'min_x': float(min(xs)),
                'max_x': float(max(xs)),
                'min_z': float(min(zs)),
                'max_z': float(max(zs)),
                'width': float(max(xs) - min(xs)),
                'height': float(max(zs) - min(zs)),
            },
        }

    def export_route(
        self,
        *,
        start_id,
        goal_id,
        state_value,
        path_ids,
        trajectory,
        display_path_ids=None,
        display_trajectory=None,
        waypoints,
        current_pose,
        speed_state,
    ):
        try:
            self.route_export_seq += 1
            self.active_route_start_id = start_id
            self.active_route_state_value = int(state_value)
            payload = self.build_route_snapshot(
                start_id=start_id,
                goal_id=goal_id,
                state_value=state_value,
                path_ids=path_ids,
                trajectory=trajectory,
                display_path_ids=display_path_ids,
                display_trajectory=display_trajectory,
                waypoints=waypoints,
                current_pose=current_pose,
                speed_state=speed_state,
            )
            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = time.time()
            self.logger.info(
                f'route json saved: {self.route_export_path} (version={self.route_export_seq})'
            )
        except Exception as error:
            self.log_minimap_export_error(error)

    def publish_live_snapshot(
        self,
        *,
        goal_wp_id,
        waypoints,
        path_ids,
        trajectory,
        display_path_ids=None,
        display_trajectory=None,
        current_pose,
        speed_state,
        current_mode,
        force=False,
    ):
        now = time.time()
        if not force and (now - self.last_minimap_publish_time) < self.config.live_publish_interval:
            return

        try:
            if trajectory and goal_wp_id is not None:
                state_value = (
                    self.active_route_state_value
                    if self.active_route_state_value is not None
                    else self.goal_state_resolver(goal_wp_id)
                )
                payload = self.build_route_snapshot(
                    start_id=self.active_route_start_id,
                    goal_id=goal_wp_id,
                    state_value=state_value,
                    path_ids=path_ids,
                    trajectory=trajectory,
                    display_path_ids=display_path_ids,
                    display_trajectory=display_trajectory,
                    waypoints=waypoints,
                    current_pose=current_pose,
                    speed_state=speed_state,
                )
            else:
                payload = self.build_empty_route_snapshot(
                    goal_wp_id=goal_wp_id,
                    waypoints=waypoints,
                    current_pose=current_pose,
                    speed_state=speed_state,
                    reason=current_mode,
                )

            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = now
        except Exception as error:
            self.log_minimap_export_error(error)
