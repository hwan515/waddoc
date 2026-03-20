from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    upstage_api_key: str | None = None
    upstage_base_url: str = "https://api.upstage.ai/v1"
    upstage_model: str = "solar-pro2"
    cors_origins_raw: str = "http://localhost:5173"
    whisper_model: str = "large-v3"
    whisper_device: str = "cuda"
    whisper_compute_type: str = "float16"
    whisper_language: str = "ko"
    whisper_initial_prompt: str = (
        "증상, 기침, 목 통증, 인후통, 코막힘, 콧물, 두통, 복통, 피부 발진, 어지러움, "
        "이비인후과, 내과, 피부과, 정형외과, 신경과, 예약"
    )
    whisper_partial_interval_ms: int = 1200
    whisper_vad_min_silence_ms: int = 500
    whisper_partial_beam_size: int = 1
    whisper_partial_best_of: int = 1
    whisper_final_beam_size: int = 5
    whisper_final_best_of: int = 5

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins_raw.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
