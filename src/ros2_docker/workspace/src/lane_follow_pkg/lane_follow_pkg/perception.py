import time
from pathlib import Path

import cv2
import numpy as np

from .config import LaneDetectionConfig, YoloConfig
from .models import LaneDetectionResult, YoloDetection

try:
    from ultralytics import YOLO
except ImportError:
    YOLO = None


class LaneVisionProcessor:
    def __init__(self, config: LaneDetectionConfig):
        self.config = config
        self.prev_vision_error = 0.0
        self.prev_target = None
        self.estimated_road_width_px = config.estimated_road_width_px

    def reset_tracking(self):
        self.prev_vision_error = 0.0
        self.prev_target = None

    def weighted_average(self, pairs):
        if len(pairs) == 0:
            return None
        denom = sum(weight for _, weight in pairs)
        if denom < 1e-9:
            return None
        return sum(value * weight for value, weight in pairs) / denom

    def keep_largest_component(self, mask):
        num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)

        if num_labels <= 1:
            return np.zeros_like(mask)

        largest_label = -1
        largest_area = 0

        for label_index in range(1, num_labels):
            area = stats[label_index, cv2.CC_STAT_AREA]
            if area > largest_area:
                largest_area = area
                largest_label = label_index

        output = np.zeros_like(mask)
        if largest_label != -1:
            output[labels == largest_label] = 255

        return output

    def compute_control_from_target(self, target_center, image_center):
        if target_center is None:
            return 0.0, 0.0, False

        pos_error = float(target_center - image_center)
        derivative = pos_error - self.prev_vision_error

        control = self.config.k_pos * pos_error + self.config.kd_vision * derivative
        control = float(
            np.clip(
                control,
                -self.config.max_angular_vision,
                self.config.max_angular_vision,
            )
        )

        self.prev_vision_error = pos_error

        speed = self.config.base_speed
        if abs(pos_error) > 20:
            speed = 0.85
        if abs(pos_error) > 40:
            speed = 0.65
        if abs(pos_error) > 60:
            speed = 0.48
        if abs(pos_error) > 90:
            speed = 0.35

        speed = max(speed, self.config.min_speed)
        return float(speed), float(-control), True

    def process(self, frame):
        height, width = frame.shape[:2]

        y0 = int(height * self.config.roi_start_ratio)
        y1 = int(height * self.config.roi_end_ratio)
        x0 = int(width * self.config.roi_side_margin_ratio)
        x1 = int(width * (1.0 - self.config.roi_side_margin_ratio))

        roi = frame[y0:y1, x0:x1].copy()
        roi_h, roi_w = roi.shape[:2]

        cut_bottom = int(roi_h * self.config.cut_bottom_ratio)
        if cut_bottom > 0:
            roi = roi[:roi_h - cut_bottom, :]
        roi_h, roi_w = roi.shape[:2]

        vis = roi.copy()

        hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)

        mask1 = cv2.inRange(hsv, self.config.lower_hsv_1, self.config.upper_hsv_1)
        mask2 = cv2.inRange(hsv, self.config.lower_hsv_2, self.config.upper_hsv_2)
        mask = cv2.bitwise_or(mask1, mask2)

        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, self.config.kernel)
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, self.config.kernel)

        edge_margin = int(roi_w * 0.02)
        mask[:, :edge_margin] = 0
        mask[:, roi_w - edge_margin:] = 0

        mask = self.keep_largest_component(mask)
        mask_pixels = int(np.count_nonzero(mask))

        rows_info = []

        for ratio, weight in zip(self.config.scanline_ratios, self.config.scanline_weights):
            y = int(roi_h * ratio)
            y = int(np.clip(y, 0, roi_h - 1))

            row = mask[y, :]
            xs = np.where(row > 0)[0]

            if len(xs) < 20:
                continue

            left_x_raw = int(xs[0])
            right_x_raw = int(xs[-1])

            left_visible = left_x_raw > 4
            right_visible = right_x_raw < roi_w - 5

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
            elif not left_visible and right_visible:
                left_x = right_x - self.estimated_road_width_px
                left_x = int(np.clip(left_x, 0, roi_w - 1))
            else:
                continue

            road_width = right_x - left_x
            if road_width < 60:
                continue

            center_x = 0.5 * (left_x + right_x)
            rows_info.append((center_x, y, weight, left_x, right_x, road_width))

        near_center = None
        mid_center = None
        top_center = None
        target_center = None
        turn_hint = 0.0
        corner_mode = False
        mean_road_width = None

        if len(rows_info) > 0:
            near_candidates = []
            mid_candidates = []
            top_candidates = []
            width_candidates = []

            for center_x, y, weight, _left_x, _right_x, road_width in rows_info:
                y_ratio = y / max(roi_h - 1, 1)
                width_candidates.append((road_width, weight))

                if y_ratio >= 0.70:
                    near_candidates.append((center_x, weight))
                elif 0.46 <= y_ratio < 0.70:
                    mid_candidates.append((center_x, weight))
                else:
                    top_candidates.append((center_x, weight))

            near_center = self.weighted_average(near_candidates)
            mid_center = self.weighted_average(mid_candidates)
            top_center = self.weighted_average(top_candidates)
            mean_road_width = self.weighted_average(width_candidates)

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

            if near_center is not None:
                target_center = near_center + 1.10 * turn_hint
            elif mid_center is not None:
                target_center = mid_center
            elif top_center is not None:
                target_center = top_center
            elif self.prev_target is not None:
                target_center = self.prev_target
        elif self.prev_target is not None:
            target_center = self.prev_target

        image_center = roi_w / 2.0

        if target_center is not None:
            target_center = float(np.clip(target_center, 0, roi_w - 1))
            self.prev_target = target_center

        linear_speed, angular_speed, has_target = self.compute_control_from_target(
            target_center=target_center,
            image_center=image_center,
        )

        lane_error_px = None
        if target_center is not None:
            lane_error_px = float(target_center - image_center)

        cv2.line(vis, (int(image_center), 0), (int(image_center), roi_h - 1), (0, 255, 255), 2)
        if target_center is not None:
            cv2.line(vis, (int(target_center), 0), (int(target_center), roi_h - 1), (0, 255, 255), 2)

        lane_err_str = 'None' if lane_error_px is None else f'{lane_error_px:.1f}'
        cv2.putText(
            vis,
            f'target={-1 if target_center is None else int(target_center)} '
            f'hint={turn_hint:.1f} lane_err={lane_err_str}',
            (20, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            2,
        )

        cv2.putText(
            vis,
            f'est_w={self.estimated_road_width_px} pts={len(rows_info)} mask={mask_pixels}',
            (20, 60),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            2,
        )

        return LaneDetectionResult(
            has_target=has_target,
            linear_speed=linear_speed,
            angular_speed=angular_speed,
            mask_pixels=mask_pixels,
            near_center=near_center,
            mid_center=mid_center,
            top_center=top_center,
            target_center=target_center,
            turn_hint=turn_hint,
            corner_mode=corner_mode,
            mean_road_width=mean_road_width,
            image_center=image_center,
            lane_error_px=lane_error_px,
            roi_width=roi_w,
            roi_rect=(x0, y0, x1, y1),
            row_count=len(rows_info),
            road_width_estimate=self.estimated_road_width_px,
            mask=mask,
            roi_visualization=vis,
        )


class YoloHazardDetector:
    def __init__(self, config: YoloConfig, logger, resolve_model_reference):
        self.config = config
        self.logger = logger
        self.resolve_model_reference = resolve_model_reference
        self.model = None
        self.ready = False
        self.model_reference = ''
        self.last_detections = []
        self.last_error_log_time = 0.0

    @staticmethod
    def normalize_detection_label(label):
        return ' '.join(
            str(label).strip().lower().replace('_', ' ').replace('-', ' ').split()
        )

    def initialize(self):
        if not self.config.enabled:
            self.logger.info('YOLO ROI e-stop disabled by parameter.')
            return

        if YOLO is None:
            self.logger.warn(
                'ultralytics 패키지가 없어 YOLO ROI e-stop을 비활성화합니다. '
                '컨테이너에서 pip3 install ultralytics 를 실행하거나 이미지를 다시 빌드하세요.'
            )
            return

        if not self.config.model_name:
            self.logger.warn(
                'yolo_model 파라미터가 비어 있어 YOLO ROI e-stop을 비활성화합니다.'
            )
            return

        self.model_reference = self.resolve_model_reference(self.config.model_name)

        try:
            model_ref_path = Path(self.model_reference).expanduser()
            if model_ref_path.suffix == '.pt':
                model_ref_path.parent.mkdir(parents=True, exist_ok=True)
        except Exception:
            pass

        try:
            self.model = YOLO(self.model_reference)
            self.ready = True
            hazard_labels = ', '.join(sorted(self.config.hazard_labels))
            device_name = self.config.device if self.config.device else 'auto'
            self.logger.info(
                'YOLO ROI e-stop 활성화: '
                f'model={self.model_reference}, '
                f'device={device_name}, '
                f'conf={self.config.confidence:.2f}, '
                f'hazards=[{hazard_labels}]'
            )
        except Exception as error:
            self.model = None
            self.ready = False
            self.logger.error(
                f'YOLO 모델 로드 실패 ({self.model_reference}): {error}'
            )

    def label_matches_hazard(self, label):
        normalized = self.normalize_detection_label(label)
        if not normalized:
            return False

        for hazard_label in self.config.hazard_labels:
            if (
                normalized == hazard_label
                or hazard_label in normalized
                or normalized in hazard_label
            ):
                return True
        return False

    def detect(self, frame, roi_rect):
        self.last_detections = []

        if not self.config.enabled or not self.ready or self.model is None:
            return []

        predict_kwargs = {
            'source': frame,
            'conf': self.config.confidence,
            'imgsz': self.config.input_size,
            'verbose': False,
        }
        if self.config.device:
            predict_kwargs['device'] = self.config.device

        try:
            results = self.model.predict(**predict_kwargs)
        except Exception as error:
            now = time.time()
            if now - self.last_error_log_time >= 5.0:
                self.logger.error(f'YOLO 추론 실패: {error}')
                self.last_error_log_time = now
            return []

        if not results:
            return []

        result = results[0]
        boxes = getattr(result, 'boxes', None)
        if boxes is None:
            return []

        names = getattr(result, 'names', {})
        rx0, ry0, rx1, ry1 = roi_rect

        detections = []
        for box in boxes:
            cls_idx = int(box.cls[0].item()) if box.cls is not None else -1
            if isinstance(names, dict):
                label = str(names.get(cls_idx, cls_idx))
            elif isinstance(names, (list, tuple)) and 0 <= cls_idx < len(names):
                label = str(names[cls_idx])
            else:
                label = str(cls_idx)

            if not self.label_matches_hazard(label):
                continue

            confidence = float(box.conf[0].item()) if box.conf is not None else 0.0
            bx0, by0, bx1, by1 = [int(value) for value in box.xyxy[0].tolist()]

            ix0 = max(bx0, rx0)
            iy0 = max(by0, ry0)
            ix1 = min(bx1, rx1)
            iy1 = min(by1, ry1)
            overlap_area = max(0, ix1 - ix0) * max(0, iy1 - iy0)

            if overlap_area <= 0:
                continue

            detections.append(
                YoloDetection(
                    label=label,
                    confidence=confidence,
                    bbox=(bx0, by0, bx1, by1),
                    overlap_bbox=(ix0, iy0, ix1, iy1),
                )
            )

        self.last_detections = detections
        return list(detections)

    def draw_detections(self, frame_vis, detections):
        for detection in detections:
            bx0, by0, bx1, by1 = detection.bbox
            ix0, iy0, ix1, iy1 = detection.overlap_bbox

            cv2.rectangle(frame_vis, (bx0, by0), (bx1, by1), (0, 0, 255), 2)
            cv2.rectangle(frame_vis, (ix0, iy0), (ix1, iy1), (0, 255, 255), 2)
            cv2.putText(
                frame_vis,
                f'{detection.label} {detection.confidence:.2f}',
                (bx0, max(24, by0 - 8)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (0, 0, 255),
                2,
            )
