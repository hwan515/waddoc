from __future__ import annotations

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field

from app.state_machine import BranchType, ScenarioState


class HealthResponse(BaseModel):
    status: str
    tts_model_loaded: bool
    asr_model_loaded: bool
    device: str
    timestamp: datetime


class ScenarioStartRequest(BaseModel):
    branch: BranchType
    patient_name: Optional[str] = Field(default=None, description="등록 환자 이름(선택)")


class ParsedIntent(BaseModel):
    yes_no: Optional[Literal["yes", "no", "unknown"]] = None
    symptom_label: Optional[str] = None
    symptom_keyword: Optional[str] = None


class ScenarioTurnResult(BaseModel):
    session_id: str
    current_state: ScenarioState
    next_state: ScenarioState
    prompt_text: Optional[str]
    prompt_audio_path: Optional[str] = None
    prompt_audio_url: Optional[str] = None
    transcript: Optional[str] = None
    parsed_intent: Optional[ParsedIntent] = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime
    updated_at: datetime


class TurnHistoryItem(BaseModel):
    turn_index: int
    state_before: ScenarioState
    state_after: ScenarioState
    prompt_text: Optional[str]
    prompt_audio_path: Optional[str]
    transcript: Optional[str]
    parsed_intent: Optional[ParsedIntent]
    metadata: dict[str, Any]
    audio_input_path: Optional[str]
    created_at: datetime


class ScenarioSessionInspectResponse(BaseModel):
    session_id: str
    branch: BranchType
    patient_name: Optional[str]
    current_state: ScenarioState
    confirmed_name: Optional[str]
    symptom_label: Optional[str]
    symptom_keyword: Optional[str]
    assigned_department: Optional[str]
    yes_no: Optional[str]
    name_retry_count: int
    yes_no_retry_count: int
    total_turns: int
    total_session_duration_sec: float
    transcript_history: list[TurnHistoryItem]
    created_at: datetime
    updated_at: datetime


class TTSSynthesizeRequest(BaseModel):
    text: str = Field(min_length=1)
    filename: Optional[str] = None
    voice_prompt: Optional[str] = Field(
        default=None,
        description="customvoice/voicedesign의 경우 스타일 지시문, base 모델의 경우 ref audio path",
    )


class TTSSynthesizeResponse(BaseModel):
    output_path: str
    output_url: str
    sample_rate: int
    duration_sec: float
    inference_ms: float
    text: str


class STTTranscribeResponse(BaseModel):
    transcript: str
    confidence: Optional[float] = None
    duration_sec: Optional[float] = None
    language: Optional[str] = None
    yes_no: Optional[Literal["yes", "no", "unknown"]] = None
    symptom_label: Optional[str] = None
    symptom_keyword: Optional[str] = None
    inference_ms: float
    recording_path: Optional[str] = None
