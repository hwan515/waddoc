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

## 팀원 소개

| 이름 | 역할 | 담당 |
| --- | --- | --- |
| 김주호 | 팀장 | `#ROS` `#Unity` |
| 권미정 | 팀원 | `#BE` |
| 배경근 | 팀원 | `#ROS` |
| 이송연 | 팀원 | `#ROS` `#Unity` |
| 이영종 | 팀원 | `#FE` `#AI` |
| 정지환 | 팀원 | `#BE` `#Infra` `#Monitoring` `#AI` |

## 대표 성과

아래 사례는 단순 구현량보다 운영 중 어떤 문제를 얼마나 깊게 파고들었는지를 보여주는 기여입니다.

| 사례 | 문제 | 깊게 들어간 지점 | 결과/검증 | 관련 이슈 |
| --- | --- | --- | --- | --- |
| 백엔드 RESTful API 설계 | 전화 예약, 보호자, 의사, 관리자, 차량 단말, 로봇 관제 흐름이 섞이면서 API 경계와 ID 노출 정책이 복잡해지는 문제 | `/api/v1` 기준 리소스 중심 endpoint, public_id 기반 외부 식별자, 역할별 인증, DTO 응답 계약, 상태 enum, 에러 코드, SSE/내부 API 경계를 문서화 | API 명세와 ERD를 기준으로 FE/BE 연동 계약을 맞추고, 환자 무계정 인테이크와 차량 단말 토큰 흐름까지 동일한 규칙으로 정리 | `S14P21A603-152`, `S14P21A603-153`, `S14P21A603-154`, `S14P21A603-155` |
| 실시간 진료 세션 안정화 | WebRTC 연결 실패, `room_finished` empty timeout 이후 실제 진료 전 세션이 조기 완료되는 문제 | TURN/coturn 포트와 credential, Nginx WebSocket/SSE proxy, LiveKit webhook, consultation session 상태 전이, mission phase 동기화까지 end-to-end로 추적 | relay candidate와 LiveKit/coturn 로그 확인, 의사-환자 smoke test, session `IN_PROGRESS`와 mission `CONSULTING` 정합성 확보 | `S14P21A603-413`, `S14P21A603-441`, `S14P21A603-442`, `S14P21A603-444`, `S14P21A603-528` |
| scale-out 알림/이벤트 신뢰성 개선 | Spring 다중 인스턴스에서 SSE 알림이 특정 인스턴스의 local emitter에 묶이고, Outbox relay가 중복 실행될 수 있는 문제 | Kafka consumer 배치, Redis Pub/Sub fan-out, Redis 분산 락, keyspace notification, Nginx SSE timeout을 함께 정리 | `core-app` 다중 인스턴스 환경에서 예약 알림 수신 확인, Outbox relay 단일 실행 로그 확인 | `S14P21A603-377`, `S14P21A603-413`, `S14P21A603-520`, `S14P21A603-521` |
| CI/CD와 운영 모니터링/품질 게이트 구축 | 배포 후 컨테이너, Kafka, Redis, PostgreSQL, LiveKit 상태를 한 화면에서 확인하기 어렵고, FE/BE 품질 게이트와 Grafana 접근 보호도 필요한 문제 | Jenkins 변경 감지 배포, Node 22 기반 FE quality gate, Docker buildx image push, compose rolling update, Prometheus/Grafana, cAdvisor, exporter, Nginx `auth_request`를 함께 정리 | Jenkins stage 통과, 변경 서비스 단위 배포, Prometheus target `UP`, Grafana `operator-overview` 대시보드, `/grafana/` 미인증 403 검증 | `S14P21A603-520`, `S14P21A603-521`, `S14P21A603-535` |
| 로봇 관제 통신 구조 개선 | polling/Zenoh 중심 구조에서 오프라인 감지, 명령 신뢰성, 최신 telemetry 반영이 불안정한 문제 | Mosquitto MQTT, Spring `robot-gateway`, ROS2 bridge, Last Will, retained message, SSE `updatedAt` ordering, mission cleanup까지 통신 경계를 재정리 | stale telemetry 덮어쓰기 방지, GPS 부재 시 pose fallback, 데모 시작 시 이전 활성 미션 정리 | `S14P21A603-509`, `S14P21A603-510`, `S14P21A603-519`, `S14P21A603-526` |
| Unity Camera 운영 장애 복구 | MediaMTX WebRTC 세션은 생성되지만 mixed content, ICE timeout, 보라색/검은 화면으로 브라우저 재생이 실패하는 문제 | edge/frontend Nginx forwarded header, `/unity_cam/` proxy, MediaMTX TCP fallback, H264 pixel format/profile, ROS camera topic fallback을 단계별로 분리 | 공개 HTTPS 경로 고정, `8189/tcp` fallback 추가, `yuv420p`/B-frame 비활성화, inner camera 미수신 시 front camera fallback | `S14P21A603-550`, `S14P21A603-551`, `S14P21A603-552`, `S14P21A603-553` |
| AI 본인확인/IDV 서버 구현 및 연동 안정화 | 차량 단말에서 촬영한 얼굴/신분증 이미지의 OCR·얼굴 대조 결과를 진료 준비 단계와 안정적으로 연결해야 하는 문제 | FastAPI GPU AI-IDV 서버, SCRFD 얼굴 검출, AdaFace 임베딩 비교, PaddleOCR 결과 구조화(name/rrn/address/confidence), Spring Boot -> GPU AI 서버 multipart 계약, OCR 필드 기반 재검증, AdaFace ONNX FP16/INT8 양자화·비교 스크립트를 정리 | 얼굴 검출 실패와 OCR 일치 케이스를 분리하고, OCR 필드 기반 false negative를 완화했으며 본인확인 성공 캐시, 환자 토큰 발급 흐름, 양자화 모델 적용/검증 경로를 문서화 | `S14P21A603-451`, `S14P21A603-480`, `S14P21A603-517`, `S14P21A603-518` |

## 개인 기여 요약

제가(정지환) 맡은 영역은 백엔드 RESTful API 설계, 백엔드 기반 구축, 실시간 진료 세션 안정화, Kafka/Outbox 기반 이벤트 처리, 로봇 관제/MQTT 전환, Jenkins 기반 CI/CD와 운영 인프라 품질 게이트, Prometheus/Grafana 기반 모니터링, AI-IDV 본인확인 서버 구현/연동까지 이어집니다.

백엔드 API 영역에서는 `/api/v1` 기준으로 인증, 환자 식별, 인테이크, 예약, 케이스, 미션, 화상진료 세션, 보호자, 관리자, 로봇 운영 API를 리소스 중심으로 설계했습니다. DB 내부 PK는 외부로 노출하지 않고 `public_id`를 API 계약으로 사용하도록 정리했으며, 환자 무계정 인테이크 세션, 차량 단말 토큰, 관리자/의사/보호자 권한 경계를 분리했습니다. API 명세에는 요청/응답 DTO, 상태 enum, 에러 코드, SSE 스트림, 내부 서비스 API까지 함께 정리해 FE/BE 연동 기준으로 사용했습니다.

CI/CD 영역에서는 Jenkins가 변경 경로를 감지해 FE, FE-phone, BE, infra, monitoring stack의 quality gate와 배포 대상을 나누도록 정리했습니다. FE 빌드는 Node 22 컨테이너 기준으로 고정하고, Docker buildx image push 후 compose가 변경된 서비스만 교체하도록 구성했습니다.

모니터링 영역에서는 운영 compose에 Prometheus, Grafana, cAdvisor, PostgreSQL/Redis/Kafka exporter를 붙이고, Spring Actuator 기반 커스텀 메트릭을 `operator-overview` 대시보드에서 확인할 수 있도록 정리했습니다. Grafana는 외부에 그대로 열지 않고 관리자 세션에서 발급한 `monitoring_access` 쿠키와 Nginx `auth_request`로 접근을 제한하는 구조로 잡았습니다.

특히 AI 영역에서는 IDV/OCR 본인확인 서버를 애플리케이션 내부에 섞지 않고 FastAPI 기반 GPU 서버로 분리했습니다. SCRFD로 얼굴을 검출하고, AdaFace로 얼굴 임베딩을 비교하며, PaddleOCR로 신분증 OCR을 수행하는 파이프라인을 구성했습니다. PaddleOCR 원시 결과는 name/rrn/address/confidence로 구조화하고 OCR 필드 기반 재검증을 Spring Boot 도메인 규칙과 연결해 신분증 본인확인 false negative를 완화했습니다. 또한 AdaFace checkpoint를 ONNX로 변환하고 FP16/INT8 양자화 및 FP32 대비 임베딩 유사도/latency 비교 스크립트를 정리했습니다. Spring Boot는 차량 단말에서 촬영한 얼굴 이미지와 신분증 이미지를 multipart로 전달하고, AI 응답의 OCR 이름·생년월일·주소 일치 여부와 얼굴 대조 결과를 서버 도메인 규칙으로 재검증합니다. AI timeout, 호출 실패, 얼굴 검출 실패, OCR 일부 일치 케이스는 진료 플로우를 막지 않도록 manual review 또는 완화 조건으로 분리했습니다.

단순 기능 구현보다 장애 재현, 원인 분리, 운영 검증까지 남기는 방식으로 기여를 관리했습니다.

## Jira 기준 기여 요약

아래 수치는 대표 사례를 뒷받침하는 보조 지표입니다. Jira JQL 기준으로 조회 가능한 전체 프로젝트 이슈 432건 중 제가(정지환) 담당한 이슈는 201건, 제가(정지환) 생성/보고한 이슈는 219건입니다. 담당 기준으로는 전체의 46.5%, 문제 정의와 기록까지 포함하면 50.7%를 차지합니다.

| 지표 | 수치 | 해석 |
| --- | ---: | --- |
| 전체 프로젝트 이슈 | 432건 | 프로젝트 전체에서 조회 가능한 Jira 이슈 |
| 제가(정지환) 담당한 이슈 | 201건 | assignee 기준 실제 수행/소유 이슈 |
| 제가(정지환) 생성/보고한 이슈 | 219건 | creator/reporter 기준 문제 정의와 기록 이슈 |
| 트러블슈팅/버그 이슈 | 21건 | 운영 장애, 품질 게이트, 실시간 통신, 카메라/로봇 상태 문제를 원인-조치-검증으로 닫은 이슈 |

아래는 제가(정지환) 생성하거나 작성한 Jira 이슈 중 프로젝트 설명에 필요한 대표 이슈와 범위입니다.

| 영역 | 대표 Jira 이슈 |
| --- | --- |
| Backend RESTful API 설계 | `S14P21A603-152`, `S14P21A603-153`, `S14P21A603-154`, `S14P21A603-155` |
| 인프라/배포/품질 게이트 | `S14P21A603-413`, `S14P21A603-520`, `S14P21A603-521`, `S14P21A603-535` |
| CI/CD | `S14P21A603-520`, `S14P21A603-521` |
| 모니터링/운영 가시성 | `S14P21A603-520`, `S14P21A603-521`, `S14P21A603-535` |
| Kafka/Outbox/배차 | `S14P21A603-353`, `S14P21A603-354`, `S14P21A603-355`~`S14P21A603-366`, `S14P21A603-377` |
| LiveKit/진료 세션 | `S14P21A603-441`, `S14P21A603-442`, `S14P21A603-444`, `S14P21A603-528` |
| 로봇/MQTT/관제 | `S14P21A603-509`, `S14P21A603-510`, `S14P21A603-519`, `S14P21A603-526`, `S14P21A603-550`, `S14P21A603-551`, `S14P21A603-552`, `S14P21A603-553` |
| 본인확인/보안/시드 | `S14P21A603-451`, `S14P21A603-480`, `S14P21A603-517`, `S14P21A603-518`, `S14P21A603-535` |
| EMR/환자/Phone UX | `S14P21A603-515`, `S14P21A603-516`, `S14P21A603-528` |

## 시스템 구성

| 영역 | 주요 역할 |
| --- | --- |
| `src/FE` | 의사, 관리자, 보호자, 관제 웹 UI |
| `src/FE-phone` | 전화 예약 시뮬레이터 UI |
| `src/AI-IDV` | FastAPI 기반 본인확인 GPU 서버, SCRFD/AdaFace/PaddleOCR 추론, AdaFace ONNX export/FP16·INT8 양자화 |
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
