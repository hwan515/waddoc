from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


def _as_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _as_int(value: str | None, default: int) -> int:
    if value is None:
        return default
    return int(value)


@dataclass(slots=True)
class Settings:
    host: str = "0.0.0.0"
    port: int = 8000
    device: str = "auto"
    qwen_tts_model_path: str = ""
    qwen_asr_model_path: str = ""
    output_dir: str = "outputs"
    default_lang: str = "Korean"
    auto_tts_on_start: bool = True
    auto_tts_on_next: bool = True
    asr_target_sample_rate: int = 16000
    asr_trim_silence: bool = True
    asr_silence_db: float = 35.0
    asr_silence_margin_ms: int = 120
    asr_low_confidence_threshold: float = 0.45
    tts_default_speaker: str = "Chelsie"
    tts_default_instruct: str = "친절하고 또렷하게 천천히 안내해 주세요."
    tts_ref_audio_path: str = ""
    tts_dtype: str = "auto"
    asr_dtype: str = "auto"
    max_name_retries: int = 2
    max_yes_no_retries: int = 1

    @property
    def project_root(self) -> Path:
        return Path(__file__).resolve().parents[1]

    @property
    def output_dir_path(self) -> Path:
        raw = Path(self.output_dir).expanduser()
        if raw.is_absolute():
            return raw
        return (self.project_root / raw).resolve()

    @property
    def tts_output_dir(self) -> Path:
        return self.output_dir_path / "tts"

    @property
    def recordings_dir(self) -> Path:
        return self.output_dir_path / "recordings"

    @property
    def transcripts_dir(self) -> Path:
        return self.output_dir_path / "transcripts"

    @property
    def tts_model_dir(self) -> Path:
        return Path(self.qwen_tts_model_path).expanduser().resolve()

    @property
    def asr_model_dir(self) -> Path:
        return Path(self.qwen_asr_model_path).expanduser().resolve()

    def validate(self) -> None:
        if not self.qwen_tts_model_path:
            raise ValueError("QWEN_TTS_MODEL_PATH is required and must point to a local directory.")
        if not self.qwen_asr_model_path:
            raise ValueError("QWEN_ASR_MODEL_PATH is required and must point to a local directory.")

        if not self.tts_model_dir.exists() or not self.tts_model_dir.is_dir():
            raise ValueError(
                f"Invalid QWEN_TTS_MODEL_PATH: {self.tts_model_dir}. "
                "Place the local Qwen3-TTS model files there."
            )
        if not self.asr_model_dir.exists() or not self.asr_model_dir.is_dir():
            raise ValueError(
                f"Invalid QWEN_ASR_MODEL_PATH: {self.asr_model_dir}. "
                "Place the local Qwen3-ASR model files there."
            )

        if self.port < 1 or self.port > 65535:
            raise ValueError(f"PORT must be in 1..65535. Received: {self.port}")
        if self.asr_target_sample_rate <= 0:
            raise ValueError("ASR target sample rate must be positive.")
        if self.max_name_retries < 0:
            raise ValueError("MAX_NAME_RETRIES must be >= 0.")
        if self.max_yes_no_retries < 0:
            raise ValueError("MAX_YES_NO_RETRIES must be >= 0.")

        self.tts_output_dir.mkdir(parents=True, exist_ok=True)
        self.recordings_dir.mkdir(parents=True, exist_ok=True)
        self.transcripts_dir.mkdir(parents=True, exist_ok=True)


def load_settings(env_file: str | None = None) -> Settings:
    load_dotenv(dotenv_path=env_file, override=False)
    settings = Settings(
        host=os.getenv("HOST", "0.0.0.0"),
        port=_as_int(os.getenv("PORT"), 8000),
        device=os.getenv("DEVICE", "auto"),
        qwen_tts_model_path=os.getenv("QWEN_TTS_MODEL_PATH", "").strip(),
        qwen_asr_model_path=os.getenv("QWEN_ASR_MODEL_PATH", "").strip(),
        output_dir=os.getenv("OUTPUT_DIR", "outputs").strip(),
        default_lang=os.getenv("DEFAULT_LANG", "Korean").strip(),
        auto_tts_on_start=_as_bool(os.getenv("AUTO_TTS_ON_START"), True),
        auto_tts_on_next=_as_bool(os.getenv("AUTO_TTS_ON_NEXT"), True),
        asr_target_sample_rate=_as_int(os.getenv("ASR_TARGET_SAMPLE_RATE"), 16000),
        asr_trim_silence=_as_bool(os.getenv("ASR_TRIM_SILENCE"), True),
        asr_silence_db=float(os.getenv("ASR_SILENCE_DB", "35.0")),
        asr_silence_margin_ms=_as_int(os.getenv("ASR_SILENCE_MARGIN_MS"), 120),
        asr_low_confidence_threshold=float(os.getenv("ASR_LOW_CONFIDENCE_THRESHOLD", "0.45")),
        tts_default_speaker=os.getenv("TTS_DEFAULT_SPEAKER", "Chelsie").strip(),
        tts_default_instruct=os.getenv(
            "TTS_DEFAULT_INSTRUCT",
            "친절하고 또렷하게 천천히 안내해 주세요.",
        ).strip(),
        tts_ref_audio_path=os.getenv("TTS_REF_AUDIO_PATH", "").strip(),
        tts_dtype=os.getenv("TTS_DTYPE", "auto").strip(),
        asr_dtype=os.getenv("ASR_DTYPE", "auto").strip(),
        max_name_retries=_as_int(os.getenv("MAX_NAME_RETRIES"), 2),
        max_yes_no_retries=_as_int(os.getenv("MAX_YES_NO_RETRIES"), 1),
    )
    settings.validate()
    return settings
