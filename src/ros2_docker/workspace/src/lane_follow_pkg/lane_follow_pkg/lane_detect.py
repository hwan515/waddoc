
import json
import math
import time
import heapq
from pathlib import Path

import rclpy
from rclpy.node import Node
from geometry_msgs.msg import Twist
from nav_msgs.msg import Odometry
from std_msgs.msg import Bool, Int32

try:
    from ament_index_python.packages import get_package_share_directory
except ImportError:
    get_package_share_directory = None

class DijkstraNavigator(Node):
    def __init__(self):
        super().__init__('dijkstra_navigator')
        self.get_logger().info("🚀 dijkstra_navigator 노드 가동 시작")
        
        # --- 구독자 및 발행자 ---
        self.sub_odom = self.create_subscription(Odometry, '/odom', self.odom_callback, 10)
        self.sub_estop = self.create_subscription(Bool, '/ec2_cmd/e_stop', self.cmd_callback, 1)
        self.sub_target = self.create_subscription(Int32, '/ec2_cmd/target_waypoint', self.target_waypoint_callback, 1)
        
        self.pub_vel = self.create_publisher(Twist, '/cmd_vel', 10)
        self.pub_state_stop = self.create_publisher(Int32, '/cmd_state/e_stop', 1)
        self.pub_state_waypoint = self.create_publisher(Int32, '/cmd_state/target_waypoint', 1)

        self.timer = self.create_timer(0.05, self.control_loop)
        self.timer.cancel()

        self.current_x = 0.0
        self.current_z = 0.0
        self.current_yaw = 0.0
        self.has_odom = False

        self.waypoints = {}      
        self.edges = {}          
        self.path = []           
        self.current_target_id = None
        self.target_x = None
        self.target_z = None
        self.last_log_time = 0.0

        # 지도 로드 (설치/소스 실행 모두 대응)
        self.load_map(str(self.resolve_map_path()))

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
            with open(filepath, 'r') as f:
                data = json.load(f)
            
            for wp in data.get('waypoints', []):
                wp_id = str(wp['id']).strip() # 공백 제거 및 문자열 강제화
                self.waypoints[wp_id] = {'x': wp['x'], 'z': wp['z']}
                self.edges[wp_id] = []
            
            for wp in data.get('waypoints', []):
                wp_id = str(wp['id']).strip()
                for connected_node in wp.get('connected_to', []):
                    c_id = str(connected_node).strip()
                    if c_id not in self.edges[wp_id]:
                        self.edges[wp_id].append(c_id)
                    # 양방향성 강제 보장
                    if c_id in self.edges and wp_id not in self.edges[c_id]:
                        self.edges[c_id].append(wp_id)
                        
            self.get_logger().info(f"🗺️ 맵 로드 완료: {len(self.waypoints)}개의 웨이포인트")
            
        except Exception as e:
            self.get_logger().error(f"Map load failed: {e}")

    def odom_callback(self, msg):
        pos = msg.pose.pose.position
        q = msg.pose.pose.orientation
        self.current_z = pos.x
        self.current_x = -pos.y
        siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        self.current_yaw = math.atan2(siny_cosp, cosy_cosp)
        self.has_odom = True

    def cmd_callback(self, msg):
        if msg.data == 1:
            self.timer.cancel()
            self.get_logger().warn("🚨 EMERGENCY STOP!")
            self.pub_vel.publish(Twist()) 
            self.path = []
            self.current_target_id = None

    def target_waypoint_callback(self, msg):
        if self.current_target_id is None:
            self.get_logger().info(f"🎯 목표 구역 수신: {msg.data}")
            goal_id = f"Waypoint_{msg.data}"
            
            if goal_id in self.waypoints:
                start_id = self.get_closest_waypoint()
                self.get_logger().info(f"🗺️ 경로 계산 시작: {start_id} -> {goal_id}")
                
                self.path = self.find_path_dijkstra(start_id, goal_id)
                
                if self.path:
                    self.timer.reset()
                    self.set_next_target()
                else:
                    self.get_logger().error("❌ 가능 경로가 없습니다.")
            else:
                self.get_logger().error(f"❌ 존재하지 않는 웨이포인트: {goal_id}")

    def get_closest_waypoint(self):
        min_dist = float('inf')
        closest_id = None
        for wp_id, coords in self.waypoints.items():
            dist = math.sqrt((self.current_x - coords['x'])**2 + (self.current_z - coords['z'])**2)
            if dist < min_dist:
                min_dist = dist
                closest_id = wp_id
        return closest_id

    def calculate_distance(self, id1, id2):
        x1, z1 = self.waypoints[id1]['x'], self.waypoints[id1]['z']
        x2, z2 = self.waypoints[id2]['x'], self.waypoints[id2]['z']
        return math.sqrt((x1 - x2)**2 + (z1 - z2)**2)

    def find_path_dijkstra(self, start_id, goal_id):
        # 다익스트라 초기화
        distances = {wp: float('inf') for wp in self.waypoints}
        distances[start_id] = 0
        previous_nodes = {wp: None for wp in self.waypoints}
        pq = [(0, start_id)]

        while pq:
            current_dist, current_id = heapq.heappop(pq)
            if current_id == goal_id: break
            if current_dist > distances[current_id]: continue

            for neighbor in self.edges.get(current_id, []):
                weight = self.calculate_distance(current_id, neighbor)
                new_dist = current_dist + weight
                if new_dist < distances[neighbor]:
                    distances[neighbor] = new_dist
                    previous_nodes[neighbor] = current_id
                    heapq.heappush(pq, (new_dist, neighbor))

        # 경로 역추적
        path = []
        curr = goal_id
        while curr is not None:
            path.append(curr)
            curr = previous_nodes[curr]
        path.reverse()

        # [최종 검증]
        if not path or path[0] != start_id or path[-1] != goal_id:
            self.get_logger().error(f"❌ 경로 생성 실패: {start_id}에서 {goal_id}로 갈 수 없습니다.")
            return []

        self.get_logger().info(f"✅ 경로 확정: {' -> '.join(path)}")
        return path

    def set_next_target(self):
        if self.path:
            self.current_target_id = self.path.pop(0)
            self.target_x = self.waypoints[self.current_target_id]['x']
            self.target_z = self.waypoints[self.current_target_id]['z']
            self.get_logger().info(f"📍 다음 경유지: {self.current_target_id}")
        else:
            self.current_target_id = None
            self.get_logger().info("🏁 목적지에 도착했습니다!")
            self.pub_vel.publish(Twist())
            self.timer.cancel()

    def control_loop(self):
        if not self.has_odom or self.current_target_id is None:
            return

        dx = self.target_x - self.current_x
        dz = self.target_z - self.current_z
        dist = math.sqrt(dx**2 + dz**2)

        # 2.5m 이내 도착 시 다음 지점으로
        if dist <15.0:
            self.set_next_target()
            return

        # 조향 제어
        target_yaw = math.atan2(dx, dz)
        error = target_yaw - self.current_yaw
        while error > math.pi: error -= 2 * math.pi
        while error < -math.pi: error += 2 * math.pi

        twist = Twist()
        twist.linear.x = 4.0
        twist.angular.z = float(max(-3.0, min(3.0, error * 1.8)))
        self.pub_vel.publish(twist)

        # 1초 주기로 상태 로깅
        

def main(args=None):
    rclpy.init(args=args)
    node = DijkstraNavigator()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()

if __name__ == '__main__':
    main()