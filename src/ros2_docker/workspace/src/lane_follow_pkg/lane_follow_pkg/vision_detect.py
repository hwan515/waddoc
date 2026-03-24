import json
import math
import time
import heapq
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np

import rclpy
from rclpy.node import Node
from geometry_msgs.msg import Twist
from nav_msgs.msg import Odometry
from sensor_msgs.msg import Image
from std_msgs.msg import Bool, Int32, String

try:
    from ament_index_python.packages import get_package_share_directory
except ImportError:
    get_package_share_directory = None
from cv_bridge import CvBridge

try:
    from ultralytics import YOLO
except ImportError:
    YOLO = None


class HybridDijkstraVisionFollower(Node):
    def __init__(self):
        super().__init__('hybrid_dijkstra_vision_follower')

        self.bridge = CvBridge()

        self.odom_sub = self.create_subscription(
            Odometry, '/odom', self.odom_callback, 10
        )
        self.image_sub = self.create_subscription(
            Image, '/camera/image_raw', self.image_callback, 10
        )
        self.estop_sub = self.create_subscription(
            Bool, '/ec2_cmd/e_stop', self.estop_callback, 1
        )
        self.target_sub = self.create_subscription(
            Int32, '/ec2_cmd/target_waypoint', self.target_waypoint_callback, 1
        )

        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', 10)
        self.estop_pub = self.create_publisher(Bool, '/ec2_cmd/e_stop', 1)
        self.pub_state_stop = self.create_publisher(Int32, '/cmd_state/e_stop', 1)
        self.pub_state_waypoint = self.create_publisher(
            Int32, '/cmd_state/target_waypoint', 1
        )
        self.pub_minimap_route = self.create_publisher(
            String, '/ec2_state/minimap_route', 1
        )
        self.timer = self.create_timer(0.05, self.control_loop)

        map_path = self.resolve_map_path()
        default_yolo_model_path = self.resolve_default_yolo_model_path()
        self.declare_parameter('waypoint_json_path', str(map_path))
        self.declare_parameter('goal_waypoint_id', '')
        self.declare_parameter('show_debug_windows', False)
        default_route_export_path = self.resolve_route_export_path(
            'vision_detect_route.json'
        )
        self.declare_parameter('route_export_path', str(default_route_export_path))
        self.declare_parameter('enable_yolo_estop', True)
        self.declare_parameter('yolo_model', str(default_yolo_model_path))
        self.declare_parameter('yolo_device', '')
        self.declare_parameter('yolo_confidence', 0.80)
        self.declare_parameter('yolo_input_size', 640)
        self.declare_parameter(
            'yolo_hazard_labels',
            [
                'person',
                'tractor',
                'cultivator',
                'power tiller',
                'walking tractor',
                'agricultural tractor',
                '경운기',
                '트랙터',
            ],
        )

        self.waypoint_json_path = str(self.get_parameter('waypoint_json_path').value)
        configured_goal = str(self.get_parameter('goal_waypoint_id').value).strip()
        self.goal_wp_id = configured_goal or None
        self.pending_goal_id = self.goal_wp_id
        self.pending_goal_state_value = self.goal_id_to_state_value(self.goal_wp_id)
        self.show_debug_windows = bool(self.get_parameter('show_debug_windows').value)
        self.cv_windows_enabled = self.show_debug_windows
        if not self.show_debug_windows:
            self.get_logger().info(
                'OpenCV debug windows are disabled. Use show_debug_windows:=true only when X11 access is available.'
            )
        self.route_export_path = Path(str(self.get_parameter('route_export_path').value))
        self.route_export_seq = 0
        self.minimap_live_publish_interval = 0.1
        self.last_minimap_publish_time = 0.0
        self.minimap_last_error_log_time = 0.0
        self.enable_yolo_estop = bool(self.get_parameter('enable_yolo_estop').value)
        self.yolo_model_name = str(self.get_parameter('yolo_model').value).strip()
        self.yolo_device = str(self.get_parameter('yolo_device').value).strip()
        self.yolo_confidence = float(self.get_parameter('yolo_confidence').value)
        self.yolo_input_size = max(320, int(self.get_parameter('yolo_input_size').value))
        raw_hazard_labels = self.get_parameter('yolo_hazard_labels').value
        if isinstance(raw_hazard_labels, str):
            raw_hazard_labels = [raw_hazard_labels]
        self.yolo_hazard_labels = {
            self.normalize_detection_label(label)
            for label in raw_hazard_labels
            if str(label).strip()
        }
        if not self.yolo_hazard_labels:
            self.yolo_hazard_labels = {'person'}

        # =========================
        # odom
        # =========================
        self.current_x = 0.0
        self.current_z = 0.0
        self.current_yaw = 0.0
        self.current_speed_ms = 0.0
        self.current_speed_kmh = 0.0
        self.has_odom = False
        self.estop_active = False

        # =========================
        # map / path / trajectory
        # =========================
        self.waypoints = {}
        self.edges = {}

        self.path = []
        self.trajectory = []
        self.closest_traj_idx = 0
        self.active_route_start_id = None
        self.active_route_state_value = None

        self.last_log_time = 0.0

        # =========================
        # waypoint 추종 파라미터
        # =========================
        self.cruise_speed = 0.5

        self.kp = 0.25
        self.kd = 0.03

        self.max_angular = 0.15
        self.max_d_term = 0.03
        self.deadband_deg = 2.0

        self.prev_error = 0.0
        self.prev_time = None

        self.angular_cmd = 0.0
        self.max_angular_step = 0.015
        self.straight_error_deg = 5.0
        self.straight_cte = 0.8
        self.hold_decay = 0.75

        self.lookahead_dist = 10.0
        self.goal_tolerance = 5.0
        self.traj_reach_tolerance = 3.0
        self.behind_angle_deg = 100.0

        self.corridor_radius = 2.5
        self.heading_hold_deg = 6.0
        self.release_corridor_radius = 4.5
        self.last_stable_angular = 0.0
        self.hold_steering_mode = False

        self.samples_per_segment = 12

        # =========================
        # vision 상태
        # =========================
        self.has_image = False
        self.vision_has_target = False
        self.latest_vision_twist = Twist()

        self.vision_mask_pixels = 0
        self.vision_near_center = None
        self.vision_mid_center = None
        self.vision_top_center = None
        self.vision_target_center = None
        self.vision_turn_hint = 0.0
        self.vision_corner_mode = False
        self.vision_mean_road_width = None
        self.vision_image_center = None
        self.vision_lane_error_px = None
        self.vision_roi_width = None
        self.yolo_last_detections = []
        self.yolo_last_reason = ''

        # =========================
        # vision 파라미터
        # =========================
        self.k_pos = 0.014
        self.kd_vision = 0.0025
        self.max_angular_vision = 1.8

        self.base_speed = 1.0
        self.min_speed = 0.50

        self.prev_vision_error = 0.0
        self.prev_target = None

        self.roi_start_ratio = 0.13
        self.roi_end_ratio = 0.93
        self.roi_side_margin_ratio = 0.05
        self.cut_bottom_ratio = 0.08

        self.scanline_ratios = np.linspace(0.22, 0.88, 18).tolist()
        self.scanline_weights = []
        for r in self.scanline_ratios:
            if r < 0.32:
                self.scanline_weights.append(0.45)
            elif r < 0.45:
                self.scanline_weights.append(0.80)
            elif r < 0.60:
                self.scanline_weights.append(1.30)
            elif r < 0.75:
                self.scanline_weights.append(2.00)
            else:
                self.scanline_weights.append(2.80)

        self.lower_hsv_1 = np.array([8, 120, 90], dtype=np.uint8)
        self.upper_hsv_1 = np.array([20, 255, 190], dtype=np.uint8)

        self.lower_hsv_2 = np.array([4, 40, 120], dtype=np.uint8)
        self.upper_hsv_2 = np.array([28, 170, 255], dtype=np.uint8)

        self.kernel = np.ones((5, 5), np.uint8)
        self.estimated_road_width_px = 290

        # =========================
        # waypoint + vision 통합 파라미터
        # =========================
        self.vision_mask_min_pixels = 1800
        self.vision_lane_k = 0.0012
        self.vision_lane_max = 0.06
        self.vision_mix_weight = 0.45
        self.vision_recovery_error_px = 95.0
        self.vision_soft_error_px = 55.0
        self.recovery_speed = 0.26

        # =========================
        # YOLO ROI e-stop
        # =========================
        self.yolo_model = None
        self.yolo_ready = False
        self.yolo_model_reference = ''
        self.yolo_last_error_log_time = 0.0
        self.estop_reason = ''

        self.current_mode = 'waiting_goal'

        self.load_map(self.waypoint_json_path)
        self.initialize_yolo_detector()

        self.get_logger().info(
            f'Hybrid follower started. json={self.waypoint_json_path}, '
            f'goal={self.goal_wp_id}, wp_count={len(self.waypoints)}'
        )

    # =====================================================
    # util
    # =====================================================
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

    def weighted_average(self, pairs):
        if len(pairs) == 0:
            return None
        denom = sum(w for _, w in pairs)
        if denom < 1e-9:
            return None
        return sum(v * w for v, w in pairs) / denom

    def speed_ms_to_kmh(self, speed_ms):
        return float(speed_ms) * 3.6

    def build_speed_snapshot(self):
        return {
            'speed': float(self.current_speed_ms),
            'speedMs': float(self.current_speed_ms),
            'speedKmh': float(self.current_speed_kmh),
        }

    def publish_int_state(self, publisher, value):
        msg = Int32()
        msg.data = int(value)
        publisher.publish(msg)

    def normalize_detection_label(self, label):
        return ' '.join(
            str(label).strip().lower().replace('_', ' ').replace('-', ' ').split()
        )

    def resolve_route_export_path(self, filename):
        return Path('/tmp/lane_follow_pkg') / filename

    def resolve_default_yolo_model_path(self):
        candidates = [
            Path('/root/workspace/models/yolov8n.pt'),
            Path.cwd() / 'models' / 'yolov8n.pt',
        ]

        for candidate in candidates:
            if candidate.exists():
                return candidate

        return candidates[0]

    def resolve_model_reference(self, model_value):
        model_value = str(model_value).strip()
        if not model_value:
            return ''

        candidate = Path(model_value).expanduser()
        candidates = []

        if candidate.is_absolute():
            candidates.append(candidate)
        else:
            candidates.append(Path.cwd() / candidate)
            candidates.append(Path(__file__).resolve().parents[1] / candidate)

            if get_package_share_directory is not None:
                try:
                    candidates.append(
                        Path(get_package_share_directory('lane_follow_pkg')) / candidate
                    )
                except Exception:
                    pass

        for path in candidates:
            if path.exists():
                return str(path)

        return model_value

    def publish_minimap_route_payload(self, payload):
        msg = String()
        msg.data = json.dumps(payload, ensure_ascii=False)
        self.pub_minimap_route.publish(msg)

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
            self.get_logger().error(f'Minimap export failed: {error}')
            self.minimap_last_error_log_time = now

    def build_empty_route_snapshot(self, reason='navigation_cleared'):
        goal_wp = self.waypoints.get(self.goal_wp_id)

        return {
            'route_version': self.route_export_seq,
            'generated_at': datetime.now(timezone.utc).isoformat(),
            'source_node': self.get_name(),
            'planner_type': 'vision+spline',
            'start_waypoint_id': None,
            'goal_waypoint_id': self.goal_wp_id,
            'target_waypoint_value': 0,
            'current_pose': {
                'x': float(self.current_x),
                'z': float(self.current_z),
                'yaw': float(self.current_yaw),
                'speed_ms': float(self.current_speed_ms),
                'speed_kmh': float(self.current_speed_kmh),
            },
            **self.build_speed_snapshot(),
            'start_waypoint': None,
            'goal_waypoint': None if goal_wp is None else {
                'id': self.goal_wp_id,
                'x': float(goal_wp['x']),
                'z': float(goal_wp['z']),
            },
            'path_waypoint_ids': [],
            'path_waypoints': [],
            'trajectory_point_count': 0,
            'trajectory': [],
            'bounds': {
                'min_x': float(self.current_x),
                'max_x': float(self.current_x),
                'min_z': float(self.current_z),
                'max_z': float(self.current_z),
                'width': 0.0,
                'height': 0.0,
            },
            'cleared': True,
            'clear_reason': reason,
        }

    def publish_cleared_minimap_route(self, reason='navigation_cleared'):
        try:
            self.route_export_seq += 1
            payload = self.build_empty_route_snapshot(reason=reason)
            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = time.time()
        except Exception as e:
            self.log_minimap_export_error(e)

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
            'planner_type': 'vision+spline',
            'start_waypoint_id': start_id,
            'goal_waypoint_id': goal_id,
            'target_waypoint_value': int(state_value),
            'current_pose': {
                'x': float(self.current_x),
                'z': float(self.current_z),
                'yaw': float(self.current_yaw),
                'speed_ms': float(self.current_speed_ms),
                'speed_kmh': float(self.current_speed_kmh),
            },
            **self.build_speed_snapshot(),
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
            self.active_route_start_id = start_id
            self.active_route_state_value = int(state_value)
            payload = self.build_route_snapshot(
                start_id,
                goal_id,
                state_value,
                path_ids,
                trajectory,
            )
            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = time.time()
            self.get_logger().info(
                f'route json saved: {self.route_export_path} (version={self.route_export_seq})'
            )
        except Exception as e:
            self.log_minimap_export_error(e)

    def publish_live_minimap_snapshot(self, force=False):
        now = time.time()
        if not force and (now - self.last_minimap_publish_time) < self.minimap_live_publish_interval:
            return

        try:
            if self.trajectory and self.goal_wp_id is not None:
                state_value = (
                    self.active_route_state_value
                    if self.active_route_state_value is not None
                    else self.goal_id_to_state_value(self.goal_wp_id)
                )
                payload = self.build_route_snapshot(
                    self.active_route_start_id,
                    self.goal_wp_id,
                    state_value,
                    self.path,
                    self.trajectory,
                )
            else:
                payload = self.build_empty_route_snapshot(reason=self.current_mode)

            self.persist_minimap_route_payload(payload)
            self.publish_minimap_route_payload(payload)
            self.last_minimap_publish_time = now
        except Exception as e:
            self.log_minimap_export_error(e)

    def clear_pending_goal(self):
        self.pending_goal_id = None
        self.pending_goal_state_value = None

    def clear_navigation(self, clear_goal=False, clear_pending=False, clear_reason='navigation_cleared'):
        self.path = []
        self.trajectory = []
        self.active_route_start_id = None
        self.active_route_state_value = None
        self.clear_tracking_state()
        if clear_goal:
            self.goal_wp_id = None
        if clear_pending:
            self.clear_pending_goal()
        self.publish_cleared_minimap_route(reason=clear_reason)

    def stop_vehicle(self):
        self.cmd_pub.publish(Twist())

    def goal_id_to_state_value(self, goal_id):
        if not goal_id:
            return 0
        try:
            return int(str(goal_id).split('_')[-1])
        except (TypeError, ValueError):
            return 0

    def show_window(self, name, image):
        if not self.cv_windows_enabled:
            return
        try:
            cv2.imshow(name, image)
        except cv2.error as e:
            self.cv_windows_enabled = False
            self.get_logger().warn(f'OpenCV GUI unavailable. Debug window disabled: {e}')

    def pump_debug_windows(self):
        if not self.cv_windows_enabled:
            return
        try:
            cv2.waitKey(1)
        except cv2.error as e:
            self.cv_windows_enabled = False
            self.get_logger().warn(f'OpenCV waitKey unavailable. Debug window disabled: {e}')

    def initialize_yolo_detector(self):
        if not self.enable_yolo_estop:
            self.get_logger().info('YOLO ROI e-stop disabled by parameter.')
            return

        if YOLO is None:
            self.get_logger().warn(
                'ultralytics 패키지가 없어 YOLO ROI e-stop을 비활성화합니다. '
                '컨테이너에서 pip3 install ultralytics 를 실행하거나 이미지를 다시 빌드하세요.'
            )
            return

        if not self.yolo_model_name:
            self.get_logger().warn(
                'yolo_model 파라미터가 비어 있어 YOLO ROI e-stop을 비활성화합니다.'
            )
            return

        self.yolo_model_reference = self.resolve_model_reference(self.yolo_model_name)

        try:
            model_ref_path = Path(self.yolo_model_reference).expanduser()
            if model_ref_path.suffix == '.pt':
                model_ref_path.parent.mkdir(parents=True, exist_ok=True)
        except Exception:
            pass

        try:
            self.yolo_model = YOLO(self.yolo_model_reference)
            self.yolo_ready = True
            hazard_labels = ', '.join(sorted(self.yolo_hazard_labels))
            device_name = self.yolo_device if self.yolo_device else 'auto'
            self.get_logger().info(
                'YOLO ROI e-stop 활성화: '
                f'model={self.yolo_model_reference}, '
                f'device={device_name}, '
                f'conf={self.yolo_confidence:.2f}, '
                f'hazards=[{hazard_labels}]'
            )
        except Exception as e:
            self.yolo_model = None
            self.yolo_ready = False
            self.get_logger().error(
                f'YOLO 모델 로드 실패 ({self.yolo_model_reference}): {e}'
            )

    def label_matches_hazard(self, label):
        normalized = self.normalize_detection_label(label)
        if not normalized:
            return False

        for hazard_label in self.yolo_hazard_labels:
            if (
                normalized == hazard_label
                or hazard_label in normalized
                or normalized in hazard_label
            ):
                return True
        return False

    def run_yolo_hazard_detection(self, frame, roi_rect):
        self.yolo_last_detections = []

        if not self.enable_yolo_estop or not self.yolo_ready or self.yolo_model is None:
            return []

        predict_kwargs = {
            'source': frame,
            'conf': self.yolo_confidence,
            'imgsz': self.yolo_input_size,
            'verbose': False,
        }
        if self.yolo_device:
            predict_kwargs['device'] = self.yolo_device

        try:
            results = self.yolo_model.predict(**predict_kwargs)
        except Exception as e:
            now = time.time()
            if now - self.yolo_last_error_log_time >= 5.0:
                self.get_logger().error(f'YOLO 추론 실패: {e}')
                self.yolo_last_error_log_time = now
            return []

        if not results:
            return []

        result = results[0]
        boxes = getattr(result, 'boxes', None)
        if boxes is None:
            return []

        names = getattr(result, 'names', {})
        rx0, ry0, rx1, ry1 = roi_rect

        for box in boxes:
            cls_idx = int(box.cls[0].item()) if box.cls is not None else -1
            if isinstance(names, dict):
                label = str(names.get(cls_idx, cls_idx))
            elif isinstance(names, (list, tuple)) and 0 <= cls_idx < len(names):
                label = str(names[cls_idx])
            else:
                label = str(cls_idx)

            if not self.label_matches_hazard(label):
                continue

            confidence = float(box.conf[0].item()) if box.conf is not None else 0.0
            bx0, by0, bx1, by1 = [int(v) for v in box.xyxy[0].tolist()]

            ix0 = max(bx0, rx0)
            iy0 = max(by0, ry0)
            ix1 = min(bx1, rx1)
            iy1 = min(by1, ry1)
            overlap_area = max(0, ix1 - ix0) * max(0, iy1 - iy0)

            if overlap_area <= 0:
                continue

            self.yolo_last_detections.append({
                'label': label,
                'confidence': confidence,
                'bbox': (bx0, by0, bx1, by1),
                'overlap_bbox': (ix0, iy0, ix1, iy1),
            })

        return list(self.yolo_last_detections)

    def draw_yolo_detections(self, frame_vis, detections):
        for detection in detections:
            bx0, by0, bx1, by1 = detection['bbox']
            ix0, iy0, ix1, iy1 = detection['overlap_bbox']
            label = detection['label']
            confidence = detection['confidence']

            cv2.rectangle(frame_vis, (bx0, by0), (bx1, by1), (0, 0, 255), 2)
            cv2.rectangle(frame_vis, (ix0, iy0), (ix1, iy1), (0, 255, 255), 2)
            cv2.putText(
                frame_vis,
                f'{label} {confidence:.2f}',
                (bx0, max(24, by0 - 8)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (0, 0, 255),
                2,
            )

    def reset_vision_tracking(self):
        self.prev_vision_error = 0.0
        self.prev_target = None
        self.latest_vision_twist = Twist()
        self.vision_has_target = False
        self.vision_lane_error_px = None

    def set_estop_state(self, active, reason=None):
        active = bool(active)
        was_active = self.estop_active
        self.estop_active = active
        self.publish_int_state(self.pub_state_stop, int(self.estop_active))

        if self.estop_active:
            if reason:
                self.estop_reason = reason
            elif not self.estop_reason:
                self.estop_reason = 'EMERGENCY STOP!'

            if not was_active:
                self.get_logger().warn(self.estop_reason)
                self.clear_navigation(
                    clear_goal=True,
                    clear_pending=True,
                    clear_reason='estop',
                )
                self.reset_vision_tracking()
                self.publish_int_state(self.pub_state_waypoint, 0)

            self.stop_vehicle()
            self.current_mode = 'estop'
            return

        if was_active:
            self.get_logger().info('EMERGENCY STOP 해제')

        self.estop_reason = ''
        self.yolo_last_reason = ''

    def trigger_yolo_estop(self, detections):
        if self.estop_active or not detections:
            return

        labels = ', '.join(
            f"{d['label']}({d['confidence']:.2f})" for d in detections[:3]
        )
        if len(detections) > 3:
            labels += ', ...'

        self.yolo_last_reason = f'YOLO ROI hazard detected: {labels}'
        self.set_estop_state(True, reason=self.yolo_last_reason)

        estop_msg = Bool()
        estop_msg.data = True
        self.estop_pub.publish(estop_msg)

    # =====================================================
    # map / path
    # =====================================================
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

    def load_map(self, filepath):
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)

            for wp in data.get('waypoints', []):
                wp_id = wp['id']
                self.waypoints[wp_id] = {
                    'x': float(wp['x']),
                    'z': float(wp['z'])
                }
                self.edges[wp_id] = list(wp.get('connected_to', []))

            self.get_logger().info(
                f"맵 로드 완료: {len(self.waypoints)}개의 웨이포인트"
            )

        except Exception as e:
            self.get_logger().error(f"Map load failed: {e}")

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
                if neighbor not in self.waypoints:
                    continue
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

    def plan_path_to_goal(self, goal_id, state_value=None):
        if len(self.waypoints) == 0:
            self.get_logger().error('맵 정보가 비어 있어 경로를 생성할 수 없습니다.')
            if self.pending_goal_id == goal_id:
                self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        if goal_id not in self.waypoints:
            self.get_logger().error(f'존재하지 않는 goal waypoint: {goal_id}')
            if self.pending_goal_id == goal_id:
                self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        if self.estop_active:
            self.get_logger().warn('E-STOP 상태에서는 목표 waypoint 명령을 무시합니다.')
            return False

        if not self.has_odom:
            self.pending_goal_id = goal_id
            self.pending_goal_state_value = state_value
            self.get_logger().info('Odom 수신 대기 중... 목표를 보류합니다.')
            return False

        if self.goal_wp_id == goal_id and self.trajectory:
            self.get_logger().info(f'이미 {goal_id}로 주행 중입니다.')
            state_value = state_value if state_value is not None else self.goal_id_to_state_value(goal_id)
            self.publish_int_state(self.pub_state_waypoint, state_value)
            self.clear_pending_goal()
            return True

        if self.goal_wp_id is not None and self.trajectory:
            self.get_logger().info(
                f'기존 목표 {self.goal_wp_id}에서 새 목표 {goal_id}로 경로를 재계산합니다.'
            )
        else:
            log_value = state_value if state_value is not None else goal_id
            self.get_logger().info(f'목표 구역 수신: {log_value}')

        start_id = self.choose_better_start_waypoint(goal_id)
        if start_id is None:
            self.get_logger().error('시작 waypoint 선택 실패')
            self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        path = self.find_path_dijkstra(start_id, goal_id)
        if not path:
            self.get_logger().error('경로 생성 실패')
            self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        if len(path) >= 2 and path[0] == start_id:
            path.pop(0)

        if not path:
            path = [goal_id]

        trajectory = self.build_spline_trajectory(path)
        if not trajectory:
            self.get_logger().error('trajectory 생성 실패')
            self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        self.path = path
        self.trajectory = trajectory
        self.goal_wp_id = goal_id
        self.clear_tracking_state()

        state_value = state_value if state_value is not None else self.goal_id_to_state_value(goal_id)
        self.publish_int_state(self.pub_state_waypoint, state_value)
        self.export_route_snapshot(start_id, goal_id, state_value, self.path, self.trajectory)
        self.clear_pending_goal()

        self.get_logger().info(f'생성된 waypoint 경로: {" -> ".join(self.path)}')
        self.get_logger().info(f'trajectory point 수: {len(self.trajectory)}')
        return True

    def try_activate_pending_goal(self):
        if self.pending_goal_id is None:
            return False
        return self.plan_path_to_goal(
            self.pending_goal_id,
            self.pending_goal_state_value,
        )

    # =====================================================
    # odom 
    # =====================================================
    def odom_callback(self, msg):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation
        linear = msg.twist.twist.linear

        self.current_z = pos.x
        self.current_x = -pos.y

        planar_speed_ms = math.sqrt(
            (linear.x * linear.x) +
            (linear.y * linear.y) +
            (linear.z * linear.z)
        )
        self.current_speed_ms = float(planar_speed_ms)
        self.current_speed_kmh = self.speed_ms_to_kmh(self.current_speed_ms)

        siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        yaw = math.atan2(siny_cosp, cosy_cosp)

        self.current_yaw = self.normalize_angle(yaw + math.pi)

        self.has_odom = True
        self.publish_live_minimap_snapshot()

    def estop_callback(self, msg):
        self.set_estop_state(bool(msg.data))

    def target_waypoint_callback(self, msg):
        if self.estop_active:
            self.get_logger().warn('E-STOP 상태에서는 목표 waypoint 명령을 무시합니다.')
            return

        goal_id = f'Waypoint_{msg.data}'
        if goal_id not in self.waypoints:
            self.get_logger().error(f'존재하지 않는 goal waypoint: {goal_id}')
            return

        self.pending_goal_id = goal_id
        self.pending_goal_state_value = int(msg.data)

        if not self.has_odom:
            self.get_logger().info('Odom 수신 대기 중... 목표를 보류합니다.')
            return

        self.try_activate_pending_goal()

    # =====================================================
    # trajectory helper
    # =====================================================
    def get_goal_distance(self):
        if self.goal_wp_id is None or self.goal_wp_id not in self.waypoints:
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

    # =====================================================
    # vision
    # =====================================================
    def compute_vision_twist_from_target(self, target_center, image_center):
        twist = Twist()

        if target_center is None:
            return twist, False

        pos_error = float(target_center - image_center)
        derivative = pos_error - self.prev_vision_error

        control = self.k_pos * pos_error + self.kd_vision * derivative
        control = float(np.clip(control, -self.max_angular_vision, self.max_angular_vision))

        self.prev_vision_error = pos_error

        speed = self.base_speed
        if abs(pos_error) > 20:
            speed = 0.85
        if abs(pos_error) > 40:
            speed = 0.65
        if abs(pos_error) > 60:
            speed = 0.48
        if abs(pos_error) > 90:
            speed = 0.35

        speed = max(speed, self.min_speed)

        twist.linear.x = float(speed)
        twist.angular.z = float(-control)
        return twist, True

    def image_callback(self, msg):
        self.has_image = True

        frame = self.bridge.imgmsg_to_cv2(msg, desired_encoding='bgr8')
        height, width = frame.shape[:2]

        y0 = int(height * self.roi_start_ratio)
        y1 = int(height * self.roi_end_ratio)
        x0 = int(width * self.roi_side_margin_ratio)
        x1 = int(width * (1.0 - self.roi_side_margin_ratio))

        roi = frame[y0:y1, x0:x1].copy()
        roi_h, roi_w = roi.shape[:2]

        cut_bottom = int(roi_h * self.cut_bottom_ratio)
        if cut_bottom > 0:
            roi = roi[:roi_h - cut_bottom, :]
        roi_h, roi_w = roi.shape[:2]

        vis = roi.copy()

        hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)

        mask1 = cv2.inRange(hsv, self.lower_hsv_1, self.upper_hsv_1)
        mask2 = cv2.inRange(hsv, self.lower_hsv_2, self.upper_hsv_2)
        mask = cv2.bitwise_or(mask1, mask2)

        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, self.kernel)
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, self.kernel)

        edge_margin = int(roi_w * 0.02)
        mask[:, :edge_margin] = 0
        mask[:, roi_w - edge_margin:] = 0

        mask = self.keep_largest_component(mask)
        self.vision_mask_pixels = int(np.count_nonzero(mask))
        self.vision_roi_width = roi_w

        rows_info = []

        for ratio, weight in zip(self.scanline_ratios, self.scanline_weights):
            y = int(roi_h * ratio)
            y = int(np.clip(y, 0, roi_h - 1))

            row = mask[y, :]
            xs = np.where(row > 0)[0]

            if len(xs) < 20:
                continue

            left_x_raw = int(xs[0])
            right_x_raw = int(xs[-1])

            left_visible = left_x_raw > 4
            right_visible = right_x_raw < roi_w - 5

            left_x = left_x_raw
            right_x = right_x_raw

            if left_visible and right_visible:
                road_width = right_x - left_x
                if road_width < 60:
                    continue

                new_width = int(0.92 * self.estimated_road_width_px + 0.08 * road_width)
                self.estimated_road_width_px = int(np.clip(new_width, 220, 330))

            elif left_visible and not right_visible:
                right_x = left_x + self.estimated_road_width_px
                right_x = int(np.clip(right_x, 0, roi_w - 1))

            elif not left_visible and right_visible:
                left_x = right_x - self.estimated_road_width_px
                left_x = int(np.clip(left_x, 0, roi_w - 1))

            else:
                continue

            road_width = right_x - left_x
            if road_width < 60:
                continue

            center_x = 0.5 * (left_x + right_x)

            rows_info.append((center_x, y, weight, left_x, right_x, road_width))

        near_center = None
        mid_center = None
        top_center = None
        target_center = None

        turn_hint = 0.0
        corner_mode = False
        mean_road_width = None

        if len(rows_info) > 0:
            near_candidates = []
            mid_candidates = []
            top_candidates = []
            width_candidates = []

            for cx, y, w, left_x, right_x, road_width in rows_info:
                y_ratio = y / max(roi_h - 1, 1)
                width_candidates.append((road_width, w))

                if y_ratio >= 0.70:
                    near_candidates.append((cx, w))
                elif 0.46 <= y_ratio < 0.70:
                    mid_candidates.append((cx, w))
                else:
                    top_candidates.append((cx, w))

            near_center = self.weighted_average(near_candidates)
            mid_center = self.weighted_average(mid_candidates)
            top_center = self.weighted_average(top_candidates)
            mean_road_width = self.weighted_average(width_candidates)

            turn_hint_near = None
            turn_hint_far = None

            if near_center is not None and mid_center is not None:
                turn_hint_near = mid_center - near_center

            if mid_center is not None and top_center is not None:
                turn_hint_far = top_center - mid_center

            if turn_hint_near is not None and turn_hint_far is not None:
                turn_hint = 0.25 * turn_hint_near + 0.75 * turn_hint_far
            elif turn_hint_far is not None:
                turn_hint = turn_hint_far
            elif turn_hint_near is not None:
                turn_hint = turn_hint_near
            elif near_center is not None and top_center is not None:
                turn_hint = top_center - near_center
            else:
                turn_hint = 0.0

            if abs(turn_hint) > 10:
                corner_mode = True

            if near_center is not None:
                target_center = near_center + 1.10 * turn_hint
            elif mid_center is not None:
                target_center = mid_center
            elif top_center is not None:
                target_center = top_center
            elif self.prev_target is not None:
                target_center = self.prev_target

        else:
            if self.prev_target is not None:
                target_center = self.prev_target

        image_center = roi_w / 2.0
        self.vision_image_center = image_center

        if target_center is not None:
            target_center = float(np.clip(target_center, 0, roi_w - 1))
            self.prev_target = target_center

        vision_twist, vision_ok = self.compute_vision_twist_from_target(
            target_center=target_center,
            image_center=image_center
        )

        self.latest_vision_twist = vision_twist
        self.vision_has_target = vision_ok

        self.vision_near_center = near_center
        self.vision_mid_center = mid_center
        self.vision_top_center = top_center
        self.vision_target_center = target_center
        self.vision_turn_hint = turn_hint
        self.vision_corner_mode = corner_mode
        self.vision_mean_road_width = mean_road_width

        if target_center is not None:
            self.vision_lane_error_px = float(target_center - image_center)
        else:
            self.vision_lane_error_px = None

        cv2.line(vis, (int(image_center), 0), (int(image_center), roi_h - 1), (0, 255, 255), 2)

        if target_center is not None:
            cv2.line(vis, (int(target_center), 0), (int(target_center), roi_h - 1), (0, 255, 255), 2)

        lane_err_str = 'None' if self.vision_lane_error_px is None else f'{self.vision_lane_error_px:.1f}'

        cv2.putText(
            vis,
            f'target={-1 if target_center is None else int(target_center)} '
            f'hint={turn_hint:.1f} lane_err={lane_err_str}',
            (20, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            2
        )

        cv2.putText(
            vis,
            f'est_w={self.estimated_road_width_px} pts={len(rows_info)} mask={self.vision_mask_pixels}',
            (20, 60),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            2
        )

        frame_vis = frame.copy()
        cv2.rectangle(frame_vis, (x0, y0), (x1, y1), (0, 0, 255), 2)
        yolo_detections = self.run_yolo_hazard_detection(frame, (x0, y0, x1, y1))
        self.draw_yolo_detections(frame_vis, yolo_detections)

        yolo_status = 'off'
        if self.enable_yolo_estop:
            yolo_status = 'ready' if self.yolo_ready else 'not_ready'

        cv2.putText(
            frame_vis,
            f'yolo_estop={yolo_status} hazard={len(yolo_detections)}',
            (20, 35),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 255, 255) if not yolo_detections else (0, 0, 255),
            2,
        )

        if yolo_detections:
            self.trigger_yolo_estop(yolo_detections)

        if self.estop_reason:
            cv2.putText(
                frame_vis,
                self.estop_reason[:70],
                (20, height - 20),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (0, 0, 255),
                2,
            )

        mask_vis = cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR)

        self.show_window('camera_with_roi', frame_vis)
        self.show_window('road_boundary_follow', vis)
        self.show_window('road_mask', mask_vis)

    def keep_largest_component(self, mask):
        num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)

        if num_labels <= 1:
            return np.zeros_like(mask)

        largest_label = -1
        largest_area = 0

        for i in range(1, num_labels):
            area = stats[i, cv2.CC_STAT_AREA]
            if area > largest_area:
                largest_area = area
                largest_label = i

        output = np.zeros_like(mask)
        if largest_label != -1:
            output[labels == largest_label] = 255

        return output

    def get_vision_lane_error(self):
        if not self.has_image:
            return None
        if not self.vision_has_target:
            return None
        if self.vision_target_center is None or self.vision_image_center is None:
            return None
        if self.vision_mask_pixels < self.vision_mask_min_pixels:
            return None
        return float(self.vision_target_center - self.vision_image_center)

    # =====================================================
    # debug map
    # =====================================================
    def draw_debug_map(self):
        if not self.cv_windows_enabled:
            return

        size = 900
        canvas = np.full((size, size, 3), 245, dtype=np.uint8)

        if not self.has_odom:
            cv2.putText(canvas, 'Waiting for odom...', (30, 50),
                        cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 0), 2)
            self.show_window('topological_map_view', canvas)
            return

        cv2.putText(canvas, f'wp_count={len(self.waypoints)}', (20, 170),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.60, (20, 20, 20), 2, cv2.LINE_AA)

        xs = [self.current_x]
        zs = [self.current_z]

        for wp in self.waypoints.values():
            xs.append(wp['x'])
            zs.append(wp['z'])

        min_x, max_x = min(xs) - 140.0, max(xs) + 140.0
        min_z, max_z = min(zs) - 140.0, max(zs) + 140.0

        dx = max(max_x - min_x, 1.0)
        dz = max(max_z - min_z, 1.0)

        pad = 60
        scale = min((size - 2 * pad) / dx, (size - 2 * pad) / dz)

        def to_px(x, z):
            px = int((x - min_x) * scale + pad)
            py = int((max_z - z) * scale + pad)
            return px, py

        for wp_id, neighs in self.edges.items():
            wp = self.waypoints[wp_id]
            p1 = to_px(wp['x'], wp['z'])
            for neigh_id in neighs:
                if neigh_id not in self.waypoints:
                    continue
                neigh = self.waypoints[neigh_id]
                p2 = to_px(neigh['x'], neigh['z'])
                cv2.line(canvas, p1, p2, (200, 200, 200), 1)

        if len(self.path) >= 2:
            for i in range(len(self.path) - 1):
                a = self.waypoints[self.path[i]]
                b = self.waypoints[self.path[i + 1]]
                p1 = to_px(a['x'], a['z'])
                p2 = to_px(b['x'], b['z'])
                cv2.line(canvas, p1, p2, (0, 0, 255), 3)

        if len(self.trajectory) >= 2:
            for i in range(len(self.trajectory) - 1):
                p1 = to_px(self.trajectory[i][0], self.trajectory[i][1])
                p2 = to_px(self.trajectory[i + 1][0], self.trajectory[i + 1][1])
                cv2.line(canvas, p1, p2, (255, 140, 0), 1)

        for wp_id, wp in self.waypoints.items():
            pt = to_px(wp['x'], wp['z'])

            color = (80, 80, 80)
            if wp_id == self.goal_wp_id:
                color = (0, 0, 255)
            if wp_id in self.path:
                color = (255, 0, 255)

            cv2.circle(canvas, pt, 7, color, -1)
            cv2.putText(
                canvas,
                f'{wp_id}',
                (pt[0] + 7, pt[1] - 7),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.40,
                (30, 30, 30),
                1,
                cv2.LINE_AA
            )

        car_pt = to_px(self.current_x, self.current_z)
        cv2.circle(canvas, car_pt, 10, (255, 0, 0), -1)

        arrow_len = 30
        end_x = int(car_pt[0] + arrow_len * math.sin(self.current_yaw))
        end_y = int(car_pt[1] - arrow_len * math.cos(self.current_yaw))
        cv2.arrowedLine(canvas, car_pt, (end_x, end_y), (255, 0, 0), 3, tipLength=0.35)

        y = 28
        cv2.putText(canvas, f'Mode: {self.current_mode}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.75, (20, 20, 20), 2, cv2.LINE_AA)
        y += 30

        cv2.putText(canvas, f'Pose: x={self.current_x:.1f}, z={self.current_z:.1f}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.60, (20, 20, 20), 2, cv2.LINE_AA)
        y += 28

        cv2.putText(canvas, f'goal={self.goal_wp_id}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)
        y += 28

        cv2.putText(canvas, f'path_len={len(self.path)}, traj_len={len(self.trajectory)}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)
        y += 28

        lane_err_str = 'None' if self.vision_lane_error_px is None else f'{self.vision_lane_error_px:.1f}'
        cv2.putText(canvas, f'lane_err={lane_err_str}, mask={self.vision_mask_pixels}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)

        if self.goal_wp_id is not None and self.goal_wp_id in self.waypoints:
            goal_dist = self.get_goal_distance()
            y += 28
            cv2.putText(canvas, f'goal_dist={goal_dist:.1f}', (20, y),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)

        if self.current_mode == 'goal_reached':
            cv2.putText(canvas, 'GOAL REACHED', (250, 70),
                        cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 255), 3, cv2.LINE_AA)

        self.show_window('topological_map_view', canvas)

    # =====================================================
    # control
    # =====================================================
    def control_loop(self):
        if not self.has_odom:
            return

        if self.estop_active:
            self.stop_vehicle()
            self.current_mode = 'estop'
            self.draw_debug_map()
            self.pump_debug_windows()
            return

        if self.pending_goal_id is not None:
            self.try_activate_pending_goal()

        twist = Twist()

        if not self.trajectory:
            self.cmd_pub.publish(twist)
            if self.pending_goal_id is not None:
                self.current_mode = 'waiting_goal_activation'
            elif self.goal_wp_id is None:
                self.current_mode = 'waiting_goal'
            else:
                self.current_mode = 'stop'
            self.draw_debug_map()
            self.pump_debug_windows()
            return

        goal_dist = self.get_goal_distance()
        if goal_dist < self.goal_tolerance:
            self.get_logger().info('최종 목적지 도착!')
            self.clear_navigation(clear_goal=True, clear_reason='goal_reset')
            self.publish_int_state(self.pub_state_waypoint, 0)
            self.stop_vehicle()
            self.current_mode = 'goal_reached'
            self.draw_debug_map()
            self.pump_debug_windows()
            return

        self.closest_traj_idx = self.find_closest_trajectory_index()
        lookahead_idx = self.find_lookahead_index(self.closest_traj_idx)

        tx, tz = self.trajectory[lookahead_idx]

        end_x, end_z = self.trajectory[-1]
        end_dist = self.dist_xy(self.current_x, self.current_z, end_x, end_z)
        if lookahead_idx >= len(self.trajectory) - 1 and end_dist < self.traj_reach_tolerance:
            self.get_logger().info('trajectory 끝점 도달')
            self.clear_navigation(clear_goal=True, clear_reason='goal_reset')
            self.publish_int_state(self.pub_state_waypoint, 0)
            self.stop_vehicle()
            self.current_mode = 'traj_end_reached'
            self.draw_debug_map()
            self.pump_debug_windows()
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

        lane_error = self.get_vision_lane_error()
        lane_err_str = 'None' if lane_error is None else f'{lane_error:.1f}'

        final_desired_angular = desired_angular
        final_speed = self.cruise_speed
        mode = 'traj_only'

        if lane_error is not None:
            vision_correction = self.clamp(
                self.vision_lane_k * lane_error,
                -self.vision_lane_max,
                self.vision_lane_max
            )

            abs_lane_error = abs(lane_error)

            if abs_lane_error >= self.vision_recovery_error_px:
                final_desired_angular = vision_correction
                final_speed = min(final_speed, self.recovery_speed)
                mode = 'vision_recovery'
            else:
                final_desired_angular = (
                    (1.0 - self.vision_mix_weight) * desired_angular +
                    self.vision_mix_weight * vision_correction
                )
                final_desired_angular = self.clamp(
                    final_desired_angular,
                    -self.max_angular,
                    self.max_angular
                )
                mode = 'traj+vision'

                if abs_lane_error >= self.vision_soft_error_px:
                    final_speed = min(final_speed, 0.35)

        self.angular_cmd = self.move_toward(
            self.angular_cmd,
            final_desired_angular,
            self.max_angular_step
        )
        angular = self.angular_cmd

        twist.linear.x = final_speed
        twist.angular.z = angular

        self.cmd_pub.publish(twist)

        self.prev_error = error_for_control
        self.prev_time = now
        self.current_mode = mode

        current_time = time.time()
        if current_time - self.last_log_time >= 1.0:
            self.get_logger().info(
                f'mode: {mode} | '
                f'traj_idx: {self.closest_traj_idx}/{len(self.trajectory)-1} | '
                f'lookahead_idx: {lookahead_idx} | '
                f'goal_dist: {goal_dist:.1f} | '
                f'cte: {cross_track_error:.2f} | '
                f'yaw: {math.degrees(self.current_yaw):.2f}deg | '
                f'target_yaw: {math.degrees(target_yaw):.2f}deg | '
                f'error: {error_deg:.2f}deg | '
                f'lane_err: {lane_err_str} | '
                f'straight: {straight_mode} | '
                f'hold: {self.hold_steering_mode} | '
                f'p: {p_term:.3f} | '
                f'd: {d_term:.3f} | '
                f'desired: {desired_angular:.3f} | '
                f'final_desired: {final_desired_angular:.3f} | '
                f'w: {twist.angular.z:.3f} | '
                f'v: {twist.linear.x:.3f}'
            )
            self.last_log_time = current_time

        self.draw_debug_map()
        self.pump_debug_windows()


def main(args=None):
    rclpy.init(args=args)
    node = HybridDijkstraVisionFollower()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        try:
            node.cmd_pub.publish(Twist())
        except Exception:
            pass
        cv2.destroyAllWindows()
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()