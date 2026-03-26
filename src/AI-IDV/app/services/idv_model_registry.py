from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from threading import Lock
from typing import Protocol

import cv2
import numpy as np

from app.core.config import Settings, get_settings

try:
    import onnxruntime as ort
except ImportError:  # pragma: no cover - import validated at runtime
    ort = None

try:
    from insightface.app import FaceAnalysis
except ImportError:  # pragma: no cover - import validated at runtime
    FaceAnalysis = None

try:
    from paddleocr import PaddleOCR
except ImportError:  # pragma: no cover - import validated at runtime
    PaddleOCR = None

try:
    import torch
except ImportError:  # pragma: no cover - import validated at runtime
    torch = None


class ModelUnavailableError(RuntimeError):
    pass


@dataclass(frozen=True)
class DetectedFace:
    bbox: tuple[float, float, float, float]
    score: float
    keypoints: np.ndarray


class AdaFaceEmbedder(Protocol):
    def embed(self, aligned_face: np.ndarray) -> np.ndarray:
        ...


def _prepare_adaface_input(aligned_face: np.ndarray) -> np.ndarray:
    resized = cv2.resize(aligned_face, (112, 112), interpolation=cv2.INTER_LINEAR)
    normalized = resized.astype(np.float32) / 255.0
    normalized = (normalized - 0.5) / 0.5
    nchw = np.transpose(normalized, (2, 0, 1))[np.newaxis, ...]
    return np.ascontiguousarray(nchw, dtype=np.float32)


class AdaFaceOnnxEmbedder:
    def __init__(self, session: "ort.InferenceSession") -> None:
        self._session = session
        self._input_name = session.get_inputs()[0].name
        self._output_name = session.get_outputs()[0].name

    def embed(self, aligned_face: np.ndarray) -> np.ndarray:
        prepared = _prepare_adaface_input(aligned_face)
        output = self._session.run([self._output_name], {self._input_name: prepared})[0]
        return np.asarray(output[0], dtype=np.float32)


class AdaFaceTorchEmbedder:
    def __init__(self, model: "torch.nn.Module", device: "torch.device") -> None:
        self._model = model
        self._device = device

    def embed(self, aligned_face: np.ndarray) -> np.ndarray:
        prepared = _prepare_adaface_input(aligned_face)
        tensor = torch.from_numpy(prepared).to(self._device)
        with torch.no_grad():
            features, _ = self._model(tensor)
        return features[0].detach().cpu().numpy().astype(np.float32)


class IdvModelRegistry:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._lock = Lock()
        self._scrfd = None
        self._adaface = None
        self._ocr = None
        self._last_error: str | None = None

    def warmup(self) -> None:
        self.get_scrfd()
        self.get_adaface()
        self.get_ocr()

    def readiness(self) -> dict[str, object]:
        components = {
            "scrfd": self._scrfd is not None,
            "adaface": self._adaface is not None,
            "ppocr": self._ocr is not None,
        }
        return {
            "ready": all(components.values()),
            "components": components,
            "modelVersion": self._settings.idv_model_version,
            "lastError": self._last_error,
        }

    def get_scrfd(self):
        with self._lock:
            if self._scrfd is not None:
                return self._scrfd

            if FaceAnalysis is None:
                self._last_error = "insightface is not installed"
                raise ModelUnavailableError(self._last_error)

            ctx_id = 0 if self._settings.idv_device.lower() == "cuda" else -1
            app = FaceAnalysis(
                name=self._settings.idv_scrfd_model_name,
                root=self._settings.idv_scrfd_root,
                allowed_modules=["detection"],
            )
            app.prepare(ctx_id=ctx_id, det_size=self._settings.idv_det_size)
            self._scrfd = app
            return self._scrfd

    def get_adaface(self) -> AdaFaceEmbedder:
        with self._lock:
            if self._adaface is not None:
                return self._adaface

            model_path = Path(self._settings.idv_adaface_model_path)
            if model_path.suffix.lower() == ".onnx" and self._settings.idv_adaface_quantization != "fp32":
                variant = self._settings.idv_adaface_quantization
                model_path = model_path.with_stem(f"{model_path.stem}_{variant}")
            if not model_path.exists():
                self._last_error = f"AdaFace model not found: {model_path}"
                raise ModelUnavailableError(self._last_error)

            if model_path.suffix.lower() == ".onnx":
                self._adaface = self._load_adaface_onnx(model_path)
            elif model_path.suffix.lower() in {".ckpt", ".pt", ".pth"}:
                self._adaface = self._load_adaface_torch(model_path)
            else:
                self._last_error = f"Unsupported AdaFace model format: {model_path.suffix}"
                raise ModelUnavailableError(self._last_error)

            self._last_error = None
            return self._adaface

    def _load_adaface_onnx(self, model_path: Path) -> AdaFaceOnnxEmbedder:
        if ort is None:
            self._last_error = "onnxruntime is not installed"
            raise ModelUnavailableError(self._last_error)

        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"] \
            if self._settings.idv_device.lower() == "cuda" \
            else ["CPUExecutionProvider"]
        session = ort.InferenceSession(str(model_path), providers=providers)
        return AdaFaceOnnxEmbedder(session)

    def _load_adaface_torch(self, model_path: Path) -> AdaFaceTorchEmbedder:
        if torch is None:
            self._last_error = (
                "torch is not installed; export AdaFace ckpt to ONNX or install requirements-export.txt"
            )
            raise ModelUnavailableError(self._last_error)

        from app.services.adaface_backbone import build_model

        device = self._resolve_torch_device()
        model = build_model(self._settings.idv_adaface_architecture)
        checkpoint = torch.load(str(model_path), map_location=device)
        state_dict = checkpoint.get("state_dict", checkpoint)
        model_state_dict = {
            self._normalize_state_dict_key(key): value
            for key, value in state_dict.items()
            if self._should_keep_state_dict_key(key)
        }
        model.load_state_dict(model_state_dict, strict=True)
        model.to(device)
        model.eval()
        return AdaFaceTorchEmbedder(model, device)

    def _resolve_torch_device(self) -> "torch.device":
        if self._settings.idv_device.lower() == "cuda" and torch.cuda.is_available():
            return torch.device("cuda")
        return torch.device("cpu")

    def _should_keep_state_dict_key(self, key: str) -> bool:
        normalized = self._normalize_state_dict_key(key)
        return normalized and not normalized.startswith("head.")

    def _normalize_state_dict_key(self, key: str) -> str:
        normalized = key
        if normalized.startswith("module."):
            normalized = normalized[7:]
        if normalized.startswith("model."):
            normalized = normalized[6:]
        return normalized

    def get_ocr(self):
        with self._lock:
            if self._ocr is not None:
                return self._ocr

            if PaddleOCR is None:
                self._last_error = "paddleocr is not installed"
                raise ModelUnavailableError(self._last_error)

            self._ocr = self._build_ocr_instance()
            self._last_error = None
            return self._ocr

    def _build_ocr_instance(self):
        common_kwargs = {
            "lang": self._settings.idv_ocr_lang,
            "use_doc_orientation_classify": False,
            "use_doc_unwarping": False,
            "use_textline_orientation": False,
        }
        preferred_device = "gpu:0" if self._settings.idv_device.lower() == "cuda" else "cpu"

        try:
            return PaddleOCR(device=preferred_device, **common_kwargs)
        except (TypeError, ValueError):
            pass

        try:
            return PaddleOCR(use_gpu=self._settings.idv_device.lower() == "cuda", **common_kwargs)
        except (TypeError, ValueError) as exc:
            self._last_error = f"PaddleOCR init failed: {exc}"
            raise ModelUnavailableError(self._last_error) from exc


@lru_cache
def get_model_registry() -> IdvModelRegistry:
    return IdvModelRegistry(get_settings())
