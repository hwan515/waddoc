# Waddoc 인프라 설정

## 디렉터리 구조

```
infra/
├── .env.example              # 환경 변수 템플릿
├── docker-compose.yml        # 개발 메인 스택 (6 서비스, AI 제외)
├── docker-compose.prod.yml   # 배포 메인 서버 (6 서비스)
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
- 호출 경로는 `https://<DEV_GPU_SERVER_HOST>/idv/...`, `wss://<DEV_GPU_SERVER_HOST>/stt/...`, `https://<DEV_GPU_SERVER_HOST>/triage/...` 기준이다.

### 개발 환경 (DB/Redis만 Docker + Backend는 로컬 JVM)

```bash
cd infra
docker compose up -d postgres redis
```

- Backend는 `src/BE/src/main/resources/application.yml`에서 기본 프로파일이 `local`로 설정되어 있으므로 IntelliJ 실행 시 별도 `SPRING_PROFILES_ACTIVE` 지정이 없어도 된다.
- `application-local.yml`의 기본값으로 Postgres/Redis는 `localhost`에 연결된다.
- 이 방식은 Backend만 로컬 JVM으로 띄우는 용도다. `spring-api` 컨테이너와 동시에 실행하지 않는다.
- AI 연동까지 확인하려면 `AI_IDV_URL`, `AI_STT_WS_URL`, `AI_TRIAGE_URL` 환경변수로 GPU 서버의 `443` 경로 기반 주소를 맞춰야 한다.
- LiveKit 연동까지 확인하려면 별도로 LiveKit 컨테이너를 올리거나 `LIVEKIT_HOST`, `LIVEKIT_URL` 환경변수를 지정해야 한다.

### 배포 환경 — 메인 서버

```bash
cd infra
docker compose -f docker-compose.prod.yml up -d
```

- `.env` 에 `PROD_GPU_SERVER_HOST` 와 `SERVER_DOMAIN` 을 반드시 설정해야 한다.
- 배포 환경의 `spring-api` 도 GPU 서버 `443`만 사용한다.

### GPU 서버

- AI 서버는 Docker Compose가 아니라 GPU 서버의 별도 런타임으로 운영한다.
- 권장 배포 방식:
  - reverse proxy(`nginx` 등)가 `443`을 listen
  - `idv-ai` 프로세스는 내부 포트 `8000`
  - `stt-triage-ai` 프로세스는 내부 포트 `8001`
  - `systemd`, `supervisor`, `pm2`, 또는 전용 ML serving runtime으로 서비스 관리
- 메인 서버는 `https://<GPU_HOST>/idv/...`, `wss://<GPU_HOST>/stt/...`, `https://<GPU_HOST>/triage/...`만 호출한다.

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
| 7880 | livekit | API + signaling WebSocket |
| 7881 | livekit | ICE/TCP |
| 7882/udp | livekit | ICE/UDP mux |
| 3478/udp | livekit | TURN UDP |

> AI 외부 접근 포트는 `443` 하나만 사용한다. `8000`, `8001` 은 GPU 서버 내부 서비스 포트다.

### 배포 환경

| 외부 포트 | 내부 포트 | 서비스 | 용도 |
|-----------|-----------|--------|------|
| 80 | 80 | nginx | HTTP → HTTPS 리다이렉트 |
| 443 | 443 | nginx | HTTPS (API, 프론트, LiveKit WSS) |
| 8881 | 7881 | livekit | ICE/TCP |
| 8882/udp | 7882/udp | livekit | ICE/UDP mux |
| 8478/udp | 3478/udp | livekit | TURN UDP |

> LiveKit signaling(7880)은 Nginx가 `/livekit` 경로로 WSS 프록시한다.
> 클라이언트는 `wss://<DOMAIN>/livekit`으로 접속한다.
