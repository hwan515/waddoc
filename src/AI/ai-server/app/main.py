from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.routes.health import router as health_router
from app.api.v1.routes.stt import router as stt_router
from app.api.v1.routes.triage import router as triage_router
from app.core.config import get_settings

settings = get_settings()

app = FastAPI(title="reservation-ai-server", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(stt_router)
app.include_router(triage_router)

