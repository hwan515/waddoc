from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.config import load_settings
from app.logging_utils import setup_logging
from app.services.qwen_tts_service import QwenTTSService


def main() -> None:
    parser = argparse.ArgumentParser(description="Qwen3-TTS smoke test")
    parser.add_argument("--env-file", default=None, help=".env path (optional)")
    parser.add_argument(
        "--text",
        default="안녕하세요. 왓닥입니다. 천천히 또렷하게 안내드리겠습니다.",
        help="Korean text prompt",
    )
    parser.add_argument("--output", default="outputs/tts/smoke_test_tts.wav", help="output wav file")
    parser.add_argument(
        "--voice-prompt",
        default=None,
        help="customvoice/voicedesign: style instruction, base model: ref wav path",
    )
    args = parser.parse_args()

    setup_logging()
    settings = load_settings(args.env_file)
    service = QwenTTSService(settings)
    target = Path(args.output)
    if not target.is_absolute():
        target = settings.project_root / target

    result = service.synthesize(args.text, str(target), voice_prompt=args.voice_prompt)
    print(
        json.dumps(
            {
                "output_path": result.output_path,
                "sample_rate": result.sample_rate,
                "duration_sec": result.duration_sec,
                "inference_ms": result.inference_ms,
                "model_kind": result.model_kind,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

