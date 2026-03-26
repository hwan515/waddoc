import math

import cv2
import numpy as np


class DebugWindowManager:
    def __init__(self, enabled, logger):
        self.enabled = enabled
        self.logger = logger

    def show_window(self, name, image):
        if not self.enabled:
            return
        try:
            cv2.imshow(name, image)
        except cv2.error as error:
            self.enabled = False
            self.logger.warn(f'OpenCV GUI unavailable. Debug window disabled: {error}')

    def pump(self):
        if not self.enabled:
            return
        try:
            cv2.waitKey(1)
        except cv2.error as error:
            self.enabled = False
            self.logger.warn(f'OpenCV waitKey unavailable. Debug window disabled: {error}')


def build_topological_map_view(
    *,
    has_odom,
    waypoints,
    edges,
    path_ids,
    trajectory,
    goal_wp_id,
    current_pose,
    current_mode,
    vision_lane_error_px,
    vision_mask_pixels,
    goal_distance=None,
):
    size = 900
    canvas = np.full((size, size, 3), 245, dtype=np.uint8)

    if not has_odom:
        cv2.putText(
            canvas,
            'Waiting for odom...',
            (30, 50),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.0,
            (0, 0, 0),
            2,
        )
        return canvas

    cv2.putText(
        canvas,
        f'wp_count={len(waypoints)}',
        (20, 170),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.60,
        (20, 20, 20),
        2,
        cv2.LINE_AA,
    )

    xs = [current_pose.x]
    zs = [current_pose.z]

    for waypoint in waypoints.values():
        xs.append(waypoint['x'])
        zs.append(waypoint['z'])

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

    for waypoint_id, neighbors in edges.items():
        waypoint = waypoints[waypoint_id]
        p1 = to_px(waypoint['x'], waypoint['z'])
        for neighbor_id in neighbors:
            if neighbor_id not in waypoints:
                continue
            neighbor = waypoints[neighbor_id]
            p2 = to_px(neighbor['x'], neighbor['z'])
            cv2.line(canvas, p1, p2, (200, 200, 200), 1)

    if len(path_ids) >= 2:
        for index in range(len(path_ids) - 1):
            waypoint_a = waypoints[path_ids[index]]
            waypoint_b = waypoints[path_ids[index + 1]]
            p1 = to_px(waypoint_a['x'], waypoint_a['z'])
            p2 = to_px(waypoint_b['x'], waypoint_b['z'])
            cv2.line(canvas, p1, p2, (0, 0, 255), 3)

    if len(trajectory) >= 2:
        for index in range(len(trajectory) - 1):
            p1 = to_px(trajectory[index][0], trajectory[index][1])
            p2 = to_px(trajectory[index + 1][0], trajectory[index + 1][1])
            cv2.line(canvas, p1, p2, (255, 140, 0), 1)

    for waypoint_id, waypoint in waypoints.items():
        pt = to_px(waypoint['x'], waypoint['z'])

        color = (80, 80, 80)
        if waypoint_id == goal_wp_id:
            color = (0, 0, 255)
        if waypoint_id in path_ids:
            color = (255, 0, 255)

        cv2.circle(canvas, pt, 7, color, -1)
        cv2.putText(
            canvas,
            f'{waypoint_id}',
            (pt[0] + 7, pt[1] - 7),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.40,
            (30, 30, 30),
            1,
            cv2.LINE_AA,
        )

    car_pt = to_px(current_pose.x, current_pose.z)
    cv2.circle(canvas, car_pt, 10, (255, 0, 0), -1)

    arrow_len = 30
    end_x = int(car_pt[0] + arrow_len * math.sin(current_pose.yaw))
    end_y = int(car_pt[1] - arrow_len * math.cos(current_pose.yaw))
    cv2.arrowedLine(canvas, car_pt, (end_x, end_y), (255, 0, 0), 3, tipLength=0.35)

    y = 28
    cv2.putText(canvas, f'Mode: {current_mode}', (20, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.75, (20, 20, 20), 2, cv2.LINE_AA)
    y += 30

    cv2.putText(canvas, f'Pose: x={current_pose.x:.1f}, z={current_pose.z:.1f}', (20, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.60, (20, 20, 20), 2, cv2.LINE_AA)
    y += 28

    cv2.putText(canvas, f'goal={goal_wp_id}', (20, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)
    y += 28

    cv2.putText(canvas, f'path_len={len(path_ids)}, traj_len={len(trajectory)}', (20, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)
    y += 28

    lane_err_str = 'None' if vision_lane_error_px is None else f'{vision_lane_error_px:.1f}'
    cv2.putText(canvas, f'lane_err={lane_err_str}, mask={vision_mask_pixels}', (20, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)

    if goal_distance is not None:
        y += 28
        cv2.putText(canvas, f'goal_dist={goal_distance:.1f}', (20, y),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.56, (20, 20, 20), 2, cv2.LINE_AA)

    if current_mode == 'goal_reached':
        cv2.putText(canvas, 'GOAL REACHED', (250, 70),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 255), 3, cv2.LINE_AA)

    return canvas
