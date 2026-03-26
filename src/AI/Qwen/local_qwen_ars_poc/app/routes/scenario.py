from __future__ import annotations

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile

from app.schemas import ScenarioSessionInspectResponse, ScenarioStartRequest, ScenarioTurnResult

router = APIRouter(prefix="/scenario", tags=["scenario"])


@router.post("/start", response_model=ScenarioTurnResult)
def start_scenario(request: Request, payload: ScenarioStartRequest) -> ScenarioTurnResult:
    service = request.app.state.scenario_service
    try:
        return service.start_session(branch=payload.branch, patient_name=payload.patient_name)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post("/next", response_model=ScenarioTurnResult)
async def next_scenario_turn(
    request: Request,
    session_id: str | None = Form(default=None),
    audio_file: UploadFile | None = File(default=None),
) -> ScenarioTurnResult:
    service = request.app.state.scenario_service
    if not session_id:
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            body = await request.json()
            session_id = body.get("session_id")
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id is required.")

    audio_bytes: bytes | None = None
    filename: str | None = None
    if audio_file is not None:
        filename = audio_file.filename
        audio_bytes = await audio_file.read()
    try:
        return service.advance_session(
            session_id=session_id,
            audio_bytes=audio_bytes,
            audio_filename=filename,
        )
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.get("/{session_id}", response_model=ScenarioSessionInspectResponse)
def get_scenario_session(request: Request, session_id: str) -> ScenarioSessionInspectResponse:
    service = request.app.state.scenario_service
    try:
        return service.get_session(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
