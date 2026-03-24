import json
import math
import time
import heapq
from datetime import datetime, timezone
from pathlib import Path

import rclpy
from rclpy.node import Node
from rclpy.qos import qos_profile_sensor_data
from geometry_msgs.msg import Twist
from nav_msgs.msg import Odometry
from std_msgs.msg import Bool, Int32

try:
    from ament_index_python.packages import get_package_share_directory
except ImportError:
    get_package_share_directory = None


class DijkstraSplineFollower(Node):
    def __init__(self):
        super().__init__('dijkstra_spline_follower')

        self.sub_odom = self.create_subscription(
            Odometry, '/odom', self.odom_callback, qos_profile_sensor_data
        )
        self.sub_estop = self.create_subscription(
            Bool, '/ec2_cmd/e_stop', self.estop_callback, 1
        )
        self.sub_target = self.create_subscription(
            Int32, '/ec2_cmd/target_waypoint', self.target_waypoint_callback, 1
        )
        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', 10)
        self.pub_state_stop = self.create_publisher(Int32, '/cmd_state/e_stop', 1)
        self.pub_state_waypoint = self.create_publisher(
            Int32, '/cmd_state/target_waypoint', 1
        )
        self.timer = self.create_timer(0.05, self.control_loop)

        self.current_x = 0.0
        self.current_z = 0.0
        self.current_yaw = 0.0
        self.has_odom = False
        self.estop_active = False

        self.waypoints = {}
        self.edges = {}

        self.path = []
        self.trajectory = []
        self.closest_traj_idx = 0
        self.goal_wp_id = None

        self.last_log_time = 0.0

        # =========================
        # 속도 / 제어 파라미터
        # =========================
        self.cruise_speed = 0.5   # 등속 고정

        self.kp = 0.25
        self.kd = 0.03

        self.max_angular = 0.15
        self.max_d_term = 0.03
        self.deadband_deg = 2.0

        self.prev_error = 0.0
        self.prev_time = None

        # 조향 안정화
        self.angular_cmd = 0.0
        self.max_angular_step = 0.015
        self.straight_error_deg = 5.0
        self.straight_cte = 0.8
        self.hold_decay = 0.75

        # =========================
        # trajectory 추종 파라미터
        # =========================
        self.lookahead_dist = 10.0
        self.goal_tolerance = 5.0
        self.traj_reach_tolerance = 3.0
        self.behind_angle_deg = 100.0

        # corridor 기반 안정화
        self.corridor_radius = 2.5
        self.heading_hold_deg = 6.0
        self.release_corridor_radius = 4.5
        self.last_stable_angular = 0.0
        self.hold_steering_mode = False

        # spline 샘플링
        self.samples_per_segment = 12

        default_route_export_path = self.resolve_route_export_path(
            'spline_detect_route.json'
        )
        self.declare_parameter('route_export_path', str(default_route_export_path))
        self.route_export_path = Path(str(self.get_parameter('route_export_path').value))
        self.route_export_seq = 0

        map_path = self.resolve_map_path()
        self.load_map(str(map_path))

    def normalize_angle(self, angle):
        while angle > math.pi:
            angle -= 2.0 * math.pi
        while angle < -math.pi:
            angle += 2.0 * math.pi
        return angle

    def clamp(self, value, min_value, max_value):
        return max(min_value, min(max_value, value))

    def move_toward(self, current, target, max_step):
        if target > current:
            return min(current + max_step, target)
        return max(current - max_step, target)

    def dist_xy(self, x1, z1, x2, z2):
        return math.sqrt((x1 - x2) ** 2 + (z1 - z2) ** 2)

    def resolve_map_path(self):
        candidates = []

        if get_package_share_directory is not None:
            try:
                candidates.append(
                    Path(get_package_share_directory('lane_follow_pkg')) / 'TopologicalMap.json'
                )
            except Exception:
                pass

        candidates.append(Path(__file__).resolve().parents[1] / 'TopologicalMap.json')

        for candidate in candidates:
            if candidate.exists():
                return candidate

        return candidates[-1]

    def resolve_route_export_path(self, filename):
        return Path('/tmp/lane_follow_pkg') / filename

    def goal_id_to_state_value(self, goal_id):
        if not goal_id:
            return 0
        try:
            return int(str(goal_id).split('_')[-1])
        except (TypeError, ValueError):
            return 0

    def build_route_snapshot(self, start_id, goal_id, state_value, path_ids, trajectory):
        path_waypoints = []
        for wp_id in path_ids:
            coords = self.waypoints.get(wp_id)
            if coords is None:
                continue
            path_waypoints.append({
                'id': wp_id,
                'x': float(coords['x']),
                'z': float(coords['z']),
            })

        trajectory_points = [
            {'index': idx, 'x': float(x), 'z': float(z)}
            for idx, (x, z) in enumerate(trajectory)
        ]

        start_wp = self.waypoints.get(start_id)
        goal_wp = self.waypoints.get(goal_id)

        bound_points = [(self.current_x, self.current_z)]
        if start_wp is not None:
            bound_points.append((float(start_wp['x']), float(start_wp['z'])))
        if goal_wp is not None:
            bound_points.append((float(goal_wp['x']), float(goal_wp['z'])))
        bound_points.extend((float(p['x']), float(p['z'])) for p in trajectory_points)

        xs = [p[0] for p in bound_points]
        zs = [p[1] for p in bound_points]

        return {
            'route_version': self.route_export_seq,
            'generated_at': datetime.now(timezone.utc).isoformat(),
            'source_node': self.get_name(),
            'planner_type': 'spline',
            'start_waypoint_id': start_id,
            'goal_waypoint_id': goal_id,
            'target_waypoint_value': int(state_value),
            'current_pose': {
                'x': float(self.current_x),
                'z': float(self.current_z),
                'yaw': float(self.current_yaw),
            },
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

    def export_route_snapshot(self, start_id, goal_id, state_value, path_ids, trajectory):
        try:
            self.route_export_seq += 1
            payload = self.build_route_snapshot(
                start_id,
                goal_id,
                state_value,
                path_ids,
                trajectory,
            )
            self.route_export_path.parent.mkdir(parents=True, exist_ok=True)
            tmp_path = self.route_export_path.parent / f'{self.route_export_path.name}.tmp'
            tmp_path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2),
                encoding='utf-8',
            )
            tmp_path.replace(self.route_export_path)
            self.get_logger().info(
                f'route json saved: {self.route_export_path} (version={self.route_export_seq})'
            )
        except Exception as e:
            self.get_logger().error(f'Route export failed: {e}')

    def load_map(self, filepath):
        try:
            with open(filepath, 'r') as f:
                data = json.load(f)

            for wp in data.get('waypoints', []):
                wp_id = wp['id']
                self.waypoints[wp_id] = {'x': wp['x'], 'z': wp['z']}
                self.edges[wp_id] = list(wp.get('connected_to', []))

            self.get_logger().info(
                f"맵 로드 완료: {len(self.waypoints)}개의 웨이포인트"
            )

        except Exception as e:
            self.get_logger().error(f"Map load failed: {e}")

    def publish_int_state(self, publisher, value):
        msg = Int32()
        msg.data = value
        publisher.publish(msg)

    def clear_navigation(self):
        self.path = []
        self.trajectory = []
        self.goal_wp_id = None
        self.clear_tracking_state()

    def stop_vehicle(self):
        self.cmd_pub.publish(Twist())

    def odom_callback(self, msg):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation

        self.current_z = pos.x
        self.current_x = -pos.y

        siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        yaw = math.atan2(siny_cosp, cosy_cosp)

        # 차량 전방축 보정
        self.current_yaw = self.normalize_angle(yaw + math.pi)

        self.has_odom = True

    def estop_callback(self, msg):
        self.estop_active = bool(msg.data)
        self.publish_int_state(self.pub_state_stop, int(self.estop_active))

        if self.estop_active:
            self.get_logger().warn("EMERGENCY STOP!")
            self.clear_navigation()
            self.stop_vehicle()
            self.publish_int_state(self.pub_state_waypoint, 0)
        else:
            self.get_logger().info("EMERGENCY STOP 해제")

    def calculate_distance_between_waypoints(self, id1, id2):
        x1, z1 = self.waypoints[id1]['x'], self.waypoints[id1]['z']
        x2, z2 = self.waypoints[id2]['x'], self.waypoints[id2]['z']
        return self.dist_xy(x1, z1, x2, z2)

    def get_heading_error_to_xy_deg(self, tx, tz):
        dx = tx - self.current_x
        dz = tz - self.current_z
        target_yaw = math.atan2(dx, dz)
        error = self.normalize_angle(target_yaw - self.current_yaw)
        return math.degrees(error)

    def get_candidate_waypoints(self, max_candidates=8):
        candidates = []
        for wp_id, coords in self.waypoints.items():
            dist = self.dist_xy(
                self.current_x, self.current_z,
                coords['x'], coords['z']
            )
            candidates.append((dist, wp_id))
        candidates.sort(key=lambda x: x[0])
        return candidates[:max_candidates]

    def choose_better_start_waypoint(self, goal_id):
        candidates = self.get_candidate_waypoints(max_candidates=8)

        best_id = None
        best_score = float('inf')

        for dist, wp_id in candidates:
            wx = self.waypoints[wp_id]['x']
            wz = self.waypoints[wp_id]['z']
            heading_err = abs(self.get_heading_error_to_xy_deg(wx, wz))
            goal_dist = self.calculate_distance_between_waypoints(wp_id, goal_id)

            behind_penalty = 1000.0 if heading_err > self.behind_angle_deg else 0.0
            score = dist + 0.3 * heading_err + 0.02 * goal_dist + behind_penalty

            if score < best_score:
                best_score = score
                best_id = wp_id

        return best_id

    def find_path_dijkstra(self, start_id, goal_id):
        distances = {wp: float('inf') for wp in self.waypoints}
        distances[start_id] = 0.0
        previous_nodes = {wp: None for wp in self.waypoints}
        pq = [(0.0, start_id)]

        while pq:
            current_dist, current_id = heapq.heappop(pq)

            if current_id == goal_id:
                break

            if current_dist > distances[current_id]:
                continue

            for neighbor in self.edges.get(current_id, []):
                weight = self.calculate_distance_between_waypoints(current_id, neighbor)
                new_dist = current_dist + weight

                if new_dist < distances[neighbor]:
                    distances[neighbor] = new_dist
                    previous_nodes[neighbor] = current_id
                    heapq.heappush(pq, (new_dist, neighbor))

        path = []
        curr = goal_id
        while curr is not None:
            path.append(curr)
            curr = previous_nodes[curr]
        path.reverse()

        if not path or path[0] != start_id:
            return []

        return path

    def waypoint_path_to_points(self, path_ids):
        pts = []
        for wp_id in path_ids:
            pts.append((self.waypoints[wp_id]['x'], self.waypoints[wp_id]['z']))
        return pts

    def catmull_rom(self, p0, p1, p2, p3, t):
        t2 = t * t
        t3 = t2 * t

        x = 0.5 * (
            (2.0 * p1[0]) +
            (-p0[0] + p2[0]) * t +
            (2.0 * p0[0] - 5.0 * p1[0] + 4.0 * p2[0] - p3[0]) * t2 +
            (-p0[0] + 3.0 * p1[0] - 3.0 * p2[0] + p3[0]) * t3
        )
        z = 0.5 * (
            (2.0 * p1[1]) +
            (-p0[1] + p2[1]) * t +
            (2.0 * p0[1] - 5.0 * p1[1] + 4.0 * p2[1] - p3[1]) * t2 +
            (-p0[1] + 3.0 * p1[1] - 3.0 * p2[1] + p3[1]) * t3
        )
        return (x, z)

    def build_spline_trajectory(self, path_ids):
        raw_pts = self.waypoint_path_to_points(path_ids)

        if len(raw_pts) == 0:
            return []

        if len(raw_pts) == 1:
            return [raw_pts[0]]

        if len(raw_pts) == 2:
            traj = []
            p1 = raw_pts[0]
            p2 = raw_pts[1]
            for i in range(self.samples_per_segment + 1):
                t = i / float(self.samples_per_segment)
                x = p1[0] + (p2[0] - p1[0]) * t
                z = p1[1] + (p2[1] - p1[1]) * t
                traj.append((x, z))
            return traj

        ext = [raw_pts[0]] + raw_pts + [raw_pts[-1]]

        traj = []
        for i in range(1, len(ext) - 2):
            p0 = ext[i - 1]
            p1 = ext[i]
            p2 = ext[i + 1]
            p3 = ext[i + 2]

            for j in range(self.samples_per_segment):
                t = j / float(self.samples_per_segment)
                traj.append(self.catmull_rom(p0, p1, p2, p3, t))

        traj.append(raw_pts[-1])

        filtered = [traj[0]]
        for p in traj[1:]:
            if self.dist_xy(filtered[-1][0], filtered[-1][1], p[0], p[1]) > 0.5:
                filtered.append(p)

        return filtered

    def plot_trajectory(self):
        try:
            import matplotlib.pyplot as plt
        except ImportError:
            self.get_logger().warn("matplotlib 미설치로 trajectory plot 생략")
            return

        if not self.trajectory:
            print("trajectory 없음")
            return

        traj_x = [p[0] for p in self.trajectory]
        traj_z = [p[1] for p in self.trajectory]

        wp_x = []
        wp_z = []
        for wp_id in self.path:
            wp_x.append(self.waypoints[wp_id]['x'])
            wp_z.append(self.waypoints[wp_id]['z'])

        plt.figure(figsize=(8, 8))
        plt.plot(traj_x, traj_z, label='trajectory')
        plt.scatter(wp_x, wp_z, label='waypoints')
        plt.scatter(self.current_x, self.current_z, s=100, label='vehicle')

        arrow_len = 5.0
        dx = arrow_len * math.sin(self.current_yaw)
        dz = arrow_len * math.cos(self.current_yaw)
        plt.arrow(self.current_x, self.current_z, dx, dz, head_width=1.0)

        plt.axis('equal')
        plt.grid(True)
        plt.legend()
        plt.title('Trajectory Visualization')
        plt.xlabel('X')
        plt.ylabel('Z')
        plt.show()

    def find_closest_trajectory_index(self):
        if not self.trajectory:
            return 0

        start_idx = max(0, self.closest_traj_idx - 5)
        end_idx = min(len(self.trajectory), self.closest_traj_idx + 80)

        best_idx = start_idx
        best_dist = float('inf')

        for i in range(start_idx, end_idx):
            tx, tz = self.trajectory[i]
            d = self.dist_xy(self.current_x, self.current_z, tx, tz)
            if d < best_dist:
                best_dist = d
                best_idx = i

        return best_idx

    def find_lookahead_index(self, start_idx):
        if not self.trajectory:
            return 0

        acc = 0.0
        prev_x, prev_z = self.trajectory[start_idx]

        for i in range(start_idx + 1, len(self.trajectory)):
            x, z = self.trajectory[i]
            acc += self.dist_xy(prev_x, prev_z, x, z)
            if acc >= self.lookahead_dist:
                return i
            prev_x, prev_z = x, z

        return len(self.trajectory) - 1

    def get_goal_distance(self):
        if self.goal_wp_id is None:
            return float('inf')
        gx = self.waypoints[self.goal_wp_id]['x']
        gz = self.waypoints[self.goal_wp_id]['z']
        return self.dist_xy(self.current_x, self.current_z, gx, gz)

    def clear_tracking_state(self):
        self.prev_error = 0.0
        self.prev_time = None
        self.closest_traj_idx = 0
        self.last_stable_angular = 0.0
        self.hold_steering_mode = False
        self.angular_cmd = 0.0

    def distance_point_to_segment(self, px, pz, ax, az, bx, bz):
        abx = bx - ax
        abz = bz - az
        apx = px - ax
        apz = pz - az

        ab_len_sq = abx * abx + abz * abz
        if ab_len_sq < 1e-9:
            return self.dist_xy(px, pz, ax, az), 0.0

        t = (apx * abx + apz * abz) / ab_len_sq
        t = self.clamp(t, 0.0, 1.0)

        cx = ax + t * abx
        cz = az + t * abz

        dist = self.dist_xy(px, pz, cx, cz)
        return dist, t

    def get_cross_track_error(self, traj_idx):
        if not self.trajectory:
            return float('inf')

        if len(self.trajectory) == 1:
            tx, tz = self.trajectory[0]
            return self.dist_xy(self.current_x, self.current_z, tx, tz)

        idx0 = max(0, traj_idx - 1)
        idx1 = min(len(self.trajectory) - 1, traj_idx + 1)

        best = float('inf')

        for i in range(idx0, idx1):
            ax, az = self.trajectory[i]
            bx, bz = self.trajectory[i + 1]
            d, _ = self.distance_point_to_segment(
                self.current_x, self.current_z, ax, az, bx, bz
            )
            if d < best:
                best = d

        return best

    def target_waypoint_callback(self, msg):
        goal_id = f"Waypoint_{msg.data}"

        if self.estop_active:
            self.get_logger().warn("E-STOP 상태에서는 목표 waypoint 명령을 무시합니다.")
            return

        if goal_id not in self.waypoints:
            self.get_logger().error(f"존재하지 않는 웨이포인트: {goal_id}")
            return

        if not self.has_odom:
            self.get_logger().info("Odom 수신 대기 중...")
            return

        if self.goal_wp_id == goal_id and self.trajectory:
            self.get_logger().info(f"이미 {goal_id}로 주행 중입니다.")
            return

        if self.goal_wp_id is not None and self.trajectory:
            self.get_logger().info(
                f"기존 목표 {self.goal_wp_id}에서 새 목표 {goal_id}로 경로를 재계산합니다."
            )
        else:
            self.get_logger().info(f"목표 구역 수신: {msg.data}")

        start_id = self.choose_better_start_waypoint(goal_id)
        if start_id is None:
            self.get_logger().error("시작 웨이포인트를 찾지 못했습니다.")
            return

        self.get_logger().info(f"선택된 시작 waypoint: {start_id}")

        path = self.find_path_dijkstra(start_id, goal_id)
        if not path:
            self.get_logger().error("경로 생성 실패")
            return

        if len(path) >= 2 and path[0] == start_id:
            path.pop(0)

        if not path:
            path = [goal_id]

        trajectory = self.build_spline_trajectory(path)
        if not trajectory:
            self.get_logger().error("trajectory 생성 실패")
            return

        self.path = path
        self.goal_wp_id = goal_id
        self.trajectory = trajectory
        self.clear_tracking_state()
        self.publish_int_state(self.pub_state_waypoint, msg.data)
        self.export_route_snapshot(start_id, goal_id, msg.data, self.path, self.trajectory)

        self.get_logger().info(f"생성된 waypoint 경로: {' -> '.join(self.path)}")
        self.get_logger().info(f"trajectory point 수: {len(self.trajectory)}")

    def control_loop(self):
        if not self.has_odom:
            return

        twist = Twist()

        if self.estop_active:
            self.cmd_pub.publish(twist)
            return

        if not self.trajectory:
            self.cmd_pub.publish(twist)
            return

        goal_dist = self.get_goal_distance()
        if goal_dist < self.goal_tolerance:
            self.get_logger().info("최종 목적지 도착!")
            self.clear_navigation()
            self.publish_int_state(self.pub_state_waypoint, 0)
            self.stop_vehicle()
            return

        self.closest_traj_idx = self.find_closest_trajectory_index()
        lookahead_idx = self.find_lookahead_index(self.closest_traj_idx)

        tx, tz = self.trajectory[lookahead_idx]

        end_x, end_z = self.trajectory[-1]
        end_dist = self.dist_xy(self.current_x, self.current_z, end_x, end_z)
        if lookahead_idx >= len(self.trajectory) - 1 and end_dist < self.traj_reach_tolerance:
            self.get_logger().info("trajectory 끝점 도달")
            self.clear_navigation()
            self.publish_int_state(self.pub_state_waypoint, 0)
            self.stop_vehicle()
            return

        dx = tx - self.current_x
        dz = tz - self.current_z

        target_yaw = math.atan2(dx, dz)
        error = self.normalize_angle(target_yaw - self.current_yaw)

        now = time.time()
        if self.prev_time is None:
            dt = 0.05
        else:
            dt = max(0.001, now - self.prev_time)

        error_deg = math.degrees(error)
        error_deg_abs = abs(error_deg)

        if error_deg_abs < self.deadband_deg:
            error_for_control = 0.0
        else:
            error_for_control = error

        cross_track_error = self.get_cross_track_error(self.closest_traj_idx)

        straight_mode = (
            cross_track_error <= self.straight_cte and
            error_deg_abs <= self.straight_error_deg
        )

        if self.hold_steering_mode:
            if cross_track_error > self.release_corridor_radius or error_deg_abs > self.heading_hold_deg * 1.5:
                self.hold_steering_mode = False
        else:
            if cross_track_error <= self.corridor_radius and error_deg_abs <= self.heading_hold_deg:
                self.hold_steering_mode = True

        if straight_mode:
            desired_angular = 0.0
            p_term = 0.0
            d_term = 0.0
            self.prev_error = 0.0
            self.last_stable_angular = 0.0
            self.hold_steering_mode = False
        elif self.hold_steering_mode:
            desired_angular = self.last_stable_angular * self.hold_decay
            if abs(desired_angular) < 0.02:
                desired_angular = 0.0
            p_term = 0.0
            d_term = 0.0
        else:
            d_error = (error_for_control - self.prev_error) / dt

            p_term = self.kp * error_for_control
            d_term = self.kd * d_error
            d_term = self.clamp(d_term, -self.max_d_term, self.max_d_term)

            desired_angular = p_term + d_term
            desired_angular = self.clamp(desired_angular, -self.max_angular, self.max_angular)

            self.last_stable_angular = desired_angular

        self.angular_cmd = self.move_toward(
            self.angular_cmd,
            desired_angular,
            self.max_angular_step
        )
        angular = self.angular_cmd

        twist.linear.x = self.cruise_speed

        # 회전 반대 문제 수정: 부호 반전 제거
        twist.angular.z = angular

        self.cmd_pub.publish(twist)

        self.prev_error = error_for_control
        self.prev_time = now

        current_time = time.time()
        if current_time - self.last_log_time >= 1.0:
            self.get_logger().info(
                f"traj_idx: {self.closest_traj_idx}/{len(self.trajectory)-1} | "
                f"lookahead_idx: {lookahead_idx} | "
                f"goal_dist: {goal_dist:.1f} | "
                f"cte: {cross_track_error:.2f} | "
                f"yaw: {math.degrees(self.current_yaw):.2f}deg | "
                f"target_yaw: {math.degrees(target_yaw):.2f}deg | "
                f"error: {error_deg:.2f}deg | "
                f"straight: {straight_mode} | "
                f"hold: {self.hold_steering_mode} | "
                f"p: {p_term:.3f} | "
                f"d: {d_term:.3f} | "
                f"desired: {desired_angular:.3f} | "
                f"w: {twist.angular.z:.3f} | "
                f"v: {twist.linear.x:.3f}"
            )
            self.last_log_time = current_time


def main(args=None):
    rclpy.init(args=args)
    node = DijkstraSplineFollower()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.cmd_pub.publish(Twist())
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()