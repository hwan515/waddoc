# ROS2–Unity 기반 차량 시뮬레이션 관제 시스템 — MVP 요구사항 계획서 v1

> Status: Archived
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Current canonical documents: [../Architecture.md](../Architecture.md), [../Infrastructure_Setup.md](../Infrastructure_Setup.md)
>
> Note: 이 문서는 ROS2/Unity 관제 초기 요구사항입니다. 최신 MQTT, SSE, MediaMTX 운영 기준은 Canonical 문서를 확인합니다.

## 1. 문서 목적

본 문서는 Unity 기반 차량 시뮬레이션 환경에서 생성되는 로봇 상태와 차량 시점 카메라 이미지를 웹 관제 시스템으로 전달하기 위한 **MVP 아키텍처, 데이터 흐름, 기능 요구사항**을 정의한다.

문서 목표

* ROS2 기반 상태 데이터 전달 구조 정의
* MQTT 기반 ROS2 데이터 브리지 구조 정리
* Unity 시뮬레이션 데이터와 웹 관제 시스템 연결 구조 정의
* 웹 관제 화면에서 필요한 최소 데이터 정의

# 2. 시스템 개요

아래 내용은 Unity 시뮬레이션 환경에서 생성되는 차량 상태 데이터를 ROS2 토픽으로 publish하고, 이를 MQTT를 통해 서버로 전달하는 MVP 구조를 설명한다.

MVP 구조는 **상태 데이터 경로는 MQTT, 카메라 이미지 경로는 Tailscale**로 분리한다.

핵심 흐름

```
Unity Simulation
   ├─ 차량 상태 생성
   ├─ 차량 pose publish
   └─ 차량 카메라 이미지 publish

ROS2
   ├─ 상태 토픽 → MQTT Bridge → Backend → SSE → Web Dashboard
   └─ 카메라 이미지 → Tailscale VPN → Web Dashboard
```

Unity 화면 자체는 웹으로 스트리밍하지 않는다.
웹에서는 **pose 기반 지도 표시 + 차량 시점 카메라 이미지**만 표시한다.

# 3. MVP 목표

## 3.1 시스템 목표

* Unity 시뮬레이션에서 생성되는 차량 상태를 ROS2 토픽으로 publish할 수 있어야 한다.
* ROS2 상태를 MQTT를 통해 서버로 전달할 수 있어야 한다.
* 차량 카메라 이미지를 Tailscale VPN을 통해 웹에서 접근할 수 있어야 한다.
* 웹 관제 화면에서 차량 위치와 상태를 확인할 수 있어야 한다.
* 웹에서 차량 시점 카메라 이미지를 확인할 수 있어야 한다.

## 3.2 제품 목표

* ROS2 기반 상태 데이터 전달
* MQTT 기반 ROS 메시지 브리지
* SSE 기반 상태 전달
* 웹 기반 관제 UI
* Tailscale 기반 차량 시점 카메라 이미지 표시

## 3.3 데모 성공 기준

1. Unity 차량 이동이 웹 지도에 반영된다.
2. 미션 상태 변경이 웹 UI에 표시된다.
3. 웹에서 차량 시점 카메라 이미지를 확인할 수 있다.
4. ROS2 상태와 카메라 이미지가 동시에 전달된다.
5. 네트워크 일시 단절 후 상태 동기화가 가능하다.

# 4. 프로젝트 범위

## 4.1 MVP 포함 범위

* Unity 차량 시뮬레이션
* ROS2 상태 토픽 publish
* ROS2 카메라 이미지 토픽 publish
* MQTT 기반 ROS2 상태 데이터 전달
* Tailscale 기반 카메라 이미지 전달
* Backend 상태 수신
* SSE 기반 웹 상태 전달
* 웹 관제 UI

## 4.2 MVP 제외 범위

* 실제 차량 센서 연동
* Unity 화면 스트리밍
* 영상 녹화 기능
* 다중 차량 관제
* 고해상도 영상 저장
* 실시간 지도 편집
* 자율주행 알고리즘 고도화

# 5. 주요 구성 요소

## 5.1 Unity Simulation

Unity는 차량 시뮬레이션 환경이다.

기능

* 차량 위치 계산
* 차량 상태 생성
* 차량 카메라 이미지 생성
* ROS2 메시지 publish

Unity는 ROS-TCP Connector를 통해 ROS2와 연결된다.

## 5.2 ROS2

ROS2는 시스템 메시지 버스 역할을 수행한다.

ROS2 노드는 다음 데이터를 생성한다.

* 차량 위치
* 차량 상태
* 미션 상태
* 카메라 이미지

ROS2는 publish/subscribe 기반 메시지 구조를 사용한다.

## 5.3 MQTT Bridge

MQTT는 ROS2 상태 메시지를 Backend 서버로 전달하는 브리지 역할을 한다.

기능

* ROS2 토픽 구독
* MQTT 토픽으로 메시지 변환 및 전달
* 네트워크 경량화

## 5.4 Tailscale VPN (카메라 이미지)

차량 카메라 이미지는 Tailscale VPN을 통해 웹 클라이언트에서 직접 접근한다.

기능

* 차량 카메라 스트림을 Tailscale 네트워크로 노출
* 웹 클라이언트가 Tailscale IP를 통해 카메라 이미지에 접근

## 5.5 Backend

Backend는 ROS2 상태를 수신하고 웹에 전달한다.

기능

* MQTT 메시지 수신
* 상태 데이터 캐싱
* SSE 이벤트 발행
* 사용자 인증 관리

## 5.6 Web Dashboard

웹 대시보드는 운영 관제 UI이다.

기능

* 차량 위치 표시
* 차량 상태 표시
* 미션 상태 표시
* 차량 카메라 이미지 표시 (Tailscale 경유)

# 6. MVP 핵심 시나리오

## 6.1 차량 상태 전달 시나리오

1. Unity에서 차량 이동이 발생한다.
2. Unity는 ROS2 `/vehicle_pose` 토픽을 publish한다.
3. ROS2 메시지가 MQTT bridge로 전달된다.
4. Backend가 MQTT 메시지를 수신한다.
5. Backend는 SSE 이벤트를 발행한다.
6. 웹 관제 화면이 차량 위치를 갱신한다.

## 6.2 차량 카메라 전달 시나리오

1. Unity 차량 카메라가 장면을 렌더링한다.
2. Unity는 카메라 프레임을 ROS2 이미지 토픽으로 publish한다.
3. 카메라 스트림이 Tailscale VPN을 통해 노출된다.
4. 웹 클라이언트가 Tailscale IP로 카메라 이미지에 직접 접근한다.
5. 웹은 프레임을 연속 갱신하여 영상처럼 표시한다.

# 7. 기능 요구사항

## 7.1 ROS2 상태 토픽

ROS2는 다음 토픽을 publish해야 한다.

| 토픽                | 설명     |
| -- |  |
| `/vehicle_pose`   | 차량 위치  |
| `/vehicle_state`  | 차량 상태  |
| `/mission_phase`  | 미션 단계  |
| `/vehicle_health` | 시스템 상태 |

## 7.2 카메라 이미지 토픽

카메라 이미지는 ROS2 이미지 메시지로 publish한다.

| 토픽                      | 설명        |
| -- |  |
| `/vehicle_camera/front` | 차량 전방 카메라 |

이미지 타입

* `sensor_msgs/Image`
* `sensor_msgs/CompressedImage`

## 7.3 Backend 기능

Backend는 다음 기능을 수행해야 한다.

* MQTT 메시지 수신
* 상태 DTO 변환
* SSE 이벤트 발행

## 7.4 웹 UI 기능

웹은 다음 정보를 표시해야 한다.

* 차량 위치
* 차량 상태
* 미션 상태
* 시스템 상태
* 차량 카메라 이미지 (Tailscale 경유)

웹 지도는 **pose 기반 차량 위치 표시 방식**을 사용한다.

# 8. 데이터 정의

## 8.1 차량 상태 DTO

```
robotId
x
y
z
yaw
speed
missionPhase
robotState
battery
updatedAt
```

## 8.2 이벤트 DTO

```
robotId
eventType
severity
message
timestamp
```

# 9. 통신 프로토콜

| 구간                    | 프로토콜       |
|  |  |
| Unity → ROS2          | ROS-TCP    |
| ROS2 → Backend (상태)  | MQTT       |
| ROS2 → Web (카메라)    | Tailscale  |
| Backend → Web (상태)   | SSE        |

# 10. 비기능 요구사항

## 성능 목표

| 항목      | 목표       |
| - | -- |
| 상태 업데이트 | < 500ms  |
| 이미지 갱신  | 3~10 fps |
| 동시 접속   | 최소 5명    |

## 안정성

* 네트워크 단절 시 재연결 가능
* 상태 캐시 유지
* SSE 재연결 지원

## 보안

* HTTPS/WSS 적용
* ROS 포트 외부 노출 금지 (Tailscale VPN 내부만 허용)
* Nginx reverse proxy 사용

# 11. 일정 계획

### 1주차

* ROS2 토픽 설계
* Unity ROS 연동

### 2주차

* MQTT bridge 구축
* Backend 상태 수신

### 3주차

* SSE 구현
* 웹 관제 UI

### 4주차

* Tailscale 카메라 이미지 전달
* 통합 테스트

# 12. 완료 기준

다음 조건을 모두 만족하면 MVP 완료로 판단한다.

1. Unity 차량 상태가 ROS2 토픽으로 publish된다.
2. ROS2 상태가 MQTT를 통해 서버에 전달된다.
3. 웹 대시보드에서 차량 위치가 표시된다.
4. 웹에서 Tailscale을 통해 차량 시점 카메라 이미지가 표시된다.
5. 상태 데이터와 이미지 데이터가 동시에 전달된다.
