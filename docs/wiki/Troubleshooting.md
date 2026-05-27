# Troubleshooting Guide

> Status: Canonical
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Purpose: 운영 중 확인한 장애를 문제, 원인, 조치, 검증 흐름으로 정리한 기준 문서입니다.

## LiveKit / TURN

| 항목 | 내용 |
| --- | --- |
| 현상 | 브라우저에서 ICE server parsing 실패, WebRTC relay candidate 미생성, 화상진료 연결 실패 |
| 원인 | TURN URL에 잘못된 hostname이 들어가거나, LiveKit 내장 TURN과 외부 coturn 설정이 섞임 |
| 조치 | LiveKit 내장 TURN을 끄고 coturn을 외부 TURN으로 고정, TURN credential을 정적 계정으로 맞춤 |
| 검증 | `chrome://webrtc-internals`에서 relay candidate 확인, LiveKit/coturn 로그 확인, 실제 의사-환자 화상 연결 smoke test |

`infra/livekit/entrypoint.sh`는 CRLF가 섞이면 컨테이너에서 `#!/bin/sh\r` 문제로 502를 만들 수 있으므로 LF를 유지해야 합니다. `livekit.yaml` 또는 `entrypoint.sh` 같은 bind mount 파일을 수정한 뒤에는 `livekit`, `coturn` 컨테이너를 재시작합니다.

```bash
cd infra
docker compose --env-file /home/ubuntu/.waddoc/prod.env -f docker-compose.prod.yml restart livekit coturn
```

## Nginx / WebSocket / SSE

| 항목 | 내용 |
| --- | --- |
| 현상 | `/livekit/` 경로 WebSocket 연결 실패, EMR 신규 예약 SSE 알림 유실 또는 지연 |
| 원인 | 프록시 경로 prefix가 LiveKit upstream으로 그대로 전달되거나, SSE가 Nginx buffering/timeout 영향을 받음 |
| 조치 | `/livekit/` rewrite로 prefix 제거, SSE 전용 location에 buffering off와 긴 timeout 적용 |
| 검증 | WebSocket 101 switching protocol, SSE keep-alive, 신규 예약 알림 수신 확인 |

SSE는 표준 `EventSource` 대신 `@microsoft/fetch-event-source`를 사용해 `Authorization: Bearer` 헤더를 유지합니다.

## LiveKit / 진료 세션 상태

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| empty timeout 이후 세션 조기 완료 | 실제 진료 전 `room_finished` webhook이 들어와도 session을 `COMPLETED`로 전이함 | `IN_PROGRESS` 상태일 때만 완료 처리하도록 제한 |
| 세션 시작 후 mission phase 불일치 | doctor/patient가 모두 입장해도 mission은 `VERIFYING`에 머묾 | session `IN_PROGRESS` 전이 시 mission을 `CONSULTING`으로 동기화 |
| 의사 진료 완료 후 환자 단말 잔류 | 진료 완료가 서버 기준 종료 이벤트로 정리되지 않음 | LiveKit room/session 정리와 환자 완료 화면 전환을 서버 흐름에 연결 |
| 진료실 재입장 불가 | 활성 consultation session 정보가 케이스 목록/EMR 액션에 노출되지 않음 | 케이스 목록 DTO에 session 상태를 포함하고 `진료실 복귀` 액션 추가 |

## Scale-out / Redis / Outbox

| 항목 | 내용 |
| --- | --- |
| 현상 | Spring Boot 다중 인스턴스에서 SSE 알림 누락, Outbox 중복 발행 가능성 |
| 원인 | Kafka consumer가 소비한 인스턴스의 로컬 emitter map에만 알림을 보내고, `@Scheduled` relay가 모든 인스턴스에서 동시에 실행됨 |
| 조치 | 의사 알림은 Redis Pub/Sub로 fan-out, Dispatch Outbox relay는 Redis 분산 락으로 단일 실행 보장 |
| 검증 | `--scale core-app=2` 이상에서 예약 생성 후 SSE 수신 확인, Outbox relay 로그에서 단일 인스턴스 실행 확인 |

Redis keyspace notification은 disconnect 타이머에도 쓰이므로 Redis 실행 옵션에 `--notify-keyspace-events Ex`가 포함되어야 합니다.

## Zenoh -> MQTT 전환

| 항목 | 내용 |
| --- | --- |
| 기존 문제 | Zenoh/FastAPI/100ms polling 구조는 오프라인 감지, 명령 신뢰성, Spring 중심 권한/감사 로그 추적이 약함 |
| 전환 방향 | Mosquitto MQTT broker, Spring `robot-gateway`, ROS2 MQTT bridge, FE SSE 구조로 정리 |
| 효과 | polling 제거, QoS 기반 명령 전달, Last Will 기반 오프라인 감지, retained message 기반 마지막 상태 복구 |

운영 경로는 ROS2가 `wss://<DOMAIN>/mqtt`로 접속하고, 내부 `robot-gateway`는 `ws://mosquitto:9001`로 접근합니다.

## 관제 / 로봇 SSE / 미니맵

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| 미니맵 위치 순간이동 | reconnect 또는 out-of-order SSE payload가 최신 telemetry를 덮어씀 | payload별 `updatedAt` 비교로 오래된 snapshot/telemetry 무시 |
| waypoint 전송 전후 마커 롤백 | 경로/odom/source 우선순위가 일관되지 않아 이전 좌표가 재반영됨 | trajectory와 최신 pose 기준으로 좌표 반영 순서 보정 |
| GPS 없음 시 차량 위치 공백 | GPS 좌표가 비어 있을 때 pose fallback이 부족함 | pose 기반 좌표 fallback과 좌표 출처 표시 보정 |
| 데모 시작 시 이전 환자 미션 노출 | 같은 차량의 이전 활성 미션이 current mission 우선순위를 차지함 | 데모 dispatch 전에 동일 차량의 기존 활성 데모 미션을 정리 |

## MediaMTX / Unity Camera

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| Mixed content / 잘못된 redirect | edge nginx와 frontend nginx 사이에 `X-Forwarded-*` 전달이 부족하거나 내부 포트 기준 절대 경로가 생성됨 | 공개 host/scheme header 전달, `/unity_cam/` redirect 재작성 제거 |
| WebRTC 연결 실패 | UDP ICE만 열려 있어 네트워크에 따라 fallback 불가 | `8189/tcp` publish, `MTX_WEBRTCLOCALTCPADDRESS=:8189` 추가 |
| 보라색 화면 | `h264_nvenc` 출력 pixel format/profile이 브라우저 친화적이지 않음 | `yuv420p`, B-frame 비활성화, browser-compatible H264 profile 적용 |
| 검은 화면 | inner camera topic publisher 부재로 초기 black frame만 송출 | inner frame 미수신 감지 후 front camera stream fallback |

현재 운영 계약은 브라우저가 `/unity_cam/`만 호출하고, frontend nginx가 `UNITY_CAM_PROXY_TARGET`을 통해 MediaMTX WebRTC endpoint로 프록시하는 구조입니다. 차량 publisher는 환경변수 또는 ROS parameter로 RTSP ingest URL을 주입받아야 하며, 소스 코드에 EC2 주소를 하드코딩하지 않습니다.

## CI/CD

| 항목 | 내용 |
| --- | --- |
| Jenkins 배포 흐름 | `dev` push -> checkout -> 변경 감지 -> quality gate -> Docker buildx push -> compose up |
| Node 버전 이슈 | Jenkins host Node 18에서 Vite 7 요구사항을 만족하지 못해 FE build 실패 |
| 조치 | FE/FE-phone quality gate를 Node 22 컨테이너 기준으로 실행 |
| Seed env 이슈 | 기본 seed 비밀번호 fallback을 제거하고 `APP_SEED_DEFAULT_PASSWORD` 누락 시 실패하도록 조정 |

## 본인확인 / 보안 / 시드

| 이슈 | 원인 | 조치 |
| --- | --- | --- |
| 차량 본인확인 false negative | 얼굴 검출 실패가 있어도 OCR은 일치하는 케이스가 있었음 | 임시로 OCR 1개 이상 일치 시 통과하도록 완화하고 기술부채를 명시 |
| JWT 통합 테스트 FK 실패 | seed cleanup이 참조 중인 `IntakeSession`까지 삭제함 | orphan `IntakeSession`만 삭제하도록 정리 |
| seed 기본 비밀번호 fallback | 운영/로컬 seed 비밀번호가 없어도 기본값으로 동작할 수 있음 | `APP_SEED_DEFAULT_PASSWORD` 누락 시 명시적으로 실패 |
| prod 기본 보안 사용자 로그 | Spring Security 기본 in-memory user가 자동 생성됨 | auto configuration 제외, SSE disconnect 로그 노이즈 정리 |

## 운영 DB 접근

운영 PostgreSQL은 공인망에 직접 열지 않고 `127.0.0.1:5432:5432`로만 바인딩합니다. 외부 점검은 SSH 터널을 통해서만 수행합니다.

```bash
ssh -L 5433:127.0.0.1:5432 ubuntu@<EC2_HOST>
```

## 점검 순서

장애 대응 시 확인 순서는 보통 `Nginx -> Spring health -> Kafka/Redis/PostgreSQL -> LiveKit/coturn -> Mosquitto -> MediaMTX -> FE 화면` 순서로 잡습니다.

```bash
cd infra
docker compose --env-file /home/ubuntu/.waddoc/prod.env -f docker-compose.prod.yml ps
docker compose --env-file /home/ubuntu/.waddoc/prod.env -f docker-compose.prod.yml logs -f nginx core-app notification-service robot-gateway livekit coturn mosquitto mediamtx
docker compose --env-file /home/ubuntu/.waddoc/prod.env -f docker-compose.prod.yml exec nginx nginx -t
```
