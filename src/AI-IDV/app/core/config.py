from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    cors_origins_raw: str = Field(
        default="http://localhost:5173",
        validation_alias=AliasChoices("CORS_ORIGINS", "cors_origins_raw"),
    )

    idv_device: str = "cuda"
    idv_det_size_raw: str = Field(
        default="640,640",
        validation_alias=AliasChoices("IDV_DET_SIZE", "idv_det_size_raw"),
    )
    idv_scrfd_model_name: str = "buffalo_l"
    idv_scrfd_root: str = "./models/insightface"
    idv_adaface_model_path: str = "./models/adaface/adaface_ir101_webface12m.onnx"
    idv_adaface_architecture: str = "ir_101"
    idv_ocr_lang: str = "korean"
    idv_face_reference_threshold: float = Field(default=0.35, ge=-1.0, le=1.0)
    idv_face_idcard_threshold: float = Field(default=0.30, ge=-1.0, le=1.0)
    idv_ocr_min_confidence: float = Field(default=0.80, ge=0.0, le=1.0)
    idv_max_concurrency: int = Field(default=1, ge=1)
    idv_timeout_ms: int = Field(default=5000, ge=100)
    idv_max_image_mb: int = Field(default=8, ge=1)
    idv_fail_fast_on_startup: bool = False
    idv_adaface_quantization: str = "fp32"
    idv_model_version: str = "scrfd-adaface-ppocrv5-korean-v1"

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins_raw.split(",") if origin.strip()]

    @property
    def idv_det_size(self) -> tuple[int, int]:
        width, height = self.idv_det_size_raw.split(",", maxsplit=1)
        return int(width), int(height)


@lru_cache
def get_settings() -> Settings:
    return Settings()
