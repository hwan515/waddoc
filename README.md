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

- [프로젝트 소개](./docs/wiki/Projectinfo.md)
- [시스템 아키텍처](./docs/wiki/Architecture.md)
- [API 명세](./docs/wiki/API_Specification.md)
- [인프라 설정](./docs/wiki/Infrastructure_Setup.md)
- [트래픽/스케일링 설계](./docs/wiki/Traffic_Scalability_Design.md)
- [Unity Camera MediaMTX 계획](./docs/wiki/Unity_Cam_EC2_MediaMTX_Plan.md)

## 주요 기여와 Jira 추적

### Jira 기준 기여 요약

Jira JQL 기준으로 조회 가능한 전체 프로젝트 이슈 432건 중 정지환 담당 이슈는 201건, 생성/보고 이슈는 219건입니다. 담당 기준으로는 전체의 46.5%, 문제 정의와 기록까지 포함하면 50.7%를 차지합니다.

| 지표 | 수치 | 해석 |
| --- | ---: | --- |
| 전체 프로젝트 이슈 | 432건 | 프로젝트 전체에서 조회 가능한 Jira 이슈 |
| 정지환 담당 이슈 | 201건 | assignee 기준 실제 수행/소유 이슈 |
| 정지환 생성/보고 이슈 | 219건 | creator/reporter 기준 문제 정의와 기록 이슈 |
| 트러블슈팅/버그 이슈 | 21건 | 운영 장애, 품질 게이트, 실시간 통신, 카메라/로봇 상태 문제를 원인-조치-검증으로 닫은 이슈 |

담당 영역은 백엔드 기반 구축, 실시간 진료 세션 안정화, Kafka/Outbox 기반 이벤트 처리, 로봇 관제/MQTT 전환, 운영 인프라 품질 게이트까지 이어집니다. 단순 기능 구현보다 장애 재현, 원인 분리, 운영 검증까지 남기는 방식으로 기여를 관리했습니다.

아래는 정지환이 생성하거나 작성한 Jira 이슈 중 프로젝트 설명에 필요한 대표 이슈와 범위입니다.

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

운영 중 확인한 주요 장애와 복구 방법을 `문제 -> 원인 -> 조치 -> 검증` 흐름으로 정리했습니다.

### LiveKit / TURN

| 항목 | 내용 |
| --- | --- |
| 현상 | 브라우저에서 ICE server parsing 실패, WebRTC relay candidate 미생성, 화상진료 연결 실패 |
| 원인 | TURN URL에 잘못된 hostname이 들어가거나, LiveKit 내장 TURN과 외부 coturn 설정이 섞임 |
| 조치 | LiveKit 내장 TURN을 끄고 coturn을 외부 TURN으로 고정, TURN credential을 정적 계정으로 맞춤 |
| 검증 | `chrome://webrtc-internals`에서 relay candidate 확인, LiveKit/coturn 로그 확인, 실제 의사-환자 화상 연결 smoke test |

추가로 `infra/livekit/entrypoint.sh`는 CRLF가 섞이면 컨테이너에서 `#!/bin/sh\r` 문제로 502를 만들 수 있으므로 LF를 유지해야 합니다. `livekit.yaml` 또는 `entrypoint.sh` 같은 bind mount 파일을 수정한 뒤에는 `livekit`, `coturn` 컨테이너를 재시작해야 합니다.

```bash
cd infra
docker compose --env-file /home/ubuntu/.waddoc/prod.env -f docker-compose.prod.yml restart livekit coturn
```

### Nginx / WebSocket / SSE

| 항목 | 내용 |
| --- | --- |
| 현상 | `/livekit/` 경로 WebSocket 연결 실패, EMR 신규 예약 SSE 알림 유실 또는 지연 |
| 원인 | 프록시 경로 prefix가 LiveKit upstream으로 그대로 전달되거나, SSE가 Nginx buffering/timeout 영향을 받음 |
| 조치 | `/livekit/` rewrite로 prefix 제거, SSE 전용 location에 buffering off와 긴 timeout 적용 |
| 관련 이슈 | `S14P21A603-413`, `S14P21A603-535` |

SSE는 표준 `EventSource` 대신 `@microsoft/fetch-event-source`를 사용해 `Authorization: Bearer` 헤더를 유지합니다.

### LiveKit / 진료 세션 상태

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| empty timeout 이후 세션 조기 완료 | 실제 진료 전 `room_finished` webhook이 들어와도 session을 `COMPLETED`로 전이함 | `IN_PROGRESS` 상태일 때만 완료 처리하도록 제한 |
| 세션 시작 후 mission phase 불일치 | doctor/patient가 모두 입장해도 mission은 `VERIFYING`에 머묾 | session `IN_PROGRESS` 전이 시 mission을 `CONSULTING`으로 동기화 |
| 의사 진료 완료 후 환자 단말 잔류 | 진료 완료가 서버 기준 종료 이벤트로 정리되지 않음 | LiveKit room/session 정리와 환자 완료 화면 전환을 서버 흐름에 연결 |
| 진료실 재입장 불가 | 활성 consultation session 정보가 케이스 목록/EMR 액션에 노출되지 않음 | 케이스 목록 DTO에 session 상태를 포함하고 `진료실 복귀` 액션 추가 |

관련 이슈: `S14P21A603-441`, `S14P21A603-442`, `S14P21A603-444`, `S14P21A603-528`

### Scale-out / Redis / Outbox

| 항목 | 내용 |
| --- | --- |
| 현상 | Spring Boot 다중 인스턴스에서 SSE 알림 누락, Outbox 중복 발행 가능성 |
| 원인 | Kafka consumer가 소비한 인스턴스의 로컬 emitter map에만 알림을 보내고, `@Scheduled` relay가 모든 인스턴스에서 동시에 실행됨 |
| 조치 | 의사 알림은 Redis Pub/Sub로 fan-out, Dispatch Outbox relay는 Redis 분산 락으로 단일 실행 보장 |
| 검증 | `--scale core-app=2` 이상에서 예약 생성 후 SSE 수신 확인, Outbox relay 로그에서 단일 인스턴스 실행 확인 |
| 관련 이슈 | `S14P21A603-377`, `S14P21A603-413` |

Redis keyspace notification은 disconnect 타이머에도 쓰이므로 Redis 실행 옵션에 `--notify-keyspace-events Ex`가 포함되어야 합니다.

### Zenoh -> MQTT 전환

| 항목 | 내용 |
| --- | --- |
| 기존 문제 | Zenoh/FastAPI/100ms polling 구조는 오프라인 감지, 명령 신뢰성, Spring 중심 권한/감사 로그 추적이 약함 |
| 전환 방향 | Mosquitto MQTT broker, Spring `robot-gateway`, ROS2 MQTT bridge, FE SSE 구조로 정리 |
| 효과 | polling 제거, QoS 기반 명령 전달, Last Will 기반 오프라인 감지, retained message 기반 마지막 상태 복구 |

운영 경로는 ROS2가 `wss://<DOMAIN>/mqtt`로 접속하고, 내부 `robot-gateway`는 `ws://mosquitto:9001`로 접근합니다.

### 관제 / 로봇 SSE / 미니맵

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| 미니맵 위치 순간이동 | reconnect 또는 out-of-order SSE payload가 최신 telemetry를 덮어씀 | payload별 `updatedAt` 비교로 오래된 snapshot/telemetry 무시 |
| waypoint 전송 전후 마커 롤백 | 경로/odom/source 우선순위가 일관되지 않아 이전 좌표가 재반영됨 | trajectory와 최신 pose 기준으로 좌표 반영 순서 보정 |
| GPS 없음 시 차량 위치 공백 | GPS 좌표가 비어 있을 때 pose fallback이 부족함 | pose 기반 좌표 fallback과 좌표 출처 표시 보정 |
| 데모 시작 시 이전 환자 미션 노출 | 같은 차량의 이전 활성 미션이 current mission 우선순위를 차지함 | 데모 dispatch 전에 동일 차량의 기존 활성 데모 미션을 정리 |

관련 이슈: `S14P21A603-509`, `S14P21A603-510`, `S14P21A603-519`, `S14P21A603-526`

### MediaMTX / Unity Camera

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| Mixed content / 잘못된 redirect | edge nginx와 frontend nginx 사이에 `X-Forwarded-*` 전달이 부족하거나 내부 포트 기준 절대 경로가 생성됨 | 공개 host/scheme header 전달, `/unity_cam/` redirect 재작성 제거 |
| WebRTC 연결 실패 | UDP ICE만 열려 있어 네트워크에 따라 fallback 불가 | `8189/tcp` publish, `MTX_WEBRTCLOCALTCPADDRESS=:8189` 추가 |
| 보라색 화면 | `h264_nvenc` 출력 pixel format/profile이 브라우저 친화적이지 않음 | `yuv420p`, B-frame 비활성화, browser-compatible H264 profile 적용 |
| 검은 화면 | inner camera topic publisher 부재로 초기 black frame만 송출 | inner frame 미수신 감지 후 front camera stream fallback |

관련 이슈: `S14P21A603-550`, `S14P21A603-551`, `S14P21A603-552`, `S14P21A603-553`

### CI/CD

| 항목 | 내용 |
| --- | --- |
| Jenkins 배포 흐름 | GitLab `dev` push -> checkout -> 변경 감지 -> quality gate -> Docker buildx push -> compose up |
| Node 버전 이슈 | Jenkins host Node 18에서 Vite 7 요구사항을 만족하지 못해 FE build 실패 |
| 조치 | FE/FE-phone quality gate를 Node 22 컨테이너 기준으로 실행 |
| Seed env 이슈 | 기본 seed 비밀번호 fallback을 제거하고 `APP_SEED_DEFAULT_PASSWORD` 누락 시 실패하도록 조정 |
| 관련 이슈 | `S14P21A603-520`, `S14P21A603-521`, `S14P21A603-518` |

### 본인확인 / 보안 / 시드

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| 차량 본인확인 false negative | 얼굴 검출 실패가 있어도 OCR은 일치하는 케이스가 있었음 | 임시로 OCR 1개 이상 일치 시 통과하도록 완화하고 기술부채를 명시 |
| JWT 통합 테스트 FK 실패 | seed cleanup이 참조 중인 `IntakeSession`까지 삭제함 | orphan `IntakeSession`만 삭제하도록 정리 |
| seed 기본 비밀번호 fallback | 운영/로컬 seed 비밀번호가 없어도 기본값으로 동작할 수 있음 | `APP_SEED_DEFAULT_PASSWORD` 누락 시 명시적으로 실패 |
| prod 기본 보안 사용자 로그 | Spring Security 기본 in-memory user가 자동 생성됨 | auto configuration 제외, SSE disconnect 로그 노이즈 정리 |

관련 이슈: `S14P21A603-451`, `S14P21A603-517`, `S14P21A603-518`, `S14P21A603-535`

### 운영 DB 접근

운영 PostgreSQL은 공인망에 직접 열지 않고 `127.0.0.1:5432:5432`로만 바인딩합니다. 외부 점검은 SSH 터널을 통해서만 수행합니다.

```bash
ssh -L 5433:127.0.0.1:5432 ubuntu@<EC2_HOST>
```

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
