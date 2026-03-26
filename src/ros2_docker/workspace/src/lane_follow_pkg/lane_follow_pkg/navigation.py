import heapq
import json
import math

from .config import PathPlannerConfig
from .models import PlannedRoute, Pose2D


def normalize_angle(angle):
    while angle > math.pi:
        angle -= 2.0 * math.pi
    while angle < -math.pi:
        angle += 2.0 * math.pi
    return angle


def clamp(value, min_value, max_value):
    return max(min_value, min(max_value, value))


def dist_xy(x1, z1, x2, z2):
    return math.sqrt((x1 - x2) ** 2 + (z1 - z2) ** 2)


class TopologicalRoutePlanner:
    def __init__(self, config: PathPlannerConfig):
        self.config = config
        self.waypoints = {}
        self.edges = {}

    def load_map(self, filepath):
        with open(filepath, 'r', encoding='utf-8') as handle:
            data = json.load(handle)

        waypoints = {}
        edges = {}
        for waypoint in data.get('waypoints', []):
            waypoint_id = waypoint['id']
            waypoints[waypoint_id] = {
                'x': float(waypoint['x']),
                'z': float(waypoint['z']),
            }
            edges[waypoint_id] = list(waypoint.get('connected_to', []))

        self.waypoints = waypoints
        self.edges = edges

    def calculate_distance_between_waypoints(self, id1, id2):
        x1, z1 = self.waypoints[id1]['x'], self.waypoints[id1]['z']
        x2, z2 = self.waypoints[id2]['x'], self.waypoints[id2]['z']
        return dist_xy(x1, z1, x2, z2)

    def get_heading_error_to_xy_deg(self, pose: Pose2D, tx, tz):
        dx = tx - pose.x
        dz = tz - pose.z
        target_yaw = math.atan2(dx, dz)
        error = normalize_angle(target_yaw - pose.yaw)
        return math.degrees(error)

    def get_candidate_waypoints(self, pose: Pose2D, max_candidates=8):
        candidates = []
        for waypoint_id, coords in self.waypoints.items():
            distance = dist_xy(pose.x, pose.z, coords['x'], coords['z'])
            candidates.append((distance, waypoint_id))
        candidates.sort(key=lambda item: item[0])
        return candidates[:max_candidates]

    def choose_better_start_waypoint(self, pose: Pose2D, goal_id):
        candidates = self.get_candidate_waypoints(pose, max_candidates=8)

        best_id = None
        best_score = float('inf')

        for distance, waypoint_id in candidates:
            waypoint_x = self.waypoints[waypoint_id]['x']
            waypoint_z = self.waypoints[waypoint_id]['z']
            heading_err = abs(self.get_heading_error_to_xy_deg(pose, waypoint_x, waypoint_z))
            goal_dist = self.calculate_distance_between_waypoints(waypoint_id, goal_id)

            behind_penalty = 1000.0 if heading_err > self.config.behind_angle_deg else 0.0
            score = distance + 0.3 * heading_err + 0.02 * goal_dist + behind_penalty

            if score < best_score:
                best_score = score
                best_id = waypoint_id

        return best_id

    def find_path_dijkstra(self, start_id, goal_id):
        distances = {waypoint: float('inf') for waypoint in self.waypoints}
        distances[start_id] = 0.0
        previous_nodes = {waypoint: None for waypoint in self.waypoints}
        priority_queue = [(0.0, start_id)]

        while priority_queue:
            current_dist, current_id = heapq.heappop(priority_queue)

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
                    heapq.heappush(priority_queue, (new_dist, neighbor))

        path = []
        current = goal_id
        while current is not None:
            path.append(current)
            current = previous_nodes[current]
        path.reverse()

        if not path or path[0] != start_id:
            return []

        return path

    def waypoint_path_to_points(self, path_ids):
        return [
            (self.waypoints[waypoint_id]['x'], self.waypoints[waypoint_id]['z'])
            for waypoint_id in path_ids
        ]

    def catmull_rom(self, p0, p1, p2, p3, t):
        t2 = t * t
        t3 = t2 * t

        x = 0.5 * (
            (2.0 * p1[0])
            + (-p0[0] + p2[0]) * t
            + (2.0 * p0[0] - 5.0 * p1[0] + 4.0 * p2[0] - p3[0]) * t2
            + (-p0[0] + 3.0 * p1[0] - 3.0 * p2[0] + p3[0]) * t3
        )
        z = 0.5 * (
            (2.0 * p1[1])
            + (-p0[1] + p2[1]) * t
            + (2.0 * p0[1] - 5.0 * p1[1] + 4.0 * p2[1] - p3[1]) * t2
            + (-p0[1] + 3.0 * p1[1] - 3.0 * p2[1] + p3[1]) * t3
        )
        return (x, z)

    def build_spline_trajectory(self, path_ids):
        raw_points = self.waypoint_path_to_points(path_ids)

        if len(raw_points) == 0:
            return []

        if len(raw_points) == 1:
            return [raw_points[0]]

        if len(raw_points) == 2:
            trajectory = []
            point_a = raw_points[0]
            point_b = raw_points[1]
            for index in range(self.config.samples_per_segment + 1):
                ratio = index / float(self.config.samples_per_segment)
                x = point_a[0] + (point_b[0] - point_a[0]) * ratio
                z = point_a[1] + (point_b[1] - point_a[1]) * ratio
                trajectory.append((x, z))
            return trajectory

        extended = [raw_points[0]] + raw_points + [raw_points[-1]]

        trajectory = []
        for index in range(1, len(extended) - 2):
            p0 = extended[index - 1]
            p1 = extended[index]
            p2 = extended[index + 1]
            p3 = extended[index + 2]

            for sample_index in range(self.config.samples_per_segment):
                ratio = sample_index / float(self.config.samples_per_segment)
                trajectory.append(self.catmull_rom(p0, p1, p2, p3, ratio))

        trajectory.append(raw_points[-1])

        filtered = [trajectory[0]]
        for point in trajectory[1:]:
            if dist_xy(filtered[-1][0], filtered[-1][1], point[0], point[1]) > 0.5:
                filtered.append(point)

        return filtered

    def plan_route(self, pose: Pose2D, goal_id):
        start_id = self.choose_better_start_waypoint(pose, goal_id)
        if start_id is None:
            return None, 'start_selection_failed'

        path = self.find_path_dijkstra(start_id, goal_id)
        if not path:
            return None, 'path_not_found'

        if len(path) >= 2 and path[0] == start_id:
            path = path[1:]

        if not path:
            path = [goal_id]

        trajectory = self.build_spline_trajectory(path)
        if not trajectory:
            return None, 'trajectory_not_found'

        return PlannedRoute(
            start_id=start_id,
            goal_id=goal_id,
            path_ids=path,
            trajectory=trajectory,
        ), None
