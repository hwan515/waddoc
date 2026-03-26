from fastapi import APIRouter, Depends, status

from app.services.idv_model_registry import IdvModelRegistry, get_model_registry

router = APIRouter(prefix="/idv/api/v1", tags=["health"])


@router.get("/health", status_code=status.HTTP_200_OK)
async def health(
    registry: IdvModelRegistry = Depends(get_model_registry),
) -> dict[str, object]:
    readiness = registry.readiness()
    status_value = "ok" if readiness["ready"] else "degraded"
    return {
        "status": status_value,
        "ready": readiness["ready"],
        "components": readiness["components"],
        "modelVersion": readiness["modelVersion"],
        "lastError": readiness["lastError"],
    }
