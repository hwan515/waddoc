# AI-IDV Implementation Plan

현재 구현 상태와 운영 절차는 [GPU_SERVER_RUNBOOK.md](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/GPU_SERVER_RUNBOOK.md)를 기준으로 본다.

## Goal

`src/AI-IDV`는 차량 탑승 환자 본인확인을 위한 FastAPI 기반 GPU 서버 구현 영역으로 사용한다.

이번 범위의 목표는 아래 3가지를 만족하는 IDV 서버를 설계하고 구현하는 것이다.

- `POST /idv/api/v1/verify` 단일 엔드포인트로 얼굴 대조, 신분증 OCR, 신분증 얼굴 대조를 수행한다.
- 모델 스택은 `SCRFD + AdaFace + PP-OCRv5(korean)`로 고정한다.
- Spring Boot 메인 서버가 이 API를 호출하고, 차량 FE는 GPU 서버를 직접 호출하지 않는다.

## Scope

포함 범위:

- FastAPI 라우터, 스키마, 서비스 계층 추가
- GPU 모델 preload 및 공용 model registry 구성
- 얼굴 검출/정렬/임베딩 추론
- 신분증 OCR 및 응답 정규화
- 품질 검사와 `reasonCodes` 조합
- 헬스체크 및 추론 timeout/에러 응답 정리

제외 범위:

- STT
- Triage
- Consent
- LiveKit 토큰 발급
- Spring의 최종 환자 정보 재검증 로직

## Model Stack

### 1. Face Detection / Alignment

- Model: `SCRFD`
- 역할:
  - `referenceImage` 얼굴 검출
  - `faceImage` 얼굴 검출
  - `idCardImage` 내부 얼굴 검출
  - landmark 기반 정렬

### 2. Face Recognition / Embedding

- Model: `AdaFace`
- 역할:
  - `referenceImage` embedding 생성
  - `faceImage` embedding 생성
  - `idCardImage` 내부 얼굴 embedding 생성
  - cosine similarity 계산
- 운영 기본 형식:
  - `adaface_ir101_webface12m.onnx`
- checkpoint 변환:
  - 공식 pretrained checkpoint `adaface_ir101_webface12m.ckpt`를 한 번 `onnx`로 export한 뒤 운영에 사용한다.

### 3. OCR

- Model: `PP-OCRv5(korean)`
- 역할:
  - 신분증 텍스트 검출 및 인식
  - `name`, `rrn`, `address` 추출
  - OCR confidence 산출

## Runtime Assumptions

- GPU 메모리: `16GB+`
- Inference device: `cuda`
- FastAPI worker: `1`
- 모델은 앱 startup 시 preload
- 다중 worker 금지

## Model Assets

- AdaFace 공식 pretrained 모델은 `ckpt` 형식으로 제공된다.
- 운영 기본 경로:
  - `./models/adaface/adaface_ir101_webface12m.onnx`
- 변환 입력 경로:
  - `./models/adaface/adaface_ir101_webface12m.ckpt`
- 예시 다운로드:

```bash
python3 -m pip install gdown
mkdir -p ./models/adaface
python3 -m gdown https://drive.google.com/uc?id=1dswnavflETcnAuplZj1IOKKP0eM8ITgT -O ./models/adaface/adaface_ir101_webface12m.ckpt
```

- `huggingface-cli`를 사용할 수도 있지만 필수는 아니다.
- 운영 서버에서는 `torch`와 `paddlepaddle-gpu` 충돌 가능성이 있으므로, runtime은 `onnxruntime-gpu + paddleocr + paddlepaddle-gpu` 조합을 기본으로 한다.
- checkpoint export는 별도 1회 작업으로 수행한다.

### AdaFace ONNX Export

checkpoint를 내려받은 뒤 아래처럼 ONNX로 변환한다.

```bash
python3 -m pip install -r requirements-export.txt
python3 scripts/export_adaface_to_onnx.py \
  --checkpoint ./models/adaface/adaface_ir101_webface12m.ckpt \
  --output ./models/adaface/adaface_ir101_webface12m.onnx \
  --architecture ir_101
```

운영 서버 `.env`는 아래 값을 사용한다.

```bash
IDV_ADAFACE_MODEL_PATH=./models/adaface/adaface_ir101_webface12m.onnx
```

## Directory Plan

```text
src/AI-IDV/
  README.md
  app/
    main.py
    core/
      config.py
    api/
      v1/
        routes/
          health.py
          idv.py
    schemas/
      idv.py
    services/
      idv_model_registry.py
      idv_service.py
      idv_quality_service.py
      idv_ocr_parser.py
      idv_similarity.py
```

## API Contract

### `GET /idv/api/v1/health`

응답:

```json
{
  "status": "ok"
}
```

### `POST /idv/api/v1/verify`

Request: `multipart/form-data`

- `verificationId`
- `patientId`
- `verificationMode=FACE_AND_IDCARD`
- `referenceImage`
- `faceImage`
- `idCardImage`

Response:

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

## Processing Pipeline

### Step 1. Input Validation

- multipart 필수 파트 존재 확인
- 이미지 MIME/type 확인
- 빈 파일 차단
- 파일 크기 상한 적용

### Step 2. Reference Face Processing

- `referenceImage`에서 얼굴 검출
- 단일 얼굴만 허용
- landmark 기반 정렬
- AdaFace embedding 생성

### Step 3. Live Face Processing

- `faceImage`에서 얼굴 검출
- 단일 얼굴만 허용
- blur, glare, low-light 등 기본 품질 체크
- 정렬 후 AdaFace embedding 생성
- `faceSimilarityScore` 계산

### Step 4. ID Card OCR Processing

- `idCardImage`에서 신분증 영역 품질 검사
- PP-OCRv5(korean) 실행
- OCR 결과에서 `name`, `rrn`, `address` 파싱
- 주민번호 원문은 메모리 내에서만 사용
- 응답에는 `rrnMasked`만 포함

### Step 5. ID Card Face Processing

- `idCardImage` 내부 얼굴 검출
- 정렬 후 AdaFace embedding 생성
- `faceImage` embedding과 비교
- `idCardFaceSimilarityScore` 계산

### Step 6. Decision

`matched=true` 조건:

- `faceSimilarityScore >= IDV_FACE_REFERENCE_THRESHOLD`
- `idCardFaceSimilarityScore >= IDV_FACE_IDCARD_THRESHOLD`
- `ocrConfidence >= IDV_OCR_MIN_CONFIDENCE`
- `name`, `rrn`, `address` 추출 성공
- 얼굴 검출/단일 얼굴/신분증 검출 품질 검사 통과

실패 시 `matched=false`와 함께 `reasonCodes`를 반환한다.

## Reason Code Plan

- `REFERENCE_FACE_NOT_FOUND`
- `LIVE_FACE_NOT_FOUND`
- `MULTIPLE_FACES_DETECTED`
- `LOW_FACE_QUALITY`
- `IDCARD_NOT_DETECTED`
- `IDCARD_FACE_NOT_FOUND`
- `LOW_FACE_SIMILARITY`
- `LOW_IDCARD_FACE_SIMILARITY`
- `OCR_FAILED`
- `OCR_LOW_CONFIDENCE`
- `REQUIRED_OCR_FIELDS_MISSING`

## Service Responsibilities

### `idv_model_registry.py`

- SCRFD, AdaFace, PP-OCRv5 모델 로딩
- singleton 관리
- startup preload

### `idv_service.py`

- 전체 추론 orchestration
- request -> pipeline -> response 변환

### `idv_quality_service.py`

- blur/glare/single-face/document-detected 검사
- `qualityChecks` 생성

### `idv_ocr_parser.py`

- OCR raw output 파싱
- `name`, `rrn`, `address` 추출
- `rrnMasked` 생성

### `idv_similarity.py`

- embedding normalization
- cosine similarity 계산
- threshold 비교

## Config Plan

`config.py`에 아래 항목을 추가한다.

- `IDV_DEVICE=cuda`
- `IDV_SCRFD_MODEL_PATH`
- `IDV_ADAFACE_MODEL_PATH`
- `IDV_ADAFACE_ARCHITECTURE`
- `IDV_OCR_LANG=korean`
- `IDV_FACE_REFERENCE_THRESHOLD`
- `IDV_FACE_IDCARD_THRESHOLD`
- `IDV_OCR_MIN_CONFIDENCE`
- `IDV_MAX_CONCURRENCY`
- `IDV_TIMEOUT_MS`
- `IDV_MAX_IMAGE_MB`

## Concurrency Plan

- GPU 서버는 `uvicorn --workers 1`
- IDV 추론은 `asyncio.Semaphore`로 동시 처리 수 제한
- 기본값은 `1~2` 수준에서 시작
- timeout 발생 시 추론 중단 후 에러 응답 반환

## Logging / Security Rules

- 로그에 주민번호 원문 저장 금지
- 로그 필드:
  - `verificationId`
  - `patientId`
  - `matched`
  - `reasonCodes`
  - latency
- 이미지 원본 저장은 기본 비활성
- 디버그 저장이 필요하면 별도 운영 옵션으로 분리

## Implementation Steps

1. `app/main.py`와 `/idv/api/v1` 라우터 골격 추가
2. `config.py`에 IDV 관련 env 추가
3. `idv_model_registry.py`에서 모델 preload 구현
4. `idv_similarity.py`와 `idv_quality_service.py` 구현
5. `idv_ocr_parser.py` 구현
6. `idv_service.py`에서 전체 pipeline 연결
7. `schemas/idv.py` request/response 스키마 정의
8. `/idv/api/v1/health`, `/idv/api/v1/verify` 라우터 연결
9. unit/integration 테스트 추가
10. Spring 연동 smoke test 수행

## Test Plan

### Unit

- cosine similarity 계산
- OCR parsing
- rrn masking
- reason code 조합

### Integration

- multipart 업로드 -> JSON 응답
- 정상 케이스
- 얼굴 불일치
- 신분증 얼굴 불일치
- OCR 실패
- 신분증 미검출

### Offline Eval

- 실제 샘플셋으로 threshold 튜닝
- false accept / false reject 확인

## Acceptance Criteria

- `POST /idv/api/v1/verify`가 문서 계약대로 응답한다.
- 동일 인물일 때 `matched=true`가 안정적으로 나온다.
- 타인 얼굴 또는 신분증 불일치 시 `matched=false`가 나온다.
- 주민번호 원문이 응답/로그에 남지 않는다.
- Spring에서 이 응답을 받아 본인확인 성공 상태를 캐시에 저장할 수 있다.

## Runtime Install

운영 서버 runtime 설치:

```bash
python -m pip install -r requirements.txt
python -m pip install paddlepaddle-gpu==3.2.0 -i https://www.paddlepaddle.org.cn/packages/stable/cu118/
```

checkpoint export 전용 설치:

```bash
python -m pip install -r requirements-export.txt
```
