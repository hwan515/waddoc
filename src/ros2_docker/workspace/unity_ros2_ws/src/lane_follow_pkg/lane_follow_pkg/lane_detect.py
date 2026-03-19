import cv2
import numpy as np

import rclpy
from rclpy.node import Node

from sensor_msgs.msg import Image
from geometry_msgs.msg import Twist
from cv_bridge import CvBridge


class RoadBoundaryFollower(Node):
    def __init__(self):
        super().__init__('road_boundary_follower')

        self.bridge = CvBridge()

        self.image_sub = self.create_subscription(
            Image,
            '/camera/image_raw',
            self.image_callback,
            10
        )

        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', 10)

        # -----------------------------
        # 제어 파라미터
        # 코너에서 덜 보수적으로
        # -----------------------------
        self.k_pos = 0.014
        self.kd = 0.0025
        self.max_angular = 1.2

        self.base_speed = 1.0
        self.min_speed = 0.55

        self.prev_error = 0.0
        self.prev_target = None

        # -----------------------------
        # ROI
        # -----------------------------
        self.roi_start_ratio = 0.13
        self.roi_end_ratio = 0.93
        self.roi_side_margin_ratio = 0.05
        self.cut_bottom_ratio = 0.08

        # -----------------------------
        # scanline
        # 위쪽은 약하게, 아래쪽은 강하게
        # -----------------------------
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

        # -----------------------------
        # HSV 범위
        # -----------------------------
        self.lower_hsv_1 = np.array([8, 120, 90], dtype=np.uint8)
        self.upper_hsv_1 = np.array([20, 255, 190], dtype=np.uint8)

        self.lower_hsv_2 = np.array([4, 40, 120], dtype=np.uint8)
        self.upper_hsv_2 = np.array([28, 170, 255], dtype=np.uint8)

        self.kernel = np.ones((5, 5), np.uint8)

        # 한쪽 경계만 보일 때 사용할 도로폭 추정
        self.estimated_road_width_px = 290

        self.debug_count = 0
        self.get_logger().info('less-conservative corner follower node started')

    def image_callback(self, msg):
        frame = self.bridge.imgmsg_to_cv2(msg, desired_encoding='bgr8')
        height, width = frame.shape[:2]

        # -----------------------------
        # ROI 설정
        # -----------------------------
        y0 = int(height * self.roi_start_ratio)
        y1 = int(height * self.roi_end_ratio)
        x0 = int(width * self.roi_side_margin_ratio)
        x1 = int(width * (1.0 - self.roi_side_margin_ratio))

        roi = frame[y0:y1, x0:x1].copy()
        roi_h, roi_w = roi.shape[:2]

        # 보닛 제거
        cut_bottom = int(roi_h * self.cut_bottom_ratio)
        if cut_bottom > 0:
            roi = roi[:roi_h - cut_bottom, :]
        roi_h, roi_w = roi.shape[:2]

        vis = roi.copy()

        # -----------------------------
        # 마스크 생성
        # -----------------------------
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
        mask_pixels = int(np.count_nonzero(mask))

        # -----------------------------
        # scanline에서 좌/우 경계와 중심 추출
        # 한쪽만 보이면 반대쪽 추정
        # -----------------------------
        rows_info = []
        # (center_x, y, weight, left_x, right_x, left_inferred, right_inferred)

        for ratio, weight in zip(self.scanline_ratios, self.scanline_weights):
            y = int(roi_h * ratio)
            y = int(np.clip(y, 0, roi_h - 1))

            cv2.line(vis, (0, y), (roi_w - 1, y), (70, 70, 70), 1)

            row = mask[y, :]
            xs = np.where(row > 0)[0]

            if len(xs) < 20:
                continue

            left_x_raw = int(xs[0])
            right_x_raw = int(xs[-1])

            left_visible = left_x_raw > 4
            right_visible = right_x_raw < roi_w - 5

            left_inferred = False
            right_inferred = False

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
                right_inferred = True

            elif not left_visible and right_visible:
                left_x = right_x - self.estimated_road_width_px
                left_x = int(np.clip(left_x, 0, roi_w - 1))
                left_inferred = True

            else:
                continue

            road_width = right_x - left_x
            if road_width < 60:
                continue

            center_x = 0.5 * (left_x + right_x)
            rows_info.append((center_x, y, weight, left_x, right_x, left_inferred, right_inferred))

        # -----------------------------
        # near / mid / top 구간 중심 계산
        # -----------------------------
        near_center = None
        mid_center = None
        top_center = None
        target_center = None
        turn_hint = 0.0
        corner_mode = False

        if len(rows_info) > 0:
            near_candidates = []
            mid_candidates = []
            top_candidates = []

            for cx, y, w, left_x, right_x, left_inf, right_inf in rows_info:
                y_ratio = y / max(roi_h - 1, 1)

                if y_ratio >= 0.70:
                    near_candidates.append((cx, w))
                elif 0.46 <= y_ratio < 0.70:
                    mid_candidates.append((cx, w))
                else:
                    top_candidates.append((cx, w))

            if len(near_candidates) > 0:
                near_center = sum(x * w for x, w in near_candidates) / sum(w for _, w in near_candidates)

            if len(mid_candidates) > 0:
                mid_center = sum(x * w for x, w in mid_candidates) / sum(w for _, w in mid_candidates)

            if len(top_candidates) > 0:
                top_center = sum(x * w for x, w in top_candidates) / sum(w for _, w in top_candidates)

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

            # -----------------------------
            # target 계산
            # 코너에서는 top 비중을 더 키움
            # -----------------------------
            if near_center is not None:
                target_center = near_center + 1.10 * turn_hint
            elif mid_center is not None:
                target_center = mid_center
            elif top_center is not None:
                target_center = top_center
            elif self.prev_target is not None:
                target_center = self.prev_target
            else:
                target_center = None

        elif self.prev_target is not None:
            target_center = self.prev_target

        if target_center is not None:
            target_center = float(np.clip(target_center, 0, roi_w - 1))
            self.prev_target = target_center

        # -----------------------------
        # 시각화
        # -----------------------------
        image_center = roi_w / 2.0
        cv2.line(vis, (int(image_center), 0), (int(image_center), roi_h - 1), (0, 255, 255), 2)

        for cx, y, w, left_x, right_x, left_inf, right_inf in rows_info:
            if left_inf:
                cv2.circle(vis, (int(left_x), int(y)), 4, (255, 255, 0), -1)
            else:
                cv2.circle(vis, (int(left_x), int(y)), 4, (255, 0, 0), -1)

            if right_inf:
                cv2.circle(vis, (int(right_x), int(y)), 4, (0, 255, 255), -1)
            else:
                cv2.circle(vis, (int(right_x), int(y)), 4, (0, 0, 255), -1)

            cv2.circle(vis, (int(cx), int(y)), 3, (0, 255, 0), -1)

        if top_center is not None:
            cv2.line(vis, (int(top_center), 0), (int(top_center), roi_h - 1), (255, 200, 0), 1)

        if mid_center is not None:
            cv2.line(vis, (int(mid_center), 0), (int(mid_center), roi_h - 1), (255, 255, 0), 1)

        if near_center is not None:
            cv2.line(vis, (int(near_center), 0), (int(near_center), roi_h - 1), (255, 0, 255), 1)

        if target_center is not None:
            cv2.line(vis, (int(target_center), 0), (int(target_center), roi_h - 1), (0, 255, 255), 2)

        # -----------------------------
        # 제어
        # 코너에서도 속도를 너무 낮추지 않음
        # -----------------------------
        twist = Twist()

        if target_center is not None:
            pos_error = float(target_center - image_center)
            derivative = pos_error - self.prev_error

            control = self.k_pos * pos_error + self.kd * derivative


            control = float(np.clip(control, -self.max_angular, self.max_angular))
            self.prev_error = pos_error

            speed = self.base_speed
            if abs(pos_error) > 20:
                speed = 0.95
            if abs(pos_error) > 40:
                speed = 0.82
            if abs(pos_error) > 60:
                speed = 0.68
            if abs(pos_error) > 90:
                speed = 0.55

            speed = max(speed, self.min_speed)

            twist.linear.x = float(speed)
            twist.angular.z = float(-control)

            cv2.putText(
                vis,
                f'corner={corner_mode} pos_err={pos_error:.1f} turn={turn_hint:.1f} ctrl={control:.3f}',
                (20, 30),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.58,
                (255, 255, 255),
                2
            )

            cv2.putText(
                vis,
                f'near={-1 if near_center is None else int(near_center)} '
                f'mid={-1 if mid_center is None else int(mid_center)} '
                f'top={-1 if top_center is None else int(top_center)} '
                f'target={int(target_center)} img={int(image_center)} pts={len(rows_info)}',
                (20, 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.53,
                (255, 255, 255),
                2
            )

            cv2.putText(
                vis,
                f'est_w={self.estimated_road_width_px} speed={twist.linear.x:.2f}',
                (20, 90),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.58,
                (255, 255, 255),
                2
            )
        else:
            twist.linear.x = 0.0
            twist.angular.z = 0.0

            cv2.putText(
                vis,
                'road points not found',
                (20, 30),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 0, 255),
                2
            )

            cv2.putText(
                vis,
                f'est_w={self.estimated_road_width_px}',
                (20, 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.58,
                (255, 255, 255),
                2
            )

        self.cmd_pub.publish(twist)

        self.debug_count += 1
        if self.debug_count % 10 == 0:
            if target_center is not None:
                self.get_logger().info(
                    f'mask={mask_pixels}, near={near_center}, mid={mid_center}, top={top_center}, '
                    f'target={target_center:.1f}, pos_err={pos_error:.1f}, turn={turn_hint:.1f}, '
                    f'lin={twist.linear.x:.2f}, ang={twist.angular.z:.3f}, '
                    f'est_w={self.estimated_road_width_px}, corner={corner_mode}'
                )
            else:
                self.get_logger().info(
                    f'mask={mask_pixels}, road points not found, est_w={self.estimated_road_width_px}'
                )

        # -----------------------------
        # 디버그 출력
        # -----------------------------
        frame_vis = frame.copy()

        cv2.rectangle(frame_vis, (x0, y0), (x1, y1), (0, 0, 255), 2)

        roi_display_bottom = y0 + roi_h
        cv2.rectangle(frame_vis, (x0, y0), (x1, roi_display_bottom), (255, 0, 0), 2)

        mask_vis = cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR)

        cv2.imshow('camera_with_roi', frame_vis)
        cv2.imshow('road_boundary_follow', vis)
        cv2.imshow('road_mask', mask_vis)
        cv2.waitKey(1)

    def keep_largest_component(self, mask):
        num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(
            mask,
            connectivity=8
        )

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


def main(args=None):
    rclpy.init(args=args)
    node = RoadBoundaryFollower()

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
