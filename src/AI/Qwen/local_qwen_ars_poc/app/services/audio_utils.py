from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Tuple

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly


@dataclass(slots=True)
class AudioInfo:
    sample_rate: int
    duration_sec: float
    num_samples: int
    peak: float


def load_wav(path: str | Path) -> tuple[np.ndarray, int]:
    audio, sample_rate = sf.read(str(path), dtype="float32", always_2d=False)
    if isinstance(audio, np.ndarray) and audio.ndim > 1:
        audio = audio.mean(axis=1)
    return np.asarray(audio, dtype=np.float32), int(sample_rate)


def to_mono(audio: np.ndarray) -> np.ndarray:
    if audio.ndim == 1:
        return audio.astype(np.float32, copy=False)
    return np.asarray(audio.mean(axis=1), dtype=np.float32)


def resample_audio(audio: np.ndarray, orig_sr: int, target_sr: int) -> np.ndarray:
    if orig_sr == target_sr:
        return audio.astype(np.float32, copy=False)
    gcd = int(np.gcd(orig_sr, target_sr))
    up = target_sr // gcd
    down = orig_sr // gcd
    resampled = resample_poly(audio, up=up, down=down)
    return np.asarray(resampled, dtype=np.float32)


def normalize_audio(audio: np.ndarray, target_peak: float = 0.9) -> np.ndarray:
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak < 1e-8:
        return audio.astype(np.float32, copy=False)
    scale = min(target_peak / peak, 3.0)
    return np.asarray(audio * scale, dtype=np.float32)


def trim_silence(
    audio: np.ndarray,
    sample_rate: int,
    silence_db: float = 35.0,
    margin_ms: int = 120,
) -> np.ndarray:
    if audio.size == 0:
        return audio
    abs_audio = np.abs(audio)
    max_amp = float(np.max(abs_audio))
    if max_amp < 1e-8:
        return audio
    threshold = max_amp * (10 ** (-silence_db / 20))
    indices = np.where(abs_audio > threshold)[0]
    if indices.size == 0:
        return audio

    margin = int(sample_rate * (margin_ms / 1000))
    start = max(int(indices[0]) - margin, 0)
    end = min(int(indices[-1]) + margin + 1, audio.shape[0])
    return np.asarray(audio[start:end], dtype=np.float32)


def compute_duration(audio: np.ndarray, sample_rate: int) -> float:
    if sample_rate <= 0:
        return 0.0
    return round(float(audio.shape[0]) / float(sample_rate), 4)


def analyze_audio(audio: np.ndarray, sample_rate: int) -> AudioInfo:
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    return AudioInfo(
        sample_rate=sample_rate,
        duration_sec=compute_duration(audio, sample_rate),
        num_samples=int(audio.shape[0]),
        peak=peak,
    )


def preprocess_audio_file(
    path: str | Path,
    target_sr: int,
    do_trim_silence: bool = True,
    silence_db: float = 35.0,
    margin_ms: int = 120,
) -> tuple[np.ndarray, int, AudioInfo]:
    audio, sr = load_wav(path)
    audio = to_mono(audio)
    audio = resample_audio(audio, sr, target_sr)
    if do_trim_silence:
        audio = trim_silence(audio, sample_rate=target_sr, silence_db=silence_db, margin_ms=margin_ms)
    audio = normalize_audio(audio)
    info = analyze_audio(audio, sample_rate=target_sr)
    return audio, target_sr, info


def save_wav(path: str | Path, audio: np.ndarray, sample_rate: int) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(target), audio, sample_rate)

