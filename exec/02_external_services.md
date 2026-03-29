# 외부 서비스 및 연동 문서

## 1. 확인 방법

- `저장소 내부에서 직접 기동`: 이 저장소의 Docker Compose로 띄우는 서비스
- `메인 배포 외부 의존성`: 메인 Compose 밖에서 별도로 떠 있어야 하는 서비스
- `저장소 내 별도 하위 프로젝트`: 저장소에는 있지만 `infra/docker-compose*.yml`과 직접 연결되지 않은 서비스

## 2. 전체 연동 목록

| 서비스 | 배치 형태 | 목적 | 사용 방식 | 필요한 설정 / 비고 |
| --- | --- | --- | --- | --- |
| Nginx | 저장소 내부에서 직접 기동 | TLS 종료, 리버스 프록시, SPA 라우팅 | `/api`, `/phone`, `/livekit`, `/mqtt`, `/grafana`, SSE 경로 프록시 | 운영에서는 `CERTS_DIR`에 인증서 필요, 커밋된 prod 설정은 `waddoc.site` / `www.waddoc.site` 하드코딩 |
| PostgreSQL | 저장소 내부에서 직접 기동 | 메인 관계형 DB | Spring Boot JPA + Flyway 사용 | `POSTGRES_DB`, `POSTGRES_USER`, `DB_PASSWORD` 필요 |
| Redis | 저장소 내부에서 직접 기동 | 캐시, 분산락, pub/sub | 알림 팬아웃, 분산락, 세션/만료 이벤트 | 운영에서는 비밀번호 사용 |
| Kafka | 저장소 내부에서 직접 기동 | 비동기 이벤트 처리 | dispatch, SMS, doctor notification, telemetry 토픽 처리 | Zookeeper 및 bootstrap 설정 필요 |
| Zookeeper | 저장소 내부에서 직접 기동 | Kafka coordination | Confluent Kafka 이미지 의존성 | Compose에서 함께 기동 |
| Mosquitto MQTT 브로커 | 저장소 내부에서 직접 기동 | 차량 명령/텔레메트리 전송 | 백엔드 MQTT publish/subscribe, ROS bridge 연결 | 운영에서는 내부 `9001`, Nginx가 `/mqtt`로 외부 노출 |
| LiveKit | 저장소 내부에서 직접 기동 | 화상진료 WebRTC 룸/토큰 서비스 | 백엔드가 토큰 발급, 프론트가 룸 참가, webhook은 Spring으로 복귀 | API key/secret, URL, webhook 설정 필요 |
| coturn | 저장소 내부에서 직접 기동 | WebRTC NAT traversal 릴레이 | LiveKit 운영 환경 보조 | `TURN_SECRET`, `LIVEKIT_NODE_IP` 필요 |
| Prometheus | 저장소 내부에서 직접 기동 | 메트릭 수집 | Spring, LiveKit, exporters, cAdvisor scrape | 모니터링 Compose로 기동 |
| Grafana | 저장소 내부에서 직접 기동 | 대시보드 시각화 | 운영자 UI에서 iframe embedding | 관리자 계정 및 Prometheus datasource 필요 |
| cAdvisor | 저장소 내부에서 직접 기동 | 컨테이너 메트릭 | Prometheus scrape 대상 | privileged host mount 필요 |
| Postgres exporter | 저장소 내부에서 직접 기동 | Postgres 메트릭 수집 | Prometheus scrape 대상 | DB 연결 env 필요 |
| Redis exporter | 저장소 내부에서 직접 기동 | Redis 메트릭 수집 | Prometheus scrape 대상 | 운영 Redis 비밀번호 필요 |
| Kafka exporter | 저장소 내부에서 직접 기동 | Kafka 메트릭 수집 | Prometheus scrape 대상 | Kafka bootstrap 필요 |
| Solapi SMS | 메인 배포 외부 의존성 | SMS 발송 | `SolapiSmsService`를 통해 예약/배차 관련 문자 전송 | `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_NUMBER` 필요 |
| AI-IDV FastAPI 서버 | 메인 배포 외부 의존성, 단 저장소 내 구현체 존재 | 얼굴/OCR 기반 본인확인 | Spring이 multipart로 `/idv/api/v1/verify` 호출 | 메인 스택은 `AI_IDV_URL` 필요, 구현체는 `src/AI-IDV`에 존재 |
| Upstage API | 저장소 내 별도 하위 프로젝트 외부 의존성 | 음성 트리아지용 진료과 추천 | `src/AI/ai-server`가 chat completions 호출 | `UPSTAGE_API_KEY` 필요 |
| MediaMTX | ROS Compose 내부 서비스 | 카메라 스트림 릴레이 | ROS compose에서 기동, `camera_streamer.py`가 `unity_cam` RTSP 송출 | 메인 `infra` Compose에는 포함되지 않음 |
| Unity ROS TCP 브리지 | 저장소 내 로보틱스 연동 | Unity와 ROS 2 연결 | Unity `ROS-TCP-Connector`, ROS 쪽 `ROS-TCP-Endpoint` 사용 | 양쪽 IP/Port 일치 필요 |
| GitLab 접근 | CI/CD 외부 의존성 | Jenkins 소스 checkout | `gitlab-deploy-token` 사용 | Jenkins credential 필요 |
| DockerHub | CI/CD 외부 의존성 | 컨테이너 이미지 저장소 | Jenkins가 이미지 push/pull | Jenkins credential 필요 |

## 3. 메시징 및 실시간 통신

### 3.1 Kafka

`src/BE/src/main/java/com/waddoc/global/config/KafkaTopics.java`에서 확인된 토픽:

- `dispatch.requests`
- `dispatch.retry`
- `sms.requests`
- `sms.requests.DLT`
- `doctor.notifications`
- `mission.telemetry`

사용 방식:

- 예약 생성 시 `DispatchOutbox`가 적재되고, relay가 이를 Kafka로 보낸다.
- SMS 발송 요청은 Kafka를 통해 비동기 처리된다.
- 의사 알림은 Kafka로 publish된 뒤 Redis/SSE로 fan-out 된다.
- 미션 telemetry는 REST로 받되, 실제 반영은 Kafka 소비로 비동기 처리된다.

필요 설정:

- Kafka + Zookeeper 가동
- Spring의 `KAFKA_BOOTSTRAP_SERVERS`
- Compose에서는 topic auto-create를 꺼 두지만, Spring이 startup 시 topic을 생성하도록 구현되어 있다.

### 3.2 MQTT

`src/BE/src/main/java/com/waddoc/domain/robot/config/MqttTopics.java`에서 확인된 토픽:

- 차량 텔레메트리 inbound:
  - `robot/odom`
  - `robot/minimap`
  - `robot/state`
  - `robot/status`
- 차량 명령 outbound:
  - `robot/cmd/estop`
  - `robot/cmd/waypoint`
  - `robot/cmd/dispatch`

사용 방식:

- Spring이 차량 텔레메트리를 구독하고 운영자 UI에 SSE로 재전달한다.
- Spring이 waypoint, dispatch, e-stop 명령을 MQTT로 publish 한다.
- ROS `mqtt_bridge.py`는 명령 토픽을 구독하고, 텔레메트리 토픽을 publish 한다.

필요 설정:

- 메인 운영 배포: Mosquitto WebSocket `9001`, 외부에서는 `/mqtt`
- ROS 쪽 환경변수:
  - `MQTT_BROKER_HOST`
  - `MQTT_BROKER_PORT`
  - `MQTT_WS_PATH`
  - `VEHICLE_ID`

### 3.3 SSE

확인된 SSE endpoint:

- `/api/v1/doctors/me/notifications/stream`
- `/api/v1/robots/stream`

목적:

- 의사 EMR에 신규 예약/상태 알림 전달
- 운영자 UI에 차량 상태/미니맵/스냅샷 전달

Nginx는 두 endpoint 모두에 대해 buffering을 끄도록 명시돼 있다.

### 3.4 LiveKit + TURN

목적:

- 의사와 환자/로봇 단말 간 화상진료 세션

사용 방식:

- 백엔드가 consultation session을 만들거나 재사용
- doctor/patient token 발급
- Doctor FE와 Robot FE가 같은 LiveKit room 참가
- LiveKit webhook이 Spring `/api/v1/sessions/webhook/livekit`로 들어옴

필요 설정:

- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `LIVEKIT_URL`
- `LIVEKIT_WEBHOOK_URL`
- `TURN_SECRET`
- `LIVEKIT_NODE_IP`

## 4. 외부 API 연동

### 4.1 AI-IDV API

확인된 계약:

- `GET /idv/api/v1/health`
- `POST /idv/api/v1/verify`

메인 백엔드 사용 방식:

- `MissionIdentityCheckService`가 `FILE_STORAGE_ROOT` 아래 환자 기준 이미지를 읽음
- `ConsultationIdentityVerificationClient`가 `AI_IDV_URL`로 multipart 요청 전송
- GPU 서버 응답을 환자 정보와 대조하고, 성공 결과를 미션 캐시에 저장

필요 설정:

- 접근 가능한 AI-IDV 서버
- 저장소 구현체를 직접 띄우는 경우 GPU 런타임과 모델 파일 준비 필요

### 4.2 Solapi SMS API

목적:

- 예약 생성 안내 문자
- 배차 지연/복구 안내 문자

사용 방식:

- 예약 생성 후 after-commit 시점에 SMS 요청 발행
- dispatch retry/assignment 시 Kafka를 통해 SMS 발행
- `SmsConsumer`가 최종적으로 `SmsService`를 호출
- 설정에 따라 실제 구현은 `SolapiSmsService`가 됨

필요 설정:

- `SOLAPI_API_KEY`
- `SOLAPI_API_SECRET`
- `SOLAPI_SENDER_NUMBER`

### 4.3 Upstage API

범위:

- 메인 운영 스택이 아니라 `src/AI` 하위 프로젝트에서만 사용됨

목적:

- 음성 증상 텍스트를 기반으로 추천 진료과 산출

사용 방식:

- `src/AI/ai-server/app/services/upstage_client.py`가 Upstage chat completions 호출

필요 설정:

- `UPSTAGE_API_KEY`
- `UPSTAGE_BASE_URL`
- `UPSTAGE_MODEL`

## 5. 로보틱스/시뮬레이션 연동

### 5.1 Unity <-> ROS 2 브리지

확인된 구성 요소:

- Unity 패키지 `com.unity.robotics.ros-tcp-connector`
- ROS 워크스페이스 패키지 `ROS-TCP-Endpoint`
- Unity 스크립트 `Assets/Scripts/RosTcpEndpointBootstrap.cs`

목적:

- Unity 시뮬레이터와 ROS graph 연결

필요 설정:

- Unity와 ROS endpoint 서버 간 IP/Port 일치
- ROS 쪽 endpoint는 `custom_entrypoint.sh`가 `ros2 run ros_tcp_endpoint default_server_endpoint`로 실행

### 5.2 ROS <-> MQTT 브리지

확인된 구현:

- `src/ros2_docker/workspace/src/my_ros2_basics/my_ros2_basics/mqtt_bridge.py`

목적:

- ROS 토픽을 MQTT 텔레메트리로 변환
- MQTT 명령을 ROS 명령으로 변환

필요 설정:

- secure WebSocket으로 접근 가능한 MQTT 브로커
- 올바른 `VEHICLE_ID`

### 5.3 Unity 카메라 스트림 경로

확인된 흐름:

- ROS `camera_streamer.py`는 `rtsp://127.0.0.1:8554/unity_cam`으로 송출
- 웹 프론트 `MapMonitoring`은 `/unity_cam/` iframe 사용
- `src/FE/nginx.conf`는 이를 `http://100.85.200.7:8889/unity_cam/`으로 프록시

해석:

- 코드 근거 기반 추정(Assumption based on code): RTSP/MediaMTX 결과를 HTTP로 노출하는 별도 브리지 또는 게이트웨이가 메인 Compose 바깥에 존재해야 한다.

## 6. 모니터링 서비스

`infra/monitoring/prometheus/prometheus.yml`에서 확인된 scrape 대상:

- `spring-api:8080/actuator/prometheus`
- `cadvisor:8080`
- `postgres-exporter:9187`
- `redis-exporter:9121`
- `kafka-exporter:9308`
- `livekit:6789`

Grafana 사용 방식:

- provisioning으로 `Waddoc` 폴더의 dashboard 자동 등록
- 운영자 UI `SystemMonitoring.jsx`에서 iframe embedding
- 백엔드 `/api/v1/admin/monitoring/session`이 모니터링용 쿠키 발급
- Nginx `auth_request`가 이를 검증

## 7. 클라우드 / 호스팅 관련 메모

- 애플리케이션 코드 안에서 AWS SDK를 직접 쓰는 흔적은 찾지 못했다.
- 다만 Jenkinsfile 주석과 경로를 보면 Ubuntu 기반 배포 서버, 즉 EC2 계열 호스트를 전제로 한 운영 흐름이 나타난다.
- AI-IDV는 별도 GPU 서버를 외부 의존성으로 둔 구조이다.

