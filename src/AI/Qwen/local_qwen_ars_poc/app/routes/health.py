from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Request

from app.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health_check(request: Request) -> HealthResponse:
    tts_service = request.app.state.tts_service
    asr_service = request.app.state.asr_service
    settings = request.app.state.settings
    return HealthResponse(
        status="ok",
        tts_model_loaded=bool(getattr(tts_service, "model_loaded", False)),
        asr_model_loaded=bool(getattr(asr_service, "model_loaded", False)),
        device=settings.device,
        timestamp=datetime.now(tz=timezone.utc),
    )

