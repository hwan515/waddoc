# GPU Server Runbook

## 목적

이 문서는 `src/AI-IDV` 기준 GPU 본인인증 서버의 현재 구현 상태, 모델 구성, 서버 기동 절차, 운영 시 주의점, 그리고 실제 구축 과정에서 확인한 이슈를 정리한다.

기준 시점:

- 문서 작성일: `2026-03-20`
- 원격 검증 환경: `j-j14a603@jupyter07`

## 현재 상태

구현 완료 범위:

- FastAPI 앱 구성 완료
- `GET /idv/api/v1/health` 구현 완료
- `POST /idv/api/v1/verify` 구현 완료
- `SCRFD + AdaFace + PP-OCRv5(korean)` 모델 로더 구현 완료
- 설정 파일(`.env`) 기반 runtime 구성 완료
- AdaFace `ckpt -> onnx` export 스크립트 추가 완료

원격 서버 기준 확인 완료:

- `2026-03-20` 기준 `health` 응답 정상
- 응답:

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

아직 별도 확인이 남은 범위:

- 실제 `POST /idv/api/v1/verify` 샘플 이미지 smoke test
- Spring Boot 서버와의 실제 연동 smoke test

## 아키텍처 요약

역할 분리:

- 차량 FE:
  - 얼굴 사진 촬영
  - 신분증 사진 촬영
- Spring Boot:
  - 세션/미션 제어
  - 기준 환자 이미지 조회
  - GPU 서버 호출
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
- `PP-OCRv5(korean)`
  - 이름, 주민번호, 주소 OCR

## 핵심 파일

- 런타임 설정: [config.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/core/config.py)
- 앱 엔트리포인트: [main.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/main.py)
- health 라우트: [health.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/api/v1/routes/health.py)
- verify 라우트: [idv.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/api/v1/routes/idv.py)
- 모델 로더: [idv_model_registry.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/services/idv_model_registry.py)
- 본인확인 파이프라인: [idv_service.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/services/idv_service.py)
- OCR 파서: [idv_ocr_parser.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/services/idv_ocr_parser.py)
- 품질 검사: [idv_quality_service.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/app/services/idv_quality_service.py)
- AdaFace export 스크립트: [export_adaface_to_onnx.py](c:/Users/SSAFY/Desktop/second_PJT/S14P21A603/src/AI-IDV/scripts/export_adaface_to_onnx.py)

## 가상환경 정책

운영 중 확인된 핵심 제약:

- `torch`와 `paddlepaddle-gpu`를 동일 `venv`에 섞을 경우 CUDA/NCCL 충돌 가능성이 높다.
- 실제로 `torch import` 시 `libtorch_cuda.so: undefined symbol: ncclCommShrink` 충돌이 발생했다.

따라서 가상환경은 아래처럼 분리한다.

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

AdaFace 공식 pretrained 모델은 `ckpt`로 내려받고, 운영 서버에서는 `onnx`만 사용한다.

기본 경로:

- checkpoint 입력:
  - `./models/adaface/adaface_ir101_webface12m.ckpt`
- 운영 ONNX:
  - `./models/adaface/adaface_ir101_webface12m.onnx`

SCRFD 모델 경로:

- `./models/insightface`

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

## `.env` 기준값

운영 서버에서는 아래 값이 중요하다.

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

- `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True`를 주면 startup 시 Paddle 원격 호스트 확인을 생략한다.
- `--workers 1`을 유지한다. 다중 worker는 GPU 모델을 중복 적재한다.
- 실제 운영 포트는 배포 환경에 맞게 조정 가능하다.
- `2026-03-20` 기준 원격 검증은 `8010` 포트에서 수행했다.
- `8000` 포트는 기존 프로세스 점유 이슈가 있어 검증 과정에서 `8010`을 사용했다.

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

## 현재 기준 체크리스트

완료:

- GPU 서버 health 정상
- SCRFD 정상 로딩
- AdaFace 정상 로딩
- PP-OCR 정상 로딩
- 환경 분리 정책 정리 완료
- ONNX export 스크립트 준비 완료

미완료:

- `POST /idv/api/v1/verify` 샘플 요청 검증
- Spring 연동 smoke test
- systemd 또는 supervisor 운영 스크립트 정리

## 다음 단계

1. 샘플 이미지 3종으로 `POST /idv/api/v1/verify` smoke test 수행
2. Spring Boot `mission identity-check`가 GPU 서버 `8010` 또는 최종 운영 포트를 바라보도록 설정
3. 운영 포트 확정 후 process manager 등록
4. 실패 케이스 로그와 latency 측정
