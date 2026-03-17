import cv2
import numpy as np

import rclpy
from rclpy.node import Node

from sensor_msgs.msg import Image
from geometry_msgs.msg import Twist
from cv_bridge import CvBridge


class SimpleLaneFollower(Node):
    def __init__(self):
        super().__init__('simple_lane_follower')

        self.bridge = CvBridge()

        self.image_sub = self.create_subscription(
            Image,
            '/camera/image_raw',
            self.image_callback,
            10
        )

        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', 10)

        # =========================
        # 사용자 설정값
        # =========================
        self.cruise_speed = 4.2          # 기본 직진 속도
        self.min_linear_speed = 2.2      # 급회전 시에도 유지할 최소 속도
        self.max_angular = 4.0           # 최대 회전값
        self.center_kp = 1.3             # 도로 폭 중심 정렬 gain
        self.theta_kp = 2.6              # 진행 방향(theta) 비례 gain
        self.curvature_kp = 0.9          # 곡률 보정 gain
        self.curvature_scale = 140.0     # 픽셀 곡률 스케일
        self.lookahead_ratio = 0.42      # 스플라인 전방 주시 거리 비율
        self.turn_speed_reduction = 0.45
        self.max_theta_for_speed = np.deg2rad(30.0)
        self.angular_filter_alpha = 0.35
        self.prev_angular = 0.0

        # =========================
        # ROI 설정
        # 언덕에서도 더 멀리 보기 위해 ROI를 조금 더 넓힘
        # =========================
        self.roi_y_ratio = 0.18
        self.roi_height_ratio = 0.72

        # =========================
        # 중심선 샘플링 / 스플라인 설정
        # =========================
        self.scan_line_count = 9
        self.min_road_width_pixels = 30
        self.spline_samples_per_segment = 15

        # =========================
        # HSV 범위 (갈색 도로 기준)
        # 필요하면 여기만 조절
        # =========================
        self.lower_hsv = np.array([6, 90, 70], dtype=np.uint8)
        self.upper_hsv = np.array([24, 255, 210], dtype=np.uint8)

        self.kernel = np.ones((5, 5), np.uint8)

        self.get_logger().info('simple_lane_follower started')

    def image_callback(self, msg):
        frame = self.bridge.imgmsg_to_cv2(msg, desired_encoding='bgr8')
        h, w = frame.shape[:2]

        # =========================
        # ROI 자르기
        # =========================
        y0 = int(h * self.roi_y_ratio)
        roi_h = int(h * self.roi_height_ratio)
        y1 = min(h, y0 + roi_h)

        roi = frame[y0:y1, :].copy()
        if roi.size == 0:
            self.publish_stop()
            return

        # =========================
        # 도로 마스크
        # =========================
        hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, self.lower_hsv, self.upper_hsv)

        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, self.kernel)
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, self.kernel)

        # 가장 큰 덩어리만 남김
        mask = self.keep_largest_component(mask)

        vis = roi.copy()
        roi_center_x = roi.shape[1] // 2
        samples = self.extract_center_samples(mask)

        for center_x, y, left_x, right_x, _ in samples:
            iy = int(round(y))
            ic = int(round(center_x))
            cv2.line(vis, (0, iy), (roi.shape[1] - 1, iy), (100, 100, 100), 1)
            cv2.circle(vis, (int(left_x), iy), 4, (255, 0, 0), -1)
            cv2.circle(vis, (int(right_x), iy), 4, (0, 0, 255), -1)
            cv2.circle(vis, (ic, iy), 5, (0, 255, 0), -1)

        twist = Twist()

        # 기준선 표시
        cv2.line(vis, (roi_center_x, 0), (roi_center_x, roi.shape[0] - 1), (0, 255, 255), 2)

        if len(samples) >= 2:
            ordered_samples = np.asarray(
                sorted(samples, key=lambda item: item[1], reverse=True),
                dtype=np.float32
            )

            center_points = ordered_samples[:, 0:2]
            road_widths = ordered_samples[:, 4]

            centerline = self.build_centerline_spline(center_points)
            centerline[:, 0] = np.clip(centerline[:, 0], 0, roi.shape[1] - 1)
            centerline[:, 1] = np.clip(centerline[:, 1], 0, roi.shape[0] - 1)

            centerline, arc_lengths, curvatures = self.compute_path_geometry(centerline)

            if len(centerline) >= 2:
                near_point, lookahead_point, lateral_error, theta, curvature_term = \
                    self.compute_follow_metrics(
                        centerline,
                        arc_lengths,
                        curvatures,
                        road_widths,
                        roi_center_x
                    )

                raw_angular = -(
                    self.center_kp * lateral_error +
                    self.theta_kp * theta +
                    self.curvature_kp * curvature_term
                )
                angular = float(np.clip(raw_angular, -self.max_angular, self.max_angular))
                angular = (
                    self.angular_filter_alpha * self.prev_angular +
                    (1.0 - self.angular_filter_alpha) * angular
                )
                self.prev_angular = angular

                turn_ratio = min(
                    1.0,
                    0.4 * min(abs(lateral_error), 1.0) +
                    0.4 * min(abs(theta) / self.max_theta_for_speed, 1.0) +
                    0.2 * min(abs(curvature_term), 1.0)
                )

                twist.linear.x = max(
                    self.min_linear_speed,
                    self.cruise_speed * (1.0 - self.turn_speed_reduction * turn_ratio)
                )
                twist.angular.z = angular

                path_pixels = np.round(centerline).astype(np.int32).reshape(-1, 1, 2)
                cv2.polylines(vis, [path_pixels], False, (255, 255, 0), 2)

                cv2.circle(
                    vis,
                    tuple(np.round(near_point).astype(np.int32)),
                    7,
                    (0, 255, 255),
                    -1
                )
                cv2.circle(
                    vis,
                    tuple(np.round(lookahead_point).astype(np.int32)),
                    7,
                    (255, 0, 255),
                    -1
                )
                cv2.line(
                    vis,
                    tuple(np.round(near_point).astype(np.int32)),
                    tuple(np.round(lookahead_point).astype(np.int32)),
                    (255, 200, 0),
                    2
                )

                cv2.putText(
                    vis,
                    (
                        f'center_err={lateral_error:.3f} '
                        f'theta={np.degrees(theta):.1f}deg '
                        f'curv={curvature_term:.3f}'
                    ),
                    (20, 30),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.65,
                    (255, 255, 255),
                    2
                )
                cv2.putText(
                    vis,
                    f'linear={twist.linear.x:.2f} angular={twist.angular.z:.2f}',
                    (20, 60),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.65,
                    (255, 255, 255),
                    2
                )
            else:
                self.prev_angular = 0.0
                twist.linear.x = 0.0
                twist.angular.z = 0.0
        else:
            # 못 찾으면 정지
            self.prev_angular = 0.0
            twist.linear.x = 0.0
            twist.angular.z = 0.0

            cv2.putText(
                vis,
                'lane not found',
                (20, 30),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 0, 255),
                2
            )

        self.cmd_pub.publish(twist)

        # =========================
        # 디버그 출력
        # =========================
        frame_vis = frame.copy()
        cv2.rectangle(frame_vis, (0, y0), (w - 1, y1), (0, 0, 255), 2)
        cv2.line(frame_vis, (w // 2, 0), (w // 2, h - 1), (255, 255, 0), 1)

        mask_vis = cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR)

        cv2.imshow('camera', frame_vis)
        cv2.imshow('roi_follow', vis)
        cv2.imshow('mask', mask_vis)
        cv2.waitKey(1)

    def extract_center_samples(self, mask):
        sample_rows = np.linspace(
            int(mask.shape[0] * 0.12),
            int(mask.shape[0] * 0.95),
            self.scan_line_count
        ).astype(int)

        samples = []

        for y in sample_rows:
            y = int(np.clip(y, 0, mask.shape[0] - 1))
            row = mask[y, :]
            xs = np.flatnonzero(row > 0)

            if xs.size < self.min_road_width_pixels:
                continue

            left_x = int(xs[0])
            right_x = int(xs[-1])
            width = right_x - left_x

            if width < self.min_road_width_pixels:
                continue

            center_x = 0.5 * (left_x + right_x)
            samples.append((center_x, float(y), left_x, right_x, float(width)))

        return samples

    def build_centerline_spline(self, center_points):
        points = np.asarray(center_points, dtype=np.float32)

        if len(points) < 2:
            return points

        if len(points) == 2:
            t_values = np.linspace(0.0, 1.0, self.spline_samples_per_segment)
            return points[0] + (points[1] - points[0]) * t_values[:, None]

        padded = np.vstack((points[0], points, points[-1]))
        spline_points = []

        for i in range(1, len(padded) - 2):
            p0 = padded[i - 1]
            p1 = padded[i]
            p2 = padded[i + 1]
            p3 = padded[i + 2]

            t_values = np.linspace(
                0.0,
                1.0,
                self.spline_samples_per_segment,
                endpoint=(i == len(padded) - 3)
            )

            if i > 1:
                t_values = t_values[1:]

            for t in t_values:
                spline_points.append(self.catmull_rom_point(p0, p1, p2, p3, t))

        return np.asarray(spline_points, dtype=np.float32)

    def catmull_rom_point(self, p0, p1, p2, p3, t):
        t2 = t * t
        t3 = t2 * t

        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )

    def compute_path_geometry(self, centerline):
        path = np.asarray(centerline, dtype=np.float32)

        if len(path) < 2:
            return path, np.zeros(len(path), dtype=np.float32), np.zeros(len(path), dtype=np.float32)

        step_lengths = np.linalg.norm(np.diff(path, axis=0), axis=1)
        keep_mask = np.concatenate(([True], step_lengths > 1e-3))
        path = path[keep_mask]

        if len(path) < 2:
            return path, np.zeros(len(path), dtype=np.float32), np.zeros(len(path), dtype=np.float32)

        arc_lengths = np.concatenate(([0.0], np.cumsum(np.linalg.norm(np.diff(path, axis=0), axis=1))))

        if len(path) < 3 or arc_lengths[-1] <= 1e-3:
            curvatures = np.zeros(len(path), dtype=np.float32)
            return path, arc_lengths, curvatures

        edge_order = 2 if len(path) >= 5 else 1
        dx = np.gradient(path[:, 0], arc_lengths, edge_order=edge_order)
        dy = np.gradient(path[:, 1], arc_lengths, edge_order=edge_order)
        ddx = np.gradient(dx, arc_lengths, edge_order=edge_order)
        ddy = np.gradient(dy, arc_lengths, edge_order=edge_order)

        denom = np.power(dx * dx + dy * dy, 1.5)
        denom = np.maximum(denom, 1e-6)
        curvatures = (dx * ddy - dy * ddx) / denom

        return path, arc_lengths, curvatures

    def compute_follow_metrics(self, centerline, arc_lengths, curvatures, road_widths, roi_center_x):
        total_length = float(arc_lengths[-1]) if len(arc_lengths) > 0 else 0.0

        if total_length <= 1e-3:
            near_point = centerline[0]
            return near_point, near_point, 0.0, 0.0, 0.0

        near_count = min(4, len(centerline))
        near_point = np.mean(centerline[:near_count], axis=0)

        near_width = float(np.mean(road_widths[:min(3, len(road_widths))]))
        half_width = max(near_width * 0.5, 20.0)
        lateral_error = float(np.clip((near_point[0] - roi_center_x) / half_width, -1.5, 1.5))

        lookahead_distance = np.clip(
            max(35.0, total_length * self.lookahead_ratio),
            1.0,
            total_length
        )
        lookahead_index = int(np.searchsorted(arc_lengths, lookahead_distance))
        lookahead_index = int(np.clip(lookahead_index, 1, len(centerline) - 1))

        lookahead_point = centerline[lookahead_index]

        dx = float(lookahead_point[0] - near_point[0])
        forward_distance = max(float(near_point[1] - lookahead_point[1]), 1.0)
        theta = float(np.arctan2(dx, forward_distance))

        curvature_scaled = float(
            np.clip(curvatures[lookahead_index] * self.curvature_scale, -1.0, 1.0)
        )

        return near_point, lookahead_point, lateral_error, theta, curvature_scaled

    def keep_largest_component(self, mask):
        num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)

        if num_labels <= 1:
            return np.zeros_like(mask)

        largest_label = 1
        largest_area = stats[1, cv2.CC_STAT_AREA]

        for i in range(2, num_labels):
            area = stats[i, cv2.CC_STAT_AREA]
            if area > largest_area:
                largest_area = area
                largest_label = i

        output = np.zeros_like(mask)
        output[labels == largest_label] = 255
        return output

    def publish_stop(self):
        twist = Twist()
        twist.linear.x = 0.0
        twist.angular.z = 0.0
        self.prev_angular = 0.0
        self.cmd_pub.publish(twist)


def main(args=None):
    rclpy.init(args=args)
    node = SimpleLaneFollower()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        stop_msg = Twist()
        node.cmd_pub.publish(stop_msg)
        cv2.destroyAllWindows()
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
