import math
from pathlib import Path
import cv2
import rclpy
from rclpy.node import Node
from rclpy.qos import qos_profile_sensor_data
from geometry_msgs.msg import Twist
from nav_msgs.msg import Odometry
from sensor_msgs.msg import Image
from std_msgs.msg import Bool, Int32, String

try:
    from ament_index_python.packages import get_package_share_directory
except ImportError:
    get_package_share_directory = None
from cv_bridge import CvBridge

from .config import (
    LaneDetectionConfig,
    PathPlannerConfig,
    PathTrackingConfig,
    RouteExportConfig,
    VisionFusionConfig,
    YoloConfig,
)
from .control import TrajectoryFollower, move_toward
from .debug_tools import DebugWindowManager, build_topological_map_view
from .exporters import MinimapRouteExporter
from .models import Pose2D, SpeedState
from .navigation import TopologicalRoutePlanner, clamp, dist_xy, normalize_angle
from .perception import LaneVisionProcessor, YoloHazardDetector


class HybridDijkstraVisionFollower(Node):
    def __init__(self):
        super().__init__('hybrid_dijkstra_vision_follower')

        self.bridge = CvBridge()

        self.odom_sub = self.create_subscription(
            Odometry, '/odom', self.odom_callback, qos_profile_sensor_data
        )
        self.image_sub = self.create_subscription(
            Image, '/camera/image_raw', self.image_callback, qos_profile_sensor_data
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
        self.state_pub = self.create_publisher(String, '/state', 1)
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

        self.route_export_path = Path(str(self.get_parameter('route_export_path').value))
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

        self.route_export_config = RouteExportConfig()
        self.path_planner_config = PathPlannerConfig()
        self.path_tracking_config = PathTrackingConfig()
        self.lane_detection_config = LaneDetectionConfig()
        self.vision_fusion_config = VisionFusionConfig()
        self.yolo_config = YoloConfig(
            enabled=self.enable_yolo_estop,
            model_name=self.yolo_model_name,
            device=self.yolo_device,
            confidence=self.yolo_confidence,
            input_size=self.yolo_input_size,
            hazard_labels=self.yolo_hazard_labels,
        )
        self._apply_config_aliases()

        self.debug_windows = DebugWindowManager(
            enabled=self.show_debug_windows,
            logger=self.get_logger(),
        )
        self.cv_windows_enabled = self.debug_windows.enabled
        if not self.show_debug_windows:
            self.get_logger().info(
                'OpenCV debug windows are disabled. Use show_debug_windows:=true only when X11 access is available.'
            )

        self.route_exporter = MinimapRouteExporter(
            route_export_path=self.route_export_path,
            publisher=self.pub_minimap_route,
            logger=self.get_logger(),
            source_name_resolver=self.get_name,
            goal_state_resolver=self.goal_id_to_state_value,
            config=self.route_export_config,
        )
        self.route_export_seq = 0
        self.last_minimap_publish_time = 0.0
        self.minimap_last_error_log_time = 0.0
        self.active_route_start_id = None
        self.active_route_state_value = None

        self.route_planner = TopologicalRoutePlanner(self.path_planner_config)
        self.waypoints = {}
        self.edges = {}
        self.path = []
        self.trajectory = []
        self.last_log_time = 0.0

        self.trajectory_follower = TrajectoryFollower(
            self.path_tracking_config,
            self.vision_fusion_config,
        )
        self.prev_error = 0.0
        self.prev_time = None
        self.closest_traj_idx = 0
        self.angular_cmd = 0.0
        self.last_stable_angular = 0.0
        self.hold_steering_mode = False
        self.sync_tracking_state_fields()

        self.lane_processor = LaneVisionProcessor(self.lane_detection_config)
        self.prev_vision_error = 0.0
        self.prev_target = None
        self.estimated_road_width_px = self.lane_processor.estimated_road_width_px

        self.yolo_detector = YoloHazardDetector(
            self.yolo_config,
            self.get_logger(),
            self.resolve_model_reference,
        )
        self.yolo_model = None
        self.yolo_ready = False
        self.yolo_model_reference = ''
        self.yolo_last_error_log_time = 0.0
        self.yolo_last_detections = []
        self.yolo_last_reason = ''

        self.current_x = 0.0
        self.current_z = 0.0
        self.current_yaw = 0.0
        self.current_speed_ms = 0.0
        self.current_speed_kmh = 0.0
        self.has_odom = False
        self.estop_active = False
        self.estop_reason = ''

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

        self.current_mode = 'waiting_goal'
        self.current_state_label = None
        self.pending_state_timer = None

        self.load_map(self.waypoint_json_path)
        self.initialize_yolo_detector()
        self.publish_state_if_changed('대기')

        self.get_logger().info(
            f'Hybrid follower started. json={self.waypoint_json_path}, '
            f'goal={self.goal_wp_id}, wp_count={len(self.waypoints)}'
        )

    def _apply_config_aliases(self):
        self.minimap_live_publish_interval = self.route_export_config.live_publish_interval

        self.cruise_speed = self.path_tracking_config.cruise_speed
        self.kp = self.path_tracking_config.kp
        self.kd = self.path_tracking_config.kd
        self.max_angular = self.path_tracking_config.max_angular
        self.max_d_term = self.path_tracking_config.max_d_term
        self.deadband_deg = self.path_tracking_config.deadband_deg
        self.max_angular_step = self.path_tracking_config.max_angular_step
        self.straight_error_deg = self.path_tracking_config.straight_error_deg
        self.straight_cte = self.path_tracking_config.straight_cte
        self.hold_decay = self.path_tracking_config.hold_decay
        self.lookahead_dist = self.path_tracking_config.lookahead_dist
        self.goal_tolerance = self.path_tracking_config.goal_tolerance
        self.traj_reach_tolerance = self.path_tracking_config.traj_reach_tolerance
        self.corridor_radius = self.path_tracking_config.corridor_radius
        self.heading_hold_deg = self.path_tracking_config.heading_hold_deg
        self.release_corridor_radius = self.path_tracking_config.release_corridor_radius
        self.behind_angle_deg = self.path_planner_config.behind_angle_deg
        self.samples_per_segment = self.path_planner_config.samples_per_segment

        self.k_pos = self.lane_detection_config.k_pos
        self.kd_vision = self.lane_detection_config.kd_vision
        self.max_angular_vision = self.lane_detection_config.max_angular_vision
        self.base_speed = self.lane_detection_config.base_speed
        self.min_speed = self.lane_detection_config.min_speed
        self.roi_start_ratio = self.lane_detection_config.roi_start_ratio
        self.roi_end_ratio = self.lane_detection_config.roi_end_ratio
        self.roi_side_margin_ratio = self.lane_detection_config.roi_side_margin_ratio
        self.cut_bottom_ratio = self.lane_detection_config.cut_bottom_ratio
        self.scanline_ratios = list(self.lane_detection_config.scanline_ratios)
        self.scanline_weights = list(self.lane_detection_config.scanline_weights)
        self.lower_hsv_1 = self.lane_detection_config.lower_hsv_1
        self.upper_hsv_1 = self.lane_detection_config.upper_hsv_1
        self.lower_hsv_2 = self.lane_detection_config.lower_hsv_2
        self.upper_hsv_2 = self.lane_detection_config.upper_hsv_2
        self.kernel = self.lane_detection_config.kernel

        self.vision_mask_min_pixels = self.vision_fusion_config.vision_mask_min_pixels
        self.vision_lane_k = self.vision_fusion_config.vision_lane_k
        self.vision_lane_max = self.vision_fusion_config.vision_lane_max
        self.vision_mix_weight = self.vision_fusion_config.vision_mix_weight
        self.vision_recovery_error_px = self.vision_fusion_config.vision_recovery_error_px
        self.vision_soft_error_px = self.vision_fusion_config.vision_soft_error_px
        self.recovery_speed = self.vision_fusion_config.recovery_speed

    def current_pose(self):
        return Pose2D(
            x=float(self.current_x),
            z=float(self.current_z),
            yaw=float(self.current_yaw),
        )

    def current_speed_state(self):
        return SpeedState(
            speed_ms=float(self.current_speed_ms),
            speed_kmh=float(self.current_speed_kmh),
        )

    def sync_tracking_state_fields(self):
        self.prev_error = self.trajectory_follower.prev_error
        self.prev_time = self.trajectory_follower.prev_time
        self.closest_traj_idx = self.trajectory_follower.closest_traj_idx
        self.angular_cmd = self.trajectory_follower.angular_cmd
        self.last_stable_angular = self.trajectory_follower.last_stable_angular
        self.hold_steering_mode = self.trajectory_follower.hold_steering_mode

    def sync_route_export_state_fields(self):
        self.route_export_seq = self.route_exporter.route_export_seq
        self.last_minimap_publish_time = self.route_exporter.last_minimap_publish_time
        self.minimap_last_error_log_time = self.route_exporter.minimap_last_error_log_time
        self.active_route_start_id = self.route_exporter.active_route_start_id
        self.active_route_state_value = self.route_exporter.active_route_state_value

    def sync_yolo_state_fields(self, detections=None):
        self.yolo_model = self.yolo_detector.model
        self.yolo_ready = self.yolo_detector.ready
        self.yolo_model_reference = self.yolo_detector.model_reference
        self.yolo_last_error_log_time = self.yolo_detector.last_error_log_time
        if detections is None:
            detections = self.yolo_detector.last_detections
        self.yolo_last_detections = [
            {
                'label': detection.label,
                'confidence': detection.confidence,
                'bbox': detection.bbox,
                'overlap_bbox': detection.overlap_bbox,
            }
            for detection in detections
        ]

    def normalize_angle(self, angle):
        return normalize_angle(angle)

    def clamp(self, value, min_value, max_value):
        return clamp(value, min_value, max_value)

    def move_toward(self, current, target, max_step):
        return move_toward(current, target, max_step)

    def dist_xy(self, x1, z1, x2, z2):
        return dist_xy(x1, z1, x2, z2)

    def weighted_average(self, pairs):
        return self.lane_processor.weighted_average(pairs)

    def speed_ms_to_kmh(self, speed_ms):
        return float(speed_ms) * 3.6

    def build_speed_snapshot(self):
        return self.route_exporter.build_speed_snapshot(self.current_speed_state())

    def publish_int_state(self, publisher, value):
        message = Int32()
        message.data = int(value)
        publisher.publish(message)

    def publish_state_if_changed(self, state_text):
        state_text = str(state_text).strip()
        if not state_text or self.current_state_label == state_text:
            return False

        message = String()
        message.data = state_text
        self.state_pub.publish(message)
        self.current_state_label = state_text
        return True

    def cancel_pending_state_timer(self):
        timer = self.pending_state_timer
        if timer is None:
            return

        timer.cancel()
        self.destroy_timer(timer)
        self.pending_state_timer = None

    def schedule_delayed_state_publish(self, state_text, delay_sec=1.0):
        self.cancel_pending_state_timer()

        def delayed_publish_callback():
            timer = self.pending_state_timer
            if timer is not None:
                self.pending_state_timer = None
                timer.cancel()
                self.destroy_timer(timer)
            self.publish_state_if_changed(state_text)

        self.pending_state_timer = self.create_timer(delay_sec, delayed_publish_callback)

    def normalize_detection_label(self, label):
        return YoloHazardDetector.normalize_detection_label(label)

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
        self.route_exporter.publish_minimap_route_payload(payload)
        self.sync_route_export_state_fields()

    def persist_minimap_route_payload(self, payload):
        self.route_exporter.persist_minimap_route_payload(payload)
        self.sync_route_export_state_fields()

    def log_minimap_export_error(self, error):
        self.route_exporter.log_minimap_export_error(error)
        self.sync_route_export_state_fields()

    def build_empty_route_snapshot(self, reason='navigation_cleared'):
        return self.route_exporter.build_empty_route_snapshot(
            goal_wp_id=self.goal_wp_id,
            waypoints=self.waypoints,
            current_pose=self.current_pose(),
            speed_state=self.current_speed_state(),
            reason=reason,
        )

    def publish_cleared_minimap_route(self, reason='navigation_cleared'):
        self.route_exporter.publish_cleared_route(
            goal_wp_id=self.goal_wp_id,
            waypoints=self.waypoints,
            current_pose=self.current_pose(),
            speed_state=self.current_speed_state(),
            reason=reason,
        )
        self.sync_route_export_state_fields()

    def build_route_snapshot(self, start_id, goal_id, state_value, path_ids, trajectory):
        return self.route_exporter.build_route_snapshot(
            start_id=start_id,
            goal_id=goal_id,
            state_value=state_value,
            path_ids=path_ids,
            trajectory=trajectory,
            waypoints=self.waypoints,
            current_pose=self.current_pose(),
            speed_state=self.current_speed_state(),
        )

    def export_route_snapshot(self, start_id, goal_id, state_value, path_ids, trajectory):
        self.route_exporter.export_route(
            start_id=start_id,
            goal_id=goal_id,
            state_value=state_value,
            path_ids=path_ids,
            trajectory=trajectory,
            waypoints=self.waypoints,
            current_pose=self.current_pose(),
            speed_state=self.current_speed_state(),
        )
        self.sync_route_export_state_fields()

    def publish_live_minimap_snapshot(self, force=False):
        self.route_exporter.publish_live_snapshot(
            goal_wp_id=self.goal_wp_id,
            waypoints=self.waypoints,
            path_ids=self.path,
            trajectory=self.trajectory,
            current_pose=self.current_pose(),
            speed_state=self.current_speed_state(),
            current_mode=self.current_mode,
            force=force,
        )
        self.sync_route_export_state_fields()

    def clear_pending_goal(self):
        self.pending_goal_id = None
        self.pending_goal_state_value = None

    def clear_navigation(self, clear_goal=False, clear_pending=False, clear_reason='navigation_cleared'):
        self.path = []
        self.trajectory = []
        self.route_exporter.clear_active_route()
        self.sync_route_export_state_fields()
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
        self.debug_windows.show_window(name, image)
        self.cv_windows_enabled = self.debug_windows.enabled

    def pump_debug_windows(self):
        self.debug_windows.pump()
        self.cv_windows_enabled = self.debug_windows.enabled

    def initialize_yolo_detector(self):
        self.yolo_detector.initialize()
        self.sync_yolo_state_fields()

    def label_matches_hazard(self, label):
        return self.yolo_detector.label_matches_hazard(label)

    def run_yolo_hazard_detection(self, frame, roi_rect):
        detections = self.yolo_detector.detect(frame, roi_rect)
        self.sync_yolo_state_fields(detections)
        return detections

    def draw_yolo_detections(self, frame_vis, detections):
        self.yolo_detector.draw_detections(frame_vis, detections)

    def reset_vision_tracking(self):
        self.lane_processor.reset_tracking()
        self.prev_vision_error = self.lane_processor.prev_vision_error
        self.prev_target = self.lane_processor.prev_target
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

            self.cancel_pending_state_timer()
            self.publish_state_if_changed('긴급 정지')
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
            f'{detection.label}({detection.confidence:.2f})'
            for detection in detections[:3]
        )
        if len(detections) > 3:
            labels += ', ...'

        self.yolo_last_reason = f'YOLO ROI hazard detected: {labels}'
        self.set_estop_state(True, reason=self.yolo_last_reason)

        estop_msg = Bool()
        estop_msg.data = True
        self.estop_pub.publish(estop_msg)

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
            self.route_planner.load_map(filepath)
            self.waypoints = self.route_planner.waypoints
            self.edges = self.route_planner.edges
            self.get_logger().info(
                f'맵 로드 완료: {len(self.waypoints)}개의 웨이포인트'
            )
        except Exception as error:
            self.get_logger().error(f'Map load failed: {error}')

    def calculate_distance_between_waypoints(self, id1, id2):
        return self.route_planner.calculate_distance_between_waypoints(id1, id2)

    def get_heading_error_to_xy_deg(self, tx, tz):
        return self.route_planner.get_heading_error_to_xy_deg(self.current_pose(), tx, tz)

    def get_candidate_waypoints(self, max_candidates=8):
        return self.route_planner.get_candidate_waypoints(
            self.current_pose(),
            max_candidates=max_candidates,
        )

    def choose_better_start_waypoint(self, goal_id):
        return self.route_planner.choose_better_start_waypoint(self.current_pose(), goal_id)

    def find_path_dijkstra(self, start_id, goal_id):
        return self.route_planner.find_path_dijkstra(start_id, goal_id)

    def waypoint_path_to_points(self, path_ids):
        return self.route_planner.waypoint_path_to_points(path_ids)

    def catmull_rom(self, p0, p1, p2, p3, t):
        return self.route_planner.catmull_rom(p0, p1, p2, p3, t)

    def build_spline_trajectory(self, path_ids):
        return self.route_planner.build_spline_trajectory(path_ids)

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
            state_value = (
                state_value if state_value is not None else self.goal_id_to_state_value(goal_id)
            )
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

        route, error_code = self.route_planner.plan_route(self.current_pose(), goal_id)
        self.waypoints = self.route_planner.waypoints
        self.edges = self.route_planner.edges
        if route is None:
            if error_code == 'start_selection_failed':
                self.get_logger().error('시작 waypoint 선택 실패')
            elif error_code == 'path_not_found':
                self.get_logger().error('경로 생성 실패')
            elif error_code == 'trajectory_not_found':
                self.get_logger().error('trajectory 생성 실패')
            else:
                self.get_logger().error('경로 생성 실패')
            self.clear_pending_goal()
            if self.goal_wp_id == goal_id and not self.trajectory:
                self.goal_wp_id = None
            return False

        self.path = route.path_ids
        self.trajectory = route.trajectory
        self.goal_wp_id = goal_id
        self.clear_tracking_state()

        state_value = (
            state_value if state_value is not None else self.goal_id_to_state_value(goal_id)
        )
        self.publish_int_state(self.pub_state_waypoint, state_value)
        self.export_route_snapshot(
            route.start_id,
            goal_id,
            state_value,
            self.path,
            self.trajectory,
        )
        self.clear_pending_goal()
        self.cancel_pending_state_timer()
        self.publish_state_if_changed('출발')
        self.schedule_delayed_state_publish('주행 중')

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

    def odom_callback(self, msg):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation
        linear = msg.twist.twist.linear

        self.current_z = pos.x
        self.current_x = -pos.y

        planar_speed_ms = math.sqrt(
            (linear.x * linear.x)
            + (linear.y * linear.y)
            + (linear.z * linear.z)
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

    def get_goal_distance(self):
        if self.goal_wp_id is None or self.goal_wp_id not in self.waypoints:
            return float('inf')
        goal_x = self.waypoints[self.goal_wp_id]['x']
        goal_z = self.waypoints[self.goal_wp_id]['z']
        return self.dist_xy(self.current_x, self.current_z, goal_x, goal_z)

    def clear_tracking_state(self):
        self.trajectory_follower.reset_tracking_state()
        self.sync_tracking_state_fields()

    def find_closest_trajectory_index(self):
        closest_idx = self.trajectory_follower.find_closest_trajectory_index(
            self.current_pose(),
            self.trajectory,
        )
        self.trajectory_follower.closest_traj_idx = closest_idx
        self.sync_tracking_state_fields()
        return closest_idx

    def find_lookahead_index(self, start_idx):
        return self.trajectory_follower.find_lookahead_index(self.trajectory, start_idx)

    def distance_point_to_segment(self, px, pz, ax, az, bx, bz):
        return self.trajectory_follower.distance_point_to_segment(px, pz, ax, az, bx, bz)

    def get_cross_track_error(self, traj_idx):
        return self.trajectory_follower.get_cross_track_error(
            self.current_pose(),
            self.trajectory,
            traj_idx,
        )

    def compute_vision_twist_from_target(self, target_center, image_center):
        linear_speed, angular_speed, has_target = self.lane_processor.compute_control_from_target(
            target_center,
            image_center,
        )
        twist = Twist()
        twist.linear.x = linear_speed
        twist.angular.z = angular_speed
        self.prev_vision_error = self.lane_processor.prev_vision_error
        return twist, has_target

    def process_lane_frame(self, frame):
        return self.lane_processor.process(frame)

    def apply_lane_detection_result(self, result):
        twist = Twist()
        twist.linear.x = result.linear_speed
        twist.angular.z = result.angular_speed
        self.latest_vision_twist = twist
        self.vision_has_target = result.has_target

        self.vision_mask_pixels = result.mask_pixels
        self.vision_near_center = result.near_center
        self.vision_mid_center = result.mid_center
        self.vision_top_center = result.top_center
        self.vision_target_center = result.target_center
        self.vision_turn_hint = result.turn_hint
        self.vision_corner_mode = result.corner_mode
        self.vision_mean_road_width = result.mean_road_width
        self.vision_image_center = result.image_center
        self.vision_lane_error_px = result.lane_error_px
        self.vision_roi_width = result.roi_width

        self.prev_vision_error = self.lane_processor.prev_vision_error
        self.prev_target = self.lane_processor.prev_target
        self.estimated_road_width_px = self.lane_processor.estimated_road_width_px

    def process_yolo_frame(self, frame, roi_rect):
        frame_vis = frame.copy()
        x0, y0, x1, y1 = roi_rect
        cv2.rectangle(frame_vis, (x0, y0), (x1, y1), (0, 0, 255), 2)

        yolo_detections = self.run_yolo_hazard_detection(frame, roi_rect)
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
            height = frame.shape[0]
            cv2.putText(
                frame_vis,
                self.estop_reason[:70],
                (20, height - 20),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (0, 0, 255),
                2,
            )

        return frame_vis, yolo_detections

    def image_callback(self, msg):
        self.has_image = True

        frame = self.bridge.imgmsg_to_cv2(msg, desired_encoding='bgr8')
        lane_result = self.process_lane_frame(frame)
        self.apply_lane_detection_result(lane_result)

        frame_vis, _ = self.process_yolo_frame(frame, lane_result.roi_rect)
        mask_vis = cv2.cvtColor(lane_result.mask, cv2.COLOR_GRAY2BGR)

        self.show_window('camera_with_roi', frame_vis)
        self.show_window('road_boundary_follow', lane_result.roi_visualization)
        self.show_window('road_mask', mask_vis)

    def keep_largest_component(self, mask):
        return self.lane_processor.keep_largest_component(mask)

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

    def draw_debug_map(self):
        if not self.debug_windows.enabled:
            return

        goal_distance = None
        if self.goal_wp_id is not None and self.goal_wp_id in self.waypoints:
            goal_distance = self.get_goal_distance()

        canvas = build_topological_map_view(
            has_odom=self.has_odom,
            waypoints=self.waypoints,
            edges=self.edges,
            path_ids=self.path,
            trajectory=self.trajectory,
            goal_wp_id=self.goal_wp_id,
            current_pose=self.current_pose(),
            current_mode=self.current_mode,
            vision_lane_error_px=self.vision_lane_error_px,
            vision_mask_pixels=self.vision_mask_pixels,
            goal_distance=goal_distance,
        )
        self.show_window('topological_map_view', canvas)

    def handle_no_active_trajectory(self):
        self.cmd_pub.publish(Twist())
        if self.pending_goal_id is not None:
            self.current_mode = 'waiting_goal_activation'
        elif self.goal_wp_id is None:
            self.current_mode = 'waiting_goal'
        else:
            self.current_mode = 'stop'
        self.draw_debug_map()
        self.pump_debug_windows()

    def finish_navigation(self, log_message, mode_name):
        self.get_logger().info(log_message)
        self.cancel_pending_state_timer()
        self.publish_state_if_changed('도착')
        self.schedule_delayed_state_publish('진료중')
        self.clear_navigation(clear_goal=True, clear_reason='goal_reset')
        self.publish_int_state(self.pub_state_waypoint, 0)
        self.stop_vehicle()
        self.current_mode = mode_name
        self.draw_debug_map()
        self.pump_debug_windows()

    def trajectory_end_reached(self):
        closest_idx = self.trajectory_follower.find_closest_trajectory_index(
            self.current_pose(),
            self.trajectory,
        )
        self.trajectory_follower.closest_traj_idx = closest_idx
        lookahead_idx = self.trajectory_follower.find_lookahead_index(
            self.trajectory,
            closest_idx,
        )
        self.sync_tracking_state_fields()

        end_x, end_z = self.trajectory[-1]
        end_dist = self.dist_xy(self.current_x, self.current_z, end_x, end_z)
        reached = (
            lookahead_idx >= len(self.trajectory) - 1
            and end_dist < self.traj_reach_tolerance
        )
        return reached, lookahead_idx

    def compute_tracking_control(self):
        lane_error = self.get_vision_lane_error()
        control_result = self.trajectory_follower.compute_control(
            self.current_pose(),
            self.trajectory,
            lane_error,
        )
        self.sync_tracking_state_fields()
        return control_result

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

        if not self.trajectory:
            self.handle_no_active_trajectory()
            return

        goal_dist = self.get_goal_distance()
        if goal_dist < self.goal_tolerance:
            self.finish_navigation('최종 목적지 도착!', 'goal_reached')
            return

        traj_end_reached, _ = self.trajectory_end_reached()
        if traj_end_reached:
            self.finish_navigation('trajectory 끝점 도달', 'traj_end_reached')
            return

        control_result = self.compute_tracking_control()
        twist = Twist()
        twist.linear.x = control_result.linear_speed
        twist.angular.z = control_result.angular_speed
        self.cmd_pub.publish(twist)

        self.current_mode = control_result.mode

        current_time = self.get_clock().now().nanoseconds / 1e9
        lane_err_str = (
            'None' if control_result.lane_error is None else f'{control_result.lane_error:.1f}'
        )
        if current_time - self.last_log_time >= 1.0:
            self.get_logger().info(
                f'mode: {control_result.mode} | '
                f'traj_idx: {control_result.closest_traj_idx}/{len(self.trajectory)-1} | '
                f'lookahead_idx: {control_result.lookahead_idx} | '
                f'goal_dist: {goal_dist:.1f} | '
                f'cte: {control_result.cross_track_error:.2f} | '
                f'yaw: {math.degrees(self.current_yaw):.2f}deg | '
                f'target_yaw: {math.degrees(control_result.target_yaw):.2f}deg | '
                f'error: {control_result.error_deg:.2f}deg | '
                f'lane_err: {lane_err_str} | '
                f'straight: {control_result.straight_mode} | '
                f'hold: {control_result.hold_steering_mode} | '
                f'p: {control_result.p_term:.3f} | '
                f'd: {control_result.d_term:.3f} | '
                f'desired: {control_result.desired_angular:.3f} | '
                f'final_desired: {control_result.final_desired_angular:.3f} | '
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
