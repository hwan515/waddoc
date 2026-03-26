from typing import Literal

from pydantic import BaseModel


class SttStartEvent(BaseModel):
    type: Literal["stt.start"]
    language: str = "ko"
    sampleRate: int = 16000


class SttStopEvent(BaseModel):
    type: Literal["stt.stop"]


class SttReadyEvent(BaseModel):
    type: Literal["stt.ready"] = "stt.ready"


class SttPartialEvent(BaseModel):
    type: Literal["stt.partial"] = "stt.partial"
    turnId: str
    text: str


class SttFinalEvent(BaseModel):
    type: Literal["stt.final"] = "stt.final"
    turnId: str
    text: str
    confidence: float


class SttErrorEvent(BaseModel):
    type: Literal["stt.error"] = "stt.error"
    code: str
    message: str

