# Waddoc 인프라 설정

## 디렉터리 구조

```
infra/
├── .env.example              # 환경 변수 템플릿
├── Jenkinsfile               # Jenkins CI/CD 파이프라인 정의
├── docker-compose.yml        # 개발 메인 스택 (AI, coturn 제외)
├── docker-compose.prod.yml   # 배포 메인 서버 (coturn + ROS2 bridge/FastAPI 포함)
├── docker-compose.monitoring.prod.yml # 배포 monitoring 스택 (Prometheus/Grafana/exporter)
├── monitoring/
│   ├── prometheus/
│   └── grafana/
├── nginx/
│   ├── dev.conf.template     # 개발 Nginx 템플릿 (원격 robot API 프록시)
│   └── prod.conf             # 배포 Nginx (SSL + WSS 프록시)
├── livekit/
│   ├── entrypoint.sh         # LiveKit env 치환 래퍼 (LF 유지)
│   └── livekit.yaml          # LiveKit RTC + 외부 TURN(coturn) 설정
├── certs/                    # SSL 인증서 (gitignored)
└── README.md
```

## 사전 요구사항

- Docker Engine 24+
- Docker Compose v2
- (배포) Web SSL 인증서 (`fullchain.pem`, `privkey.pem`)

## 환경 변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 실제 값으로 수정
```

## 실행 명령어

### 개발 환경 (권장: 전체 Docker Compose)

```bash
cd infra
docker compose up -d              # 메인 스택 기동 (nginx/frontend/frontend-phone/spring-api/postgres/redis/zookeeper/kafka/livekit)
docker compose logs -f spring-api  # 로그 확인
docker compose up -d --build spring-api  # 특정 서비스 재빌드
docker compose down                # 종료
```

- `.env` 에 `DEV_GPU_SERVER_HOST` 를 반드시 설정해야 한다.
- `.env` 에 `DEV_ROBOT_API_PROXY_TARGET` 를 설정하면 `/api/minimap`, `/api/odom`, `/api/cmd/*` 요청을 로컬이 아닌 원격 EC2 robot API로 프록시한다. 기본값은 `https://www.waddoc.site`다.
- 개발 환경의 `spring-api` 는 GPU 서버의 `443` 포트만 사용한다.
- 호출 경로는 `https://<DEV_GPU_SERVER_HOST>/idv/...` 기준이다.
- 진료/LiveKit/webhook 검증은 이 전체 compose 구성을 기본 경로로 사용한다.
- 이 방식에서는 `spring-api`, `livekit`, `postgres`, `redis`, `zookeeper`, `kafka`가 같은 네트워크에서 뜨므로 진료 세션 상태 전이와 webhook 흐름이 기본 설정과 일치한다.
- 웹 앱은 `http://localhost`, 환자용 phone 앱은 `http://localhost/phone`으로 접근한다.
- 로컬 compose는 monitoring stack을 포함하지 않으며 `VITE_ENABLE_MONITORING_TAB=false` 기본값으로 관제의 `시스템 모니터링` 탭도 숨긴다.
- 로컬 개발 compose는 더 이상 `ros2_app_ec2`나 `zenoh_bridge_ec2`를 띄우지 않는다. 차량/ROS API는 EC2 쪽을 기준으로 본다.

### 개발 환경 (고급: DB/Redis/Kafka만 Docker + Backend는 로컬 JVM)

```bash
cd infra
docker compose up -d postgres redis zookeeper kafka
```

- Backend는 `src/BE/src/main/resources/application.yml`에서 기본 프로파일이 `local`로 설정되어 있으므로 IntelliJ 실행 시 별도 `SPRING_PROFILES_ACTIVE` 지정이 없어도 된다.
- `application-local.yml`과 `application.yml` 기본값으로 Postgres/Redis/Kafka는 각각 `localhost:5432`, `localhost:6379`, `localhost:9092`에 연결된다.
- 이 방식은 Backend만 로컬 JVM으로 띄우는 용도다. `spring-api` 컨테이너와 동시에 실행하지 않는다.
- AI 연동까지 확인하려면 `AI_IDV_URL` 환경변수로 GPU 서버의 `443` 경로 기반 주소를 맞춰야 한다.
- Kafka listener가 활성화된 상태로 Backend를 띄우므로, `zookeeper`/`kafka` 없이 로컬 JVM을 실행하면 이벤트 소비 기능이 비정상 동작한다.
- 진료/LiveKit 검증은 이 혼합 실행 대신 위의 전체 compose 구성을 권장한다. `spring-api`가 컨테이너 밖에서 뜨면 LiveKit webhook 경로를 별도로 맞추지 않는 한 기본 설정과 어긋날 수 있다.
- 로컬 더미데이터가 필요하면 `APP_SEED_ENABLED=true`로 Backend를 실행한다. 기본 로그인 비밀번호는 `APP_SEED_DEFAULT_PASSWORD` 또는 기본값 `Passw0rd!`를 사용한다.
- 시드 계정: `seed_admin`
- 시드 의사 계정: `seed_doc_im_kim`, `seed_doc_im_park`, `seed_doc_derm_lee`, `seed_doc_ortho_choi`, `seed_doc_neuro_jung`, `seed_doc_eye_han`
- 시드 환자 전화번호: 신규 예약/최근 진료 이력 확인용 `01012345678`, 기존 예약 조회용 `01055554444`

### 배포 환경 — 메인 서버

```bash
cd infra
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.prod.yml up -d
```

- `.env` 에 `PROD_GPU_SERVER_HOST` 와 `SERVER_DOMAIN` 을 반드시 설정해야 한다.
- `.env` 에 `MONITORING_COOKIE_SECRET`, `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD` 도 설정해야 한다.
- 운영 compose에는 `zenoh_bridge_ec2`, `ros2_app_ec2`가 기본 포함된다.
- frontend runtime-config의 `VITE_ENABLE_MONITORING_TAB` 기본값은 `true`이며, 별도 override가 없으면 운영 관제에서 `시스템 모니터링` 탭이 노출된다.
- 운영 monitoring UI는 `https://<DOMAIN>/grafana/` 경로를 사용한다.
- Prometheus는 외부 공개 경로를 두지 않고 Docker 내부 네트워크에서만 접근한다.
- 운영 브라우저는 `https://<DOMAIN>:8000`을 직접 호출하지 않고, Nginx가 `/api/minimap`, `/api/odom`, `/api/cmd/*`를 내부 프록시한다.
- 운영 Zenoh transport는 웹 Nginx를 거치지 않고 `tcp://zenoh.waddoc.site:8081`을 직접 사용한다.
- `zenoh.waddoc.site` DNS A/AAAA 레코드는 운영 EC2 public address를 가리켜야 한다.
- 운영 보안그룹은 `8081/TCP`를 Zenoh 클라이언트가 붙는 소스 대역으로만 제한해야 한다.
- `livekit.yaml`, `entrypoint.sh` 같은 bind mount 파일을 수정한 배포라면 아래 재시작까지 수행해야 한다.

```bash
docker compose -f docker-compose.prod.yml restart livekit coturn
```
- 배포 환경의 `spring-api` 도 GPU 서버 `443`만 사용한다.

#### Monitoring V1 검증 순서

1. `docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.prod.yml ps`로 `prometheus`, `grafana`, `cadvisor`, `postgres-exporter`, `redis-exporter`가 모두 기동됐는지 확인한다.
2. `spring-api` 각 인스턴스에서 `8080/actuator/prometheus`가 내부 네트워크 기준으로 열려 있는지 확인한다.
3. `https://<DOMAIN>/grafana/`를 직접 열었을 때 monitoring 쿠키가 없으면 `403`이 반환되는지 확인한다.
4. 관리자 관제 화면의 `시스템 모니터링` 탭에서 `Grafana 열기` 버튼을 눌러 `operator-overview` 대시보드가 열리는지 확인한다.

#### Monitoring V1 제외 범위

- Kafka lag 대시보드
- LiveKit 전용 metrics 수집
- Alertmanager
- Slack/Discord 알림
- 로컬 개발 compose용 monitoring stack

### CI/CD 파이프라인 (Jenkins)

운영 배포는 `infra/Jenkinsfile`에 정의된 Jenkins 파이프라인이 자동으로 수행한다.

#### 파이프라인 흐름

```
GitLab (dev push) → Checkout → 변경 감지 → 테스트 → Docker buildx → DockerHub push → docker compose up
```

#### 스테이지 상세

| 스테이지 | 설명 |
|----------|------|
| Checkout | GitLab deploy token으로 소스 checkout (shallow clone) |
| Compute Changes | `src/BE/`, `src/FE/`, `src/FE-phone/`, `src/zenoh-server/`, `infra/` 경로별 변경 감지 |
| Quality Gate | BE 변경 시 단위 테스트 실행 (`-PskipIntegrationTests=true`) |
| Build & Push | 변경된 이미지 서비스만 `docker buildx build --push`로 DockerHub에 병렬 푸시 (`ros2_app_ec2`는 배포 서버 로컬 build) |
| Deploy | 메인 compose + monitoring compose를 함께 참조하여 배포. `infra/monitoring/**` 변경 시 monitoring 서비스만 교체하고, spring-api는 항상 3개로 보정 |

#### 빌더 아키텍처

서비스별 독립 buildx builder를 사용하여 병렬 빌드 충돌을 방지하고 캐시를 유지한다.

| 빌더 이름 | 대상 서비스 |
|-----------|------------|
| `waddoc-builder-be` | Backend (`src/BE`) |
| `waddoc-builder-fe` | Frontend (`src/FE`) |
| `waddoc-builder-fp` | Phone (`src/FE-phone`) |

#### 이미지 태깅 전략

- `BUILD_NUMBER` 태그: 배포 추적용 (예: `hwan515/waddoc-backend:51`)
- `latest` 태그: 수동 확인/기본 fallback용
- 부분 배포 시 변경되지 않은 서비스는 현재 실행 중인 이미지 태그를 그대로 유지한다.

#### 필요한 Jenkins Credentials

- `gitlab-deploy-token`: GitLab Deploy Token (Username+Password)
- `dockerhub-credentials`: DockerHub 계정

#### 환경 변수

- `PROD_ENV_FILE`: 운영 환경변수 파일 경로 (서버 로컬, `/home/ubuntu/.waddoc/prod.env`)
- `PROJECT_PATH`: EC2 내 프로젝트 경로 (`/home/ubuntu/S14P21A603`)

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
  - `systemd`, `supervisor`, `pm2`, 또는 전용 ML serving runtime으로 서비스 관리
- 메인 서버는 `https://<GPU_HOST>/idv/...`만 호출한다.
- `idv-ai`는 최소 다음 경로를 제공해야 한다:
  - `GET https://<GPU_HOST>/idv/api/v1/health`
  - `POST https://<GPU_HOST>/idv/api/v1/verify`
- `POST /idv/api/v1/verify`는 `faceImage`, `idCardImage` multipart 업로드를 받아야 하고, `referenceImage`는 optional 이어야 한다.

## Web SSL 인증서 배치

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

## Zenoh 도메인 배치

운영 Zenoh 브리지는 웹용 Nginx 443과 별도로 `tcp://zenoh.waddoc.site:8081`을 직접 listen한다.

- `zenoh.waddoc.site` DNS A/AAAA 레코드는 운영 EC2 public address를 가리켜야 한다.
- 로컬 ROS/Unity 측 `src/ros2_docker/docker-compose.yml`도 같은 도메인 endpoint를 사용한다.

검증 예시:

```bash
nslookup zenoh.waddoc.site
nc -vz zenoh.waddoc.site 8081
docker logs zenoh_bridge | tail -n 50
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
| 443 | 443 | nginx | HTTPS (API, 프론트, LiveKit WSS, `/grafana/`) |
| 8092 | 9092 | kafka | Kafka host access / 운영 점검 |
| 8081 | 8081 | zenoh_bridge_ec2 | Zenoh TCP bridge |
| 8881 | 7881 | livekit | ICE/TCP |
| 8882/udp | 7882/udp | livekit | ICE/UDP mux |
| 8478/udp | 8478/udp | coturn | TURN listener |
| 8600-8699/udp | 8600-8699/udp | coturn | TURN relay range |

> LiveKit signaling(7880)은 Nginx가 `/livekit` 경로로 WSS 프록시한다.
> 클라이언트는 `wss://<DOMAIN>/livekit`으로 접속한다.
> 로컬 개발에서는 `/api/minimap`, `/api/odom`, `/api/cmd/*`를 로컬 Nginx가 `${DEV_ROBOT_API_PROXY_TARGET}`으로 프록시한다.
> 브라우저는 로컬이든 운영이든 `:8000`으로 직접 접근하지 않는다.
> `/grafana/`는 Waddoc ADMIN 기반 monitoring 쿠키가 있어야만 접근된다.
> ROS/Zenoh transport는 브라우저 API와 별개이며 `tcp://zenoh.waddoc.site:8081`로 직접 연결된다.
> 운영에서는 `rtc.use_external_ip: false`와 `LIVEKIT_NODE_IP=<EC2 공인 IP>` 조합으로 공인 IP를 고정한다.
> TURN 릴레이는 LiveKit 내장 TURN이 아니라 `coturn` 컨테이너가 담당한다.
> `livekit.yaml`, `entrypoint.sh`는 bind mount 파일이므로 수정 후 `docker compose restart livekit coturn`이 필요하다.
