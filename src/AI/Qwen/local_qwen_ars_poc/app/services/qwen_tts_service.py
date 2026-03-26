from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional

import numpy as np
import soundfile as sf

from app.config import Settings
from app.logging_utils import get_logger

try:
    import torch
    from qwen_tts import Qwen3TTSModel
except ImportError as exc:  # pragma: no cover - import guard
    raise ImportError(
        "qwen-tts and torch are required. Install dependencies with requirements.txt first."
    ) from exc


@dataclass(slots=True)
class TTSResult:
    output_path: str
    sample_rate: int
    duration_sec: float
    inference_ms: float
    text: str
    model_kind: str


class QwenTTSService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.logger = get_logger(self.__class__.__name__)
        self.model = self._load_model()
        self.model_kind = self._detect_model_kind()
        self.model_loaded = True

    def _resolve_dtype(self) -> Any:
        dtype_map = {
            "float16": torch.float16,
            "bfloat16": torch.bfloat16,
            "float32": torch.float32,
        }
        if self.settings.tts_dtype == "auto":
            return "auto"
        return dtype_map.get(self.settings.tts_dtype.lower(), "auto")

    def _resolve_device_map(self) -> str:
        device = self.settings.device.strip().lower()
        if device in {"auto", "cpu"}:
            return device
        return self.settings.device

    def _load_model(self) -> Qwen3TTSModel:
        start = time.perf_counter()
        model = Qwen3TTSModel.from_pretrained(
            str(self.settings.tts_model_dir),
            device_map=self._resolve_device_map(),
            dtype=self._resolve_dtype(),
        )
        elapsed_ms = (time.perf_counter() - start) * 1000
        self.logger.info(
            "TTS model loaded",
            extra={
                "event": "tts_model_loaded",
                "elapsed_ms": round(elapsed_ms, 2),
            },
        )
        return model

    def _detect_model_kind(self) -> str:
        lower = str(self.settings.tts_model_dir).lower()
        if "customvoice" in lower:
            return "customvoice"
        if "voicedesign" in lower:
            return "voicedesign"
        return "base"

    def synthesize(self, text: str, output_path: str, voice_prompt: Optional[str] = None) -> TTSResult:
        prompt = text.strip()
        if not prompt:
            raise ValueError("TTS text must not be empty.")
        target = Path(output_path)
        target.parent.mkdir(parents=True, exist_ok=True)

        start = time.perf_counter()
        try:
            wavs, sample_rate = self._run_tts(prompt, voice_prompt=voice_prompt)
        except Exception as exc:
            raise RuntimeError(f"Qwen3-TTS inference failed: {exc}") from exc

        if isinstance(wavs, np.ndarray):
            waveform = np.asarray(wavs, dtype=np.float32)
        elif isinstance(wavs, (list, tuple)) and len(wavs) > 0:
            waveform = np.asarray(wavs[0], dtype=np.float32)
        else:
            raise RuntimeError("Qwen3-TTS returned empty waveform.")
        sf.write(str(target), waveform, sample_rate)
        elapsed_ms = (time.perf_counter() - start) * 1000
        duration_sec = round(float(waveform.shape[0]) / float(sample_rate), 4)

        result = TTSResult(
            output_path=str(target),
            sample_rate=int(sample_rate),
            duration_sec=duration_sec,
            inference_ms=round(elapsed_ms, 2),
            text=prompt,
            model_kind=self.model_kind,
        )
        self.logger.info(
            "TTS generation done",
            extra={
                "event": "tts_generated",
                "elapsed_ms": result.inference_ms,
                "output_file": result.output_path,
            },
        )
        return result

    def _run_tts(self, text: str, voice_prompt: Optional[str]) -> tuple[list[np.ndarray], int]:
        language = self.settings.default_lang
        if self.model_kind == "customvoice":
            try:
                return self.model.generate_custom_voice(
                    text=text,
                    language=language,
                    speaker=self.settings.tts_default_speaker,
                    instruct=voice_prompt or self.settings.tts_default_instruct,
                )
            except TypeError:
                return self.model.generate_custom_voice(
                    text=text,
                    language=language,
                    speaker=self.settings.tts_default_speaker,
                )

        if self.model_kind == "voicedesign":
            return self.model.generate_voice_design(
                text=text,
                language=language,
                instruct=voice_prompt or self.settings.tts_default_instruct,
            )

        effective_ref_audio = voice_prompt or self.settings.tts_ref_audio_path
        if not effective_ref_audio:
            raise ValueError(
                "Base Qwen3-TTS model requires voice prompt audio. "
                "Set TTS_REF_AUDIO_PATH in .env or pass voice_prompt."
            )

        return self.model.generate_voice_clone(
            text=text,
            language=language,
            ref_audio=effective_ref_audio,
            x_vector_only_mode=True,
        )
