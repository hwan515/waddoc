# GPU Server Runbook

## 목적

이 문서는 `src/AI-IDV` 기준 GPU 본인인증 서버의 현재 운영 절차와 실제 구축 이슈를 정리한다.

문서 경계:

- `README.md`: 현재 코드 계약 요약
- `GPU_SERVER_RUNBOOK.md`: 설치, 기동, 운영 메모, 확인 결과

기준 시점:

- 문서 작성 기준: `2026-03-26`
- 원격 검증 환경: `j-j14a603@jupyter07`

## 현재 상태

현재 구현 완료 범위:

- FastAPI 앱 구성 완료
- `GET /idv/api/v1/health` 구현 완료
- `POST /idv/api/v1/verify` 구현 완료
- `referenceImage` optional 경로 구현 완료
- `SCRFD + AdaFace + PaddleOCR` 모델 로더 구현 완료
- `.env` 기반 runtime 구성 완료
- AdaFace `ckpt -> onnx` export 스크립트 준비 완료

원격 서버 기준 확인 완료:

- `health` 응답 정상
- 모델 warmup 후 `scrfd`, `adaface`, `ppocr` 적재 확인
- `2026-03-26` 기준 `POST /idv/api/v1/verify` 샘플 요청 검증 완료

아직 별도 확인이 남은 범위:

- Spring Boot 서버와의 실제 연동 smoke test
- process manager 등록 정리

## 현재 API 동작 요약

### `GET /idv/api/v1/health`

현재 구현 응답 예시:

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

운영 해석:

- `ready=true`: 세 모델 모두 적재됨
- `status=degraded`: 하나 이상 로딩 실패
- `lastError`: 마지막 적재 실패 원인

### `POST /idv/api/v1/verify`

Request: `multipart/form-data`

- `verificationId`
- `patientId`
- `verificationMode=FACE_AND_IDCARD`
- `referenceImage` (optional)
- `faceImage`
- `idCardImage`

현재 판정 규칙:

- `matched=true`는 `reasonCodes`가 비어 있을 때만 가능
- `referenceImage`가 없으면 `faceSimilarityScore`는 `null`일 수 있다
- no-reference 경로에서는 `live face ↔ id card face + OCR` 기준으로만 통과 여부를 판단한다
- OCR 응답은 `rrnMasked`만 외부로 노출한다

현재 품질/판정 메모:

- 라이브 얼굴 품질 검사는 `faceImage`에만 적용한다
- `idCardImage`에 대해 별도 문서 검출기는 없다
- `qualityChecks.idCardDetected`는 현재 코드상 고정 `true`다

## 아키텍처 요약

역할 분리:

- 차량 FE:
  - 얼굴 사진 촬영
  - 신분증 사진 촬영
- Spring Boot:
  - 세션/미션 제어
  - 기준 환자 이미지 조회
  - GPU 서버 호출
  - OCR 결과와 환자 원본 정보 재검증
  - 본인확인 성공 상태 캐시 저장
- GPU FastAPI:
  - 얼굴 검출
  - 얼굴 임베딩 비교
  - 신분증 OCR
  - 신분증 얼굴 비교

모델 스택:

- `SCRFD`
  - 얼굴 검출 및 landmark 추출
- `AdaFace`
  - 얼굴 임베딩 생성
- `PaddleOCR`
  - 이름, 주민번호, 주소 OCR

## 핵심 파일

- 런타임 설정: `app/core/config.py`
- 앱 엔트리포인트: `app/main.py`
- health 라우트: `app/api/v1/routes/health.py`
- verify 라우트: `app/api/v1/routes/idv.py`
- 모델 로더: `app/services/idv_model_registry.py`
- 본인확인 파이프라인: `app/services/idv_service.py`
- OCR 파서: `app/services/idv_ocr_parser.py`
- 품질 검사: `app/services/idv_quality_service.py`
- AdaFace export 스크립트: `scripts/export_adaface_to_onnx.py`
- AdaFace 양자화 스크립트: `scripts/quantize_adaface_onnx.py`
- 양자화 비교 스크립트: `scripts/compare_quantized_models.py`

## 가상환경 정책

운영 중 확인된 핵심 제약:

- `torch`와 `paddlepaddle-gpu`를 동일 `venv`에 섞을 경우 CUDA/NCCL 충돌 가능성이 높다
- 실제로 `torch import` 시 `libtorch_cuda.so: undefined symbol: ncclCommShrink` 충돌이 발생했다

권장 분리:

### 1. `venv`

운영용 가상환경이다.

목적:

- `uvicorn`으로 FastAPI 서버 실행
- `onnxruntime-gpu + paddleocr + paddlepaddle-gpu + insightface` 사용

포함:

- `requirements.txt`
- `paddlepaddle-gpu`

제외:

- `torch`

### 2. `venv-export`

변환 전용 가상환경이다.

목적:

- AdaFace checkpoint를 ONNX로 1회 변환

포함:

- `requirements-export.txt`
- `torch`
- `onnx`
- `onnxscript`

## 모델 파일 정책

AdaFace 운영 기본값:

- `./models/adaface/adaface_ir101_webface12m.onnx`

checkpoint 입력 예시:

- `./models/adaface/adaface_ir101_webface12m.ckpt`

SCRFD root:

- `./models/insightface`

모델 로더 동작:

- 확장자가 `.onnx`면 ONNX Runtime으로 적재
- 확장자가 `.ckpt`, `.pt`, `.pth`면 torch 경로로 적재
- 운영 기본값은 ONNX 사용

## 운영 서버 설치 절차

### 1. 운영용 `venv`

```bash
cd ~/AI-IDV
python3 -m venv venv
source venv/bin/activate
python -m pip install -U pip setuptools wheel
python -m pip install -r requirements.txt
python -m pip install paddlepaddle-gpu==3.2.0 -i https://www.paddlepaddle.org.cn/packages/stable/cu118/
```

### 2. export 전용 `venv-export`

```bash
cd ~/AI-IDV
python3 -m venv venv-export
source venv-export/bin/activate
python -m pip install -U pip setuptools wheel
python -m pip install -r requirements-export.txt
```

## AdaFace ONNX 변환 절차

checkpoint 다운로드 예시:

```bash
cd ~/AI-IDV
source venv-export/bin/activate
python -m pip install gdown
mkdir -p ./models/adaface
python -m gdown https://drive.google.com/uc?id=1dswnavflETcnAuplZj1IOKKP0eM8ITgT -O ./models/adaface/adaface_ir101_webface12m.ckpt
```

ONNX export:

```bash
cd ~/AI-IDV
source venv-export/bin/activate
python scripts/export_adaface_to_onnx.py \
  --checkpoint ./models/adaface/adaface_ir101_webface12m.ckpt \
  --output ./models/adaface/adaface_ir101_webface12m.onnx \
  --architecture ir_101
```

결과 확인:

```bash
ls -l ./models/adaface/adaface_ir101_webface12m.onnx
```

## AdaFace ONNX 양자화 절차

FP32 ONNX 모델을 FP16 또는 INT8로 양자화하여 추론 성능을 개선할 수 있다.

### FP16 양자화

모델 크기가 약 50% 감소하며, GPU 추론 속도가 10~20% 향상된다.
임베딩 정밀도 손실은 거의 없다 (cosine similarity > 0.99).

```bash
cd ~/AI-IDV
source venv-export/bin/activate
python scripts/quantize_adaface_onnx.py \
  --input ./models/adaface/adaface_ir101_webface12m.onnx \
  --output ./models/adaface/adaface_ir101_webface12m_fp16.onnx \
  --mode fp16 --verify
```

### INT8 동적 양자화

모델 크기가 약 75% 감소하며, CPU 추론 속도가 2~3배 향상된다.
임베딩 정밀도가 약간 떨어질 수 있어 threshold 재검증이 필요하다.

```bash
cd ~/AI-IDV
source venv-export/bin/activate
python scripts/quantize_adaface_onnx.py \
  --input ./models/adaface/adaface_ir101_webface12m.onnx \
  --output ./models/adaface/adaface_ir101_webface12m_int8.onnx \
  --mode int8 --verify
```

### 양자화 모델 품질 검증

FP32 대비 임베딩 cosine similarity와 추론 latency를 비교한다.

```bash
cd ~/AI-IDV
source venv-export/bin/activate
python scripts/compare_quantized_models.py \
  --model-dir ./models/adaface \
  --basename adaface_ir101_webface12m
```

기대 결과:

- FP16 vs FP32 cosine similarity > 0.99
- INT8 vs FP32 cosine similarity > 0.95

cosine similarity가 기대치 이하이면 해당 variant는 사용하지 않는다.

### 양자화 모델 적용

`.env`에서 `IDV_ADAFACE_QUANTIZATION`을 변경하고 서버를 재시작한다.

```bash
# .env 수정
IDV_ADAFACE_QUANTIZATION=fp16

# 서버 재시작
# health 확인
curl -s http://127.0.0.1:8010/idv/api/v1/health
```

`modelVersion` 값과 `adaface=true`를 확인한다.
기존 `IDV_FACE_REFERENCE_THRESHOLD`(0.35)과 `IDV_FACE_IDCARD_THRESHOLD`(0.30)가 양자화 후에도 적절한지 실제 verify 요청으로 재검증한다.

## `.env` 기준값

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

## 서버 실행 절차

```bash
cd ~/AI-IDV
source venv/bin/activate
export PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
python -m uvicorn app.main:app --host 0.0.0.0 --port 8010 --workers 1
```

운영 메모:

- `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True`를 주면 startup 시 Paddle 원격 호스트 확인을 생략한다
- `--workers 1`을 유지한다. 다중 worker는 GPU 모델을 중복 적재한다
- FastAPI 기본 문서 URL은 `/docs`, OpenAPI는 `/openapi.json`이다
- 원격 검증은 `8010` 포트 기준으로 수행했다
- `8000` 포트는 기존 프로세스 점유 이슈가 있어 검증 과정에서 `8010`을 사용했다

## health 확인 절차

```bash
curl -s http://127.0.0.1:8010/idv/api/v1/health
```

정상 기준:

- `status=ok`
- `ready=true`
- `scrfd=true`
- `adaface=true`
- `ppocr=true`
- `lastError=null`

## 테스트 현황

현재 저장소의 자동화 테스트 범위:

- 설정 alias 파싱
- AdaFace 입력 전처리
- OCR 파서
- 유사도 계산

아직 없는 범위:

- route 단위 테스트
- service 통합 테스트
- 샘플 이미지 기반 smoke test

## 구축 중 실제로 겪은 이슈

### 1. `python-multipart` 누락

증상:

- FastAPI startup 시 `Form data requires "python-multipart" to be installed`

조치:

- `requirements.txt`에 `python-multipart` 추가

### 2. PaddleOCR 3.x API 차이

증상:

- `ValueError('Unknown argument: use_gpu')`

조치:

- `PaddleOCR(device=...)` 우선 사용
- 실패 시 구버전 `use_gpu=...` fallback 유지

### 3. `.env` alias 불일치

증상:

- `CORS_ORIGINS`, `IDV_DET_SIZE`가 extra input으로 처리됨

조치:

- `config.py`에서 `AliasChoices`로 env alias 처리
- `extra="ignore"` 설정

### 4. `torch`와 `paddlepaddle-gpu` 충돌

증상:

- `libtorch_cuda.so: undefined symbol: ncclCommShrink`

조치:

- 운영 runtime에서 `torch` 제거
- `venv`와 `venv-export` 분리
- 운영 기본 입력을 `AdaFace ONNX`로 전환

### 5. AdaFace ONNX 미생성

증상:

- `AdaFace model not found: models/adaface/adaface_ir101_webface12m.onnx`

조치:

- `venv-export`에서 `ckpt -> onnx` 변환 수행

### 6. ONNX export 의존성 누락

증상:

- `ModuleNotFoundError: No module named 'onnxscript'`

조치:

- `requirements-export.txt`에 `onnxscript` 추가

### 7. 포트 충돌

증상:

- `address already in use`

조치:

- 기존 `8000` 점유 프로세스 종료
- 검증 시 `8010` 포트 사용

## 현재 운영 체크리스트

완료:

- GPU 서버 health 정상
- SCRFD 적재 확인
- AdaFace 적재 확인
- PaddleOCR 적재 확인
- `POST /idv/api/v1/verify` 샘플 요청 검증 완료
- `referenceImage` optional 경로 코드 반영
- 환경 분리 정책 정리 완료
- ONNX export 스크립트 준비 완료

미완료:

- Spring 연동 smoke test
- process manager 등록
- `qualityChecks.idCardDetected` 의미 정리 또는 코드 수정

## 다음 단계

1. Spring Boot 연동 smoke test 수행
2. 운영 포트 확정 후 process manager 등록
3. `qualityChecks.idCardDetected`와 문서 검출 정책 정리
