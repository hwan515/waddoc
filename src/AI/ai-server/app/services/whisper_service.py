import asyncio
import re
from dataclasses import dataclass
from functools import lru_cache
from threading import Lock
from typing import Literal

import numpy as np

from app.core.config import Settings, get_settings

try:
    from faster_whisper import WhisperModel
except ImportError:  # pragma: no cover - handled at runtime
    WhisperModel = None


@dataclass
class TranscriptionResult:
    text: str
    confidence: float


TranscriptionMode = Literal["partial", "final"]


class WhisperService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model = None
        self._model_lock = Lock()

    async def transcribe_pcm(
        self,
        pcm_bytes: bytes,
        language: str | None = None,
        mode: TranscriptionMode = "final",
    ) -> TranscriptionResult:
        return await asyncio.to_thread(
            self._transcribe_pcm_sync,
            pcm_bytes,
            language or self._settings.whisper_language,
            mode,
        )

    def _transcribe_pcm_sync(
        self,
        pcm_bytes: bytes,
        language: str,
        mode: TranscriptionMode,
    ) -> TranscriptionResult:
        if WhisperModel is None:
            raise RuntimeError("faster-whisper 패키지가 설치되지 않았습니다.")

        if len(pcm_bytes) < 3200:
            return TranscriptionResult(text="", confidence=0.0)

        audio = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        if audio.size == 0:
            return TranscriptionResult(text="", confidence=0.0)

        model = self._get_model()
        is_final = mode == "final"
        segments, info = model.transcribe(
            audio,
            language=language,
            beam_size=self._settings.whisper_final_beam_size if is_final else self._settings.whisper_partial_beam_size,
            best_of=self._settings.whisper_final_best_of if is_final else self._settings.whisper_partial_best_of,
            temperature=0.0,
            condition_on_previous_text=is_final,
            without_timestamps=True,
            word_timestamps=False,
            vad_filter=True,
            initial_prompt=self._settings.whisper_initial_prompt,
            vad_parameters={"min_silence_duration_ms": self._settings.whisper_vad_min_silence_ms},
        )

        joined = " ".join(segment.text.strip() for segment in segments if segment.text)
        text = re.sub(r"\s+", " ", joined).strip()
        confidence = getattr(info, "language_probability", None)
        normalized_confidence = float(confidence) if confidence is not None else 0.82
        normalized_confidence = max(0.0, min(1.0, normalized_confidence))

        return TranscriptionResult(text=text, confidence=normalized_confidence)

    def _get_model(self):
        with self._model_lock:
            if self._model is None:
                self._model = WhisperModel(
                    self._settings.whisper_model,
                    device=self._settings.whisper_device,
                    compute_type=self._settings.whisper_compute_type,
                )

        return self._model


@lru_cache
def get_whisper_service() -> WhisperService:
    return WhisperService(get_settings())
