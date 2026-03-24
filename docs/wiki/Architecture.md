# 도서·산간 방문형 비대면 진료 서비스 — 시스템 아키텍처

> 2026-03 기준 현재 MVP 신규 예약 흐름은 STT를 사용하지 않고 DTMF 진료과 선택을 사용한다.
> 아래 STT/WebSocket 관련 설계는 향후 확장 검토용 legacy 초안으로 본다.

## 1. 아키텍처 원칙

| 원칙 | 설명 |
|------|------|
| 제어면 / 미디어면 / 추론면 분리 | Spring Boot = 상태·권한·오케스트레이션, LiveKit = WebRTC 미디어, AI = 추론 전용 |
| AI 내부망 격리 | AI 서버는 외부 직접 노출 금지. React → AI 직접 호출 금지 |
| AI 분리 배포 | DEV/PROD 공통으로 AI 추론은 별도 GPU 서버에서 실행. 메인 서버 Compose에 AI 컨테이너를 포함하지 않음 |
| AI 프로토콜 분리 | IDV/OCR = REST multipart, 실시간 STT/문진 = WebSocket |
| 파일 전달 표준화 | IDV/OCR 이미지 전달은 DEV/PROD 공통 multipart 전송. 공유 디렉터리 방식 미사용 |
| 환자 무계정 정책 | 환자는 로그인 계정 없음. 본인확인 완료 후 room token만 발급 |
| TURN 전제 WebRTC | NAT/방화벽 환경 대비 TURN 릴레이 필수 구성. 품질 저하 시 비디오 off → 오디오 전용 fallback |
| JWT 분리 저장 | Access Token = localStorage(Zustand persist), Refresh Token = HttpOnly 쿠키. API는 Authorization 헤더 |
| 이중 ID | 내부 PK는 bigint 자동 증가, 외부 API에는 `public_id`(접두사 + nanoid) 노출. PK 추론 방지, API 가독성 향상 |
| 이벤트 버스 분리 | SMS, 의사 SSE 알림, 미션 텔레메트리, 배차 재시도는 Kafka 토픽으로 비동기 분리 |
| 환자 SMS 알림 | 환자는 계정이 없으므로 예약 결과/취소 알림은 SOLAPI SMS 게이트웨이로 발송. 개발환경은 Mock |

---

## 2. 논리 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                        Clients                          │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌───────────┐  ┌──────────┐ │
│  │ 전화     │  │ 의사 웹  │  │ 관리자 │  │ 보호자    │  │ 차량     │ │
│  │ 시뮬레이터│  │          │  │ 웹     │  │ 웹(읽기)  │  │ 터미널FE │ │
│  └────┬─────┘  └────┬─────┘  └───┬────┘  └─────┬─────┘  └────┬─────┘ │
│       └──────────────┴────────────┴─────────────┘       │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS
┌───────────────────────────┴─────────────────────────────┐
│                 Main App Server / Cluster               │
│  ┌───────────────────────────────────────────────────┐  │
│  │               Reverse Proxy (Nginx)               │  │
│  │         /api → spring       / → react            │  │
│  └──────┬──────────────┬───────────────┬────────────┘  │
│         │              │               │               │
│  ┌──────┴──────┐ ┌─────┴─────┐ ┌──────┴──────┐         │
│  │ Spring Boot │ │  React    │ │   LiveKit   │         │
│  │ (제어면)    │ │ (프론트)  │ │ (미디어면)  │         │
│  │ - 인증/권한 │ └───────────┘ └─────────────┘         │
│  │ - 도메인 API│                                       │
│  │ - 오케스트레│                                       │
│  │   이션      │                                       │
│  │ - 감사 로그 │                                       │
│  │ - 파일 관리 │                                       │
│  └──────┬──────┘                                       │
│         │                                              │
│   ┌─────┴─────┐ ┌───────┐ ┌─────────────┐              │
│   │ PostgreSQL │ │ Redis │ │ Kafka + ZK │              │
│   └────────────┘ └───────┘ └─────────────┘              │
└───────────────────────────┬─────────────────────────────┘
                            │ REST multipart / WebSocket / REST JSON
┌───────────────────────────┴─────────────────────────────┐
│                  GPU Server (AI Inference)              │
│              ┌─────────┐   ┌────────────────┐           │
│              │ IDV AI  │   │ STT AI         │           │
│              └─────────┘   └────────────────┘           │
└─────────────────────────────────────────────────────────┘
```

- DEV와 PROD 모두 `Spring Boot -> GPU Server` 경로로만 AI 추론을 호출한다.
- `Spring Boot -> IDV AI` 는 REST multipart를 사용한다.
- `Spring Boot <-> STT AI` 는 WebSocket 스트리밍으로 partial/final transcript를 주고받는다.
- `Spring Boot -> Kafka` 는 내부 비동기 이벤트 버스로만 사용하며, 외부 클라이언트는 직접 접근하지 않는다.
- React, 관리자 웹, 차량 단말은 GPU 서버를 직접 호출하지 않는다.

---

### 2.1 Kafka 이벤트 흐름

- `sms.requests` → `SmsConsumer` → `SmsService` (실패 시 `sms.requests.DLT`에 적재)
- `doctor.notifications` → `DoctorNotificationConsumer` → Redis Pub/Sub publish → `RedisMessageListenerContainer` → 활성 SSE 연결에 전달 (다중 인스턴스 대응)
- `mission.telemetry` → `MissionTelemetryConsumer` → `MISSION` 위치/단계 반영
- `dispatch.requests` → `DispatchConsumer` → 가용 차량 배정 후 `MISSION` 자동 생성
- `dispatch.retry` → `DispatchRetryConsumer` → 동일 권역 배차 재평가 트리거
- `dispatch_outbox` 테이블은 예약 확정과 Kafka publish 사이를 느슨하게 연결하는 outbox 역할을 담당한다.

---

## 3. 개발 환경 (Dev)

### 3.1 구성 원칙

- 메인 애플리케이션 스택만 로컬 Docker Compose로 실행한다.
- IDV AI, STT AI는 **별도 GPU 서버**에서 실행한다.
- DEV와 PROD 모두 Spring Boot는 GPU 서버의 AI 엔드포인트를 직접 호출한다.
- 본인확인(IDV/OCR)은 **REST multipart**를 사용한다.
- 실시간 문진/STT는 **WebSocket 스트리밍**을 사용한다.
- 로컬 개발에서도 React → AI 직접 호출은 금지하고, 반드시 Spring Boot를 경유한다.

### 3.2 컨테이너 구성

```yaml
# docker-compose.yml (개발 환경 - 메인 스택만)
services:
  # === Reverse Proxy ===
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    depends_on:
      - spring-api
      - frontend
      - livekit

  # === Frontend ===
  frontend:
    build: ./src/FE
    expose:
      - "3000"

  # === Backend ===
  spring-api:
    build: ./src/BE
    expose:
      - "8080"
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - DB_HOST=postgres
      - REDIS_HOST=redis
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - LIVEKIT_HOST=http://livekit:7880
      - AI_IDV_URL=https://<DEV_GPU_SERVER_HOST>/idv/api/v1/verify
      - FILE_STORAGE_ROOT=/data/uploads
      - AI_IDV_TRANSFER_MODE=multipart
    volumes:
      - ./local-storage/uploads:/data/uploads
    depends_on:
      - postgres
      - redis
      - kafka

  # === Database ===
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: waddoc
      POSTGRES_USER: waddoc
      POSTGRES_PASSWORD: waddoc_dev
    volumes:
      - pg_data:/var/lib/postgresql/data
    expose:
      - "5432"

  # === Cache ===
  redis:
    image: redis:7-alpine
    command: redis-server --notify-keyspace-events Ex
    expose:
      - "6379"
    volumes:
      - redis_data:/data

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

  # === WebRTC ===
  livekit:
    image: livekit/livekit-server:latest
    ports:
      - "7880:7880"
      - "7881:7881"
      - "7882:7882/udp"
      - "3478:3478/udp"
    environment:
      - LIVEKIT_KEYS=devkey:devsecret

volumes:
  pg_data:
  redis_data:
```

### 3.3 네트워크 구성

```
docker network: waddoc-net (bridge, 메인 스택 컨테이너 연결)

외부 공개 포트:
  - 80        → nginx (HTTP)
  - 9092      → kafka (host access / 로컬 JVM 연동)
  - 7880      → livekit (API + signaling WebSocket)
  - 7881      → livekit (ICE/TCP)
  - 7882/udp  → livekit (ICE/UDP mux)
  - 3478/udp  → livekit (TURN UDP)

Nginx 내부 라우팅:
  - /api      → spring-api:8080
  - /         → frontend:3000

내부 전용 (expose only):
  - 8080  → spring-api
  - 3000  → frontend
  - 5432  → postgres
  - 6379  → redis
  - 2181  → zookeeper
  - 29092 → kafka (container 간 통신)

원격 의존성:
  - spring-api → kafka:29092 (SMS / 알림 / 텔레메트리 / 배차 이벤트)
  - spring-api → https://<DEV_GPU_SERVER_HOST>/idv/api/v1/verify (IDV AI REST)
  - spring-api ↔ wss://<DEV_GPU_SERVER_HOST>/stt/ws/transcribe (STT AI WebSocket)
```

### 3.4 파일 저장 방식 (개발)

```
Spring Boot 로컬 저장소: ./local-storage/uploads → /data/uploads

디렉터리 레이아웃:
  /data/uploads/
    ├── patient-reference/    # 사전 등록 환자 얼굴 사진
    ├── verification-probe/   # 본인확인 촬영 사진
    ├── audio/
    │   └── intake/           # STT용 오디오
    └── temp/                 # 임시 파일

※ AI 서버와의 bind mount 공유는 하지 않는다.
※ Spring Boot가 IDV/OCR 요청 시 파일을 읽어 GPU 서버로 multipart 전송한다.
```

### 3.5 AI 통신 방식 (개발)

```text
개발 환경에서도 GPU 서버를 직접 호출한다.
다만 작업 유형에 따라 프로토콜을 분리한다.

Spring Boot → IDV AI (POST https://<DEV_GPU_SERVER_HOST>/idv/api/v1/verify)
Content-Type: multipart/form-data

Parts:
  - verificationId: "vrf_001"
  - patientId: "patient_001"
  - referenceImage: (binary)
  - probeImage: (binary)
  - thresholdProfile: "MEDICAL_REMOTE_VISIT"

Spring Boot ↔ STT AI
Connect: wss://<DEV_GPU_SERVER_HOST>/stt/ws/transcribe

Client → Server:
  {"type":"start","requestId":"stt_001","language":"ko","sampleRate":16000}
  [binary audio chunk #1]
  [binary audio chunk #2]
  {"type":"end","requestId":"stt_001"}

Server → Client:
  {"type":"partial","requestId":"stt_001","text":"머리가"}
  {"type":"partial","requestId":"stt_001","text":"머리가 아프고"}
  {"type":"final","requestId":"stt_001","text":"머리가 아프고 어지러워요","confidence":0.87}

## 4. 배포 환경 (Production)

### 4.1 구성 원칙

- **메인 서버**: Spring Boot, React, Nginx, PostgreSQL, Redis, Kafka, Zookeeper, LiveKit
- **AI 서버 (별도)**: IDV AI, STT AI
- 서버 간 통신: **REST + WebSocket**
- 파일 전달: IDV/OCR만 **HTTP multipart** (공유 디렉터리 없음)
- AI 서버는 메인 서버에서만 접근 가능 (외부 직접 노출 금지)

### 4.2 서버 구성도

```
┌─────────────────────────────────────────────────────┐
│                  메인 서버 (Ubuntu)                   │
│                                                      │
│  Docker Compose                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │                                                │  │
│  │  ┌───────────────────────────────────────────┐ │  │
│  │  │              Nginx (SSL termination)      │ │  │
│  │  │  :80 :443                                 │ │  │
│  │  │  /api → spring-api   / → react              │ │  │
│  │  │  /livekit → livekit (WSS→WS proxy)        │ │  │
│  │  └───┬──────────┬──────────┬─────────────────┘ │  │
│  │      │          │          │                     │  │
│  │  ┌───┴──────┐ ┌─┴────────┐ ┌──────────┐        │  │
│  │  │ Spring   │ │ React    │ │ LiveKit  │        │  │
│  │  │ Boot     │ │ :3000    │ │ :7880    │        │  │
│  │  │ :8080    │ └──────────┘ │ :7881-82 │        │  │
│  │  └─────┬────┘              └──────────┘        │  │
│  │        │                                        │  │
│  │  ┌─────┴──────┐  ┌──────────┐  ┌───────────┐  │  │
│  │  │ PostgreSQL │  │  Redis   │  │ Kafka+ZK  │  │  │
│  │  │ :5432      │  │  :6379   │  │ :29092    │  │  │
│  │  └────────────┘  └──────────┘  └───────────┘  │  │
│  │        │                                       │  │
│  └────────┼───────────────────────────────────────┘  │
│           │ REST multipart + WS + REST JSON         │
└───────────┼──────────────────────────────────────────┘
            │ HTTPS (내부 네트워크 or VPN)
┌───────────┴──────────────────────────────────────────┐
│                  AI 서버 (Ubuntu)                      │
│                                                       │
│  Docker Compose                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │                                                  │ │
│  │  ┌───────────┐          ┌────────────┐          │ │
│  │  │  IDV AI   │          │ STT AI     │          │ │
│  │  │  :8000    │          │  :8001     │          │ │
│  │  └───────────┘          └────────────┘          │ │
│  │                                                  │ │
│  │  /data/uploads/ (AI 서버 로컬 저장)              │ │
│  │    ├── received/       # 수신된 파일             │ │
│  │    └── temp/           # 처리 후 삭제            │ │
│  │                                                  │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### 4.3 메인 서버 Docker Compose

```yaml
# docker-compose.prod.yml (메인 서버)
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/prod.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - spring-api
      - frontend
      - livekit

  frontend:
    build:
      context: ./src/FE
      args:
        - VITE_API_URL=/api
    expose:
      - "3000"

  spring-api:
    build: ./src/BE
    expose:
      - "8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=postgres
      - REDIS_HOST=redis
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}
      - AI_IDV_URL=https://<PROD_GPU_SERVER_HOST>/idv/api/v1/verify
      - FILE_STORAGE_ROOT=/data/uploads
      - AI_IDV_TRANSFER_MODE=multipart              # 배포: 본인확인 파일 전송
    volumes:
      - uploads:/data/uploads
    depends_on:
      - postgres
      - redis
      - kafka
    deploy:
      resources:
        limits:
          memory: 1G

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: waddoc
      POSTGRES_USER: waddoc
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    deploy:
      resources:
        limits:
          memory: 256M

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: ${ZOOKEEPER_CLIENT_PORT:-2181}

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    ports:
      - "${KAFKA_PORT:-8092}:9092"
    environment:
      KAFKA_BROKER_ID: ${KAFKA_BROKER_ID:-1}
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:${ZOOKEEPER_CLIENT_PORT:-2181}
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://${KAFKA_EXTERNAL_HOST:-localhost}:${KAFKA_PORT:-8092}
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: ${KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR:-1}
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "${KAFKA_AUTO_CREATE_TOPICS_ENABLE:-false}"
    depends_on:
      - zookeeper

  livekit:
    image: livekit/livekit-server:latest
    expose:
      - "7880"               # API + signaling WebSocket (Nginx가 프록시)
    ports:
      - "8881:7881"          # ICE/TCP (직접 노출 — 미디어 전용)
      - "8882:7882/udp"      # ICE/UDP mux (직접 노출 — 미디어 전용)
      - "8478:3478/udp"      # TURN UDP (직접 노출 — NAT traversal)
    volumes:
      - ./livekit/livekit.yaml:/etc/livekit.yaml:ro
    environment:
      - LIVEKIT_KEYS=${LIVEKIT_API_KEY}:${LIVEKIT_API_SECRET}
      - LIVEKIT_CONFIG=/etc/livekit.yaml
    deploy:
      resources:
        limits:
          memory: 1G

volumes:
  pg_data:
  redis_data:
  uploads:
```

### 4.4 GPU 서버 런타임 구성 (DEV/PROD 공통)

GPU 서버의 AI 서비스는 Docker Compose가 아니라 별도 프로세스로 운영한다.

```text
GPU Server
  - reverse proxy (nginx 등)
      - listen 443 ssl
      - /idv/*    -> 127.0.0.1:8000
      - /stt/*    -> 127.0.0.1:8001

  - idv-ai process
      - bind 127.0.0.1:8000
      - 역할: 얼굴 비교 / OCR

  - stt-ai process
      - bind 127.0.0.1:8001
      - 역할: 실시간 STT WebSocket

  - process manager
      - systemd, supervisor, pm2, 또는 전용 ML serving runtime 사용
```

운영 원칙:

- 메인 서버는 GPU 서버의 `443`만 호출한다.
- 내부 서비스 포트 `8000`, `8001`은 loopback 또는 내부망에서만 바인딩한다.
- TLS 종료와 경로 라우팅은 GPU 서버 reverse proxy가 담당한다.

### 4.5 AI 통신 방식 (배포)

배포 환경에서도 작업 유형별로 프로토콜을 분리한다.

```
Spring Boot → IDV AI (POST https://<PROD_GPU_SERVER_HOST>/idv/api/v1/verify)
Content-Type: multipart/form-data

Parts:
  - verificationId: "vrf_001"
  - patientId: "patient_001"
  - referenceImage: (binary) ← 사전 등록 사진 파일
  - probeImage: (binary)     ← 현재 촬영 사진 파일
  - thresholdProfile: "MEDICAL_REMOTE_VISIT"

Response (JSON):
{
  "verificationId": "vrf_001",
  "status": "SUCCEEDED",
  "matched": true,
  "similarityScore": 0.93,
  "reasonCodes": []
}
Spring Boot ↔ STT AI (WS wss://<PROD_GPU_SERVER_HOST>/stt/ws/transcribe)

Client → Server:
  {"type":"start","requestId":"stt_001","language":"ko","sampleRate":16000}
  [binary audio chunks ...]
  {"type":"end","requestId":"stt_001"}

Server → Client:
  {"type":"partial","requestId":"stt_001","text":"머리가"}
  {"type":"final","requestId":"stt_001","text":"머리가 아프고 어지러워요","confidence":0.87}

## 5. AI 통신 추상화 레이어

현재 표준 운영 모델은 **역할별 프로토콜 분리**다.
- IDV/OCR: multipart REST
- 실시간 STT: WebSocket

과거의 공유 디렉터리(JSON 경로 전달) 전략은 더 이상 기본 아키텍처에 포함하지 않는다.

```
interface IdvAiClient {
    VerificationResult verify(VerificationRequest request);
}

interface RealtimeSttClient {
    void openSession(SttSessionRequest request);
    void sendAudioChunk(byte[] chunk);
    void closeSession(String requestId);
}
```

```yaml
# application-local.yml
ai:
  idv-url: https://<DEV_GPU_SERVER_HOST>/idv/api/v1/verify

# application-prod.yml
ai:
  idv-url: https://<PROD_GPU_SERVER_HOST>/idv/api/v1/verify
```

---

## 6. AI API 스펙

AI 서버는 **업무 성격에 따라 프로토콜을 분리**한다.
- 본인확인/신분증 OCR: multipart REST
- 실시간 STT: WebSocket

### 6.1 IDV AI API

| Endpoint | Method | 설명 |
|----------|--------|------|
| `/api/v1/verify` | POST | 본인확인 요청 (multipart) |
| `/api/v1/health` | GET | 헬스체크 |

**요청 모드**
```
Content-Type: multipart/form-data
Parts: verificationId, referenceImage(file), probeImage(file)
```

**응답 (공통)**
```json
{
  "verificationId": "vrf_001",
  "status": "SUCCEEDED",
  "matched": true,
  "livenessPassed": true,
  "similarityScore": 0.93,
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "blurScore": 0.12,
    "brightnessOk": true
  },
  "reasonCodes": [],
  "modelVersion": "face-match-v1"
}
```

### 6.1.1 확장형 IDV + OCR API (P1 선택)

신분증 OCR 기반 확장형 본인확인을 적용하는 경우, GPU 서버는 얼굴 이미지와 신분증 이미지를 함께 받아 OCR과 얼굴 대조를 수행한다.

```text
Spring Boot → IDV AI/OCR (POST https://<GPU_SERVER_HOST>/idv/api/v1/verify)
Content-Type: multipart/form-data

Parts:
  - verificationId: "vrf_001"
  - patientId: "patient_001"
  - referenceImage: (binary)   ← 사전 등록 얼굴 사진
  - faceImage: (binary)        ← 실시간 촬영 얼굴 사진
  - idCardImage: (binary)      ← 신분증 촬영 이미지
  - verificationMode: "FACE_AND_IDCARD"
```

```json
Response:
{
  "verificationId": "vrf_001",
  "status": "SUCCEEDED",
  "ocr": {
    "name": "홍길동",
    "rrnMasked": "580315-1******",
    "address": "강원도 강릉시 ..."
  },
  "matches": {
    "liveVsRegisteredScore": 0.94,
    "liveVsIdCardFaceScore": 0.91,
    "idCardFaceVsRegisteredScore": 0.89
  },
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "idCardDetected": true,
    "ocrConfidence": 0.97
  },
  "reasonCodes": [],
  "modelVersion": "idv-ocr-face-v1"
}
```

Spring Boot는 위 응답을 받아 다음을 수행한다.

1. OCR 추출 이름과 `PATIENT.name` 비교
2. 생년월일 또는 주민등록번호 마스킹값과 `PATIENT.birth_date6` 비교
3. OCR 주소와 `PATIENT.address` 비교
4. 얼굴 3자 점수와 OCR 신뢰도를 함께 사용해 최종 `VERIFIED`, `FAILED`, `MANUAL_REVIEW` 판정
5. `VERIFIED`인 경우 영속 테이블 대신 TTL 캐시에 최근 본인 확인 성공 상태를 저장하고, 활력징후 단계 이후 환자 토큰 발급 시 재사용

보안 원칙:

- 주민등록번호 전체 원문은 GPU 서버 응답, Spring 로그, DB에 저장하지 않는다.
- 응답에는 `rrnMasked` 또는 해시 비교 결과만 포함한다.
- 원본 신분증 이미지는 기본적으로 요청 처리 후 즉시 폐기하고, 실패 케이스 또는 운영자 수동 요청 시에만 예외 저장 후 TTL 정리한다.

### 6.2 실시간 STT AI API

| Endpoint | Method | 설명 |
|----------|--------|------|
| `/ws/transcribe` | WS | 음성 스트리밍 → partial/final transcript |
| `/api/v1/health` | GET | 헬스체크 |

**연결**
```text
wss://<GPU_SERVER_HOST>/stt/ws/transcribe
```

**클라이언트 → 서버**
```json
{ "type": "start", "requestId": "stt_001", "language": "ko", "sampleRate": 16000 }
```

```text
[binary audio chunk...]
```

```json
{ "type": "end", "requestId": "stt_001" }
```

**서버 → 클라이언트**
```json
{ "type": "partial", "requestId": "stt_001", "text": "머리가" }
```

```json
{
  "type": "final",
  "requestId": "stt_001",
  "text": "머리가 아프고 어지러워요",
  "confidence": 0.87,
  "language": "ko",
  "durationMs": 3200
}
```

### 6.3 추천/분류 AI API

| Endpoint | Method | 설명 |
|----------|--------|------|
| `/api/v1/recommend` | POST | STT 결과 기반 최종 증상 분류 및 추천 |
| `/api/v1/health` | GET | 헬스체크 |

**요청**
```json
{
  "requestId": "rec_001",
  "intakeSessionId": "its_001",
  "transcript": "머리가 아프고 어지러워요",
  "patientContext": {
    "age": 78,
    "region": "GANGWON"
  }
}
```

**응답**
```json
{
  "requestId": "rec_001",
  "classification": "INTERNAL_MEDICINE",
  "confidence": 0.82,
  "doctorRecommendation": {
    "doctorId": "doc_001",
    "doctorName": "김OO"
  },
  "reason": "두통 + 어지럼 증상으로 내과 진료 추천"
}
```

---

## 7. WebRTC / LiveKit 구성

### 7.1 TURN 서버 전제

NAT/방화벽 뒤의 환자·의사 환경을 고려하여 **TURN 릴레이를 필수로 구성**한다.

> [!NOTE]
> **사용 가능 포트**: 서버에서 80, 443, 8000–8999 포트가 개방되어 있으므로, LiveKit 관련 포트는 8000번대로 한다.
>
> **포트 모델**: 단일 UDP mux (`rtc.udp_port`) 사용. 모든 ICE/UDP 미디어가 단일 포트(7882)를 통과하므로 포트 범위(port_range) 불필요.
>
> **TURN/TLS**: MVP에서는 사용하지 않음. TURN/UDP 8478 + ICE/TCP 8881로 충분.

| 항목 | 개발 환경 | 배포 환경 |
|------|----------|----------|
| TURN 서버 | LiveKit 내장 TURN | LiveKit 내장 TURN |
| TURN 포트 | 3478/udp (publish) | 8478/udp (publish) |
| ICE/TCP | 7881 (publish) | 8881 (publish) |
| ICE/UDP | 7882/udp mux (publish) | 8882/udp mux (publish) |
| TLS TURN | 없음 | 없음 (P1 검토) |
| 프로토콜 | UDP 우선, TCP fallback | UDP 우선, TCP fallback |

#### LiveKit 포트 역할 정리

```
포트     역할                 비고
────    ───────────────   ─────────────────────────
7880    API + signaling WS  개발: 직접 접속, 배포: Nginx가 WSS 프록시 (/livekit)
7881    ICE/TCP             TCP fallback, 방화벽에서 UDP 차단 시 사용
7882    ICE/UDP mux         모든 UDP 미디어가 단일 포트 통과
3478    TURN/UDP            NAT traversal 릴레이
```

#### 배포 환경 포트 매핑

```
외부 포트     내부 포트     용도
───────     ───────     ─────────────────────
80          80          Nginx HTTP → HTTPS 리다이렉트
443         443         Nginx HTTPS (API, 프론트, LiveKit WS — SSL termination)
8881        7881        LiveKit ICE/TCP (직접 노출 — 미디어 전용)
8882/udp    7882/udp    LiveKit ICE/UDP mux (직접 노출 — 미디어 전용)
8478/udp    3478/udp    TURN UDP (직접 노출 — NAT traversal)

※ LiveKit signaling(7880)은 Nginx가 /livekit 경로로 WSS 프록시.
  클라이언트는 wss://<DOMAIN>/livekit 으로 접속.
```

#### LiveKit 설정 (livekit.yaml)

```yaml
# livekit/livekit.yaml
rtc:
  use_external_ip: false          # 운영에서는 --node-ip로 공인 IP를 명시
  udp_port: 7882               # UDP mux: 모든 ICE/UDP가 단일 포트 통과
  tcp_port: 7881               # ICE/TCP fallback
  # port_range_start/end 사용 안 함 (udp_port 사용 시 무시됨)

turn:
  enabled: true
  domain: <SERVER_DOMAIN>
  tls_port: 0                  # MVP에서 TLS TURN 사용 안 함
  udp_port: 3478               # TURN/UDP (외부 8478으로 매핑)
```

> 운영 docker-compose에서는 `livekit-server --config /etc/livekit.yaml --node-ip <PUBLIC_IP>` 형태로 공인 IP를 명시한다.

#### Nginx 설정 (배포 환경)

Nginx는 API + 프론트엔드 + **LiveKit signaling WebSocket** 프록시를 담당한다.
LiveKit의 7880(API+WS) 포트는 Nginx 뒤에서 SSL termination을 거쳐 WSS로 서비스된다.
ICE/TCP, ICE/UDP, TURN은 미디어 전용이므로 직접 노출한다.

```nginx
# nginx/prod.conf (발취)
upstream spring_api {
    server spring-api:8080;
}

upstream livekit_ws {
    server livekit:7880;
}

server {
    listen 80;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;

    # API
    location /api/ {
        proxy_pass http://spring_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # LiveKit signaling WebSocket (SSL termination)
    location /livekit/ {
        proxy_pass http://livekit_ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    # React 프론트엔드
    location / {
        proxy_pass http://frontend:3000;
    }
}
```

#### 배포 환경 방화벽 규칙

```bash
# 메인 서버 방화벽 (포트 80, 443, 8000-8999 허용 전제)
sudo ufw allow 80/tcp              # HTTP → HTTPS 리다이렉트
sudo ufw allow 443/tcp             # HTTPS (API, 프론트, LiveKit WS — SSL termination)
sudo ufw allow 8881/tcp            # LiveKit ICE/TCP
sudo ufw allow 8882/udp            # LiveKit ICE/UDP mux
sudo ufw allow 8478/udp            # TURN UDP
sudo ufw enable
```

> [!NOTE]
> **8880 포트 불필요**: LiveKit signaling(7880)은 Nginx가 443 포트에서 `/livekit` 경로로 WSS 프록시하므로, 8880 포트를 별도로 개방할 필요가 없다.

> [!WARNING]
> **ICE/UDP mux (8882/udp)** 포트가 방화벽에서 차단되면 UDP 미디어가 불가능하고 ICE/TCP(8881)로 fallback된다. TURN/UDP(8478)도 차단되면 NAT traversal이 실패할 수 있다.

### 7.2 네트워크 품질 저하 대응

프론트엔드(React)에서 LiveKit SDK의 connection quality 이벤트를 감지하여 단계별로 대응한다.

```
품질 상태       조건                     대응
─────────────  ───────────────────────  ──────────────────────────────
GOOD           정상                     영상 + 음성
POOR           quality = POOR, 3초 이상  비디오 자동 비활성화, 오디오만 유지
                                        UI: "네트워크 불안정, 음성 진료로 전환합니다"
LOST           disconnected, 10초 이상   재연결 시도 시작
                                        UI: "연결 복구 중입니다..."
ABANDONED      disconnected, 30초 이상   세션 abandoned 판정
                                        관제 알림 발송
```

### 7.3 참가자별 Reconnect 정책

| 참가자 | reconnect timeout | 최대 재시도 | 실패 시 |
|--------|-------------------|------------|--------|
| 의사 | 30초 | 3회 | 관리자에게 알림, 세션 유지 대기 |
| 환자 | 30초 | 3회 | 의사에게 안내, 세션 유지 대기 |

```
재연결 흐름:
1. 연결 끊김 감지
2. LiveKit SDK 자동 reconnect 시도 (exponential backoff)
3. 3회 실패 → Spring Boot에 reconnect_failed 이벤트 전송
4. Spring Boot → 해당 참가자의 connection_state를 RECONNECTING으로 변경하고, 세션 status는 IN_PROGRESS를 유지한다
5. 상대방 UI에 "상대방 연결 복구 중" 표시
6. 30초 내 복구 실패 → ABANDONED 판정
```

### 7.4 토큰 발급 및 재발급 정책

> **의사와 환자의 토큰은 별도 엔드포인트에서 발급한다.**

| 참가자 | 발급 시점 | API | Auth | 발급 조건 |
|--------|-----------|-----|------|-----------|
| 의사 | 세션 생성 시 | `POST /cases/{caseId}/sessions` | Bearer Token (DOCTOR) | 로그인 + 케이스 배정 확인 |
| 환자 | 본인확인 시 | `POST /missions/{missionId}/identity-check` | Bearer Token (ADMIN) — 차량 태블릿(운영 단말) | `MISSION.phase = VERIFYING`, 미션의 케이스 환자 조회, 기준 이미지 존재 |
| 환자 | 세션 입장 시 | `POST /sessions/{sessionId}/participants/patient/token` | Bearer Token (ADMIN) — 차량 태블릿(운영 단말) | 최근 본인 확인 성공 상태 + 세션 준비 완료 |

| 항목 | 정책 |
|------|------|
| 토큰 발급 주체 | Spring Boot (LiveKit Server SDK) |
| 토큰 유효시간 | 2시간 (진료 세션 최대 시간) |
| 재발급 | 토큰 만료 시 `/api/v1/sessions/{id}/token` 재요청 |
| 재발급 검증 | 세션 상태가 IN_PROGRESS인 경우에만 재발급 허용 |

```
의사 토큰 발급 흐름:
1. 의사가 "진료 시작" 클릭
2. POST /api/v1/cases/{caseId}/sessions (Bearer Token)
3. 응답: doctorToken + room 정보
4. 의사 WebRTC 입장

환자 현장 진료 준비 흐름:
1. 차량 도착 → 환자 탑승 → "진료 시작" 클릭
2. 서버: MISSION.phase = VERIFYING
3. 차량 태블릿(운영 단말, 관리자 로그인)에서 POST /api/v1/missions/{missionId}/identity-check (ADMIN Bearer)
4. 서버: `missionId -> case -> patient` 조회 → 기준 이미지 조회 → GPU IDV API 호출 → OCR 재검증 → 최근 본인 확인 성공 상태 캐시
5. 차량 태블릿: 활력징후 단계 진행
6. 의사 세션 준비 후 POST /api/v1/sessions/{sessionId}/participants/patient/token (ADMIN Bearer)
7. 서버: 최근 본인 확인 성공 상태 검증 → patientToken 발급
8. 환자 WebRTC 입장

LiveKit Room Token 생성 시 포함 정보:
- room: "session_{consultation_session_id}"
- identity: "doctor_{user_id}" 또는 "patient_{patient_id}"
- name: 참가자 표시 이름
- grants:
    canPublish: true
    canSubscribe: true
    canPublishData: true  (채팅/메모용)
```

### 7.5 세션 Abandoned 판정 기준

| 조건 | 판정 |
|------|------|
| 모든 참가자 퇴장 후 30초 경과 | ABANDONED |
| 환자만 이탈, 30초 내 미복귀 | ABANDONED (의사에게 안내 후 종료) |
| 의사만 이탈, 30초 내 미복귀 | 관리자 알림, 60초 후 ABANDONED |
| 의사가 명시적 종료 | COMPLETED (정상 종료) |

```
판정 주체: Spring Boot
판정 방식: LiveKit Webhook → Spring Boot
  - participant_joined
  - participant_left
  - room_finished

ABANDONED 시:
  1. CONSULTATION_SESSION.status = ABANDONED
  2. AUDIT_LOG에 abandoned 사유 기록
  3. 관리자에게 알림
  4. 의사에게 재진료 예약 여부 확인 요청
```

---

## 8. 인증 / 세션 설계

### 8.1 JWT 분리 저장 전략

```
┌──────────────────────────────────────────────────────┐
│                    React (Browser)                    │
│                                                      │
│  ┌─────────────────┐    ┌──────────────────────────┐ │
│  │ Access Token    │    │ Refresh Token            │ │
│  │ (localStorage   │    │ (HttpOnly Secure Cookie) │ │
│  │  via Zustand)   │    │                          │ │
│  │ 수명: 15분      │    │ 수명: 7일               │ │
│  └────────┬────────┘    └──────────┬───────────────┘ │
│           │                        │                 │
│  API 요청 시                  자동 전송 (쿠키)       │
│  Authorization: Bearer {AT}   POST /api/v1/auth/     │
│                                     refresh          │
└───────────┬────────────────────────┬─────────────────┘
            │                        │
┌───────────┴────────────────────────┴─────────────────┐
│                    Spring Boot                        │
│                                                      │
│  모든 API → Authorization 헤더의 Access Token 검증   │
│  /auth/refresh → HttpOnly 쿠키의 Refresh Token 검증  │
└──────────────────────────────────────────────────────┘
```

### 8.2 왜 이 구조인가

| 결정 | 이유 |
|------|------|
| Access Token → localStorage (Zustand persist) | Zustand 상태 관리로 새로고침 시에도 유지. `auth-storage` 키 사용 |
| Refresh Token → HttpOnly 쿠키 | JS 접근 불가, 브라우저가 자동 전송 |
| API → Authorization 헤더 | 쿠키가 아닌 헤더 전송이므로 **CSRF 방어 부담 없음** |
| Auth 전용 쿠키 | Path를 `/api/v1/auth`로 제한하여 auth 하위 경로(login, refresh, logout)에만 쿠키 전송 |

### 8.3 토큰 스펙

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 저장 위치 | localStorage (Zustand persist) | HttpOnly Secure Cookie |
| 수명 | 15분 | 7일 |
| 전송 방식 | `Authorization: Bearer {token}` | 쿠키 자동 전송 |
| 갱신 | `/api/v1/auth/refresh` 호출 | 로그인 시 발급 |
| Payload | userId, role, iat, exp | userId, role, iat, exp |
| 서명 | HS256 (공유 시크릿) | HS256 (공유 시크릿) |

### 8.4 쿠키 설정

```
Set-Cookie: refresh_token={token};
  HttpOnly;          ← JS 접근 차단
  Secure;            ← HTTPS에서만 전송 (배포 환경)
  SameSite=Strict;   ← 크로스사이트 요청 차단
  Path=/api/v1/auth;          ← auth 하위 경로(login, refresh, logout) 전체에 전송
  Max-Age=604800;    ← 7일
```

### 8.5 인증 흐름

```
1. 로그인
   POST /api/v1/auth/login { username, password }
   → 응답 Body: { accessToken, expiresIn, user: {...} }
   → 응답 Cookie: refresh_token (HttpOnly)
   → React: accessToken을 Zustand store (localStorage)에 저장

2. API 호출
   GET /api/v1/bookings
   Headers: Authorization: Bearer {accessToken}
   → Spring: 헤더에서 AT 추출 → 서명 검증 → claims에서 userId/role 추출

3. AT 만료 시 갱신
   POST /api/v1/auth/refresh
   Cookie: refresh_token={RT}  (브라우저 자동 전송)
   → Spring: RT 검증 → 새 AT 발급 (Body) + 새 RT 발급 (Cookie)
   → React: 새 accessToken으로 Zustand store 교체

4. 로그아웃
   POST /api/v1/auth/logout
   → Spring: Redis에서 Refresh Token 무효화
   → 응답: refresh_token 쿠키 삭제 (Max-Age=0)
   → React: Zustand store에서 accessToken 삭제
```

### 8.6 Refresh Token 보안

| 정책 | 구현 |
|------|------|
| Redis 저장 | Refresh Token을 `refresh:{token}` 키로 Redis에 저장 (TTL 7일). DB 기반 세션 테이블은 사용하지 않음 |
| Token Rotation | 갱신 시 새 RT 발급 + 이전 RT Redis에서 삭제 |
| 강제 무효화 | 관리자가 특정 사용자의 모든 세션 강제 로그아웃 가능 (Redis 키 삭제) |

### 8.7 역할별 인증 정리

| 대상 | 인증 방식 | 토큰 |
|------|----------|------|
| 의사 | ID/PW 로그인 | Access(localStorage) + Refresh(쿠키) |
| 관리자 | ID/PW 로그인 | Access(localStorage) + Refresh(쿠키) |
| 보호자 | ID/PW 로그인 | Access(localStorage) + Refresh(쿠키) |
| 환자 | 계정 없음 | 본인확인 완료 후 LiveKit Room Token만 |
| 환자 (인테이크) | 계정 없음 | `intakeSessionId`(nanoid)를 capability token으로 사용 |
| 미션 터미널 | 미션 기반 토큰 | JWT tokenType=MISSION_TERMINAL (missionId, caseId, scopes 포함, 30분) |
| 디바이스 터미널 | 부트스트랩 토큰 | JWT tokenType=DEVICE_TERMINAL (terminalId, vehicleId, regionCode 포함, 30분) |

### 8.8 공개 인테이크 세션 접근 제어

공개 인테이크 플로우(전화 시뮬레이터)에서는 Bearer 인증이 없으므로, `intakeSessionId`(`public_id`)가 사실상 세션 접근 권한을 가진 식별자(capability token)로 동작한다.

| 정책 | 구현 |
|------|------|
| ID 생성 | nanoid (충분히 랜덤, 예측 불가능) |
| TTL | 서버에서 무활동 타임아웃 관리 (예: 10분) |
| 재사용 방지 | 세션 종료(`COMPLETED`/`ABANDONED`/`FAILED`) 후 상태 변경 요청 거부 |
| 접근 범위 | 해당 세션의 식별/턴/추천/예약/조회만 가능 |
| 로그 | 세션 내 모든 API 호출을 `AUDIT_LOG`에 기록. `actorId="SYSTEM"`, `actorRole="SYSTEM"` (무인증 공개 API) |
| correlationId | `corr_ints_<publicId>` (인테이크 세션), `corr_case_<publicId>` (케이스). 전 구간 흐름 추적용 |

### 8.9 CSRF 분석

```
CSRF 공격 조건: 브라우저가 쿠키를 자동으로 인증에 사용할 때 위험

본 구조에서:
- 모든 API 인증 = Authorization 헤더 (Zustand store에서 직접 세팅)
- 쿠키(Refresh Token)는 /api/v1/auth 하위 경로에만 전송 (login, refresh, logout)
- SameSite=Strict로 크로스사이트에서 쿠키 미전송

결론: CSRF 토큰 불필요 (Authorization 헤더 기반이므로)
단, SameSite=Strict + Path=/api/v1/auth 제한은 반드시 유지
```

### 8.10 SSE 인증 전략

> [!WARNING]
> 브라우저의 표준 `EventSource` API는 커스텀 헤더를 지원하지 않는다. 따라서 `Authorization: Bearer` 헤더 전송이 불가능하며, 현재 인증 전략과 **직접적으로 충돌**한다.

**해결: `@microsoft/fetch-event-source` 폴리필 사용**

React에서 `fetch` 기반 SSE 라이브러리를 사용하여 커스텀 헤더 전송을 허용한다.

```javascript
import { fetchEventSource } from '@microsoft/fetch-event-source';

fetchEventSource('/api/v1/doctors/me/notifications/stream', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
  },
  onmessage(ev) {
    const notification = JSON.parse(ev.data);
    // 알림 처리
  },
  onerror(err) {
    // AT 만료 시: refresh 후 재연결
    // 네트워크 오류 시: exponential backoff 재연결
  },
  onclose() {
    // 재연결 로직
  },
});
```

| 항목 | 정책 |
|------|------|
| 라이브러리 | `@microsoft/fetch-event-source` |
| 인증 | `Authorization: Bearer {AT}` 헤더 전송 (기존 전략 유지) |
| AT 만료 시 | `onerror`에서 refresh API 호출 후 새 AT로 재연결 |
| 재연결 | 네트워크 오류 시 exponential backoff |
| 표준 EventSource | 사용 금지 (커스텀 헤더 불가) |

> 신규 예약 알림은 예약/케이스 생성 트랜잭션 커밋 후 `doctor.notifications` Kafka 토픽으로 발행되고, `DoctorNotificationConsumer`가 활성 SSE 연결이 있는 의사에게만 전달한다.

---

## 8.11 SMS 게이트웨이 (SOLAPI)

환자는 시스템 계정이 없으므로 웹 내 알림 수신이 불가능하다. **예약 생성/취소 결과는 SOLAPI SMS 게이트웨이를 통해 환자 휴대전화로 발송**한다.

```
Spring Boot → Kafka(sms.requests) → SmsConsumer → SOLAPI SDK → 환자 SMS 발송

발송 대상:
  - 예약 확정 시: 예약 일시/의사/진료과 안내 SMS
  - 예약 취소 시: 취소 완료 안내 SMS

환경별 처리:
  - 개발 (local): SMS를 실제 발송하지 않고 로그로 기록 (MockSmsService)
  - 배포 (prod):  SOLAPI API로 실제 발송 (SolapiSmsService)
  - 발송 실패: Kafka 재시도 후 `sms.requests.DLT`에 적재
```

```
추상화 레이어 (Spring Boot):

interface SmsService {
    void send(String to, String message);
    String getContactNumber();
}

@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "mock", matchIfMissing = true)
class MockSmsService implements SmsService {
    // 개발: 로그만 기록, 실제 발송 안 함
}

@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "solapi")
class SolapiSmsService implements SmsService {
    // 배포: SOLAPI SDK로 실제 SMS 발송
}
```

```yaml
# application-local.yml
sms:
  provider: mock
  sender-number: "01000000000"
  contact-number: "01000000000"

# application-prod.yml
sms:
  provider: solapi
  api-key: ${SOLAPI_API_KEY}
  api-secret: ${SOLAPI_API_SECRET}
  sender-number: ${SOLAPI_SENDER_NUMBER}
```

---

## 9. 네트워크 보안

### 9.1 개발 환경

```
개발 환경에서도 AI는 별도 GPU 서버에서 동작
메인 서버/개발 PC → GPU 서버 443 outbound만 허용
GPU 서버 방화벽은 개발용 메인 서버 IP 또는 VPN 대역에서 오는 443만 허용
GPU 서버 내부 서비스 포트 8000, 8001은 외부 직접 공개하지 않음
```

### 9.2 배포 환경

```
메인 서버:
  외부 공개: 80, 443 (Nginx — API, 프론트, LiveKit WS SSL termination),
            8092 (Kafka host access), 8881 (ICE/TCP), 8882/udp (ICE/UDP), 8478/udp (TURN)
  내부 전용: 8080 (Spring), 7880 (LiveKit WS), 5432 (PostgreSQL), 6379 (Redis), 2181 (Zookeeper), 29092 (Kafka broker)

AI 서버:
  외부 공개: 443 (TLS reverse proxy)
  메인 서버에서만 접근: 443
  방화벽: 메인 서버 IP만 허용 (iptables/ufw)
  내부 전용: 8000 (IDV), 8001 (STT)

서버 간 통신:
  - 같은 VPC/내부 네트워크 내에서 private IP 사용
  - 필요 시 HTTPS + API Key 인증 추가
```

```bash
# AI 서버 방화벽 설정 예시
sudo ufw default deny incoming
sudo ufw allow from <MAIN_SERVER_IP> to any port 443   # AI Gateway (HTTPS/WSS)
sudo ufw allow 22/tcp                                    # SSH
sudo ufw enable
```

---

## 10. 자원 배분 권장

### 10.1 메인 서버 (권장 최소: 8GB RAM, 4 CPU)

| 컨테이너 | Memory Limit | CPU Limit | 비고 |
|-----------|-------------|-----------|------|
| nginx | 128M | 0.5 | |
| frontend | 256M | 0.5 | |
| spring-api | 1G | 2.0 | |
| postgres | 1G | 1.0 | reservations: 512M |
| redis | 256M | 0.5 | |
| livekit | 1G | 1.0 | |
| **합계** | **~3.6G** | **5.5** | OS·버퍼 포함 ~6G |

### 10.2 AI 서버 (권장 최소: 8GB RAM, 4 CPU, GPU 권장)

| 프로세스 | Memory Limit | CPU Limit | 비고 |
|-----------|-------------|-----------|------|
| idv-ai process | 4G | 4.0 | 얼굴 비교 / OCR 모델 |
| stt-ai process | 4G | 4.0 | 실시간 음성 인식 |
| **합계** | **8G** | **8.0** | GPU 있으면 CPU 부담 감소 |

---

## 11. 타임아웃 및 장애 대응

| 항목 | 타임아웃 | 재시도 | 실패 시 |
|------|---------|--------|---------|
| Spring → IDV AI | 10초 | 1회 자동 | MANUAL_REVIEW 전환 |
| Spring ↔ STT AI (WS 연결) | 3초 | 1회 자동 | STT_FAILED 기록, 수동 입력 전환 |
| STT final 응답 대기 | 12초 | 1회 자동 | STT_FAILED 기록, 수동 입력 전환 |
| Spring → PostgreSQL | 5초 | 3회 (exponential backoff) | 503 응답 |
| Spring → Redis | 3초 | 2회 | DB fallback |
| Spring → LiveKit | 5초 | 1회 | 세션 생성 실패 안내 |
| WebRTC 재연결 | 30초 | 3회 (SDK 자동) | ABANDONED 판정 |

> [!NOTE]
> STT UX 목표는 10초 이내 응답 (MVP_Requirements §9.2). 서버 hard timeout은 12초로 네트워크 오버헤드를 포함한다.

---

## 12. 파일 저장 정책

### 12.1 메인 서버 파일 구조

```
/data/uploads/
  ├── patient-reference/      # 사전 등록 환자 사진 (Spring write)
  ├── verification-probe/     # 본인확인 촬영 사진 (Spring write)
  ├── audio/
  │   └── intake/             # 인테이크 오디오 (Spring write)
  └── temp/                   # 임시 파일
```

### 12.2 저장 규칙

- 파일명: UUID 기반 (원본 이름 사용 금지)
- DB 저장: **상대경로** (예: `patient-reference/9f2c...jpg`)
- 마운트 루트: 환경 변수 `FILE_STORAGE_ROOT`로 관리

### 12.3 정리(Cleanup) 정책

| 디렉터리 | TTL | 정리 주체 |
|----------|-----|----------|
| temp/ | 24시간 | Spring @Scheduled |
| verification-probe/ | 실패 케이스 또는 운영자 요청 저장분만 7일 | Spring @Scheduled |
| audio/intake/ | STT 완료 후 30일 | Spring @Scheduled |
| patient-reference/ | 환자 탈퇴 시까지 유지 | 관리자 수동 |

---

## 13. 실행 명령어

### 개발 환경
```bash
# 메인 스택 올리기 (AI 제외)
docker compose up -d

# 로그 확인
docker compose logs -f spring-api

# 특정 서비스만 재빌드
docker compose up -d --build spring-api

# 원격 GPU 서버 헬스체크 예시
curl https://<DEV_GPU_SERVER_HOST>/idv/api/v1/health
```

### 배포 환경 — 메인 서버
```bash
docker compose -f docker-compose.prod.yml up -d
```

### GPU 서버
```bash
# reverse proxy 재시작
sudo systemctl restart nginx

# AI 프로세스 재시작 예시
sudo systemctl restart idv-ai
sudo systemctl restart stt-ai
```

---

## 14. 환경별 차이 요약

| 항목 | 개발 (Dev) | 배포 (Prod) |
|------|------------|-------------|
| Compose 파일 | `docker-compose.yml` (메인 스택) + 원격 GPU 서버 | `docker-compose.prod.yml` (메인 스택) + 원격 GPU 서버 |
| AI 서버 위치 | 별도 GPU 서버 | 별도 GPU 서버 |
| Spring → IDV AI | https://\<DEV_GPU_SERVER_HOST\>/idv/api/v1/verify | https://\<PROD_GPU_SERVER_HOST\>/idv/api/v1/verify |
| Spring ↔ STT AI | wss://\<DEV_GPU_SERVER_HOST\>/stt/ws/transcribe | wss://\<PROD_GPU_SERVER_HOST\>/stt/ws/transcribe |
| 프로토콜 모델 | IDV=REST multipart, STT=WebSocket | IDV=REST multipart, STT=WebSocket |
| AI 파일 접근 | IDV/OCR 수신 파일은 로컬 저장, STT는 스트림 처리 후 필요 시 임시 저장 | IDV/OCR 수신 파일은 로컬 저장, STT는 스트림 처리 후 필요 시 임시 저장 |
| Spring Profile | `local` | `prod` |
| DB 비밀번호 | 하드코딩 (dev) | 환경 변수 / secrets |
| TLS | 없음 | Nginx에서 종료 |
| 방화벽 | GPU 서버에 Dev 메인 서버/VPN 대역만 허용 | GPU 서버에 Prod 메인 서버 IP만 허용 |
| 자원 제한 | 느슨 | 프로세스별 limits / systemd 제어 권장 |
| 인증 쿠키 Secure | 없음 (HTTP) | Secure 필수 (HTTPS) |
| TURN | LiveKit 내장, 3478/udp (publish) | LiveKit 내장, 8478/udp (publish) |
| LiveKit signaling | 7880 직접 접속 (HTTP) | Nginx WSS 프록시 (`/livekit` → 7880, SSL termination) |
| LiveKit 미디어 포트 | 7881, 7882/udp (publish) | 8881, 8882/udp (직접 노출) |
| 포트 모델 | UDP mux (7882) | UDP mux (8882←7882) |
| 허용 포트 범위 | 80, 3478, 7880-7882 | 80, 443, 8478, 8881-8882 |
