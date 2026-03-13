from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.config import load_settings
from app.logging_utils import setup_logging
from app.services.qwen_asr_service import QwenASRService


def main() -> None:
    parser = argparse.ArgumentParser(description="Qwen3-ASR smoke test")
    parser.add_argument("--env-file", default=None, help=".env path (optional)")
    parser.add_argument("--audio", required=True, help="input wav file")
    args = parser.parse_args()

    setup_logging()
    settings = load_settings(args.env_file)
    service = QwenASRService(settings)

    audio_path = Path(args.audio)
    if not audio_path.exists():
        raise FileNotFoundError(f"Audio not found: {audio_path}")

    result = service.transcribe_file(audio_path)
    yes_no, _ = service.classify_yes_no(result.transcript)
    symptom = service.extract_symptom(result.transcript)
    print(
        json.dumps(
            {
                "transcript": result.transcript,
                "confidence": result.confidence,
                "duration_sec": result.duration_sec,
                "language": result.language,
                "inference_ms": result.inference_ms,
                "yes_no": yes_no,
                "symptom_label": symptom["label"],
                "symptom_keyword": symptom["matched_keyword"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

