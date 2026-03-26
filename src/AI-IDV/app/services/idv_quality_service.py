from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from app.schemas.idv import QualityChecks


@dataclass(frozen=True)
class ImageQualityMetrics:
    blur_score: float
    brightness_score: float
    glare_ratio: float


class IdvQualityService:
    def measure(self, image: np.ndarray) -> ImageQualityMetrics:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        blur_score = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        brightness_score = float(gray.mean())
        glare_ratio = float((gray > 245).mean())
        return ImageQualityMetrics(
            blur_score=blur_score,
            brightness_score=brightness_score,
            glare_ratio=glare_ratio,
        )

    def build_quality_checks(
        self,
        *,
        face_detected: bool,
        single_face: bool,
        id_card_detected: bool,
        ocr_confidence: float,
    ) -> QualityChecks:
        return QualityChecks(
            faceDetected=face_detected,
            singleFace=single_face,
            idCardDetected=id_card_detected,
            ocrConfidence=round(ocr_confidence, 4),
        )

    def extend_reason_codes(
        self,
        metrics: ImageQualityMetrics,
        reason_codes: list[str],
    ) -> list[str]:
        if metrics.blur_score < 70.0 and "LOW_FACE_QUALITY" not in reason_codes:
            reason_codes.append("LOW_FACE_QUALITY")
        if metrics.brightness_score < 40.0 and "LOW_FACE_QUALITY" not in reason_codes:
            reason_codes.append("LOW_FACE_QUALITY")
        if metrics.glare_ratio > 0.18 and "LOW_FACE_QUALITY" not in reason_codes:
            reason_codes.append("LOW_FACE_QUALITY")
        return reason_codes
