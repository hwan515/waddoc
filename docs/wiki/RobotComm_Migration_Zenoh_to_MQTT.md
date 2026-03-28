# 차량 통신 아키텍처 개선안: Zenoh → MQTT 전환 당위성

> 작성일: 2026-03-27
> 대상: 차량 관제 통신 레이어 (ros2_docker, zenoh-server)
> 현재 브랜치: dev

---

## 1. 현황 및 문제 정의

### 1.1 현재 통신 구조

```
[로봇 PC]                        [EC2]                        [클라이언트]
ROS2 노드
 ├─ /odom                    zenoh_bridge_ec2              FE (어느 PC든)
 ├─ /state         Zenoh      (TCP :8081)    ros2_app_ec2    │
 ├─ /ec2_state/  ──────────→  DDS 중계   →  FastAPI      ←──┤ GET /api/minimap
 │   minimap_route             로컬통신                       │ (100ms 폴링)
 └─ zenoh_bridge              ROS2 노드      /api/odom        │
     └─ tcp/zenoh.                           /api/minimap  ←──┘
        waddoc.site:8081                     /api/cmd/*
                                             /api/cmd/estop
                                             /api/cmd/waypoint
```

### 1.2 확인된 문제점

#### 문제 1. 프론트엔드 100ms 폴링

프론트엔드가 차량 위치·경로를 갱신하기 위해 FastAPI 엔드포인트를 **100ms마다 반복 호출**하고 있다.

```
초당 10회 HTTP 요청 × 운영자 수 = 불필요한 서버 부하
```

- 변경이 없어도 매 100ms 응답을 생성·전송
- 운영자 화면 수가 늘어날수록 선형으로 부하 증가
- HTTP 요청-응답 오버헤드로 인한 불필요한 레이턴시

#### 문제 2. 명령 전달 신뢰성 없음

현재 웨이포인트 명령(`POST /api/cmd/waypoint/{n}`)은 HTTP fire-and-forget 방식이다.

- 네트워크 순단 시 명령 유실 여부 확인 불가
- 차량이 명령을 수신했는지 보장하는 메커니즘 없음
- 재전송 로직 부재

#### 문제 3. 차량 오프라인 감지 불가

차량이 네트워크에서 끊겼는지 실시간으로 감지하는 수단이 없다.

- Zenoh 연결 상태를 애플리케이션 레벨에서 별도 모니터링 필요
- 차량 이상 상태를 관제 화면에 즉시 반영하기 어려움

#### 문제 4. API 진입점 이중화

차량 제어 API가 두 곳에 분산되어 있다.

| 위치 | 엔드포인트 | 역할 |
|---|---|---|
| FastAPI (zenoh-server) | `POST /api/cmd/estop`, `/api/cmd/waypoint` | 차량 직접 제어 |
| Spring Boot | `POST /api/v1/missions/{id}/telemetry` | 텔레메트리 DB 저장 |

- 인증/권한 체계가 FastAPI에는 적용되어 있지 않음
- Spring Boot의 미션 상태와 FastAPI 차량 명령 간 정합성 관리 어려움

#### 문제 5. Zenoh 의존성의 확장성 한계

Zenoh는 ROS2 생태계 내부 통신에 특화된 프로토콜이다.

- 차량이 복수가 되면 `ROS_DOMAIN_ID` 또는 Zenoh 세션 분리 필요
- 클라우드 서비스(AWS IoT, Azure IoT Hub 등)와의 통합 불가
- 운영 모니터링 도구 생태계 부재

---

## 2. 개선안: MQTT 기반 아키텍처

### 2.1 MQTT가 차량 관제에 적합한 이유

MQTT(Message Queuing Telemetry Transport)는 IoT·차량 텔레매틱스 분야의 **산업 표준 프로토콜**이다.

- Tesla, 현대자동차 커넥티드카 플랫폼
- AWS IoT Core, Azure IoT Hub, Google Cloud IoT
- 모든 주요 차량 원격 관제 솔루션

### 2.2 현재 문제점 해소 방안

| 문제 | 현재 | MQTT 적용 후 |
|---|---|---|
| 100ms 폴링 | FE가 주기적 HTTP 요청 | 브로커 → Spring Boot SSE → FE **푸시** |
| 명령 전달 보장 | HTTP fire-and-forget | **QoS 1**: 차량이 수신 확인할 때까지 재전송 |
| 오프라인 감지 | 없음 | **Last Will Testament**: 연결 끊기면 브로커가 자동 알림 발행 |
| API 이중화 | FastAPI + Spring Boot | Spring Boot 단일 진입점으로 통합 |
| 확장성 | Domain ID 충돌 | `robot/{vehicle_id}/odom` 토픽 네임스페이스로 N대 지원 |

### 2.3 목표 아키텍처

```
[로봇 PC]                    [EC2]                      [클라이언트]
ROS2 (내부 통신 유지)
 └─ MQTT bridge 노드      Mosquitto/EMQX              FE (어느 PC든)
     (paho-mqtt)           MQTT Broker                 │
     publish:              (:1883)         Spring Boot  │ SSE 구독
       robot/odom      ──────────────→    MQTT sub   ──→│ /api/v1/robots/stream
       robot/state                         ↓            │
       robot/minimap_route              SSE broadcast    │
     subscribe:                            ↓            │ REST (변경 없음)
       robot/cmd/estop  ←──────────────  MQTT pub    ←──┤ POST /api/v1/cmd/waypoint
       robot/cmd/waypoint               (Spring Boot)   │ POST /api/v1/cmd/estop
```

**핵심 변화:**
- 차량 → 브로커: MQTT publish (변경 사항 발생 시 또는 1Hz 주기)
- 브로커 → Spring Boot: MQTT subscribe (서버 사이드, 상시 연결)
- Spring Boot → FE: 기존 SSE 인프라 재사용 (`DoctorNotificationSseService` 패턴)
- FE → Spring Boot: REST API 동일 (변경 없음)

---

## 3. MQTT 핵심 기능 활용

### 3.1 Last Will Testament (LWT) — 차량 오프라인 자동 감지

MQTT 연결 수립 시 브로커에 "유언" 메시지를 등록한다. 차량이 비정상 종료되면 브로커가 자동으로 해당 토픽에 메시지를 발행한다.

```python
# 로봇 MQTT bridge 초기화 시
mqtt_client.will_set(
    topic="robot/status",
    payload='{"online": false}',
    qos=1,
    retain=True
)
```

Spring Boot가 `robot/status`를 구독하면 차량 오프라인을 즉시 감지하고 관제 화면에 알림 가능.

### 3.2 QoS 1 — 웨이포인트 명령 전달 보장

```python
# QoS 0: fire-and-forget (현재 HTTP와 동일)
mqtt.publish("robot/cmd/waypoint", "3", qos=0)

# QoS 1: 차량이 PUBACK 응답할 때까지 브로커가 재전송 보장
mqtt.publish("robot/cmd/waypoint", "3", qos=1)
```

네트워크 순단 후 재연결 시에도 명령이 안전하게 전달된다.

### 3.3 Retained Message — 마지막 상태 즉시 조회

```python
# retain=True: 브로커가 마지막 메시지를 보관
mqtt.publish("robot/status", '{"online": true, "battery": 87}', retain=True)
```

새로운 구독자(FE 새 탭 열기, 서버 재시작 등)가 연결하면 즉시 최신 차량 상태를 수신한다. 별도의 초기 상태 조회 API 불필요.

---

## 4. 변경 범위

### 4.1 제거 대상

| 컴포넌트 | 파일 | 비고 |
|---|---|---|
| Zenoh bridge (로봇) | `src/ros2_docker/docker-compose.yml` zenoh_bridge 서비스 | 제거 |
| Zenoh server (EC2) | `src/zenoh-server/` 전체 | 제거 |
| FastAPI router app | `src/zenoh-server/router_app/router_app.py` | 제거 |
| EC2 zenoh 브릿지 | `infra/docker-compose.prod.yml` zenoh_bridge_ec2, ros2_app_ec2 | 제거 |

### 4.2 추가 대상

| 컴포넌트 | 위치 | 내용 |
|---|---|---|
| MQTT bridge 노드 | `src/ros2_docker/workspace/src/my_ros2_basics/mqtt_bridge.py` | ROS2 ↔ MQTT 변환 |
| MQTT broker | `infra/docker-compose.prod.yml` | Mosquitto 또는 EMQX 컨테이너 |
| Spring Boot MQTT 연동 | `src/BE/` | `spring-integration-mqtt` 의존성 추가 |
| Robot SSE 엔드포인트 | `src/BE/` | `/api/v1/robots/stream` (기존 SSE 패턴 재사용) |

### 4.3 변경 없는 대상

| 컴포넌트 | 이유 |
|---|---|
| FE REST API 호출 | Spring Boot 엔드포인트 동일 유지 |
| ROS2 내부 노드 | 로봇 내부 통신은 DDS 유지 |
| Unity ROS-TCP-Endpoint | 시뮬레이션 내부 통신 유지 |
| Spring Boot 인증/권한 | 변경 없음 |
| LiveKit, AI-IDV | 관련 없음 |

---

## 5. 기대 효과

### 정량적

| 항목 | 현재 | 개선 후 |
|---|---|---|
| 관제 화면 1개당 HTTP 요청 | 10회/초 | 0회/초 (SSE 상시 연결 1개) |
| 명령 전달 보장 | 없음 | QoS 1 (브로커 재전송) |
| 차량 오프라인 감지 시간 | 알 수 없음 | MQTT Keep-Alive 설정값 (기본 60초, 조정 가능) |

### 정성적

- **운영 단순화**: Zenoh 라우터, FastAPI 서버 제거 → 관리 포인트 감소
- **보안 통합**: 모든 차량 명령이 Spring Boot를 통과 → JWT 인증/권한 일원화
- **확장성 확보**: 차량 ID 기반 토픽 네임스페이스(`robot/{id}/*`)로 다차량 지원 구조
- **표준 준수**: 차량 관제 산업 표준 프로토콜 채택으로 향후 외부 시스템 연동 용이

---

## 6. 마이그레이션 리스크 및 대응

| 리스크 | 대응 |
|---|---|
| MQTT 브로커 단일 장애점 | Mosquitto HA 구성 또는 EMQX 클러스터 (현재 단일 차량이므로 단일 브로커로 충분) |
| 기존 Zenoh 동작 검증 필요 | 병행 운영 기간 동안 두 경로 모두 유지 후 전환 |
| Spring Boot MQTT 연동 러닝커브 | `spring-integration-mqtt` 공식 문서 기반, 기존 Kafka consumer 패턴과 유사 |

---

## 7. 결론

현재 Zenoh 기반 구조는 ROS2 개발 단계에서 빠르게 구성한 솔루션으로, 시뮬레이션 검증 목적으로는 충분하다. 그러나 실차 운영 및 서비스 안정성 관점에서 다음 이유로 MQTT 전환이 필요하다.

1. **100ms 폴링 제거** — 실시간 관제의 근본적 구조 문제 해소
2. **명령 전달 신뢰성** — 비상정지·웨이포인트 명령의 QoS 보장
3. **차량 상태 가시성** — 오프라인 감지 및 최신 상태 즉시 제공
4. **단일 제어 진입점** — Spring Boot 중심 인증·권한 통합
5. **산업 표준 채택** — 실차 전환 및 다차량 확장 시 구조 변경 최소화
