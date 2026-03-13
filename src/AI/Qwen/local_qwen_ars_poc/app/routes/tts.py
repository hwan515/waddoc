from __future__ import annotations

import time
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request

from app.schemas import TTSSynthesizeRequest, TTSSynthesizeResponse

router = APIRouter(prefix="/tts", tags=["tts"])


@router.post("/synthesize", response_model=TTSSynthesizeResponse)
def synthesize_tts(request: Request, payload: TTSSynthesizeRequest) -> TTSSynthesizeResponse:
    tts_service = request.app.state.tts_service
    settings = request.app.state.settings

    filename = payload.filename or f"tts_{int(time.time() * 1000)}.wav"
    target = settings.tts_output_dir / filename
    if target.suffix.lower() != ".wav":
        target = Path(str(target) + ".wav")

    try:
        result = tts_service.synthesize(
            text=payload.text,
            output_path=str(target),
            voice_prompt=payload.voice_prompt,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    output_url = "/outputs/" + target.resolve().relative_to(settings.output_dir_path.resolve()).as_posix()
    return TTSSynthesizeResponse(
        output_path=result.output_path,
        output_url=output_url,
        sample_rate=result.sample_rate,
        duration_sec=result.duration_sec,
        inference_ms=result.inference_ms,
        text=result.text,
    )

