from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from app.config import Settings, load_settings
from app.logging_utils import get_logger, setup_logging
from app.routes.health import router as health_router
from app.routes.scenario import router as scenario_router
from app.routes.stt import router as stt_router
from app.routes.tts import router as tts_router
from app.services.qwen_asr_service import QwenASRService
from app.services.qwen_tts_service import QwenTTSService
from app.services.scenario_service import ScenarioService
from app.state_machine import DeterministicStateMachine


def create_app() -> FastAPI:
    setup_logging()
    logger = get_logger("app.main")
    settings = load_settings()
    project_root = settings.project_root

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        logger.info("Startup checks begin", extra={"event": "startup_checks"})
        state_machine = DeterministicStateMachine(
            max_name_retries=settings.max_name_retries,
            max_yes_no_retries=settings.max_yes_no_retries,
        )
        tts_service = QwenTTSService(settings=settings)
        asr_service = QwenASRService(settings=settings)
        scenario_service = ScenarioService(
            settings=settings,
            state_machine=state_machine,
            tts_service=tts_service,
            asr_service=asr_service,
        )
        app.state.settings = settings
        app.state.tts_service = tts_service
        app.state.asr_service = asr_service
        app.state.scenario_service = scenario_service
        logger.info("Startup checks complete", extra={"event": "startup_ready"})
        yield

    app = FastAPI(
        title="Local Qwen ARS PoC",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.state.settings = settings

    static_dir = project_root / "static"
    app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")
    app.mount("/outputs", StaticFiles(directory=str(settings.output_dir_path)), name="outputs")

    app.include_router(health_router)
    app.include_router(scenario_router)
    app.include_router(tts_router)
    app.include_router(stt_router)

    @app.get("/")
    async def index() -> FileResponse:
        return FileResponse(path=str(static_dir / "index.html"))

    @app.exception_handler(ValueError)
    async def handle_value_error(_, exc: ValueError) -> JSONResponse:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    return app


app = create_app()


def get_runtime_settings() -> Settings:
    return app.state.settings

