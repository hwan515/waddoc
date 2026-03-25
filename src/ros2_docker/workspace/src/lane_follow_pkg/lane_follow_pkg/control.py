import math
import time

from .config import PathTrackingConfig, VisionFusionConfig
from .models import Pose2D, TrajectoryControlResult
from .navigation import clamp, dist_xy, normalize_angle


def move_toward(current, target, max_step):
    if target > current:
        return min(current + max_step, target)
    return max(current - max_step, target)


class TrajectoryFollower:
    def __init__(
        self,
        tracking_config: PathTrackingConfig,
        fusion_config: VisionFusionConfig,
    ):
        self.tracking_config = tracking_config
        self.fusion_config = fusion_config
        self.reset_tracking_state()

    def reset_tracking_state(self):
        self.prev_error = 0.0
        self.prev_time = None
        self.closest_traj_idx = 0
        self.last_stable_angular = 0.0
        self.hold_steering_mode = False
        self.angular_cmd = 0.0

    def find_closest_trajectory_index(self, pose: Pose2D, trajectory):
        if not trajectory:
            return 0

        start_idx = max(0, self.closest_traj_idx - 5)
        end_idx = min(len(trajectory), self.closest_traj_idx + 80)

        best_idx = start_idx
        best_dist = float('inf')

        for index in range(start_idx, end_idx):
            tx, tz = trajectory[index]
            distance = dist_xy(pose.x, pose.z, tx, tz)
            if distance < best_dist:
                best_dist = distance
                best_idx = index

        return best_idx

    def find_lookahead_index(self, trajectory, start_idx):
        if not trajectory:
            return 0

        accumulated = 0.0
        prev_x, prev_z = trajectory[start_idx]

        for index in range(start_idx + 1, len(trajectory)):
            x, z = trajectory[index]
            accumulated += dist_xy(prev_x, prev_z, x, z)
            if accumulated >= self.tracking_config.lookahead_dist:
                return index
            prev_x, prev_z = x, z

        return len(trajectory) - 1

    def distance_point_to_segment(self, px, pz, ax, az, bx, bz):
        abx = bx - ax
        abz = bz - az
        apx = px - ax
        apz = pz - az

        ab_len_sq = abx * abx + abz * abz
        if ab_len_sq < 1e-9:
            return dist_xy(px, pz, ax, az), 0.0

        ratio = (apx * abx + apz * abz) / ab_len_sq
        ratio = clamp(ratio, 0.0, 1.0)

        cx = ax + ratio * abx
        cz = az + ratio * abz

        distance = dist_xy(px, pz, cx, cz)
        return distance, ratio

    def get_cross_track_error(self, pose: Pose2D, trajectory, traj_idx):
        if not trajectory:
            return float('inf')

        if len(trajectory) == 1:
            tx, tz = trajectory[0]
            return dist_xy(pose.x, pose.z, tx, tz)

        idx0 = max(0, traj_idx - 1)
        idx1 = min(len(trajectory) - 1, traj_idx + 1)

        best = float('inf')

        for index in range(idx0, idx1):
            ax, az = trajectory[index]
            bx, bz = trajectory[index + 1]
            distance, _ = self.distance_point_to_segment(pose.x, pose.z, ax, az, bx, bz)
            if distance < best:
                best = distance

        return best

    def compute_control(self, pose: Pose2D, trajectory, lane_error):
        if not trajectory:
            return TrajectoryControlResult(
                linear_speed=0.0,
                angular_speed=0.0,
                desired_angular=0.0,
                final_desired_angular=0.0,
                lookahead_idx=0,
                closest_traj_idx=0,
                lane_error=lane_error,
                mode='stop',
                error_for_control=0.0,
                error_deg=0.0,
                cross_track_error=float('inf'),
                straight_mode=False,
                hold_steering_mode=self.hold_steering_mode,
                p_term=0.0,
                d_term=0.0,
                target_yaw=pose.yaw,
            )

        self.closest_traj_idx = self.find_closest_trajectory_index(pose, trajectory)
        lookahead_idx = self.find_lookahead_index(trajectory, self.closest_traj_idx)

        tx, tz = trajectory[lookahead_idx]
        dx = tx - pose.x
        dz = tz - pose.z

        target_yaw = math.atan2(dx, dz)
        error = normalize_angle(target_yaw - pose.yaw)

        now = time.time()
        if self.prev_time is None:
            dt = 0.05
        else:
            dt = max(0.001, now - self.prev_time)

        error_deg = math.degrees(error)
        error_deg_abs = abs(error_deg)

        if error_deg_abs < self.tracking_config.deadband_deg:
            error_for_control = 0.0
        else:
            error_for_control = error

        cross_track_error = self.get_cross_track_error(pose, trajectory, self.closest_traj_idx)

        straight_mode = (
            cross_track_error <= self.tracking_config.straight_cte
            and error_deg_abs <= self.tracking_config.straight_error_deg
        )

        if self.hold_steering_mode:
            release_heading = self.tracking_config.heading_hold_deg * 1.5
            if (
                cross_track_error > self.tracking_config.release_corridor_radius
                or error_deg_abs > release_heading
            ):
                self.hold_steering_mode = False
        else:
            if (
                cross_track_error <= self.tracking_config.corridor_radius
                and error_deg_abs <= self.tracking_config.heading_hold_deg
            ):
                self.hold_steering_mode = True

        if straight_mode:
            desired_angular = 0.0
            p_term = 0.0
            d_term = 0.0
            self.prev_error = 0.0
            self.last_stable_angular = 0.0
            self.hold_steering_mode = False
        elif self.hold_steering_mode:
            desired_angular = self.last_stable_angular * self.tracking_config.hold_decay
            if abs(desired_angular) < 0.02:
                desired_angular = 0.0
            p_term = 0.0
            d_term = 0.0
        else:
            d_error = (error_for_control - self.prev_error) / dt

            p_term = self.tracking_config.kp * error_for_control
            d_term = self.tracking_config.kd * d_error
            d_term = clamp(
                d_term,
                -self.tracking_config.max_d_term,
                self.tracking_config.max_d_term,
            )

            desired_angular = p_term + d_term
            desired_angular = clamp(
                desired_angular,
                -self.tracking_config.max_angular,
                self.tracking_config.max_angular,
            )

            self.last_stable_angular = desired_angular

        final_desired_angular = desired_angular
        final_speed = self.tracking_config.cruise_speed
        mode = 'traj_only'

        if lane_error is not None:
            vision_correction = clamp(
                self.fusion_config.vision_lane_k * lane_error,
                -self.fusion_config.vision_lane_max,
                self.fusion_config.vision_lane_max,
            )

            abs_lane_error = abs(lane_error)

            if abs_lane_error >= self.fusion_config.vision_recovery_error_px:
                final_desired_angular = vision_correction
                final_speed = min(final_speed, self.fusion_config.recovery_speed)
                mode = 'vision_recovery'
            else:
                final_desired_angular = (
                    (1.0 - self.fusion_config.vision_mix_weight) * desired_angular
                    + self.fusion_config.vision_mix_weight * vision_correction
                )
                final_desired_angular = clamp(
                    final_desired_angular,
                    -self.tracking_config.max_angular,
                    self.tracking_config.max_angular,
                )
                mode = 'traj+vision'

                if abs_lane_error >= self.fusion_config.vision_soft_error_px:
                    final_speed = min(final_speed, 0.35)

        self.angular_cmd = move_toward(
            self.angular_cmd,
            final_desired_angular,
            self.tracking_config.max_angular_step,
        )

        self.prev_error = error_for_control
        self.prev_time = now

        return TrajectoryControlResult(
            linear_speed=final_speed,
            angular_speed=self.angular_cmd,
            desired_angular=desired_angular,
            final_desired_angular=final_desired_angular,
            lookahead_idx=lookahead_idx,
            closest_traj_idx=self.closest_traj_idx,
            lane_error=lane_error,
            mode=mode,
            error_for_control=error_for_control,
            error_deg=error_deg,
            cross_track_error=cross_track_error,
            straight_mode=straight_mode,
            hold_steering_mode=self.hold_steering_mode,
            p_term=p_term,
            d_term=d_term,
            target_yaw=target_yaw,
        )
