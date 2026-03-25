import json
import math
import threading
import rclpy
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from nav_msgs.msg import Odometry
from rclpy.node import Node
from rclpy.qos import DurabilityPolicy, HistoryPolicy, QoSProfile, ReliabilityPolicy, qos_profile_sensor_data
from std_msgs.msg import Bool, Int32, String

state_lock = threading.Lock()
current_state = {
    'odom': {
        'x': 0.0,
        'y': 0.0,
        'z': 0.0,
        'speed_ms': 0.0,
        'speed_kmh': 0.0,
    },
    'minimap_pose': {'x': 0.0, 'z': 0.0, 'yaw': 0.0},
    'route_snapshot': {
        'route_version': 0,
        'planner_type': 'vision+spline',
        'goal_waypoint_id': None,
        'generated_at': None,
        'source_node': None,
        'status': None,
        'trajectory': [],
        'current_pose': None,
        'speed_ms': 0.0,
        'speed_kmh': 0.0,
        'cleared': False,
        'clear_reason': None,
    },
    'has_odom': False,
    'robot_state': '대기',
}

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)


def normalize_angle(angle: float) -> float:
    while angle > math.pi:
        angle -= 2.0 * math.pi
    while angle < -math.pi:
        angle += 2.0 * math.pi
    return angle


def to_float(value):
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(parsed):
        return None
    return parsed


def round_or_none(value, digits=3):
    parsed = to_float(value)
    if parsed is None:
        return None
    return round(parsed, digits)


def speed_ms_to_kmh(speed_ms: float) -> float:
    return float(speed_ms) * 3.6


def sanitize_pose(pose):
    if not isinstance(pose, dict):
        return None

    x = to_float(pose.get('x'))
    z = to_float(pose.get('z'))
    if x is None or z is None:
        return None

    yaw = to_float(pose.get('yaw'))
    if yaw is None:
        yaw = 0.0

    return {
        'x': round(x, 3),
        'z': round(z, 3),
        'yaw': round(yaw, 6),
    }


def sanitize_path_points(points):
    if not isinstance(points, list):
        return []

    sanitized = []
    for point in points:
        if not isinstance(point, dict):
            continue

        x = to_float(point.get('x'))
        z = to_float(point.get('z'))
        if x is None or z is None:
            continue

        sanitized.append({'x': float(x), 'z': float(z)})

    return sanitized


def resolve_snapshot_speed_ms(payload, current_pose):
    candidates = [
        payload.get('speedMs'),
        payload.get('speed_ms'),
        payload.get('speed'),
    ]

    if isinstance(current_pose, dict):
        candidates.extend([
            current_pose.get('speed_ms'),
            current_pose.get('speedMs'),
        ])

    for candidate in candidates:
        parsed = to_float(candidate)
        if parsed is not None:
            return parsed

    return 0.0


def resolve_snapshot_speed_kmh(payload, current_pose, speed_ms):
    candidates = [
        payload.get('speedKmh'),
        payload.get('speed_kmh'),
    ]

    if isinstance(current_pose, dict):
        candidates.extend([
            current_pose.get('speed_kmh'),
            current_pose.get('speedKmh'),
        ])

    for candidate in candidates:
        parsed = to_float(candidate)
        if parsed is not None:
            return parsed

    return speed_ms_to_kmh(speed_ms)


def resolve_minimap_status(route_snapshot, path_points):
    raw_status = route_snapshot.get('status')
    if isinstance(raw_status, str) and raw_status.strip():
        return raw_status.strip()

    clear_reason = str(route_snapshot.get('clear_reason') or '').strip().lower()
    if clear_reason == 'estop':
        return '긴급정지'
    if clear_reason in {'goal_reset', 'traj_end_reached'}:
        return '도착'
    if path_points:
        return '주행 중'
    return '대기'


@app.get('/api/odom')
def get_odom():
    with state_lock:
        return dict(current_state['odom'])


@app.get('/api/minimap')
def get_minimap_state():
    with state_lock:
        route_snapshot = current_state['route_snapshot']
        path_points = sanitize_path_points(route_snapshot.get('trajectory', []))

        route_pose = sanitize_pose(route_snapshot.get('current_pose'))
        odom_pose = sanitize_pose(current_state['minimap_pose']) if current_state['has_odom'] else None
        vehicle_pose = route_pose or odom_pose

        speed_ms = to_float(route_snapshot.get('speed_ms'))
        if speed_ms is None:
            speed_ms = to_float(current_state['odom'].get('speed_ms'))
        if speed_ms is None:
            speed_ms = 0.0

        speed_kmh = to_float(route_snapshot.get('speed_kmh'))
        if speed_kmh is None:
            speed_kmh = to_float(current_state['odom'].get('speed_kmh'))
        if speed_kmh is None:
            speed_kmh = speed_ms_to_kmh(speed_ms)

        explicit_state = current_state.get('robot_state')
        if isinstance(explicit_state, str) and explicit_state.strip():
            status = explicit_state.strip()
        else:
            status = resolve_minimap_status(route_snapshot, path_points)
        has_pose = vehicle_pose is not None

        return {
            'vehiclePose': vehicle_pose,
            'current_pose': vehicle_pose,
            'currentPose': vehicle_pose,
            'pathPoints': path_points,
            'trajectory': path_points,
            'routeVersion': int(route_snapshot.get('route_version', 0) or 0),
            'plannerType': route_snapshot.get('planner_type'),
            'goalWaypointId': route_snapshot.get('goal_waypoint_id'),
            'updatedAt': route_snapshot.get('generated_at'),
            'sourceNode': route_snapshot.get('source_node'),
            'status': status,
            'state': status,
            'speed': float(speed_ms),
            'speedMs': float(speed_ms),
            'speedKmh': float(speed_kmh),
            'vehicleSpeedMs': float(speed_ms),
            'vehicleSpeedKmh': float(speed_kmh),
            'hasOdom': has_pose,
        }


@app.post('/api/cmd/estop/{state}')
def trigger_estop(state: int):
    if ros_node:
        ros_node.send_emergency_stop(bool(state))
    return {'message': f'E-Stop set to {bool(state)}'}


@app.post('/api/cmd/waypoint/{target}')
def trigger_waypoint(target: int):
    if ros_node:
        ros_node.send_waypoint(target)
    return {'message': f'Waypoint {target} sent'}


class Ec2ControlNode(Node):
    def __init__(self):
        super().__init__('ec2_control_node')
        self.bridge_subscription_qos = QoSProfile(
            history=HistoryPolicy.KEEP_LAST,
            depth=10,
            reliability=ReliabilityPolicy.BEST_EFFORT,
            durability=DurabilityPolicy.VOLATILE,
        )
        self.create_subscription(Odometry, '/odom', self.odom_callback, qos_profile_sensor_data)
        self.create_subscription(
            String,
            '/ec2_state/minimap_route',
            self.route_snapshot_callback,
            self.bridge_subscription_qos,
        )
        self.create_subscription(String, '/state', self.state_callback, self.bridge_subscription_qos)
        self.estop_pub = self.create_publisher(Bool, '/ec2_cmd/e_stop', 1)
        self.waypoint_pub = self.create_publisher(Int32, '/ec2_cmd/target_waypoint', 1)
        print('ROS 2 노드 준비 완료', flush=True)

    def odom_callback(self, msg):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation
        linear = msg.twist.twist.linear

        siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        yaw = math.atan2(siny_cosp, cosy_cosp)
        speed_ms = math.sqrt(
            (linear.x * linear.x) +
            (linear.y * linear.y) +
            (linear.z * linear.z)
        )
        minimap_pose = {
            'x': round(-pos.y, 3),
            'z': round(pos.x, 3),
            'yaw': round(normalize_angle(yaw + math.pi), 6),
        }

        with state_lock:
            current_state['odom']['x'] = round(pos.x, 3)
            current_state['odom']['y'] = round(pos.y, 3)
            current_state['odom']['z'] = round(pos.z, 3)
            current_state['odom']['speed_ms'] = round(speed_ms, 4)
            current_state['odom']['speed_kmh'] = round(speed_ms_to_kmh(speed_ms), 4)
            current_state['minimap_pose'] = minimap_pose
            current_state['has_odom'] = True

    def route_snapshot_callback(self, msg):
        try:
            payload = json.loads(msg.data)
        except json.JSONDecodeError as error:
            self.get_logger().error(f'Failed to decode minimap route snapshot: {error}')
            return

        current_pose = sanitize_pose(payload.get('current_pose'))
        trajectory = payload.get('trajectory', [])
        speed_ms = resolve_snapshot_speed_ms(payload, payload.get('current_pose'))
        speed_kmh = resolve_snapshot_speed_kmh(payload, payload.get('current_pose'), speed_ms)

        with state_lock:
            current_state['route_snapshot'] = {
                'route_version': int(payload.get('route_version', 0) or 0),
                'planner_type': payload.get('planner_type', 'vision+spline'),
                'goal_waypoint_id': payload.get('goal_waypoint_id'),
                'generated_at': payload.get('generated_at'),
                'source_node': payload.get('source_node'),
                'status': payload.get('status'),
                'trajectory': trajectory if isinstance(trajectory, list) else [],
                'current_pose': current_pose,
                'speed_ms': float(speed_ms),
                'speed_kmh': float(speed_kmh),
                'cleared': bool(payload.get('cleared', False)),
                'clear_reason': payload.get('clear_reason'),
            }

            if current_pose is not None:
                current_state['minimap_pose'] = current_pose
                current_state['has_odom'] = True

    def state_callback(self, msg):
        state_value = str(msg.data).strip() or '대기'
        with state_lock:
            current_state['robot_state'] = state_value

    def send_emergency_stop(self, stop_signal: bool):
        msg = Bool()
        msg.data = stop_signal
        self.estop_pub.publish(msg)
        print(f'비상 정지 전송: {stop_signal}', flush=True)

    def send_waypoint(self, target_number):
        msg = Int32()
        msg.data = int(target_number)
        self.waypoint_pub.publish(msg)
        print(f'목표 구역 전송: {target_number}', flush=True)


ros_node = None


def run_ros_node():
    global ros_node
    rclpy.init()
    ros_node = Ec2ControlNode()
    rclpy.spin(ros_node)
    ros_node.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    ros_thread = threading.Thread(target=run_ros_node, daemon=True)
    ros_thread.start()

    print('FastAPI 웹 서버 시작 (포트 8000)...', flush=True)
    uvicorn.run(app, host='0.0.0.0', port=8000)
