from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass
from functools import lru_cache

import cv2
import numpy as np
from fastapi import HTTPException, UploadFile, status

from app.core.config import Settings, get_settings
from app.schemas.idv import IdvVerifyResponse, OcrResult
from app.services.idv_model_registry import DetectedFace, IdvModelRegistry, ModelUnavailableError, get_model_registry
from app.services.idv_ocr_parser import IdvOcrParser
from app.services.idv_quality_service import IdvQualityService
from app.services.idv_similarity import cosine_similarity

ARCFACE_TEMPLATE = np.array(
    [
        [38.2946, 51.6963],
        [73.5318, 51.5014],
        [56.0252, 71.7366],
        [41.5493, 92.3655],
        [70.7299, 92.2041],
    ],
    dtype=np.float32,
)


@dataclass(frozen=True)
class IdvRequestContext:
    verification_id: str
    patient_id: str
    reference_image: UploadFile
    face_image: UploadFile
    id_card_image: UploadFile


class IdvService:
    def __init__(
        self,
        settings: Settings,
        registry: IdvModelRegistry,
        quality_service: IdvQualityService,
        ocr_parser: IdvOcrParser,
    ) -> None:
        self._settings = settings
        self._registry = registry
        self._quality_service = quality_service
        self._ocr_parser = ocr_parser
        self._semaphore = asyncio.Semaphore(settings.idv_max_concurrency)

    async def verify(self, context: IdvRequestContext) -> IdvVerifyResponse:
        async with self._semaphore:
            try:
                return await asyncio.wait_for(
                    asyncio.to_thread(self._verify_sync, context),
                    timeout=self._settings.idv_timeout_ms / 1000,
                )
            except asyncio.TimeoutError as exc:
                raise HTTPException(
                    status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                    detail="IDV inference timed out",
                ) from exc
            except ModelUnavailableError as exc:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=str(exc),
                ) from exc

    def _verify_sync(self, context: IdvRequestContext) -> IdvVerifyResponse:
        start = time.perf_counter()
        reference_image = self._load_image(context.reference_image)
        face_image = self._load_image(context.face_image)
        id_card_image = self._load_image(context.id_card_image)

        detector = self._registry.get_scrfd()
        embedder = self._registry.get_adaface()
        ocr_engine = self._registry.get_ocr()

        reference_faces = self._detect_faces(detector, reference_image)
        live_faces = self._detect_faces(detector, face_image)
        id_card_faces = self._detect_faces(detector, id_card_image)

        face_detected = bool(live_faces)
        single_face = len(live_faces) == 1
        id_card_detected = True
        reason_codes: list[str] = []

        if not reference_faces:
            reason_codes.append("REFERENCE_FACE_NOT_FOUND")
        if not live_faces:
            reason_codes.append("LIVE_FACE_NOT_FOUND")
        if len(live_faces) > 1:
            reason_codes.append("MULTIPLE_FACES_DETECTED")
        if not id_card_faces:
            reason_codes.append("IDCARD_FACE_NOT_FOUND")

        live_quality = self._quality_service.measure(face_image)
        self._quality_service.extend_reason_codes(live_quality, reason_codes)

        face_similarity = None
        id_card_face_similarity = None

        if reference_faces and len(live_faces) == 1:
            reference_embedding = embedder.embed(self._align_face(reference_image, reference_faces[0].keypoints))
            live_embedding = embedder.embed(self._align_face(face_image, live_faces[0].keypoints))
            face_similarity = cosine_similarity(reference_embedding, live_embedding)
            if face_similarity < self._settings.idv_face_reference_threshold:
                reason_codes.append("LOW_FACE_SIMILARITY")
        else:
            live_embedding = None

        parsed_ocr = self._run_ocr(ocr_engine, id_card_image)
        if parsed_ocr.name is None or parsed_ocr.rrn_masked is None or parsed_ocr.address is None:
            reason_codes.append("REQUIRED_OCR_FIELDS_MISSING")
        if parsed_ocr.confidence < self._settings.idv_ocr_min_confidence:
            reason_codes.append("OCR_LOW_CONFIDENCE")
        if parsed_ocr.confidence == 0.0:
            reason_codes.append("OCR_FAILED")

        if id_card_faces and live_faces and len(live_faces) == 1 and live_embedding is not None:
            largest_face = max(id_card_faces, key=lambda face: (face.bbox[2] - face.bbox[0]) * (face.bbox[3] - face.bbox[1]))
            id_card_embedding = embedder.embed(self._align_face(id_card_image, largest_face.keypoints))
            id_card_face_similarity = cosine_similarity(live_embedding, id_card_embedding)
            if id_card_face_similarity < self._settings.idv_face_idcard_threshold:
                reason_codes.append("LOW_IDCARD_FACE_SIMILARITY")

        quality_checks = self._quality_service.build_quality_checks(
            face_detected=face_detected,
            single_face=single_face,
            id_card_detected=id_card_detected,
            ocr_confidence=parsed_ocr.confidence,
        )

        unique_reason_codes = list(dict.fromkeys(reason_codes))
        matched = not unique_reason_codes
        _ = time.perf_counter() - start

        return IdvVerifyResponse(
            verificationId=context.verification_id,
            status="SUCCEEDED" if matched else "FAILED",
            matched=matched,
            faceSimilarityScore=self._round_score(face_similarity),
            idCardFaceSimilarityScore=self._round_score(id_card_face_similarity),
            reasonCodes=unique_reason_codes,
            ocr=OcrResult(
                name=parsed_ocr.name,
                rrnMasked=parsed_ocr.rrn_masked,
                address=parsed_ocr.address,
            ),
            qualityChecks=quality_checks,
            modelVersion=self._settings.idv_model_version,
        )

    def _load_image(self, upload_file: UploadFile) -> np.ndarray:
        max_size_bytes = self._settings.idv_max_image_mb * 1024 * 1024
        raw_bytes = upload_file.file.read()
        if not raw_bytes:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"{upload_file.filename} is empty")
        if len(raw_bytes) > max_size_bytes:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"{upload_file.filename} exceeds {self._settings.idv_max_image_mb}MB",
            )

        image_array = np.frombuffer(raw_bytes, dtype=np.uint8)
        image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
        if image is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"{upload_file.filename} is not a valid image")
        return image

    def _detect_faces(self, detector, image: np.ndarray) -> list[DetectedFace]:
        faces = detector.get(image)
        results: list[DetectedFace] = []
        for face in faces:
            keypoints = np.asarray(face.kps, dtype=np.float32)
            bbox = tuple(float(value) for value in face.bbox)
            results.append(DetectedFace(bbox=bbox, score=float(face.det_score), keypoints=keypoints))
        return results

    def _align_face(self, image: np.ndarray, keypoints: np.ndarray) -> np.ndarray:
        transform, _ = cv2.estimateAffinePartial2D(keypoints, ARCFACE_TEMPLATE, method=cv2.LMEDS)
        if transform is None:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Failed to align face")
        return cv2.warpAffine(image, transform, (112, 112), borderValue=0.0)

    def _run_ocr(self, ocr_engine, image: np.ndarray):
        if hasattr(ocr_engine, "predict"):
            result = ocr_engine.predict(image)
            texts, scores = self._extract_predict_result(result)
            return self._ocr_parser.parse(texts, scores)
        if hasattr(ocr_engine, "ocr"):
            result = ocr_engine.ocr(image, cls=False)
            texts, scores = self._extract_legacy_result(result)
            return self._ocr_parser.parse(texts, scores)
        raise ModelUnavailableError("Unsupported PaddleOCR runtime")

    def _extract_predict_result(self, result) -> tuple[list[str], list[float]]:
        texts: list[str] = []
        scores: list[float] = []
        for page in result or []:
            rec_texts = page.get("rec_texts", []) if isinstance(page, dict) else getattr(page, "rec_texts", [])
            rec_scores = page.get("rec_scores", []) if isinstance(page, dict) else getattr(page, "rec_scores", [])
            texts.extend([str(value) for value in rec_texts])
            scores.extend([float(value) for value in rec_scores])
        return texts, scores

    def _extract_legacy_result(self, result) -> tuple[list[str], list[float]]:
        texts: list[str] = []
        scores: list[float] = []
        for page in result or []:
            for item in page or []:
                if len(item) < 2:
                    continue
                recognized = item[1]
                if len(recognized) < 2:
                    continue
                texts.append(str(recognized[0]))
                scores.append(float(recognized[1]))
        return texts, scores

    def _round_score(self, score: float | None) -> float | None:
        if score is None:
            return None
        return round(float(score), 4)


@lru_cache
def get_idv_service() -> IdvService:
    return IdvService(
        settings=get_settings(),
        registry=get_model_registry(),
        quality_service=IdvQualityService(),
        ocr_parser=IdvOcrParser(),
    )
