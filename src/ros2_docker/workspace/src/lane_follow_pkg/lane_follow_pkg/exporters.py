import json
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

    def build_speed_snapshot(self, speed_state):
        return {
            'speed': float(speed_state.speed_ms),
            'speedMs': float(speed_state.speed_ms),
            'speedKmh': float(speed_state.speed_kmh),
        }

    def publish_minimap_route_payload(self, payload):
        message = String()
        message.data = json.dumps(payload, ensure_ascii=False)
        self.publisher.publish(message)

    def persist_minimap_route_payload(self, payload):
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
        waypoints,
        current_pose,
        speed_state,
    ):
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

        trajectory_points = [
            {'index': index, 'x': float(x), 'z': float(z)}
            for index, (x, z) in enumerate(trajectory)
        ]

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
            'path_waypoint_ids': list(path_ids),
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
