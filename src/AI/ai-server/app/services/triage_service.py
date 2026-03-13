from functools import lru_cache

from app.core.config import get_settings
from app.schemas.triage import TriageRecommendationRequest, TriageRecommendationResponse
from app.services.department_mapper import DepartmentMapper
from app.services.upstage_client import UpstageClient


class TriageService:
    def __init__(self, upstage_client: UpstageClient, department_mapper: DepartmentMapper) -> None:
        self._upstage_client = upstage_client
        self._department_mapper = department_mapper

    async def recommend(
        self, payload: TriageRecommendationRequest
    ) -> TriageRecommendationResponse:
        history = [{"role": item.role, "text": item.text} for item in payload.history]

        try:
            ai_payload = await self._upstage_client.recommend(payload.transcript, history)
        except Exception:
            ai_payload = None

        if ai_payload is None:
            return self._department_mapper.recommend_from_keywords(payload.transcript)

        return self._department_mapper.normalize(ai_payload, payload.transcript)


@lru_cache
def get_triage_service() -> TriageService:
    settings = get_settings()
    return TriageService(
        upstage_client=UpstageClient(settings),
        department_mapper=DepartmentMapper(),
    )

