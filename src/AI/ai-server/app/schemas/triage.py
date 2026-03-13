from pydantic import BaseModel, Field


class HistoryItem(BaseModel):
    id: str | None = None
    role: str
    text: str


class TriageRecommendationRequest(BaseModel):
    sessionId: str
    turnId: str
    transcript: str
    history: list[HistoryItem] = Field(default_factory=list)


class TriageRecommendationResponse(BaseModel):
    departmentCode: str
    departmentName: str
    assistantMessage: str
    ttsText: str
    confidence: float
    reason: str

