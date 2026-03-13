# local_qwen_ars_poc

- 로컬 Qwen3-TTS / Qwen3-ASR 기반 한국어 ARS(IVR) 예약 보조 PoC 테스트 문서 
- 핵심 목표는 `등록/미등록 분기 이후` STT/TTS + 결정적 상태머신 타당성 검증

## 1) 프로젝트 개요

- FastAPI 백엔드 + 최소 웹 UI
- 로컬 모델만 사용 (클라우드 TTS/STT/API 미사용)
- 규칙 기반 상태머신 (LLM 대화 관리 미사용)
- 출력 저장
  - TTS: `outputs/tts/`
  - 녹음: `outputs/recordings/`
  - 세션 전사/메타: `outputs/transcripts/`

## 2) Scope / Non-Scope

### Scope
- 등록/미등록 분기 이후 시나리오
- 이름/예아니오/증상 짧은 발화 STT
- yes/no 판별, 증상 키워드 추출(룰 기반)
- 상태 전이/재시도/로그/메트릭 기록

### Non-Scope
- 전화망/콜 인입 연동
- DB/EMR 조회
- SMS 실제 발송
- 실제 예약 저장

## 3) 요구 환경

- Python 3.10+
- Ubuntu/WSL 우선 (Windows도 가능)
- 로컬 GPU 권장

## 4) 빌드/설치 (실제 테스트 순서)

아래는 실제 검증한 순서

### 4-1. 가상환경

프로젝트 루트(`local_qwen_ars_poc`)에서 실행:

```bash
python -m venv venv
```

PowerShell:
```powershell
.\venv\Scripts\Activate.ps1
```

Git Bash:
```bash
source venv/Scripts/activate
```

### 4-2. 기본 의존성

```bash
python -m pip install --upgrade pip
pip install -r requirements.txt
```

### 4-3. Qwen 관련 의존성 + 패키지

```bash
pip install -r requirements-qwen.txt
pip install qwen-tts==0.1.1 --no-deps
pip install qwen-asr==0.0.6 --no-deps
```

### 4-4. `huggingface-hub` 충돌 시 (중요)

`huggingface-hub==1.x`가 깔리면 `transformers`가 실패

아래로 고정:

```bash
pip install "huggingface_hub>=0.36.0,<1.0"
```

버전 확인:

```bash
python -c "import transformers, huggingface_hub; print(transformers.__version__, huggingface_hub.__version__)"
```

### 4-5. Windows SoX (권장)

`sox` 경고 제거용:

```bash
choco install sox.portable
sox --version
```

### 4-6. Hugging Face CLI로 모델 다운로드 (models/qwen에서 실행)

버전/대용량 파일 처리 이슈 때문에 `git clone` 대신 `huggingface-cli download` 방식 사용:

```bash
# (명령어가 없으면)
pip install "huggingface_hub[cli]"

# (최초 1회) CLI 로그인
huggingface-cli login

# 프로젝트 루트에서
mkdir -p models/qwen
cd models/qwen

huggingface-cli download Qwen/Qwen3-TTS-12Hz-0.6B-Base --local-dir Qwen3-TTS-12Hz-0.6B-Base
huggingface-cli download Qwen/Qwen3-TTS-Tokenizer-12Hz --local-dir Qwen3-TTS-Tokenizer-12Hz
huggingface-cli download Qwen/Qwen3-ASR-0.6B --local-dir Qwen3-ASR-0.6B

cd ../..
```

## 5) 모델 배치 확인

모델은 로컬 폴더에 두고 `.env`에서 경로 지정

예시(현재 테스트 구조):

```text
models/qwen/Qwen3-ASR-0.6B
models/qwen/Qwen3-TTS-12Hz-0.6B-Base
models/qwen/Qwen3-TTS-Tokenizer-12Hz
```

## 6) .env 설정 (실제 동작 기준)

```bash
cp .env.example .env
```

핵심:

```env
# 예시
QWEN_TTS_MODEL_PATH=C:/Users/SSAFY/Desktop/tts/local_qwen_ars_poc/models/qwen/Qwen3-TTS-12Hz-0.6B-Base
QWEN_ASR_MODEL_PATH=C:/Users/SSAFY/Desktop/tts/local_qwen_ars_poc/models/qwen/Qwen3-ASR-0.6B
```

### Base TTS 사용 시 필수

`Qwen3-TTS-...-Base`는 참조 음성 없으면 TTS 생성이 실패

```env
TTS_REF_AUDIO_PATH=C:/path/to/ref_voice.wav
```

`TTS_REF_AUDIO_PATH`가 비어 있으면 응답 metadata에 `tts_error`가 들어오고 음성 파일이 생성되지 않음

## 7) 서버 실행

반드시 프로젝트 루트에서 실행:

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

확인:
- UI: `http://localhost:8000/`
- Health: `http://localhost:8000/health`

## 8) UI 자동 테스트 (현재 기본)

UI는 `자동 시작` 1회 클릭 후 자동 진행됩니다.

1. 브랜치 선택 (`registered` / `unregistered`)
2. `자동 시작` 클릭
3. 마이크 권한 허용
4. 음성 입력 턴마다 **3초 고정 녹음** 후 자동 전송
5. 시스템 안내 턴은 자동으로 다음 전이

중지 시 `중지` 버튼 클릭

## 9) 코드별 역할

- `app/main.py`
  - FastAPI 앱 엔트리포인트
  - 설정 로드, 모델/서비스 초기화, 라우터 등록
- `app/config.py`
  - `.env` 기반 설정 로드/검증
  - 모델 경로 fail-fast 체크, 출력 폴더 생성
- `app/state_machine.py`
  - 결정적 상태머신 전이 로직
  - 이름/예아니오/증상 처리와 재시도 제한
- `app/schemas.py`
  - API 요청/응답 스키마(Pydantic)
- `app/logging_utils.py`
  - JSON 구조 로그 포맷터/로거 설정

- `app/routes/health.py`
  - `/health` 헬스 체크
- `app/routes/scenario.py`
  - `/scenario/start`, `/scenario/next`, `/scenario/{session_id}`
- `app/routes/stt.py`
  - `/stt/transcribe` 단일 STT 테스트
- `app/routes/tts.py`
  - `/tts/synthesize` 단일 TTS 테스트

- `app/services/qwen_asr_service.py`
  - Qwen3-ASR 로컬 모델 로드/추론
  - 오디오 전처리 호출, yes/no/증상 후처리
- `app/services/qwen_tts_service.py`
  - Qwen3-TTS 로컬 모델 로드/합성
  - Base/CustomVoice/VoiceDesign 분기 처리
- `app/services/audio_utils.py`
  - wav 로드, mono/resample/normalize/silence trim 유틸
- `app/services/scenario_service.py`
  - 세션 저장(in-memory), 턴 진행 orchestration
  - STT → 상태전이 → TTS 생성 → transcript 저장

- `app/data/prompt_templates.py`
  - 한국어 안내 멘트 템플릿 중앙 관리
- `app/data/symptom_dictionary.py`
  - 증상 키워드/정규화 라벨/진료과 매핑
- `app/data/mock_data.py`
  - 데모용 목 데이터

- `static/index.html`
  - 자동 ARS 데모 UI
  - 3초 고정 녹음 + 자동 next 전송
- `scripts/smoke_test_asr.py`
  - ASR 단독 스모크 테스트
- `scripts/smoke_test_tts.py`
  - TTS 단독 스모크 테스트

- `requirements.txt`
  - 서버 공통 의존성
- `requirements-qwen.txt`
  - Qwen 관련 추가 의존성
- `.env.example`
  - 환경 변수 샘플 템플릿


## 10) 참고 
[Repo](https://github.com/QwenLM/Qwen3-ASR) |
[모델 1](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-Base) |
[모델 2](https://huggingface.co/collections/Qwen/qwen3-asr) |
[다양한 모델 결과 추출 가능한 플랫폼](https://pinokio.co/download.html)
