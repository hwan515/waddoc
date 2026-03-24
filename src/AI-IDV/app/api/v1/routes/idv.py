from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.schemas.idv import IdvVerifyResponse
from app.services.idv_service import IdvRequestContext, IdvService, get_idv_service

router = APIRouter(prefix="/idv/api/v1", tags=["idv"])


@router.post("/verify", response_model=IdvVerifyResponse, status_code=status.HTTP_200_OK)
async def verify_identity(
    verification_id: str = Form(..., alias="verificationId"),
    patient_id: str = Form(..., alias="patientId"),
    verification_mode: str = Form(..., alias="verificationMode"),
    reference_image: UploadFile | None = File(None, alias="referenceImage"),
    face_image: UploadFile = File(..., alias="faceImage"),
    id_card_image: UploadFile = File(..., alias="idCardImage"),
    service: IdvService = Depends(get_idv_service),
) -> IdvVerifyResponse:
    if verification_mode != "FACE_AND_IDCARD":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="verificationMode must be FACE_AND_IDCARD",
        )

    return await service.verify(
        IdvRequestContext(
            verification_id=verification_id,
            patient_id=patient_id,
            reference_image=reference_image,
            face_image=face_image,
            id_card_image=id_card_image,
        )
    )
