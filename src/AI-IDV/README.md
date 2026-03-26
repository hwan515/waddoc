# AI-IDV

`src/AI-IDV`는 차량 탑승 환자 본인확인을 위한 FastAPI 기반 GPU 서버 구현 영역이다.

현재 문서 역할은 아래처럼 나눈다.

- `README.md`: 코드 기준 현재 계약과 범위 요약
- `GPU_SERVER_RUNBOOK.md`: 설치, 기동, 운영 메모, 실제 구축 이슈

## 현재 구현 범위

구현된 API:

- `GET /idv/api/v1/health`
- `POST /idv/api/v1/verify`

구현된 모델 스택:

- `SCRFD`
- `AdaFace`
- `PaddleOCR`

이 서버가 담당하는 일:

- `referenceImage` 얼굴 검출 및 임베딩 비교
- `faceImage` 얼굴 검출, 품질 검사, 임베딩 생성
- `idCardImage` OCR
- `idCardImage` 내부 얼굴과 `faceImage` 간 임베딩 비교

이 서버가 하지 않는 일:

- 환자 기준 이미지 조회
- OCR 결과와 환자 원본 정보 재검증
- 본인확인 성공 상태 캐시 저장
- LiveKit 토큰 발급
- FE 촬영 UX 제어

위 범위는 Spring Boot 및 FE 문서와 의도적으로 분리된다.

## 빠른 진입점

핵심 파일:

- 앱 엔트리포인트: `app/main.py`
- 설정: `app/core/config.py`
- health 라우트: `app/api/v1/routes/health.py`
- verify 라우트: `app/api/v1/routes/idv.py`
- 추론 오케스트레이션: `app/services/idv_service.py`
- 모델 로더: `app/services/idv_model_registry.py`
- OCR 파서: `app/services/idv_ocr_parser.py`
- 품질 검사: `app/services/idv_quality_service.py`
- 유사도 계산: `app/services/idv_similarity.py`
- 양자화 스크립트: `scripts/quantize_adaface_onnx.py`
- 양자화 비교: `scripts/compare_quantized_models.py`
- 운영 절차: `GPU_SERVER_RUNBOOK.md`

## API 계약

### `GET /idv/api/v1/health`

현재 구현은 단순 `"status": "ok"`만 반환하지 않는다.

응답 예시:

```json
{
  "status": "ok",
  "ready": true,
  "components": {
    "scrfd": true,
    "adaface": true,
    "ppocr": true
  },
  "modelVersion": "scrfd-adaface-ppocrv5-korean-v1",
  "lastError": null
}
```

동작 규칙:

- 세 모델이 모두 적재되면 `status="ok"`, `ready=true`
- 하나라도 미적재면 `status="degraded"`, `ready=false`
- `lastError`에는 마지막 로딩 실패 원인이 남는다

### `POST /idv/api/v1/verify`

Request: `multipart/form-data`

- `verificationId`
- `patientId`
- `verificationMode=FACE_AND_IDCARD`
- `referenceImage` (optional)
- `faceImage`
- `idCardImage`

제약:

- `verificationMode`는 `FACE_AND_IDCARD`만 허용
- 파일은 비어 있으면 안 된다
- 파일 크기는 `IDV_MAX_IMAGE_MB`를 초과할 수 없다
- OpenCV로 디코드 가능한 이미지여야 한다

응답 예시:

```json
{
  "verificationId": "vrf_001",
  "status": "SUCCEEDED",
  "matched": true,
  "faceSimilarityScore": 0.94,
  "idCardFaceSimilarityScore": 0.91,
  "reasonCodes": [],
  "ocr": {
    "name": "홍길동",
    "rrnMasked": "580315-1******",
    "address": "경북 울릉군 울릉읍 ..."
  },
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "idCardDetected": true,
    "ocrConfidence": 0.97
  },
  "modelVersion": "scrfd-adaface-ppocrv5-korean-v1"
}
```

응답 규칙:

- `matched`는 `reasonCodes`가 비어 있을 때만 `true`
- `status`는 `matched=true`면 `SUCCEEDED`, 아니면 `FAILED`
- `referenceImage`가 없으면 `faceSimilarityScore`는 `null`일 수 있다
- `ocr`에는 `rrnMasked`만 포함되고 원문 주민번호는 응답으로 내보내지 않는다

## 현재 처리 파이프라인

### 1. 입력 적재

- 업로드 파일 바이트를 읽는다
- 빈 파일, 최대 크기 초과, 디코드 불가 이미지를 바로 거절한다

### 2. 얼굴 검출

- `referenceImage`가 있으면 얼굴을 검출한다
- `faceImage`에서 얼굴을 검출한다
- `idCardImage`에서도 얼굴을 검출한다

현재 구현은 별도 "신분증 문서 검출기"를 두지 않는다. `idCardImage`에 대해 수행하는 시각 처리는 OCR과 얼굴 검출이다.

### 3. 라이브 얼굴 품질 검사

`faceImage`에 대해서만 아래 품질 지표를 계산한다.

- blur score
- brightness score
- glare ratio

이 값이 임계값을 넘으면 `LOW_FACE_QUALITY`를 reason code에 추가한다.

### 4. 얼굴 임베딩 및 유사도 계산

- 얼굴 정렬은 landmark 기반 affine transform을 사용한다
- AdaFace 입력은 `112x112`, `BGR -> NCHW`, `[-1, 1]` 정규화로 변환한다
- `referenceImage`가 있고 기준 얼굴 검출에 성공한 경우에만 `faceSimilarityScore`를 계산한다
- `idCardImage`에서는 검출된 얼굴 중 가장 큰 얼굴만 사용해 `idCardFaceSimilarityScore`를 계산한다

### 5. OCR

- PaddleOCR `predict()`가 가능하면 우선 사용한다
- 아니면 legacy `ocr()` 경로로 fallback 한다
- OCR 결과에서 아래 필드를 파싱한다
  - `name`
  - `rrn`
  - `address`
- 외부 응답에는 `rrnMasked`만 포함한다

### 6. 최종 판정

현재 구현의 `matched=true` 조건은 아래와 같다.

- `referenceImage`가 있으면 기준 얼굴 검출 성공
- `faceImage`에서 단일 얼굴 검출 성공
- `idCardImage`에서 얼굴 검출 성공
- 라이브 얼굴 품질 검사 통과
- OCR confidence가 `IDV_OCR_MIN_CONFIDENCE` 이상
- `name`, `rrn`, `address`가 모두 파싱됨
- `referenceImage`가 있을 때만 `faceSimilarityScore >= IDV_FACE_REFERENCE_THRESHOLD`
- `idCardFaceSimilarityScore >= IDV_FACE_IDCARD_THRESHOLD`

반대로 말하면 현재 구현은 `reasonCodes`가 하나라도 생기면 실패한다.

## 실제 reason code

현재 코드에서 실제로 추가되는 reason code는 아래 목록이다.

- `REFERENCE_FACE_NOT_FOUND`
- `LIVE_FACE_NOT_FOUND`
- `MULTIPLE_FACES_DETECTED`
- `LOW_FACE_QUALITY`
- `IDCARD_FACE_NOT_FOUND`
- `LOW_FACE_SIMILARITY`
- `LOW_IDCARD_FACE_SIMILARITY`
- `OCR_FAILED`
- `OCR_LOW_CONFIDENCE`
- `REQUIRED_OCR_FIELDS_MISSING`

이전 계획 문서에 있던 `IDCARD_NOT_DETECTED`는 현재 코드에서 사용하지 않는다.

## 설정 기준

주요 환경 변수:

```bash
CORS_ORIGINS=http://localhost:5173
IDV_DEVICE=cuda
IDV_DET_SIZE=640,640
IDV_SCRFD_MODEL_NAME=buffalo_l
IDV_SCRFD_ROOT=./models/insightface
IDV_ADAFACE_MODEL_PATH=./models/adaface/adaface_ir101_webface12m.onnx
IDV_ADAFACE_ARCHITECTURE=ir_101
IDV_OCR_LANG=korean
IDV_FACE_REFERENCE_THRESHOLD=0.35
IDV_FACE_IDCARD_THRESHOLD=0.30
IDV_OCR_MIN_CONFIDENCE=0.80
IDV_MAX_CONCURRENCY=1
IDV_TIMEOUT_MS=5000
IDV_MAX_IMAGE_MB=8
IDV_FAIL_FAST_ON_STARTUP=false
IDV_ADAFACE_QUANTIZATION=fp32
IDV_MODEL_VERSION=scrfd-adaface-ppocrv5-korean-v1
```

세부 값은 `.env.example`와 `app/core/config.py`를 기준으로 본다.

## 모델 양자화

AdaFace ONNX 모델은 FP16/INT8 양자화를 지원한다.

지원 variant:

- `fp32`: 기본값. 원본 ONNX 모델
- `fp16`: FP16 양자화. GPU 추론 시 권장. 모델 크기 약 50% 감소
- `int8`: INT8 동적 양자화. CPU 추론 시 권장. 모델 크기 약 75% 감소

설정: `IDV_ADAFACE_QUANTIZATION` 환경변수로 선택한다.

양자화 생성, 검증, 적용 절차는 `GPU_SERVER_RUNBOOK.md`를 참고한다.

## 의존성 정책

운영 런타임:

- `fastapi`
- `uvicorn`
- `python-multipart`
- `pydantic-settings`
- `numpy`
- `opencv-python-headless`
- `onnxruntime-gpu`
- `insightface`
- `paddleocr`
- 별도 설치: `paddlepaddle-gpu==3.2.0`

export 전용 런타임:

- `torch`
- `onnx`
- `onnxscript`

운영 런타임과 export 런타임을 분리하는 이유는 `torch`와 `paddlepaddle-gpu`의 CUDA/NCCL 충돌 가능성 때문이다.

## 테스트 현황

저장소에 있는 테스트:

- `tests/test_config.py`
- `tests/test_idv_model_registry.py`
- `tests/test_idv_ocr_parser.py`
- `tests/test_idv_similarity.py`

현재 테스트가 보장하는 범위:

- env alias 파싱
- AdaFace 입력 전처리
- OCR 파싱 및 주민번호 마스킹
- cosine similarity 계산

현재 저장소 기준으로 route/service 통합 테스트는 없다.

## 현재 한계

- `POST /idv/api/v1/verify` 수동 smoke test는 완료됐지만 자동화된 route/service 통합 테스트는 아직 없다
- Spring Boot와의 실제 연동 smoke test도 아직 미완료 상태다
- `qualityChecks.idCardDetected`는 현재 구현상 실질적인 문서 검출 결과가 아니라 고정 `true`로 내려간다
- 별도 신분증 문서 검출 reason code는 아직 없다

운영 절차와 구축 이슈는 `GPU_SERVER_RUNBOOK.md`를 본다.
