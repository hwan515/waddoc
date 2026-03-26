from __future__ import annotations

import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from app.config import Settings
from app.data.symptom_dictionary import DEFAULT_SYMPTOM_LABEL, SYMPTOM_KEYWORDS
from app.logging_utils import get_logger
from app.services.audio_utils import AudioInfo, preprocess_audio_file

try:
    import torch
    from qwen_asr import Qwen3ASRModel
except ImportError as exc:  # pragma: no cover - import guard
    raise ImportError(
        "qwen-asr and torch are required. Install dependencies with requirements.txt first."
    ) from exc


YES_TOKENS = ("네", "예", "맞아요", "맞습니다", "응", "그래", "맞아")
NO_TOKENS = ("아니오", "아니요", "아니", "틀려요", "아닙니다", "아냐")


@dataclass(slots=True)
class ASRResult:
    transcript: str
    confidence: float | None
    duration_sec: float | None
    language: str | None
    inference_ms: float
    audio_info: AudioInfo
    raw: Any


class QwenASRService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.logger = get_logger(self.__class__.__name__)
        self.model = self._load_model()
        self.model_loaded = True

    def _resolve_dtype(self) -> Any:
        dtype_map = {
            "float16": torch.float16,
            "bfloat16": torch.bfloat16,
            "float32": torch.float32,
        }
        if self.settings.asr_dtype == "auto":
            return "auto"
        return dtype_map.get(self.settings.asr_dtype.lower(), "auto")

    def _resolve_device_map(self) -> str:
        device = self.settings.device.strip().lower()
        if device in {"auto", "cpu"}:
            return device
        return self.settings.device

    def _load_model(self) -> Qwen3ASRModel:
        start = time.perf_counter()
        model = Qwen3ASRModel.from_pretrained(
            str(self.settings.asr_model_dir),
            dtype=self._resolve_dtype(),
            device_map=self._resolve_device_map(),
            max_new_tokens=256,
        )
        elapsed_ms = (time.perf_counter() - start) * 1000
        self.logger.info(
            "ASR model loaded",
            extra={
                "event": "asr_model_loaded",
                "elapsed_ms": round(elapsed_ms, 2),
            },
        )
        return model

    def transcribe_file(self, audio_path: str | Path, language: str | None = None) -> ASRResult:
        start = time.perf_counter()
        waveform, sample_rate, info = preprocess_audio_file(
            path=audio_path,
            target_sr=self.settings.asr_target_sample_rate,
            do_trim_silence=self.settings.asr_trim_silence,
            silence_db=self.settings.asr_silence_db,
            margin_ms=self.settings.asr_silence_margin_ms,
        )

        try:
            results = self.model.transcribe(
                audio=[(waveform, sample_rate)],
                language=language or self.settings.default_lang,
            )
        except Exception as exc:
            raise RuntimeError(f"Qwen3-ASR inference failed: {exc}") from exc

        if not results:
            raise RuntimeError("Qwen3-ASR returned no result.")

        raw_item = results[0]
        transcript = self._extract_value(raw_item, "text") or ""
        confidence = self._extract_float(raw_item, "confidence")
        language_out = self._extract_value(raw_item, "language")

        elapsed_ms = (time.perf_counter() - start) * 1000
        transcript = self.clean_transcript(transcript)

        result = ASRResult(
            transcript=transcript,
            confidence=confidence,
            duration_sec=info.duration_sec,
            language=language_out,
            inference_ms=round(elapsed_ms, 2),
            audio_info=info,
            raw=raw_item,
        )
        self.logger.info(
            "ASR inference done",
            extra={
                "event": "asr_inference",
                "elapsed_ms": result.inference_ms,
                "transcript": result.transcript,
            },
        )
        return result

    def is_low_confidence(self, result: ASRResult) -> bool:
        if not result.transcript.strip():
            return True
        if result.confidence is None:
            return False
        return result.confidence < self.settings.asr_low_confidence_threshold

    def classify_yes_no(self, transcript: str) -> tuple[str, float]:
        normalized = self._normalize_korean_text(transcript)
        if not normalized:
            return "unknown", 0.0

        yes_hit = any(token in normalized for token in map(self._normalize_korean_text, YES_TOKENS))
        no_hit = any(token in normalized for token in map(self._normalize_korean_text, NO_TOKENS))

        if yes_hit and not no_hit:
            return "yes", 0.9
        if no_hit and not yes_hit:
            return "no", 0.9
        return "unknown", 0.2

    def extract_symptom(self, transcript: str) -> dict[str, str | None]:
        normalized = self._normalize_korean_text(transcript)
        if not normalized:
            return {"label": DEFAULT_SYMPTOM_LABEL, "matched_keyword": None}

        for label, keywords in SYMPTOM_KEYWORDS.items():
            for keyword in keywords:
                if self._normalize_korean_text(keyword) in normalized:
                    return {"label": label, "matched_keyword": keyword}
        return {"label": DEFAULT_SYMPTOM_LABEL, "matched_keyword": None}

    @staticmethod
    def clean_transcript(text: str) -> str:
        cleaned = re.sub(r"\s+", " ", text or "").strip()
        return cleaned

    @staticmethod
    def _normalize_korean_text(text: str) -> str:
        text = (text or "").lower()
        text = re.sub(r"[^\w가-힣]", "", text)
        return text

    @staticmethod
    def _extract_value(obj: Any, key: str) -> Any:
        if hasattr(obj, key):
            return getattr(obj, key)
        if isinstance(obj, dict):
            return obj.get(key)
        return None

    @classmethod
    def _extract_float(cls, obj: Any, key: str) -> float | None:
        value = cls._extract_value(obj, key)
        if value is None:
            return None
        try:
            if isinstance(value, np.ndarray):
                return float(value.mean())
            return float(value)
        except (TypeError, ValueError):
            return None

