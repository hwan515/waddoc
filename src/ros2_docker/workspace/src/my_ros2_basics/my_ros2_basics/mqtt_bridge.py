import json
import math
import os
import ssl
import threading

import paho.mqtt.client as mqtt
import rclpy
from nav_msgs.msg import Odometry
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy, qos_profile_sensor_data
from std_msgs.msg import Bool, Int32, String

MQTT_BROKER_HOST = os.getenv('MQTT_BROKER_HOST', 'waddoc.site')
MQTT_BROKER_PORT = int(os.getenv('MQTT_BROKER_PORT', '443'))
MQTT_WS_PATH = os.getenv('MQTT_WS_PATH', '/mqtt')

TOPIC_ODOM = 'robot/odom'
TOPIC_MINIMAP = 'robot/minimap'
TOPIC_STATE = 'robot/state'
TOPIC_STATUS = 'robot/status'
TOPIC_CMD_ESTOP = 'robot/cmd/estop'
TOPIC_CMD_WAYPOINT = 'robot/cmd/waypoint'

ODOM_PUBLISH_HZ = 1.0


def _normalize_angle(angle: float) -> float:
    while angle > math.pi:
        angle -= 2.0 * math.pi
    while angle < -math.pi:
        angle += 2.0 * math.pi
    return angle


class MqttBridgeNode(Node):
    def __init__(self):
        super().__init__('mqtt_bridge')

        self._last_odom = None
        self._last_state = None
        self._lock = threading.Lock()

        # ROS2 구독
        self.create_subscription(
            Odometry, '/odom', self._odom_callback, qos_profile_sensor_data
        )
        self.create_subscription(
            String, '/ec2_state/minimap_route',
            self._minimap_callback,
            QoSProfile(depth=10, reliability=ReliabilityPolicy.BEST_EFFORT),
        )
        self.create_subscription(
            String, '/state',
            self._state_callback,
            QoSProfile(depth=10, reliability=ReliabilityPolicy.BEST_EFFORT),
        )

        # ROS2 발행 (MQTT → ROS2 명령)
        self._estop_pub = self.create_publisher(Bool, '/ec2_cmd/e_stop', 1)
        self._waypoint_pub = self.create_publisher(Int32, '/ec2_cmd/target_waypoint', 1)

        # odom 1Hz 타이머
        self.create_timer(1.0 / ODOM_PUBLISH_HZ, self._publish_odom)

        # MQTT 클라이언트 초기화
        self._mqtt = mqtt.Client(transport='websockets')
        self._mqtt.ws_set_options(path=MQTT_WS_PATH)
        self._mqtt.tls_set(cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLS)

        self._mqtt.will_set(
            TOPIC_STATUS,
            json.dumps({'online': False}),
            qos=1,
            retain=True,
        )
        self._mqtt.on_connect = self._on_mqtt_connect
        self._mqtt.on_message = self._on_mqtt_message
        self._mqtt.on_disconnect = self._on_mqtt_disconnect

        self._mqtt.connect_async(MQTT_BROKER_HOST, MQTT_BROKER_PORT)
        self._mqtt.loop_start()

        self.get_logger().info(
            f'MQTT bridge 시작: wss://{MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}{MQTT_WS_PATH}'
        )

    # ── MQTT 콜백 ──────────────────────────────────────────────────────────

    def _on_mqtt_connect(self, client, userdata, flags, rc):
        if rc != 0:
            self.get_logger().error(f'MQTT 연결 실패: rc={rc}')
            return
        self.get_logger().info('MQTT 연결 성공')
        client.publish(TOPIC_STATUS, json.dumps({'online': True}), qos=1, retain=True)
        client.subscribe([(TOPIC_CMD_ESTOP, 1), (TOPIC_CMD_WAYPOINT, 1)])

    def _on_mqtt_disconnect(self, client, userdata, rc):
        self.get_logger().warning(f'MQTT 연결 끊김: rc={rc} — 자동 재연결 대기 중')

    def _on_mqtt_message(self, client, userdata, msg):
        topic = msg.topic
        try:
            payload = msg.payload.decode('utf-8').strip()
        except Exception as e:
            self.get_logger().error(f'MQTT 페이로드 디코딩 실패: {e}')
            return

        if topic == TOPIC_CMD_ESTOP:
            ros_msg = Bool()
            ros_msg.data = payload in ('1', 'true', 'True')
            self._estop_pub.publish(ros_msg)
            self.get_logger().info(f'E-Stop 전달: {ros_msg.data}')

        elif topic == TOPIC_CMD_WAYPOINT:
            try:
                ros_msg = Int32()
                ros_msg.data = int(payload)
                self._waypoint_pub.publish(ros_msg)
                self.get_logger().info(f'웨이포인트 전달: {ros_msg.data}')
            except ValueError:
                self.get_logger().error(f'웨이포인트 값 오류: {payload!r}')

    # ── ROS2 콜백 ──────────────────────────────────────────────────────────

    def _odom_callback(self, msg: Odometry):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation
        linear = msg.twist.twist.linear

        siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        yaw = math.atan2(siny_cosp, cosy_cosp)

        speed_ms = math.sqrt(linear.x ** 2 + linear.y ** 2 + linear.z ** 2)

        with self._lock:
            self._last_odom = {
                'x': round(pos.x, 3),
                'y': round(pos.y, 3),
                'z': round(pos.z, 3),
                'speed_ms': round(speed_ms, 4),
                'speed_kmh': round(speed_ms * 3.6, 4),
                'minimap_pose': {
                    'x': round(-pos.y, 3),
                    'z': round(pos.x, 3),
                    'yaw': round(_normalize_angle(yaw + math.pi), 6),
                },
            }

    def _publish_odom(self):
        with self._lock:
            payload = self._last_odom
        if payload is None:
            return
        self._mqtt.publish(TOPIC_ODOM, json.dumps(payload), qos=1)

    def _minimap_callback(self, msg: String):
        self._mqtt.publish(TOPIC_MINIMAP, msg.data, qos=1)

    def _state_callback(self, msg: String):
        new_state = msg.data
        if new_state == self._last_state:
            return
        self._last_state = new_state
        self._mqtt.publish(TOPIC_STATE, json.dumps({'state': new_state}), qos=1)

    def destroy_node(self):
        self._mqtt.loop_stop()
        self._mqtt.disconnect()
        super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    node = MqttBridgeNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()
