import json
import math
import heapq
import matplotlib.pyplot as plt


MAP_PATH = "/home/user/S14P21A603/src/ros2_docker/workspace/unity_ros2_ws/workspace/src/lane_follow_pkg/TopologicalMap.json"


class TopologicalMapViewer:
    def __init__(self, map_path):
        self.map_path = map_path
        self.waypoints = {}   # {id: {'x': x, 'z': z}}
        self.edges = {}       # {id: [neighbor_id, ...]}
        self.load_map()

    def load_map(self):
        with open(self.map_path, "r") as f:
            data = json.load(f)

        raw_waypoints = data.get("waypoints", [])

        # waypoint 저장
        for wp in raw_waypoints:
            wp_id = wp["id"]
            self.waypoints[wp_id] = {
                "x": float(wp["x"]),
                "z": float(wp["z"])
            }
            self.edges[wp_id] = []

        # edge 저장
        for wp in raw_waypoints:
            wp_id = wp["id"]
            for neighbor in wp.get("connected_to", []):
                if neighbor == wp_id:
                    # self-loop 제거
                    print(f"[WARN] self-loop 제거: {wp_id} -> {neighbor}")
                    continue

                if neighbor not in self.waypoints:
                    print(f"[WARN] 없는 waypoint 연결 무시: {wp_id} -> {neighbor}")
                    continue

                if neighbor not in self.edges[wp_id]:
                    self.edges[wp_id].append(neighbor)

        # 양방향 보정
        for wp_id, neighbors in list(self.edges.items()):
            for neighbor in neighbors:
                if wp_id not in self.edges[neighbor]:
                    self.edges[neighbor].append(wp_id)

        total_edges = sum(len(v) for v in self.edges.values())
        print(f"[INFO] 맵 로드 완료: waypoint {len(self.waypoints)}개, edge {total_edges}개")

    def calculate_distance(self, id1, id2):
        x1, z1 = self.waypoints[id1]["x"], self.waypoints[id1]["z"]
        x2, z2 = self.waypoints[id2]["x"], self.waypoints[id2]["z"]
        return math.sqrt((x1 - x2) ** 2 + (z1 - z2) ** 2)

    def find_path_dijkstra(self, start_id, goal_id):
        if start_id not in self.waypoints:
            print(f"[ERROR] 시작 waypoint 없음: {start_id}")
            return []
        if goal_id not in self.waypoints:
            print(f"[ERROR] 목표 waypoint 없음: {goal_id}")
            return []

        distances = {wp: float("inf") for wp in self.waypoints}
        previous = {wp: None for wp in self.waypoints}
        distances[start_id] = 0.0

        pq = [(0.0, start_id)]

        while pq:
            current_dist, current_id = heapq.heappop(pq)

            if current_dist > distances[current_id]:
                continue

            if current_id == goal_id:
                break

            for neighbor in self.edges.get(current_id, []):
                weight = self.calculate_distance(current_id, neighbor)
                new_dist = current_dist + weight

                if new_dist < distances[neighbor]:
                    distances[neighbor] = new_dist
                    previous[neighbor] = current_id
                    heapq.heappush(pq, (new_dist, neighbor))

        if distances[goal_id] == float("inf"):
            print("[ERROR] 경로를 찾지 못했습니다.")
            return []

        path = []
        curr = goal_id
        while curr is not None:
            path.append(curr)
            curr = previous[curr]

        path.reverse()
        return path

    def draw_map(self, path=None, start_id=None, goal_id=None):
        plt.figure(figsize=(14, 10))

        # 전체 edge 회색으로
        drawn = set()
        for wp_id, neighbors in self.edges.items():
            x1, z1 = self.waypoints[wp_id]["x"], self.waypoints[wp_id]["z"]

            for neighbor in neighbors:
                edge_key = tuple(sorted([wp_id, neighbor]))
                if edge_key in drawn:
                    continue
                drawn.add(edge_key)

                x2, z2 = self.waypoints[neighbor]["x"], self.waypoints[neighbor]["z"]
                plt.plot([x1, x2], [z1, z2], linewidth=0.8, alpha=0.35)

        # 전체 waypoint
        xs = [v["x"] for v in self.waypoints.values()]
        zs = [v["z"] for v in self.waypoints.values()]
        plt.scatter(xs, zs, s=10, alpha=0.6, label="Waypoints")

        # 경로 강조
        if path and len(path) >= 2:
            path_x = [self.waypoints[wp]["x"] for wp in path]
            path_z = [self.waypoints[wp]["z"] for wp in path]
            plt.plot(path_x, path_z, linewidth=3, label="Dijkstra Path")
            plt.scatter(path_x, path_z, s=30)

            # waypoint 번호 표시
            for wp in path:
                x, z = self.waypoints[wp]["x"], self.waypoints[wp]["z"]
                plt.text(x, z, wp.split("_")[-1], fontsize=8)

        # 시작점 강조
        if start_id and start_id in self.waypoints:
            x, z = self.waypoints[start_id]["x"], self.waypoints[start_id]["z"]
            plt.scatter([x], [z], s=120, marker="o", label=f"Start: {start_id}")
            plt.text(x, z, f"START\n{start_id}", fontsize=10)

        # 목표점 강조
        if goal_id and goal_id in self.waypoints:
            x, z = self.waypoints[goal_id]["x"], self.waypoints[goal_id]["z"]
            plt.scatter([x], [z], s=160, marker="*", label=f"Goal: {goal_id}")
            plt.text(x, z, f"GOAL\n{goal_id}", fontsize=10)

        plt.title("Topological Map with Dijkstra Path")
        plt.xlabel("X")
        plt.ylabel("Z")
        plt.axis("equal")
        plt.grid(True, alpha=0.3)
        plt.legend()
        plt.tight_layout()
        plt.show()


def normalize_waypoint_input(user_input: str) -> str:
    user_input = user_input.strip()
    if user_input.startswith("Waypoint_"):
        return user_input
    return f"Waypoint_{user_input}"


def main():
    viewer = TopologicalMapViewer(MAP_PATH)

    start_input = input("시작 waypoint 번호 입력 (예: 5 또는 Waypoint_5): ").strip()
    goal_input = input("목표 waypoint 번호 입력 (예: 60 또는 Waypoint_60): ").strip()

    start_id = normalize_waypoint_input(start_input)
    goal_id = normalize_waypoint_input(goal_input)

    path = viewer.find_path_dijkstra(start_id, goal_id)

    if not path:
        print("[ERROR] 경로 생성 실패")
        return

    print("\n[INFO] 생성된 경로:")
    print(" -> ".join(path))

    viewer.draw_map(path=path, start_id=start_id, goal_id=goal_id)


if __name__ == "__main__":
    main()