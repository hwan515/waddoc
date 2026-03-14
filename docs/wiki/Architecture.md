# 도서·산간 방문형 비대면 진료 서비스 — 시스템 아키텍처

## 1. 아키텍처 원칙

| 원칙 | 설명 |
|------|------|
| 제어면 / 미디어면 / 추론면 분리 | Spring Boot = 상태·권한·오케스트레이션, LiveKit = WebRTC 미디어, AI = 추론 전용 |
| AI 내부망 격리 | AI 서버는 외부 직접 노출 금지. React → AI 직접 호출 금지 |
| 환경별 파일 전달 추상화 | 개발: 공유 디렉터리, 배포: REST multipart |
| 환자 무계정 정책 | 환자는 로그인 계정 없음. 본인확인 완료 후 room token만 발급 |
| TURN 전제 WebRTC | NAT/방화벽 환경 대비 TURN 릴레이 필수 구성. 품질 저하 시 비디오 off → 오디오 전용 fallback |
| JWT 분리 저장 | Access Token = 메모리(JS 변수), Refresh Token = HttpOnly 쿠키. API는 Authorization 헤더 |
| 이중 ID | 내부 PK는 bigint 자동 증가, 외부 API에는 `public_id`(접두사 + nanoid) 노출. PK 추론 방지, API 가독성 향상 |
| 환자 SMS 알림 | 환자는 계정이 없으므로 예약 결과/취소 알림은 SOLAPI SMS 게이트웨이로 발송. 개발환경은 Mock |

---

## 2. 논리 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                        Clients                          │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌───────────┐ │
│  │ 전화     │  │ 의사 웹  │  │ 관리자 │  │ 보호자    │ │
│  │ 시뮬레이터│  │          │  │ 웹     │  │ 웹(읽기)  │ │
│  └────┬─────┘  └────┬─────┘  └───┬────┘  └─────┬─────┘ │
│       └──────────────┴────────────┴─────────────┘       │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS
┌───────────────────────────┴─────────────────────────────┐
│                    Reverse Proxy (Nginx)                 │
│            /api → spring       / → react               │
└──────┬──────────────┬───────────────┬───────────────────┘
       │              │               │
┌──────┴──────┐ ┌─────┴─────┐ ┌──────┴──────┐
│ Spring Boot │ │  React    │ │   LiveKit   │
│ (제어면)    │ │ (프론트)  │ │ (미디어면)  │
│             │ └───────────┘ └─────────────┘
│ - 인증/권한 │
│ - 도메인 API│         ┌─────────────────────┐
│ - 오케스트레│────REST──│   AI 서버 (추론면)  │
│   이션      │   API    │ ┌───────┐ ┌───────┐│
│ - 감사 로그 │         │ │IDV AI │ │STT AI ││
│ - 파일 관리 │         │ └───────┘ └───────┘│
└──────┬──────┘         └─────────────────────┘
       │
 ┌─────┴─────┐ ┌───────┐
 │ PostgreSQL │ │ Redis │
 └────────────┘ └───────┘
```

---

## 3. 개발 환경 (Local)

### 3.1 구성 원칙

- **전체 서비스를 단일 Docker Compose로 실행**
- AI 서버 포함하여 한 번에 올림
- 이미지 파일은 **호스트 공유 디렉터리 (bind mount)** 로 Spring Boot ↔ AI 공유
- 외부 의존성 없음 (AWS, SMS 등 사용 안 함)

### 3.2 컨테이너 구성

```yaml
# docker-compose.yml (개발 환경 - 전체 올리기)
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
      - AI_IDV_URL=http://idv-ai:8000
      - AI_STT_URL=http://stt-ai:8001
      - FILE_STORAGE_ROOT=/data/uploads
      - AI_FILE_TRANSFER_MODE=shared_directory  # 개발: 공유 디렉터리
    volumes:
      - ./local-storage/uploads:/data/uploads
    depends_on:
      - postgres
      - redis

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
    expose:
      - "6379"
    volumes:
      - redis_data:/data

  # === WebRTC ===
  livekit:
    image: livekit/livekit-server:latest
    ports:
      - "7880:7880"      # API + signaling WebSocket (직접 접속)
      - "7881:7881"      # ICE/TCP
      - "7882:7882/udp"  # ICE/UDP mux
      - "3478:3478/udp"  # TURN UDP
    environment:
      - LIVEKIT_KEYS=devkey:devsecret

  # === AI - IDV (본인확인) ===
  idv-ai:
    build: ./src/AI/idv
    expose:
      - "8000"
    environment:
      - FILE_STORAGE_ROOT=/data/uploads
    volumes:
      - ./local-storage/uploads:/data/uploads:ro  # 읽기 전용
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2.0'

  # === AI - STT ===
  stt-ai:
    build: ./src/AI/stt
    expose:
      - "8001"
    environment:
      - FILE_STORAGE_ROOT=/data/uploads
    volumes:
      - ./local-storage/uploads:/data/uploads:ro  # 읽기 전용
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2.0'

volumes:
  pg_data:
  redis_data:
```

### 3.3 네트워크 구성

```
docker network: waddoc-net (bridge, 모든 컨테이너 연결)

외부 공개 포트:
  - 80        → nginx (HTTP)
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
  - 8000  → idv-ai
  - 8001  → stt-ai
```

### 3.4 파일 공유 방식 (개발)

```
Host Bind Mount: ./local-storage/uploads

공유 구조:
  spring-api  → /data/uploads (read/write)
  idv-ai      → /data/uploads (read-only)
  stt-ai      → /data/uploads (read-only)

디렉터리 레이아웃:
  /data/uploads/
    ├── patient-reference/    # 사전 등록 환자 얼굴 사진
    ├── verification-probe/   # 본인확인 촬영 사진
    ├── audio/
    │   └── intake/           # STT용 오디오
    └── temp/                 # 임시 파일
```

### 3.5 AI 통신 방식 (개발)

```
개발 환경에서는 공유 디렉터리 + JSON 경로 전달:

Spring Boot → IDV AI (POST http://idv-ai:8000/api/v1/verify)
{
  "verificationId": "vrf_001",
  "patientId": "patient_001",
  "referencePath": "patient-reference/abc123.jpg",    ← 상대경로
  "probePath": "verification-probe/vrf_001.jpg",      ← 상대경로
  "thresholdProfile": "MEDICAL_REMOTE_VISIT"
}

Spring Boot → STT AI (POST http://stt-ai:8001/api/v1/transcribe)
{
  "requestId": "stt_001",
  "audioPath": "audio/intake/stt_001.wav",            ← 상대경로
  "language": "ko"
}
```

---

## 4. 배포 환경 (Production)

### 4.1 구성 원칙

- **메인 서버**: Spring Boot, React, Nginx, PostgreSQL, Redis, LiveKit
- **AI 서버 (별도)**: IDV AI, STT AI
- 서버 간 통신: **REST API** (HTTP/HTTPS)
- 파일 전달: **HTTP multipart** (공유 디렉터리 없음)
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
│  │  │ PostgreSQL │  │  Redis   │  │           │  │  │
│  │  │ :5432      │  │  :6379   │  │           │  │  │
│  │  └────────────┘  └──────────┘  └───────────┘  │  │
│  │        │                                       │  │
│  └────────┼───────────────────────────────────────┘  │
│           │ REST API + multipart                     │
└───────────┼──────────────────────────────────────────┘
            │ HTTPS (내부 네트워크 or VPN)
┌───────────┴──────────────────────────────────────────┐
│                  AI 서버 (Ubuntu)                      │
│                                                       │
│  Docker Compose                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │                                                  │ │
│  │  ┌───────────┐          ┌────────────┐          │ │
│  │  │  IDV AI   │          │  STT AI    │          │ │
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
      - AI_IDV_URL=http://<AI_SERVER_IP>:8000      # AI 서버 내부 IP
      - AI_STT_URL=http://<AI_SERVER_IP>:8001      # AI 서버 내부 IP
      - FILE_STORAGE_ROOT=/data/uploads
      - AI_FILE_TRANSFER_MODE=multipart             # 배포: multipart 전송
    volumes:
      - uploads:/data/uploads
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

### 4.4 AI 서버 Docker Compose

```yaml
# docker-compose.ai.yml (AI 서버)
services:
  idv-ai:
    build: ./src/AI/idv
    ports:
      - "8000:8000"
    environment:
      - FILE_STORAGE_ROOT=/data/uploads
      - FILE_RECEIVE_MODE=multipart                 # multipart로 파일 수신
    volumes:
      - ai_uploads:/data/uploads
    deploy:
      resources:
        limits:
          memory: 4G
          cpus: '4.0'

  stt-ai:
    build: ./src/AI/stt
    ports:
      - "8001:8001"
    environment:
      - FILE_STORAGE_ROOT=/data/uploads
      - FILE_RECEIVE_MODE=multipart
    volumes:
      - ai_uploads:/data/uploads
    deploy:
      resources:
        limits:
          memory: 4G
          cpus: '4.0'

volumes:
  ai_uploads:
```

### 4.5 AI 통신 방식 (배포)

배포 환경에서는 공유 디렉터리가 없으므로 **multipart로 파일을 직접 전송**한다.

```
Spring Boot → IDV AI (POST http://<AI_IP>:8000/api/v1/verify)
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
```

```
Spring Boot → STT AI (POST http://<AI_IP>:8001/api/v1/transcribe)
Content-Type: multipart/form-data

Parts:
  - requestId: "stt_001"
  - audioFile: (binary) ← 오디오 파일
  - language: "ko"

Response (JSON):
{
  "requestId": "stt_001",
  "text": "머리가 아프고 어지러워요",
  "confidence": 0.87,
  "language": "ko"
}
```

---

## 5. 파일 전달 추상화 레이어

개발/배포 환경에 따라 파일 전달 방식이 달라지므로, **Spring Boot 내부에 추상화 레이어**를 둔다.

```
interface AiFileTransferStrategy {
    VerificationResult sendVerification(VerificationRequest request);
    TranscriptionResult sendTranscription(TranscriptionRequest request);
}

class SharedDirectoryStrategy implements AiFileTransferStrategy {
    // 개발 환경: JSON에 상대경로만 담아서 전송
    // AI 서버가 같은 볼륨 마운트로 파일 접근
}

class MultipartStrategy implements AiFileTransferStrategy {
    // 배포 환경: 파일 바이너리를 multipart로 전송
    // AI 서버가 수신 후 로컬에 저장하여 처리
}
```

```yaml
# application-local.yml
ai:
  file-transfer-mode: shared_directory
  idv-url: http://idv-ai:8000
  stt-url: http://stt-ai:8001

# application-prod.yml
ai:
  file-transfer-mode: multipart
  idv-url: http://<AI_SERVER_IP>:8000
  stt-url: http://<AI_SERVER_IP>:8001
```

---

## 6. AI API 스펙 (양쪽 호환)

AI 서버는 **두 가지 모드를 모두 지원**하도록 설계한다.

### 6.1 IDV AI API

| Endpoint | Method | 설명 |
|----------|--------|------|
| `/api/v1/verify` | POST | 본인확인 요청 |
| `/api/v1/health` | GET | 헬스체크 |

**요청 모드 A: JSON (공유 디렉터리)**
```json
Content-Type: application/json
{
  "verificationId": "vrf_001",
  "referencePath": "patient-reference/abc.jpg",
  "probePath": "verification-probe/vrf_001.jpg"
}
```

**요청 모드 B: Multipart (파일 직접 전송)**
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

### 6.2 STT AI API

| Endpoint | Method | 설명 |
|----------|--------|------|
| `/api/v1/transcribe` | POST | 음성→텍스트 변환 |
| `/api/v1/health` | GET | 헬스체크 |

**요청 모드 A: JSON** — `{ "requestId", "audioPath", "language" }`
**요청 모드 B: Multipart** — `requestId, audioFile(binary), language`

**응답 (공통)**
```json
{
  "requestId": "stt_001",
  "text": "머리가 아프고 어지러워요",
  "confidence": 0.87,
  "language": "ko",
  "durationMs": 3200
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
  use_external_ip: true
  udp_port: 7882               # UDP mux: 모든 ICE/UDP가 단일 포트 통과
  tcp_port: 7881               # ICE/TCP fallback
  # port_range_start/end 사용 안 함 (udp_port 사용 시 무시됨)

turn:
  enabled: true
  domain: <SERVER_DOMAIN>
  tls_port: 0                  # MVP에서 TLS TURN 사용 안 함
  udp_port: 3478               # TURN/UDP (외부 8478으로 매핑)
```

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
| 환자 | 본인확인 후 | `POST /sessions/{sessionId}/participants/patient/token` | Bearer Token (ADMIN) — 차량 태블릿(운영 단말) | VERIFICATION.status = VERIFIED |

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

환자 토큰 발급 흐름:
1. 차량 도착 → 본인확인 VERIFIED
2. 차량 태블릿(운영 단말, 관리자 로그인)에서 POST /api/v1/sessions/{sessionId}/participants/patient/token (ADMIN Bearer)
3. 서버: VERIFICATION 상태 검증 → patientToken 발급
4. 환자 WebRTC 입장

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
│  │ (JS 메모리 변수) │    │ (HttpOnly Secure Cookie) │ │
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
| Access Token → JS 메모리 | XSS로 localStorage 탈취 방지. 탭 닫으면 소멸 |
| Refresh Token → HttpOnly 쿠키 | JS 접근 불가, 브라우저가 자동 전송 |
| API → Authorization 헤더 | 쿠키가 아닌 헤더 전송이므로 **CSRF 방어 부담 없음** |
| Auth 전용 쿠키 | Path를 `/api/v1/auth`로 제한하여 auth 하위 경로(login, refresh, logout)에만 쿠키 전송 |

### 8.3 토큰 스펙

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 저장 위치 | JS 메모리 (변수) | HttpOnly Secure Cookie |
| 수명 | 15분 | 7일 |
| 전송 방식 | `Authorization: Bearer {token}` | 쿠키 자동 전송 |
| 갱신 | `/api/v1/auth/refresh` 호출 | 로그인 시 발급 |
| Payload | userId, role, iat, exp | userId, tokenFamily, iat, exp |
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
   → React: accessToken을 메모리 변수에 저장

2. API 호출
   GET /api/v1/bookings
   Headers: Authorization: Bearer {accessToken}
   → Spring: 헤더에서 AT 추출 → 서명 검증 → claims에서 userId/role 추출

3. AT 만료 시 갱신
   POST /api/v1/auth/refresh
   Cookie: refresh_token={RT}  (브라우저 자동 전송)
   → Spring: RT 검증 → 새 AT 발급 (Body) + 새 RT 발급 (Cookie)
   → React: 새 accessToken으로 메모리 변수 교체

4. 로그아웃
   POST /api/v1/auth/logout
   → Spring: Refresh Token DB에서 무효화
   → 응답: refresh_token 쿠키 삭제 (Max-Age=0)
   → React: 메모리의 accessToken 삭제
```

### 8.6 Refresh Token 보안

| 정책 | 구현 |
|------|------|
| DB 저장 | Refresh Token을 해시하여 `AUTH_SESSION` 테이블에 저장 |
| Token Rotation | 갱신 시 새 RT 발급 + 이전 RT 무효화 |
| Token Family | 탈취 감지용. 같은 family의 이미 사용된 RT로 갱신 시도 시 family 전체 무효화 |
| 강제 무효화 | 관리자가 특정 사용자의 모든 세션 강제 로그아웃 가능 |
| 동시 세션 | 기기별 세션 관리. Redis에 활성 세션 목록 유지 |

### 8.7 역할별 인증 정리

| 대상 | 인증 방식 | 토큰 |
|------|----------|------|
| 의사 | ID/PW 로그인 | Access(메모리) + Refresh(쿠키) |
| 관리자 | ID/PW 로그인 | Access(메모리) + Refresh(쿠키) |
| 보호자 | ID/PW 로그인 | Access(메모리) + Refresh(쿠키) |
| 환자 | 계정 없음 | 본인확인 완료 후 LiveKit Room Token만 |
| 환자 (인테이크) | 계정 없음 | `intakeSessionId`(nanoid)를 capability token으로 사용 |

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
- 모든 API 인증 = Authorization 헤더 (JS 메모리에서 직접 세팅)
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

fetchEventSource('/api/v1/notifications/subscribe', {
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

---

## 8.11 SMS 게이트웨이 (SOLAPI)

환자는 시스템 계정이 없으므로 웹 내 알림 수신이 불가능하다. **예약 생성/취소 결과는 SOLAPI SMS 게이트웨이를 통해 환자 휴대전화로 발송**한다.

```
Spring Boot → SOLAPI SDK → 환자 SMS 발송

발송 대상:
  - 예약 확정 시: 예약 일시/의사/진료과 안내 SMS
  - 예약 취소 시: 취소 완료 안내 SMS

환경별 처리:
  - 개발 (local): SMS를 실제 발송하지 않고 로그로 기록 (MockSmsService)
  - 배포 (prod):  SOLAPI API로 실제 발송 (SolapiSmsService)
```

```
추상화 레이어 (Spring Boot):

interface SmsService {
    SmsResult send(String recipientPhone, String senderPhone, String messageBody);
}

class MockSmsService implements SmsService {
    // 개발: 로그만 기록, 실제 발송 안 함
}

class SolapiSmsService implements SmsService {
    // 배포: SOLAPI SDK로 실제 SMS 발송
}
```

```yaml
# application-local.yml
sms:
  provider: mock
  sender-number: "01000000000"

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
단일 Docker bridge network - 별도 보안 조치 불필요
AI 서버 포트는 expose만 (호스트 바인딩 안 함)
```

### 9.2 배포 환경

```
메인 서버:
  외부 공개: 80, 443 (Nginx — API, 프론트, LiveKit WS SSL termination),
            8881 (ICE/TCP), 8882/udp (ICE/UDP), 8478/udp (TURN)
  내부 전용: 8080 (Spring), 7880 (LiveKit WS), 5432 (PostgreSQL), 6379 (Redis)

AI 서버:
  외부 공개: 없음
  메인 서버에서만 접근: 8000, 8001
  방화벽: 메인 서버 IP만 허용 (iptables/ufw)

서버 간 통신:
  - 같은 VPC/내부 네트워크 내에서 private IP 사용
  - 필요 시 HTTPS + API Key 인증 추가
```

```bash
# AI 서버 방화벽 설정 예시
sudo ufw default deny incoming
sudo ufw allow from <MAIN_SERVER_IP> to any port 8000  # IDV AI
sudo ufw allow from <MAIN_SERVER_IP> to any port 8001  # STT AI
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

| 컨테이너 | Memory Limit | CPU Limit | 비고 |
|-----------|-------------|-----------|------|
| idv-ai | 4G | 4.0 | 얼굴 비교 모델 |
| stt-ai | 4G | 4.0 | 음성 인식 모델 |
| **합계** | **8G** | **8.0** | GPU 있으면 CPU 부담 감소 |

---

## 11. 타임아웃 및 장애 대응

| 항목 | 타임아웃 | 재시도 | 실패 시 |
|------|---------|--------|---------|
| Spring → IDV AI | 10초 | 1회 자동 | MANUAL_REVIEW 전환 |
| Spring → STT AI | 12초 | 1회 자동 | STT_FAILED 기록, 수동 입력 전환 |

> [!NOTE]
> STT UX 목표는 10초 이내 응답 (MVP_Requirements §9.2). 서버 hard timeout은 12초로 네트워크 오버헤드를 포함한다.
| Spring → PostgreSQL | 5초 | 3회 (exponential backoff) | 503 응답 |
| Spring → Redis | 3초 | 2회 | DB fallback |
| Spring → LiveKit | 5초 | 1회 | 세션 생성 실패 안내 |
| WebRTC 재연결 | 30초 | 3회 (SDK 자동) | ABANDONED 판정 |

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
| verification-probe/ | 검증 완료 후 7일 | Spring @Scheduled |
| audio/intake/ | STT 완료 후 30일 | Spring @Scheduled |
| patient-reference/ | 환자 탈퇴 시까지 유지 | 관리자 수동 |

---

## 13. 실행 명령어

### 개발 환경
```bash
# 전체 올리기
docker compose up -d

# 로그 확인
docker compose logs -f spring-api

# 특정 서비스만 재빌드
docker compose up -d --build spring-api
```

### 배포 환경 — 메인 서버
```bash
docker compose -f docker-compose.prod.yml up -d
```

### 배포 환경 — AI 서버
```bash
docker compose -f docker-compose.ai.yml up -d
```

---

## 14. 환경별 차이 요약

| 항목 | 개발 (Local) | 배포 (Prod) |
|------|-------------|-------------|
| Compose 파일 | `docker-compose.yml` | `docker-compose.prod.yml` + `docker-compose.ai.yml` |
| AI 서버 위치 | 같은 Compose | 별도 서버 |
| Spring → AI 통신 | http://idv-ai:8000 (컨테이너명) | http://\<AI_IP\>:8000 (서버 IP) |
| 파일 전달 | bind mount + JSON 경로 | REST multipart |
| AI 파일 접근 | read-only bind mount | 수신 후 로컬 저장 |
| Spring Profile | `local` | `prod` |
| DB 비밀번호 | 하드코딩 (dev) | 환경 변수 / secrets |
| TLS | 없음 | Nginx에서 종료 |
| 방화벽 | 없음 | AI 서버 접근 제한 |
| 자원 제한 | 느슨 | 컨테이너별 limits 설정 |
| 인증 쿠키 Secure | 없음 (HTTP) | Secure 필수 (HTTPS) |
| TURN | LiveKit 내장, 3478/udp (publish) | LiveKit 내장, 8478/udp (publish) |
| LiveKit signaling | 7880 직접 접속 (HTTP) | Nginx WSS 프록시 (`/livekit` → 7880, SSL termination) |
| LiveKit 미디어 포트 | 7881, 7882/udp (publish) | 8881, 8882/udp (직접 노출) |
| 포트 모델 | UDP mux (7882) | UDP mux (8882←7882) |
| 허용 포트 범위 | 80, 3478, 7880-7882 | 80, 443, 8478, 8881-8882 |
