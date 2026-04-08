# 빌드 및 배포 가이드

## 1. 문서 범위

- 메인 배포 스택: `infra/` + `src/BE` + `src/FE` + `src/FE-phone`
- 로보틱스/시뮬레이션 스택: `src/ros2_docker` + `src/unity`
- 외부 본인확인 AI 서비스 구현체: `src/AI-IDV`
- 별도 음성 트리아지 프로토타입: `src/AI`
- 별도 로컬 ARS PoC: `src/AI/Qwen/local_qwen_ars_poc`

실제 메인 운영 배포는 `infra/docker-compose.prod.yml`, `infra/docker-compose.monitoring.prod.yml`, `infra/Jenkinsfile`을 기준으로 구성되어 있다.

## 2. 시스템 환경

### 2.1 런타임 및 도구

| 영역 | 런타임/도구 | 위치 |
| --- | --- | --- |
| 메인 백엔드 | Spring Boot `3.3.7`, Java `17` | `src/BE/build.gradle.kts` |
| 메인 웹 프론트엔드 | React `19`, Vite `7`, Node `22` 빌드 이미지, Nginx 런타임 | `src/FE/package.json`, `src/FE/Dockerfile` |
| 휴대폰 프론트엔드 | React `19`, TypeScript `5.9`, Vite `7`, Node `22` 빌드 이미지, Nginx 런타임 | `src/FE-phone/package.json`, `src/FE-phone/Dockerfile` |
| 데이터베이스 | PostgreSQL `16-alpine` | `infra/docker-compose.yml`, `infra/docker-compose.prod.yml` |
| 캐시/락/팬아웃 | Redis `7-alpine` | `infra/docker-compose*.yml`, 백엔드 Redis 설정 |
| 이벤트 스트리밍 | Kafka + Zookeeper `7.6.0` | `infra/docker-compose*.yml`, 백엔드 Kafka 설정 |
| 실시간 영상 | LiveKit Server | `infra/docker-compose*.yml`, 백엔드 LiveKit 설정 |
| TURN 릴레이 | coturn | `infra/docker-compose.prod.yml` |
| MQTT 브로커 | Eclipse Mosquitto `2` | `infra/docker-compose.prod.yml`, `infra/mosquitto/mosquitto.conf` |
| 모니터링 | Prometheus `2.55.0`, Grafana `11.2.2`, cAdvisor, Postgres/Redis/Kafka exporter | `infra/docker-compose.monitoring.prod.yml` |
| ROS 스택 | ROS 2 Humble, CycloneDDS, colcon, Gazebo 관련 패키지, PyTorch, Ultralytics | `src/ros2_docker/Dockerfile`, `src/ros2_docker/README.md` |
| Unity | Unity Editor `6000.3.10f1` | `src/unity/ProjectSettings/ProjectVersion.txt` |
| Unity IDE 지원 | Rider, Visual Studio, VS Code 패키지 포함 | `src/unity/Packages/manifest.json` |
| 음성 트리아지 메인 서버 | Spring Boot `3.5.11`, Java `17` | `src/AI/main-server/build.gradle.kts` |
| 음성 트리아지 AI 서버 | FastAPI, Uvicorn, Faster-Whisper, CUDA 이미지 | `src/AI/ai-server/requirements.txt`, `src/AI/ai-server/Dockerfile` |
| AI-IDV | FastAPI, Uvicorn, ONNX Runtime GPU, InsightFace, PaddleOCR | `src/AI-IDV/requirements.txt` |
| 로컬 ARS PoC | FastAPI, Uvicorn, PyTorch, python-dotenv | `src/AI/Qwen/local_qwen_ars_poc/requirements.txt` |

### 2.2 운영체제 기대사항

- 메인 스택은 Docker Compose 기반의 리눅스 컨테이너 환경을 전제로 한다.
- ROS 스택은 `src/ros2_docker/README.md` 기준으로 Ubuntu 중심 워크플로우가 문서화되어 있다.
- ROS 베이스 이미지는 `osrf/ros:humble-desktop`이며, Ubuntu 22.04 계열이다.
- Unity Editor로 직접 프로젝트를 여는 방식이다.

## 3. 기술 스택

### 3.1 메인 배포 애플리케이션

- 백엔드: Spring Boot, Spring Web, Spring Security, Spring Data JPA, Spring Data Redis, Spring WebFlux, Spring Kafka, Spring Integration MQTT, Spring Actuator, Flyway
- 프론트엔드: React, Vite, React Router, Axios, Zustand, LiveKit React 컴포넌트, `@microsoft/fetch-event-source`
- 휴대폰 UI: React, TypeScript, Vite, Axios, Zustand
- 리버스 프록시: Nginx
- 데이터베이스: PostgreSQL
- 캐시/분산락/팬아웃: Redis
- 메시징:
  - Kafka 토픽 기반 비동기 처리
  - MQTT 기반 차량 명령/텔레메트리
  - Redis pub/sub 기반 다중 인스턴스 알림 팬아웃
  - SSE 기반 의사 알림/로봇 상태 스트림
- 화상 통신: LiveKit WebRTC, coturn
- 모니터링: Prometheus, Grafana, cAdvisor, Postgres/Redis/Kafka exporter
- CI/CD: Jenkins, GitLab checkout, DockerHub push

### 3.2 로보틱스 및 시뮬레이션

- ROS 2 Humble
- Unity `com.unity.robotics.ros-tcp-connector`
- ROS TCP Endpoint 패키지 포함
- MediaMTX 기반 스트림 릴레이
- 커스텀 ROS 노드:
  - `lane_follow_pkg/vision_detect`
  - `my_ros2_basics/mqtt_bridge`
  - `my_ros2_basics/camera_streamer`
  - `my_ros2_basics/biosignal_publisher`

### 3.3 저장소에 포함된 추가 하위 프로젝트

- `src/AI`: 별도 음성 예약/트리아지 스택
- `src/AI-IDV`: 별도 GPU 본인확인 API
- `src/AI/Qwen/local_qwen_ars_poc`: 메인 배포와 분리된 로컬 ASR/TTS PoC

## 4. 빌드 및 실행 가이드

### 4.1 저장소 클론

```bash
git clone <repository-url>
cd S14P21A603
```

### 4.2 전체 로컬 데모 권장 실행 순서

웹, 백엔드, 메시징, ROS, Unity, 본인확인까지 모두 포함한 데모를 기준으로 하면 아래 순서를 권장한다.

1. `infra/`에서 메인 로컬 스택 기동
2. `src/AI-IDV` 기동 또는 본인확인 우회 플래그 활성화
3. `src/ros2_docker` 기동
4. Unity 프로젝트 오픈 및 ROS TCP Endpoint 연결

### 4.3 `infra/docker-compose.yml` 기반 메인 로컬 스택

```bash
cd infra
cp .env.example .env
```

로컬 데모에서 우선 확인할 값:

- `APP_DEMO_MODE_ENABLED=true`
- `APP_SEED_ENABLED=true`
- `APP_SEED_DEFAULT_PASSWORD=<반드시 명시>`
- `VITE_ROBOT_TERMINAL_ID=<ROBOT_TERMINAL_REGISTRY 안의 엔트리와 일치>`
- `VITE_ROBOT_TERMINAL_KEY=<ROBOT_TERMINAL_REGISTRY 안의 엔트리와 일치>`
- `DEV_GPU_SERVER_HOST=<또는 AI_IDV_URL 직접 지정>`

중요:

- `infra/docker-compose.yml`은 `APP_SEED_ENABLED`의 기본값을 `true`로 두고 있다.
- `APP_SEED_DEFAULT_PASSWORD`는 기본값이 없다.
- 따라서 이 값을 비워 두면 시더가 기동 시 실패한다.

실행:

```bash
docker compose up --build
```

로컬 접근 경로:

- 메인 웹 진입점: `http://localhost`
- 휴대폰 시뮬레이터: `http://localhost/phone`
- Spring Swagger: `http://localhost/swagger/spring`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Kafka 외부 리스너: `localhost:9092`
- LiveKit: `7880`, `7881`, `7882/udp`

이 로컬 Compose가 띄우는 서비스:

- `nginx`
- `frontend`
- `frontend-phone`
- `core-app`
- `postgres`
- `redis`
- `zookeeper`
- `kafka`
- `livekit`

### 4.4 백엔드 단독 로컬 워크플로우

백엔드 전용 Compose는 `src/BE/docker-compose.yml`에 따로 있다.

```bash
cd src/BE
docker compose up -d
./scripts/run-local.sh
```

비고:

- `src/BE/scripts/run-local.sh`는 내부적으로 `./gradlew bootRun`을 실행한다.
- `src/BE/docker-compose.yml`에는 `APP_SEED_DEFAULT_PASSWORD` 기본값 `Passw0rd!`가 들어 있다.
- 이 구성에서는 LiveKit과 AI-IDV가 기본적으로 `host.docker.internal` 쪽에 떠 있다고 가정한다.

### 4.5 프론트엔드 단독 로컬 실행

웹 프론트엔드:

```bash
cd src/FE
npm install
npm run dev
```

휴대폰 프론트엔드:

```bash
cd src/FE-phone
npm install
npm run dev
```

비고:

- 웹 프론트는 `/api/v1` 기준으로 Axios 요청을 보낸다.
- 휴대폰 프론트는 Vite 개발 서버에서 `VITE_API_PROXY_TARGET`을 사용한다.
- `src/FE/src/pages/Home/MainEntrance.jsx` 기준으로, 개발 모드의 휴대폰 시뮬레이터 URL은 `http://localhost:5174`이다.

### 4.6 운영형 Compose 배포

```bash
cd infra
cp .env.example .env
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.prod.yml up -d
```

운영형 Compose가 띄우는 서비스:

- `nginx`
- `frontend`
- `frontend-phone`
- `core-app`
- `postgres`
- `redis`
- `zookeeper`
- `kafka`
- `livekit`
- `coturn`
- `mosquitto`
- `prometheus`
- `grafana`
- `cadvisor`
- `postgres-exporter`
- `redis-exporter`
- `kafka-exporter`

운영 공개 URL/경로:

- 커밋된 `infra/nginx/prod.conf` 기준 공개 호스트는 하드코딩되어 있다.
  - `https://www.waddoc.site/`
  - `http://waddoc.site` 및 `https://waddoc.site`는 리다이렉트
- 공개 경로:
  - 휴대폰 UI: `/phone`
  - Swagger: `/swagger/spring`
  - API: `/api/...`
  - LiveKit signaling: `/livekit/...`
  - MQTT WebSocket: `/mqtt`
  - Grafana: `/grafana/`

중요:

- `SERVER_DOMAIN`은 LiveKit/Grafana URL 생성 등에는 사용된다.
- 하지만 `infra/nginx/prod.conf`를 자동으로 템플릿 치환하지는 않는다.
- 다른 도메인으로 운영하려면 `.env`뿐 아니라 `infra/nginx/prod.conf`도 함께 수정해야 한다.

### 4.7 Jenkins 기반 CI/CD

`infra/Jenkinsfile`에서 확인된 파이프라인 동작:

- CI/CD 도구: Jenkins
- 소스 checkout: GitLab (`gitlab-deploy-token`)
- 이미지 레지스트리: DockerHub (`dockerhub-credentials`)
- 배포 서버 프로젝트 경로: `/home/ubuntu/S14P21A603`
- 배포 서버 env 파일 경로: `/home/ubuntu/.waddoc/prod.env`
- 배포 시 사용하는 Compose:
  - `docker-compose.prod.yml`
  - `docker-compose.monitoring.prod.yml`
- 품질 검사:
  - 백엔드: `./gradlew test --no-daemon -PskipIntegrationTests=true`
  - 웹 프론트: `node:22-alpine` 안에서 lint/build
  - 휴대폰 프론트: `node:22-alpine` 안에서 lint/build
- 배포 동작:
  - 변경된 백엔드/웹/폰 이미지를 빌드하여 DockerHub에 push
  - 변경 서비스 pull 및 재기동
  - `core-app`을 3 replica로 스케일
  - 필요 시 `nginx` 재기동

저장소에서 `.gitlab-ci.yml`이나 GitHub Actions 워크플로우는 발견되지 않았다.

### 4.8 ROS 2 스택 실행

```bash
cd src/ros2_docker
docker compose up -d
docker exec -it ros2_dev_env bash
```

컨테이너 내부 문서화된 빌드 경로:

```bash
source /opt/ros/humble/setup.bash
colcon build --symlink-install
source install/setup.bash
ros2 run lane_follow_pkg vision_detect
```

중요 포인트:

- 실제 엔트리포인트는 `custom_entrypoint.sh`이며, 아래 프로세스를 자동 기동한다.
  - ROS TCP endpoint
  - `camera_streamer`
  - `biosignal_publisher`
  - `mqtt_bridge`
- Compose는 `network_mode: host`를 사용한다.
- GPU reservation이 설정되어 있다.
- GUI를 쓰려면 X11 접근이 필요하며, README에는 `xhost +local:docker`가 명시되어 있다.
- MQTT는 `www.waddoc.site:443` + `/mqtt` 경로로 연결되도록 설정돼 있다.
- MediaMTX는 같은 Compose에서 host networking으로 떠 있다.

### 4.9 Unity 프로젝트 실행

`src/unity`를 Unity Editor `6000.3.10f1`로 연다.

확인된 Unity 연동 요소:

- ROS Connector 패키지: `com.unity.robotics.ros-tcp-connector`
- 부트스트랩 스크립트: `Assets/Scripts/RosTcpEndpointBootstrap.cs`
- ROS endpoint 입력 경로:
  - 커맨드라인 `--ros-ip`, `--ros-port`
  - 환경변수 `ROS_TCP_ENDPOINT_IP`, `ROS_TCP_ENDPOINT_PORT`, `ROS_IP`
  - PlayerPrefs fallback

저장소 내 Scene:

- `Assets/Scenes/final.unity`
- `Assets/Scenes/semi_final.unity`
- `Assets/Scenes/base.unity`

- `final.unity`가 대표 데모 Scene

### 4.10 AI-IDV 서버 실행

메인 백엔드는 외부 AI-IDV endpoint를 `AI_IDV_URL`로 호출한다.

저장소에 포함된 `src/AI-IDV` 구현체를 직접 띄우려면:

```bash
cd src/AI-IDV
cp .env.example .env
python3 -m venv venv
source venv/bin/activate
pip install -U pip setuptools wheel
pip install -r requirements.txt
pip install paddlepaddle-gpu==3.2.0 -i https://www.paddlepaddle.org.cn/packages/stable/cu118/
export PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
python -m uvicorn app.main:app --host 0.0.0.0 --port 8010 --workers 1
```

그 다음 백엔드에서 이 URL을 바라보게 맞춘다.

- 백엔드 단독 Compose: `AI_IDV_URL=http://host.docker.internal:8010/idv/api/v1/verify`
- infra 기준: `AI_IDV_URL=http://<gpu-host>:8010/idv/api/v1/verify`
- 또는 `DEV_GPU_SERVER_HOST`, `PROD_GPU_SERVER_HOST` 사용

AI-IDV가 준비되지 않았다면 우회 플래그를 사용할 수 있다.

- `CONSULTATION_IDENTITY_CHECK_BYPASS_ENABLED=true`

### 4.11 `src/AI` 음성 트리아지 프로토타입

이 스택은 메인 운영 배포와는 분리되어 있다.

```bash
cd src/AI
cp .env.example .env
# UPSTAGE_API_KEY 설정
docker compose up --build
```

접근 주소:

- 프론트엔드: `http://localhost:5173`
- 메인 서버: `http://localhost:8080`
- AI 서버: `http://localhost:8000`

### 4.12 로컬 Qwen ARS PoC

이 프로젝트도 메인 서비스와는 별도 실험용이다.

```bash
cd src/AI/Qwen/local_qwen_ars_poc
cp .env.example .env
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 5. 환경변수

### 5.1 메인 인프라 및 배포 변수 (`infra/.env.example`)

| 변수 | 용도 |
| --- | --- |
| `DB_PASSWORD`, `POSTGRES_DB`, `POSTGRES_USER` | PostgreSQL 접속 정보 |
| `REDIS_PASSWORD` | 운영 Redis 비밀번호 |
| `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `LIVEKIT_NODE_IP`, `LIVEKIT_WEBHOOK_URL` | LiveKit API 키/시크릿, 광고할 IP, 웹훅 대상 |
| `TURN_SECRET` | coturn/LiveKit이 공유하는 TURN 자격값 |
| `DEV_GPU_SERVER_HOST`, `PROD_GPU_SERVER_HOST`, `AI_IDV_URL`, `AI_IDV_TIMEOUT_MS` | AI-IDV endpoint 및 timeout |
| `DEV_ROBOT_API_PROXY_TARGET` | 주석상 로컬 reverse proxy 대상. 다만 현재 inspected compose/nginx에서는 직접 사용 흔적을 확인하지 못함 |
| `ROBOT_COMMAND_BASE_URL` | 백엔드의 로봇 명령 base URL |
| `SERVER_DOMAIN`, `CERTS_DIR` | 공개 도메인, SSL 인증서 마운트 경로 |
| `JWT_SECRET`, `MONITORING_COOKIE_SECRET`, `MISSION_TERMINAL_TOKEN_EXPIRY` | 인증/세션/모니터링 토큰 설정 |
| `ROBOT_TERMINAL_REGISTRY` | 백엔드가 신뢰하는 차량 단말 레지스트리 |
| `VITE_ROBOT_TERMINAL_ID`, `VITE_ROBOT_TERMINAL_KEY`, `VITE_ENABLE_MONITORING_TAB` | 웹 프론트 컨테이너 기동 시 주입되는 runtime 설정 |
| `APP_DEMO_MODE_ENABLED` | 운영자 수동 dispatch 중심 데모 모드 여부 |
| `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_NUMBER` | SMS 발송 설정 |
| `TELEMETRY_API_KEY` | `/api/v1/missions/{missionId}/telemetry` 호출용 API 키 |
| `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD` | Grafana 관리자 계정 |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_EXTERNAL_HOST`, `KAFKA_PORT`, `ZOOKEEPER_CLIENT_PORT`, `KAFKA_BROKER_ID`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`, `KAFKA_AUTO_CREATE_TOPICS_ENABLE` | Kafka/Zookeeper 구성 |
| `CONSULTATION_IDENTITY_CHECK_BYPASS_ENABLED` | 본인확인 우회 여부 |
| `APP_SEED_ENABLED`, `APP_SEED_DEFAULT_PASSWORD` | 시드 데이터 활성화 및 시드 계정 비밀번호 |

### 5.2 백엔드 변수 (`src/BE/.env.example` + Spring 설정)

| 변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Spring profile (`local`, `prod`) |
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | PostgreSQL 연결 정보 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 연결 정보 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap 주소 |
| `JWT_SECRET` | JWT 서명 키 |
| `AI_IDV_URL`, `AI_IDV_TRANSFER_MODE`, `AI_IDV_TIMEOUT_MS` | 외부 본인확인 호출 설정 |
| `AI_TRIAGE_URL` | `.env.example`에는 있으나, 메인 백엔드 실행 경로에서 실제 사용은 확인하지 못함 |
| `FILE_STORAGE_ROOT` | 업로드/기준 이미지 저장 루트 |
| `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_NUMBER`, `SMS_CONTACT_NUMBER` | SMS 관련 설정 |
| `LIVEKIT_HOST`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `LIVEKIT_URL` | LiveKit 연동 설정 |
| `ROBOT_TERMINAL_REGISTRY` | 차량 단말 자격 정보 및 vehicle 매핑 |
| `SERVER_DOMAIN` | `LIVEKIT_URL` 미지정 시 fallback 도메인 |
| `MQTT_BROKER_URL`, `MQTT_CLIENT_ID` | MQTT 연결 설정 |
| `MONITORING_COOKIE_SECRET` | Grafana embedding용 쿠키 서명 키 |
| `TELEMETRY_API_KEY` | 미션 텔레메트리 API 키 |

### 5.3 웹 프론트 변수 (`src/FE/.env.example` + runtime injection)

| 변수 | 용도 |
| --- | --- |
| `VITE_ROBOT_TERMINAL_ID` | 로봇 단말 ID |
| `VITE_ROBOT_TERMINAL_KEY` | 로봇 단말 공유 키 |
| `VITE_DEMO_MODE_ENABLED` | 로컬 Vite 실행용 데모 플래그 |
| `VITE_ENABLE_MONITORING_TAB` | 컨테이너 기동 시 runtime-config.js에 주입되는 모니터링 탭 노출 플래그 |

### 5.4 휴대폰 프론트 변수 (`src/FE-phone/.env.example`)

| 변수 | 용도 |
| --- | --- |
| `VITE_API_BASE_URL` | API base URL |
| `VITE_USE_MOCK` | mock 모드 사용 여부 |
| `VITE_CALLER_PHONE` | 데모/목업 기본 발신 번호 |
| `VITE_API_PROXY_TARGET` | Vite dev proxy 대상 |

### 5.5 음성 트리아지 변수 (`src/AI/...`)

| 그룹 | 변수 |
| --- | --- |
| Upstage API | `UPSTAGE_API_KEY`, `UPSTAGE_BASE_URL`, `UPSTAGE_MODEL` |
| Whisper/STT | `WHISPER_MODEL`, `WHISPER_DEVICE`, `WHISPER_COMPUTE_TYPE`, `WHISPER_LANGUAGE`, `WHISPER_INITIAL_PROMPT`, `WHISPER_PARTIAL_INTERVAL_MS`, `WHISPER_VAD_MIN_SILENCE_MS`, `WHISPER_PARTIAL_BEAM_SIZE`, `WHISPER_PARTIAL_BEST_OF`, `WHISPER_FINAL_BEAM_SIZE`, `WHISPER_FINAL_BEST_OF` |
| AI 서버 CORS | `CORS_ORIGINS_RAW` |
| 메인 서버 URL | `FRONTEND_BASE_URL`, `AI_SERVER_BASE_URL`, `STT_WS_BASE_URL` |
| 프론트 URL | `VITE_API_BASE_URL` |

### 5.6 AI-IDV 변수 (`src/AI-IDV/.env.example`)

| 변수 | 용도 |
| --- | --- |
| `CORS_ORIGINS` | 허용 브라우저 Origin |
| `IDV_DEVICE` | 추론 디바이스, 보통 `cuda` |
| `IDV_DET_SIZE` | 얼굴 검출 입력 크기 |
| `IDV_SCRFD_MODEL_NAME`, `IDV_SCRFD_ROOT` | SCRFD 설정 |
| `IDV_ADAFACE_MODEL_PATH`, `IDV_ADAFACE_ARCHITECTURE` | AdaFace 설정 |
| `IDV_OCR_LANG` | OCR 언어 |
| `IDV_FACE_REFERENCE_THRESHOLD`, `IDV_FACE_IDCARD_THRESHOLD` | 얼굴 비교 임계값 |
| `IDV_OCR_MIN_CONFIDENCE` | OCR confidence 임계값 |
| `IDV_MAX_CONCURRENCY`, `IDV_TIMEOUT_MS`, `IDV_MAX_IMAGE_MB` | 런타임 제한값 |
| `IDV_FAIL_FAST_ON_STARTUP` | 모델 로딩 실패 시 startup 처리 방식 |
| `IDV_MODEL_VERSION` | 응답에 노출되는 모델 버전 |

### 5.7 Qwen ARS PoC 변수 (`src/AI/Qwen/local_qwen_ars_poc/.env.example`)

| 그룹 | 변수 |
| --- | --- |
| 서버 | `HOST`, `PORT`, `DEVICE` |
| 모델 경로 | `QWEN_TTS_MODEL_PATH`, `QWEN_ASR_MODEL_PATH` |
| 출력/언어 | `OUTPUT_DIR`, `DEFAULT_LANG` |
| 자동 재생 | `AUTO_TTS_ON_START`, `AUTO_TTS_ON_NEXT` |
| ASR 전처리 | `ASR_TARGET_SAMPLE_RATE`, `ASR_TRIM_SILENCE`, `ASR_SILENCE_DB`, `ASR_SILENCE_MARGIN_MS`, `ASR_LOW_CONFIDENCE_THRESHOLD`, `ASR_DTYPE` |
| TTS 기본값 | `TTS_DEFAULT_SPEAKER`, `TTS_DEFAULT_INSTRUCT`, `TTS_REF_AUDIO_PATH`, `TTS_DTYPE` |
| 재시도 정책 | `MAX_NAME_RETRIES`, `MAX_YES_NO_RETRIES` |

## 6. 배포 메모 및 주의사항

### 6.1 네트워킹

- 운영에서는 Nginx가 TLS 종료 후 내부 서비스로 프록시한다.
- 핵심 경로:
  - `/api/` -> Spring API
  - `/phone` -> 휴대폰 프론트
  - `/livekit/` -> LiveKit signaling
  - `/mqtt` -> Mosquitto WebSocket
  - `/grafana/` -> Grafana
  - `/api/v1/robots/stream`, `/api/v1/doctors/me/notifications/stream` -> SSE

### 6.2 데이터 영속성

- PostgreSQL 데이터: `pg_data`
- Redis 데이터: `redis_data`
- 업로드/기준 이미지:
  - 로컬 infra 실행: `infra/local-storage/uploads`
  - 운영형 Compose: `uploads` volume
- Mosquitto: `mosquitto_data`, `mosquitto_log`
- Prometheus/Grafana도 운영형 모니터링 Compose에서 전용 volume 사용

### 6.3 확인된 통합상 주의점

- `src/FE/nginx.conf`는 `/unity_cam/`을 `http://100.85.200.7:8889/unity_cam/`으로 프록시한다.
  - 코드 근거 기반 추정(Assumption based on code): 메인 Compose 밖에 별도 스트리밍 브리지 또는 게이트웨이가 있어야 정상 동작한다.
- `src/ros2_docker/workspace/src/my_ros2_basics/my_ros2_basics/biosignal_publisher.py`에는 `http://3.34.123.145/...` 하드코딩 URL이 존재한다.
  - 실제 배포 전 정리 대상이다.
- `src/FE/README.md`에는 `seed_admin`, `seed_doc_im_01` 같은 예전 계정 예시가 남아 있다.
  - 현재 시더와 `infra/sql/prod_dummy_seed.sql` 기준으로는 `seed_prod_*` 계정이 실제 근거이다.
- Jenkins 파이프라인에는 Unity 자동 빌드 단계가 없다.

