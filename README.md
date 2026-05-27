# Waddoc

전화로 예약하면 집 앞까지 찾아오는 방문형 비대면 원격 진료 자율주행 로봇 서비스입니다.

![Bungbungdak](./docs/assets/Bungbungdak.png)

## 프로젝트 목표

Waddoc은 도서·산간 의료취약지 고령층이 스마트폰 앱 없이 전화 기반 흐름으로 진료를 예약하고, 예약 시간에 맞춰 자율주행 차량이 방문해 본인확인, 기초 활력징후 측정, WebRTC 화상진료, 진료 기록 조회까지 이어지도록 설계한 E2E 의료 접근성 데모입니다.

핵심 가치는 다음 네 가지입니다.

| 가치 | 구현 방향 |
| --- | --- |
| 낮은 진입 장벽 | 전화/웹 시뮬레이터 기반 예약, 환자 무계정 플로우 |
| 운영 가능성 | 관제, 배차, 장애 복구, 감사 로그, 상태 전이 중심 설계 |
| 안정적인 실시간 통신 | LiveKit WebRTC, SSE, Kafka, MQTT를 역할별로 분리 |
| 확장 가능한 서버 경계 | `edge-bff`, `core-app`, `notification-service`, `robot-gateway` 분리 |

## 시스템 구성

| 영역 | 주요 역할 |
| --- | --- |
| `src/FE` | 의사, 관리자, 보호자, 관제 웹 UI |
| `src/FE-phone` | 전화 예약 시뮬레이터 UI |
| `src/BE` | Spring Boot 기반 API, 인증, 예약, 배차, 알림, 로봇 게이트웨이 |
| `src/ros2_docker` | ROS2/Unity 연동, 카메라 스트리밍, 로봇 통신 |
| `infra` | Docker Compose, Nginx, LiveKit, coturn, Kafka, Redis, PostgreSQL, Mosquitto, MediaMTX, monitoring |
| `docs/wiki` | 요구사항, 아키텍처, API, 인프라, 스케일링, 컨벤션 문서 |

서비스 경계는 제어면, 미디어면, 추론면을 분리하는 방식으로 잡았습니다.

```text
Client
  -> Nginx
  -> edge-bff
  -> core-app / notification-service / robot-gateway
  -> PostgreSQL / Redis / Kafka / Mosquitto / LiveKit / MediaMTX
  -> 외부 GPU IDV 서버
```

자세한 설계는 다음 문서를 기준으로 봅니다.

- [문서 목차](./docs/wiki/README.md)
- [프로젝트 소개](./docs/wiki/Projectinfo.md)
- [MVP 요구사항](./docs/wiki/MVP_Requirements_v2.md)
- [시스템 아키텍처](./docs/wiki/Architecture.md)
- [API 명세](./docs/wiki/API_Specification.md)
- [ERD](./docs/wiki/ERD.md)
- [인프라 설정](./docs/wiki/Infrastructure_Setup.md)
- [트래픽/스케일링 설계](./docs/wiki/Traffic_Scalability_Design.md)
- [트러블슈팅](./docs/wiki/Troubleshooting.md)
- [팀 개발 규칙](./docs/wiki/Conventions.md)
- [FAQ](./docs/wiki/FAQ.md)

## 주요 기여와 Jira 추적

### 대표 문제 해결 사례

아래 4개 사례는 단순 구현량보다 운영 중 어떤 문제를 얼마나 깊게 파고들었는지를 보여주는 기여입니다.

| 사례 | 문제 | 깊게 들어간 지점 | 결과/검증 | 관련 이슈 |
| --- | --- | --- | --- | --- |
| 실시간 진료 세션 안정화 | WebRTC 연결 실패, `room_finished` empty timeout 이후 실제 진료 전 세션이 조기 완료되는 문제 | TURN/coturn 포트와 credential, Nginx WebSocket/SSE proxy, LiveKit webhook, consultation session 상태 전이, mission phase 동기화까지 end-to-end로 추적 | relay candidate와 LiveKit/coturn 로그 확인, 의사-환자 smoke test, session `IN_PROGRESS`와 mission `CONSULTING` 정합성 확보 | `S14P21A603-413`, `S14P21A603-441`, `S14P21A603-442`, `S14P21A603-444`, `S14P21A603-528` |
| scale-out 알림/이벤트 신뢰성 개선 | Spring 다중 인스턴스에서 SSE 알림이 특정 인스턴스의 local emitter에 묶이고, Outbox relay가 중복 실행될 수 있는 문제 | Kafka consumer 배치, Redis Pub/Sub fan-out, Redis 분산 락, keyspace notification, Nginx SSE timeout을 함께 정리 | `core-app` 다중 인스턴스 환경에서 예약 알림 수신 확인, Outbox relay 단일 실행 로그 확인 | `S14P21A603-377`, `S14P21A603-413`, `S14P21A603-520`, `S14P21A603-521` |
| 로봇 관제 통신 구조 개선 | polling/Zenoh 중심 구조에서 오프라인 감지, 명령 신뢰성, 최신 telemetry 반영이 불안정한 문제 | Mosquitto MQTT, Spring `robot-gateway`, ROS2 bridge, Last Will, retained message, SSE `updatedAt` ordering, mission cleanup까지 통신 경계를 재정리 | stale telemetry 덮어쓰기 방지, GPS 부재 시 pose fallback, 데모 시작 시 이전 활성 미션 정리 | `S14P21A603-509`, `S14P21A603-510`, `S14P21A603-519`, `S14P21A603-526` |
| Unity Camera 운영 장애 복구 | MediaMTX WebRTC 세션은 생성되지만 mixed content, ICE timeout, 보라색/검은 화면으로 브라우저 재생이 실패하는 문제 | edge/frontend Nginx forwarded header, `/unity_cam/` proxy, MediaMTX TCP fallback, H264 pixel format/profile, ROS camera topic fallback을 단계별로 분리 | 공개 HTTPS 경로 고정, `8189/tcp` fallback 추가, `yuv420p`/B-frame 비활성화, inner camera 미수신 시 front camera fallback | `S14P21A603-550`, `S14P21A603-551`, `S14P21A603-552`, `S14P21A603-553` |

### Jira 기준 기여 요약

아래 수치는 대표 사례를 뒷받침하는 보조 지표입니다. Jira JQL 기준으로 조회 가능한 전체 프로젝트 이슈 432건 중 제가(정지환) 담당한 이슈는 201건, 제가(정지환) 생성/보고한 이슈는 219건입니다. 담당 기준으로는 전체의 46.5%, 문제 정의와 기록까지 포함하면 50.7%를 차지합니다.

| 지표 | 수치 | 해석 |
| --- | ---: | --- |
| 전체 프로젝트 이슈 | 432건 | 프로젝트 전체에서 조회 가능한 Jira 이슈 |
| 제가(정지환) 담당한 이슈 | 201건 | assignee 기준 실제 수행/소유 이슈 |
| 제가(정지환) 생성/보고한 이슈 | 219건 | creator/reporter 기준 문제 정의와 기록 이슈 |
| 트러블슈팅/버그 이슈 | 21건 | 운영 장애, 품질 게이트, 실시간 통신, 카메라/로봇 상태 문제를 원인-조치-검증으로 닫은 이슈 |

제가(정지환) 맡은 영역은 백엔드 기반 구축, 실시간 진료 세션 안정화, Kafka/Outbox 기반 이벤트 처리, 로봇 관제/MQTT 전환, 운영 인프라 품질 게이트까지 이어집니다. 단순 기능 구현보다 장애 재현, 원인 분리, 운영 검증까지 남기는 방식으로 기여를 관리했습니다.

아래는 제가(정지환) 생성하거나 작성한 Jira 이슈 중 프로젝트 설명에 필요한 대표 이슈와 범위입니다.

| 영역 | 대표 Jira 이슈 |
| --- | --- |
| Backend 기반/API | `S14P21A603-152`, `S14P21A603-153`, `S14P21A603-154`, `S14P21A603-155` |
| 인프라/배포/품질 게이트 | `S14P21A603-413`, `S14P21A603-520`, `S14P21A603-521`, `S14P21A603-535` |
| Kafka/Outbox/배차 | `S14P21A603-353`, `S14P21A603-354`, `S14P21A603-355`~`S14P21A603-366`, `S14P21A603-377` |
| LiveKit/진료 세션 | `S14P21A603-441`, `S14P21A603-442`, `S14P21A603-444`, `S14P21A603-528` |
| 로봇/MQTT/관제 | `S14P21A603-509`, `S14P21A603-510`, `S14P21A603-519`, `S14P21A603-526`, `S14P21A603-550`, `S14P21A603-551`, `S14P21A603-552`, `S14P21A603-553` |
| 본인확인/보안/시드 | `S14P21A603-451`, `S14P21A603-517`, `S14P21A603-518`, `S14P21A603-535` |
| EMR/환자/Phone UX | `S14P21A603-515`, `S14P21A603-516`, `S14P21A603-528` |

## 트러블슈팅 정리

운영 중 확인한 주요 장애와 복구 방법은 [트러블슈팅 문서](./docs/wiki/Troubleshooting.md)로 분리했습니다. README에서는 대표 문제 해결 사례만 요약하고, 실제 장애 대응 시에는 `문제 -> 원인 -> 조치 -> 검증` 흐름으로 정리된 위키 문서를 기준으로 봅니다.

## 실행 방법

### 로컬 전체 스택

```bash
cd infra
docker compose --env-file .env.local -f docker-compose.yml up -d
docker compose --env-file .env.local -f docker-compose.yml ps
docker compose --env-file .env.local -f docker-compose.yml logs -f edge-bff core-app notification-service robot-gateway
```

### 특정 서비스 재빌드

```bash
cd infra
docker compose --env-file .env.local -f docker-compose.yml up -d --build core-app
```

### 운영 메인 서버

```bash
cd infra
docker compose --env-file /home/ubuntu/.waddoc/prod.env \
  -f docker-compose.prod.yml \
  -f docker-compose.monitoring.prod.yml \
  up -d --remove-orphans
```

### Backend 테스트

```bash
cd src/BE
./gradlew test --no-daemon
```

### Frontend 검증

```bash
cd src/FE
npm ci
npm run lint
npm run build

cd ../FE-phone
npm ci
npm run lint
npm run build
```

## 운영 점검 체크리스트

```bash
cd infra

# 컨테이너 상태
docker compose --env-file /home/ubuntu/.waddoc/prod.env \
  -f docker-compose.prod.yml \
  -f docker-compose.monitoring.prod.yml \
  ps

# 주요 로그
docker compose --env-file /home/ubuntu/.waddoc/prod.env \
  -f docker-compose.prod.yml \
  logs -f nginx core-app notification-service robot-gateway livekit coturn mosquitto mediamtx

# Nginx config validation
docker compose --env-file /home/ubuntu/.waddoc/prod.env \
  -f docker-compose.prod.yml \
  exec nginx nginx -t
```

확인 순서는 보통 `Nginx -> Spring health -> Kafka/Redis/PostgreSQL -> LiveKit/coturn -> Mosquitto -> MediaMTX -> FE 화면` 순서로 잡습니다.
