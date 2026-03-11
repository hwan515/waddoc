# Waddoc 인프라 설정

## 디렉터리 구조

```
infra/
├── .env.example              # 환경 변수 템플릿
├── docker-compose.yml        # 개발 환경 (8 서비스)
├── docker-compose.prod.yml   # 배포 메인 서버 (6 서비스)
├── docker-compose.ai.yml     # 배포 AI 서버 (2 서비스)
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

### 개발 환경 (전체 서비스)

```bash
cd infra
docker compose up -d              # 전체 기동
docker compose logs -f spring-api  # 로그 확인
docker compose up -d --build spring-api  # 특정 서비스 재빌드
docker compose down                # 종료
```

### 배포 환경 — 메인 서버

```bash
cd infra
docker compose -f docker-compose.prod.yml up -d
```

### 배포 환경 — AI 서버

```bash
cd infra
docker compose -f docker-compose.ai.yml up -d
```

## SSL 인증서 배치

배포 환경에서는 `infra/certs/` 디렉터리에 인증서를 배치합니다:

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
