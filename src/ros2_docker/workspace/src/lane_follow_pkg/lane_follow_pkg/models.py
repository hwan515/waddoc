from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class Pose2D:
    x: float
    z: float
    yaw: float


@dataclass(frozen=True)
class SpeedState:
    speed_ms: float
    speed_kmh: float


@dataclass(frozen=True)
class PlannedRoute:
    start_id: str
    goal_id: str
    path_ids: list[str]
    trajectory: list[tuple[float, float]]
    display_path_ids: list[str]
    display_trajectory: list[tuple[float, float]]


@dataclass(frozen=True)
class YoloDetection:
    label: str
    confidence: float
    bbox: tuple[int, int, int, int]
    overlap_bbox: tuple[int, int, int, int]


@dataclass
class LaneDetectionResult:
    has_target: bool = False
    linear_speed: float = 0.0
    angular_speed: float = 0.0
    mask_pixels: int = 0
    near_center: float | None = None
    mid_center: float | None = None
    top_center: float | None = None
    target_center: float | None = None
    turn_hint: float = 0.0
    corner_mode: bool = False
    mean_road_width: float | None = None
    image_center: float | None = None
    lane_error_px: float | None = None
    roi_width: int | None = None
    roi_rect: tuple[int, int, int, int] = (0, 0, 0, 0)
    row_count: int = 0
    road_width_estimate: int = 0
    mask: Any = None
    roi_visualization: Any = None


@dataclass(frozen=True)
class TrajectoryControlResult:
    linear_speed: float
    angular_speed: float
    desired_angular: float
    final_desired_angular: float
    lookahead_idx: int
    closest_traj_idx: int
    lane_error: float | None
    mode: str
    error_for_control: float
    error_deg: float
    cross_track_error: float
    straight_mode: bool
    hold_steering_mode: bool
    p_term: float
    d_term: float
    target_yaw: float
