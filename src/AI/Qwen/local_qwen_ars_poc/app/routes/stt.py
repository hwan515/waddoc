from __future__ import annotations

import time
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, Request, UploadFile

from app.schemas import STTTranscribeResponse

router = APIRouter(prefix="/stt", tags=["stt"])


@router.post("/transcribe", response_model=STTTranscribeResponse)
async def transcribe_audio(
    request: Request,
    audio_file: UploadFile = File(...),
) -> STTTranscribeResponse:
    asr_service = request.app.state.asr_service
    settings = request.app.state.settings
    suffix = Path(audio_file.filename or "input.wav").suffix or ".wav"
    saved_path = settings.recordings_dir / f"stt_{int(time.time() * 1000)}{suffix}"

    payload = await audio_file.read()
    saved_path.write_bytes(payload)

    try:
        result = asr_service.transcribe_file(saved_path)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    yes_no, _ = asr_service.classify_yes_no(result.transcript)
    symptom = asr_service.extract_symptom(result.transcript)
    return STTTranscribeResponse(
        transcript=result.transcript,
        confidence=result.confidence,
        duration_sec=result.duration_sec,
        language=result.language,
        yes_no=yes_no,
        symptom_label=symptom["label"],
        symptom_keyword=symptom["matched_keyword"],
        inference_ms=result.inference_ms,
        recording_path=str(saved_path),
    )

