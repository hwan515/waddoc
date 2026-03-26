from fastapi import APIRouter, Depends

from app.schemas.triage import TriageRecommendationRequest, TriageRecommendationResponse
from app.services.triage_service import TriageService, get_triage_service

router = APIRouter(prefix="/api/v1/triage", tags=["triage"])


@router.post("/recommendations", response_model=TriageRecommendationResponse)
async def recommend_department(
    payload: TriageRecommendationRequest,
    triage_service: TriageService = Depends(get_triage_service),
) -> TriageRecommendationResponse:
    return await triage_service.recommend(payload)

