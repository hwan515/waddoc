from dataclasses import dataclass, field

import numpy as np


def _default_scanline_ratios():
    return np.linspace(0.22, 0.88, 18).tolist()


def _default_scanline_weights():
    weights = []
    for ratio in _default_scanline_ratios():
        if ratio < 0.32:
            weights.append(0.45)
        elif ratio < 0.45:
            weights.append(0.80)
        elif ratio < 0.60:
            weights.append(1.30)
        elif ratio < 0.75:
            weights.append(2.00)
        else:
            weights.append(2.80)
    return weights


@dataclass(frozen=True)
class RouteExportConfig:
    live_publish_interval: float = 0.1


@dataclass(frozen=True)
class PathPlannerConfig:
    behind_angle_deg: float = 100.0
    samples_per_segment: int = 12


@dataclass(frozen=True)
class PathTrackingConfig:
    cruise_speed: float = 0.5
    kp: float = 0.25
    kd: float = 0.03
    max_angular: float = 0.15
    max_d_term: float = 0.03
    deadband_deg: float = 2.0
    max_angular_step: float = 0.015
    straight_error_deg: float = 5.0
    straight_cte: float = 0.8
    hold_decay: float = 0.75
    lookahead_dist: float = 10.0
    goal_tolerance: float = 5.0
    traj_reach_tolerance: float = 3.0
    corridor_radius: float = 2.5
    heading_hold_deg: float = 6.0
    release_corridor_radius: float = 4.5


@dataclass
class LaneDetectionConfig:
    k_pos: float = 0.014
    kd_vision: float = 0.0025
    max_angular_vision: float = 1.8
    base_speed: float = 1.0
    min_speed: float = 0.50
    roi_start_ratio: float = 0.13
    roi_end_ratio: float = 0.93
    roi_side_margin_ratio: float = 0.05
    cut_bottom_ratio: float = 0.08
    scanline_ratios: list[float] = field(default_factory=_default_scanline_ratios)
    scanline_weights: list[float] = field(default_factory=_default_scanline_weights)
    lower_hsv_1: np.ndarray = field(
        default_factory=lambda: np.array([8, 120, 90], dtype=np.uint8)
    )
    upper_hsv_1: np.ndarray = field(
        default_factory=lambda: np.array([20, 255, 190], dtype=np.uint8)
    )
    lower_hsv_2: np.ndarray = field(
        default_factory=lambda: np.array([4, 40, 120], dtype=np.uint8)
    )
    upper_hsv_2: np.ndarray = field(
        default_factory=lambda: np.array([28, 170, 255], dtype=np.uint8)
    )
    kernel: np.ndarray = field(
        default_factory=lambda: np.ones((5, 5), np.uint8)
    )
    estimated_road_width_px: int = 290


@dataclass(frozen=True)
class VisionFusionConfig:
    vision_mask_min_pixels: int = 1800
    vision_lane_k: float = 0.0012
    vision_lane_max: float = 0.06
    vision_mix_weight: float = 0.45
    vision_recovery_error_px: float = 95.0
    vision_soft_error_px: float = 55.0
    recovery_speed: float = 0.26


@dataclass(frozen=True)
class YoloConfig:
    enabled: bool
    model_name: str
    device: str
    confidence: float
    input_size: int
    hazard_labels: set[str]
