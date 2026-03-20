from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.routes.health import router as health_router
from app.api.v1.routes.idv import router as idv_router
from app.core.config import get_settings
from app.services.idv_model_registry import get_model_registry

settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    registry = get_model_registry()
    try:
        registry.warmup()
    except Exception:  # pragma: no cover - startup behavior is environment dependent
        if settings.idv_fail_fast_on_startup:
            raise
    yield


app = FastAPI(title="ai-idv-server", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(idv_router)
