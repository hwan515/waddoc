from pydantic import BaseModel, ConfigDict


class OcrResult(BaseModel):
    name: str | None = None
    rrnMasked: str | None = None
    address: str | None = None


class QualityChecks(BaseModel):
    faceDetected: bool
    singleFace: bool
    idCardDetected: bool
    ocrConfidence: float


class IdvVerifyResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    verificationId: str
    status: str
    matched: bool
    faceSimilarityScore: float | None = None
    idCardFaceSimilarityScore: float | None = None
    reasonCodes: list[str]
    ocr: OcrResult
    qualityChecks: QualityChecks
    modelVersion: str
