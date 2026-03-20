# Waddoc 인프라 설정

## 디렉터리 구조

```
infra/
├── .env.example              # 환경 변수 템플릿
├── docker-compose.yml        # 개발 메인 스택 (8 서비스, AI 제외)
├── docker-compose.prod.yml   # 배포 메인 서버 (8 서비스)
├── nginx/
│   ├── dev.conf              # 개발 Nginx (HTTP)
│   └── prod.conf             # 배포 Nginx (SSL + WSS 프록시)
├── livekit/
│   └── livekit.yaml          # LiveKit RTC/TURN 설정
├── certs/                    # SSL 인증서 (gitignored)
└── README.md
```

## 사전 요구사항

- Docker Engine 24+
- Docker Compose v2
- (배포) SSL 인증서 (`fullchain.pem`, `privkey.pem`)

## 환경 변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 실제 값으로 수정
```

## 실행 명령어

### 개발 환경 (메인 스택)

```bash
cd infra
docker compose up -d              # 메인 스택 기동 (AI 제외)
docker compose logs -f spring-api  # 로그 확인
docker compose up -d --build spring-api  # 특정 서비스 재빌드
docker compose down                # 종료
```

- `.env` 에 `DEV_GPU_SERVER_HOST` 를 반드시 설정해야 한다.
- 개발 환경의 `spring-api` 는 GPU 서버의 `443` 포트만 사용한다.
- 호출 경로는 `https://<DEV_GPU_SERVER_HOST>/idv/...`, `https://<DEV_GPU_SERVER_HOST>/triage/...` 기준이다.

### 개발 환경 (DB/Redis/Kafka만 Docker + Backend는 로컬 JVM)

```bash
cd infra
docker compose up -d postgres redis zookeeper kafka
```

- Backend는 `src/BE/src/main/resources/application.yml`에서 기본 프로파일이 `local`로 설정되어 있으므로 IntelliJ 실행 시 별도 `SPRING_PROFILES_ACTIVE` 지정이 없어도 된다.
- `application-local.yml`과 `application.yml` 기본값으로 Postgres/Redis/Kafka는 각각 `localhost:5432`, `localhost:6379`, `localhost:9092`에 연결된다.
- 이 방식은 Backend만 로컬 JVM으로 띄우는 용도다. `spring-api` 컨테이너와 동시에 실행하지 않는다.
- AI 연동까지 확인하려면 `AI_IDV_URL`, `AI_TRIAGE_URL` 환경변수로 GPU 서버의 `443` 경로 기반 주소를 맞춰야 한다.
- LiveKit 연동까지 확인하려면 `docker compose up -d livekit`로 컨테이너를 추가로 올리면 된다. 이 경우 `application-local.yml` 기본값으로 `localhost:7880`에 연결되므로 별도 환경변수 지정은 필요 없다.
- Kafka listener가 활성화된 상태로 Backend를 띄우므로, `zookeeper`/`kafka` 없이 로컬 JVM을 실행하면 이벤트 소비 기능이 비정상 동작한다.
- 로컬 더미데이터가 필요하면 `APP_SEED_ENABLED=true`로 Backend를 실행한다. 기본 로그인 비밀번호는 `APP_SEED_DEFAULT_PASSWORD` 또는 기본값 `Passw0rd!`를 사용한다.
- 시드 계정: `seed_admin`
- 시드 의사 계정: `seed_doc_im_kim`, `seed_doc_im_park`, `seed_doc_derm_lee`, `seed_doc_ortho_choi`, `seed_doc_neuro_jung`, `seed_doc_eye_han`
- 시드 환자 전화번호: 신규 예약/최근 진료 이력 확인용 `01012345678`, 기존 예약 조회용 `01055554444`

### 배포 환경 — 메인 서버

```bash
cd infra
docker compose -f docker-compose.prod.yml up -d
```

- `.env` 에 `PROD_GPU_SERVER_HOST` 와 `SERVER_DOMAIN` 을 반드시 설정해야 한다.
- 배포 환경의 `spring-api` 도 GPU 서버 `443`만 사용한다.

### 배포 환경 — Backend 다중 인스턴스 (수평 확장)

Backend를 여러 인스턴스로 띄워 부하를 분산할 수 있다.

```bash
cd infra
docker compose -f docker-compose.prod.yml up -d --build --scale spring-api=3
```

- `--scale spring-api=N`으로 인스턴스 수를 지정한다. compose 파일이나 nginx 설정 수정 없이 숫자만 변경하면 된다.
- Nginx는 Docker Compose 내부 DNS(`resolver 127.0.0.11`)를 사용하여 scale된 모든 컨테이너로 요청을 분산한다.
- 인스턴스 수를 변경하려면 같은 명령어를 다른 숫자로 다시 실행한다.

#### 다중 인스턴스 아키텍처

```
                  ┌─────────────┐
  Client ──443──▶ │   Nginx     │
                  │ (SSL + LB)  │
                  └──────┬──────┘
                         │ round-robin
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         spring-api  spring-api  spring-api
         (inst 1)    (inst 2)    (inst 3)
              │          │          │
              └──────────┼──────────┘
                    ┌────┴────┐
                    ▼         ▼
                PostgreSQL   Redis
```

#### 다중 인스턴스에서 안전한 이유

| 항목 | 방식 | 비고 |
|------|------|------|
| 인증 | JWT Stateless | HttpSession 미사용, 어떤 인스턴스에서든 토큰 검증 가능 |
| Refresh Token | Redis 저장 | 인스턴스 간 공유됨 |
| DB | PostgreSQL 중앙 DB | 모든 인스턴스가 동일 DB 접근 |
| Disconnect 타이머 | Redis TTL 키 | `disconnect:{sessionId}:{role}` 키로 저장, 어떤 인스턴스에서든 취소 가능 |
| 웹훅 멱등성 | Redis SETNX | 이벤트 ID를 Redis에 5분간 저장, 중복 처리 방지 |
| 파일 저장 | Docker named volume (`uploads`) | 모든 인스턴스가 동일 볼륨 마운트 |

#### Redis Keyspace Notification

다중 인스턴스의 disconnect 타이머는 Redis 키 만료 이벤트에 의존한다. `docker-compose.prod.yml`의 Redis 설정에 `--notify-keyspace-events Ex` 옵션이 포함되어 있으므로 별도 설정 없이 동작한다.

흐름:
1. `participant_left` 웹훅 → Redis에 `disconnect:{sessionId}:{role}` 키 저장 (TTL 30초)
2. 30초 내 `participant_joined` 웹훅 → 해당 키 삭제 (타이머 취소, 어떤 인스턴스에서든 가능)
3. 30초 후 키 만료 → Redis expired 이벤트 → `RedisKeyExpirationListener`가 timeout 처리

### GPU 서버

- AI 서버는 Docker Compose가 아니라 GPU 서버의 별도 런타임으로 운영한다.
- 권장 배포 방식:
  - reverse proxy(`nginx` 등)가 `443`을 listen
  - `idv-ai` 프로세스는 내부 포트 `8000`
  - `triage-ai` 프로세스는 내부 포트 `8001`
  - `systemd`, `supervisor`, `pm2`, 또는 전용 ML serving runtime으로 서비스 관리
- 메인 서버는 `https://<GPU_HOST>/idv/...`, `https://<GPU_HOST>/triage/...`만 호출한다.
- `idv-ai`는 최소 다음 경로를 제공해야 한다:
  - `GET https://<GPU_HOST>/idv/api/v1/health`
  - `POST https://<GPU_HOST>/idv/api/v1/verify`
- `POST /idv/api/v1/verify`는 `referenceImage`, `faceImage`, `idCardImage` multipart 업로드를 받아야 한다.

## SSL 인증서 배치

배포 환경에서는 `infra/certs/` 디렉터리에 인증서를 배치합:

```
infra/certs/
├── fullchain.pem
└── privkey.pem
```

Let's Encrypt 사용 시:
```bash
sudo certbot certonly --standalone -d your-domain.com
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem infra/certs/
cp /etc/letsencrypt/live/your-domain.com/privkey.pem infra/certs/
```

## 포트 매핑

### 개발 환경

| 외부 포트 | 서비스 | 용도 |
|-----------|--------|------|
| 80 | nginx | HTTP (API + 프론트엔드) |
| 9092 | kafka | Kafka host access / 로컬 JVM 연동 |
| 7880 | livekit | API + signaling WebSocket |
| 7881 | livekit | ICE/TCP |
| 7882/udp | livekit | ICE/UDP mux |
| 3478/udp | livekit | TURN UDP |

> AI 외부 접근 포트는 `443` 하나만 사용한다. `8000`, `8001` 은 GPU 서버 내부 서비스 포트다.
> `zookeeper`는 개발/배포 모두 내부 네트워크 전용이며 호스트 포트를 노출하지 않는다.

### 배포 환경

| 외부 포트 | 내부 포트 | 서비스 | 용도 |
|-----------|-----------|--------|------|
| 80 | 80 | nginx | HTTP → HTTPS 리다이렉트 |
| 443 | 443 | nginx | HTTPS (API, 프론트, LiveKit WSS) |
| 8092 | 9092 | kafka | Kafka host access / 운영 점검 |
| 8881 | 7881 | livekit | ICE/TCP |
| 8882/udp | 7882/udp | livekit | ICE/UDP mux |
| 8478/udp | 3478/udp | livekit | TURN UDP |

> LiveKit signaling(7880)은 Nginx가 `/livekit` 경로로 WSS 프록시한다.
> 클라이언트는 `wss://<DOMAIN>/livekit`으로 접속한다.
