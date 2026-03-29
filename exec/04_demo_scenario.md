# 데모 시나리오

## 1. 목표

이 시나리오는 저장소 코드로 실제 확인 가능한 전체 흐름을 기준으로 작성했다.

1. 예약 생성
2. care case 및 mission 생성
3. 운영자 관제 화면에서 차량 dispatch
4. ROS/Unity 텔레메트리 전송
5. 차량 단말에서 본인확인 및 활력징후 측정
6. 의사가 LiveKit 화상진료실에 입장
7. 진료 요약 저장

## 2. 데모 전제 조건

### 2.1 필요한 서비스

가장 완전한 데모를 하려면 아래 서비스가 준비되어 있어야 한다.

- `infra/docker-compose.yml` 기반 메인 스택
- `src/AI-IDV` 서버 또는 `CONSULTATION_IDENTITY_CHECK_BYPASS_ENABLED=true`
- `src/ros2_docker` ROS 스택
- `src/unity` Unity 프로젝트

### 2.2 권장 로컬 데모 플래그

- `APP_DEMO_MODE_ENABLED=true`
- `APP_SEED_ENABLED=true`
- `APP_SEED_DEFAULT_PASSWORD=qweasd`

이유:

- 로컬 데모 모드에서는 `DemoModePolicy`에 따라 운영자가 dispatch를 직접 제어하는 흐름이 활성화된다.
- 실제 프론트 코드에도 운영 대시보드의 `출동`, `도착 처리` 버튼이 이 흐름에 맞춰 구현되어 있다.

### 2.3 현재 시연용 계정 및 대상 환자

현재 시연 기준으로 확인된 계정/대상 정보는 아래와 같다.

| 구분 | 계정 또는 식별 정보 | 비밀번호 | 비고 |
| --- | --- | --- | --- |
| 관리자 | `seed_prod_admin` | `qweasd` | 운영 대시보드 로그인 |
| 의사 | `seed_prod_doc_im_01` | `qweasd` | EMR 로그인 |
| 보호자 | `seed_prod_guardian_wp_059` | `qweasd` | 보호자 포털 로그인 |
| 대상 환자 | `김원준(김주호)` | 해당 없음 | 휴대폰 시뮬레이터에서 전화번호로 식별 |
| 환자 전화번호 | `01049163720` | 해당 없음 | 예약 생성 시 사용 |
| 환자 주소 | `경상북도 김천시 증산면 장전4길 14` | 해당 없음 | 코드 주석 기준 waypoint `59` |

중요:

- 환자 본인은 이 문서 기준 별도 웹 로그인 계정이 확인되지 않았고, 휴대폰 시뮬레이터와 차량 단말 흐름에서 전화번호/본인확인으로 접근한다.
- 로봇 단말은 사용자 ID/PW 로그인 방식이 아니라 `bootstrap-token` 및 `mission terminal token` 흐름으로 동작한다.
- `src/FE/README.md`에는 예전 예시인 `seed_admin`, `seed_doc_im_01`이 남아 있으므로, 실제 시연은 위 표 기준으로 맞추는 것이 안전하다.

## 3. 상위 시스템 흐름

### 3.1 비즈니스 흐름

```text
Phone Simulator
  -> Spring intake API
  -> PostgreSQL (intake_session, booking, care_case, mission, dispatch_outbox)
  -> Kafka (doctor notification, SMS)
  -> Redis / SSE
  -> Operator Dashboard
  -> MQTT dispatch command
  -> ROS 2 robot nodes
  -> MQTT telemetry
  -> Spring robot subscriber
  -> SSE / Operator Map
  -> Robot Tablet Flow
  -> AI-IDV
  -> Spring mission verification
  -> LiveKit
  -> Doctor EMR + Robot Tablet
  -> PostgreSQL (consultation_session, consultation_summary, vital_measurement)
```

### 3.2 로보틱스/시뮬레이션 흐름

```text
Unity
  <-> ROS TCP Connector / ROS TCP Endpoint
  <-> ROS 2 Graph
  -> lane_follow_pkg
  -> mqtt_bridge
  -> Mosquitto (/mqtt)
  -> Spring Boot
  -> Operator UI / Robot UI
```

## 4. 단계별 데모 절차

### Step 1. 휴대폰 시뮬레이터에서 예약 생성

UI 동작:

1. `http://localhost` 접속
2. 메인 화면에서 `휴대폰 시뮬 열기` 클릭
3. 발신 전화번호로 `01049163720` 입력
   - 현재 시연 대상 환자는 `김원준(김주호)`이며, 주소는 `경상북도 김천시 증산면 장전4길 14`이다.
4. 새 예약 흐름 선택
5. 발신 번호 또는 직접 입력한 번호로 환자 식별
6. 진료과 선택
7. 추천된 슬롯 중 하나 선택
8. 예약 생성 확정

시스템 내부 동작:

- `POST /api/v1/intake/sessions`
- `/api/v1/intake/sessions/{id}/identify/...` 계열 호출
- `POST /api/v1/intake/sessions/{id}/recommend`
- `POST /api/v1/intake/sessions/{id}/bookings`

`BookingService` 기준 백엔드 side effect:

- `booking` 생성
- `care_case` 생성
- `dispatch_outbox` 생성
- 환자 주소 기반 waypoint를 계산하여 `mission`을 `CREATED` 상태로 생성 또는 갱신
- 트랜잭션 커밋 후 의사 알림 발행
- 트랜잭션 커밋 후 SMS 발행

### Step 2. 운영자 콘솔에서 신규 케이스 확인

UI 동작:

1. 메인 화면으로 돌아감
2. `관리자 로그인` 클릭
3. 관리자 계정 `seed_prod_admin / qweasd`로 로그인
4. `운영 대시보드` 탭 진입
5. 오늘 일정/미션 패널에서 방금 생성된 미션 확인

추가 확인 가능한 탭:

- `지도 모니터링`: 차량 위치/속도/카메라 보기
- `환자 관리`: 환자 등록
- `가입 승인`: 보호자 승인
- `시스템 모니터링`: Grafana 임베딩

시스템 내부 동작:

- 운영 대시보드는 예약/미션 API를 조회
- 의사 화면이 이미 켜져 있다면 SSE로 신규 예약 알림 전달
- 운영 화면은 `/api/v1/robots/stream`을 통해 실시간 차량 상태를 받을 수 있음

### Step 3. 운영자가 미션 dispatch

UI 동작:

1. `운영 대시보드`에서 해당 미션의 `출동` 버튼 클릭

이 버튼이 존재하는 이유:

- `DemoModePolicy`에서 데모 모드 시 automatic dispatch 대신 operator dispatch 중심으로 동작하기 때문이다.

시스템 내부 동작:

- 프론트가 `POST /api/v1/admin/demo/missions/{missionId}/dispatch` 호출
- 백엔드 `AdminDemoMissionService`가 mission을 `CREATED`에서 `EN_ROUTE`까지 진전
- `targetWaypointNumber`가 있으면 MQTT `robot/cmd/dispatch` 토픽으로 dispatch payload 발행
- 해당 데모 흐름의 dispatch outbox를 완료 처리

ROS가 꺼져 있어도:

- 다음 단계에서 `도착 처리` 버튼으로 수동 진행이 가능하다.

ROS가 켜져 있다면:

- `mqtt_bridge.py`가 dispatch 명령 수신
- ROS 노드가 주행/상태 텔레메트리 갱신
- Spring이 MQTT를 받아 운영 화면에 SSE로 재전달

### Step 4. 지도 모니터링에서 차량 이동 상태 확인

UI 동작:

1. `지도 모니터링` 탭 진입
2. 대상 차량이 선택되어 있는지 확인
3. 위치, 속도, 상태, 카메라 iframe 확인

시스템 내부 동작:

- ROS가 아래 MQTT 토픽 publish
  - `robot/odom`
  - `robot/minimap`
  - `robot/state`
  - `robot/status`
- Spring `RobotMqttSubscriber`가 이를 소비
- Spring이 운영자 프론트로 SSE 이벤트 송신
- 운영자 화면은 `/unity_cam/` iframe도 함께 표시

코드 근거 기반 추정(Assumption based on code):

- `/unity_cam/`은 메인 Compose 외부의 추가 스트리밍 브리지/게이트웨이가 있어야 완전하게 동작할 가능성이 크다.

### Step 5. 필요 시 운영자가 도착 처리

UI 동작:

1. `운영 대시보드`에서 `도착 처리` 클릭

시스템 내부 동작:

- 프론트가 `POST /api/v1/admin/demo/missions/{missionId}/arrive` 호출
- 백엔드가 mission phase를 `ARRIVED`로 전이

왜 중요한가:

- 로봇 단말 메인 화면의 `진료 시작` 버튼은 미션이 도착 가능 상태가 되어야 활성화된다.
- 의사 EMR의 `진료 시작 🎬` 버튼도 예약 당일 + mission 도착 상태 조건이 맞아야 활성화된다.

### Step 6. 차량 단말에서 진료 시작

UI 동작:

1. 메인 화면으로 돌아가 `로봇 화면 열기` 클릭
2. 화면에 `차량이 도착했습니다.`가 보이는지 확인
3. 큰 `진료 시작` 버튼 클릭

시스템 내부 동작:

- 로봇 프론트가 `POST /api/v1/terminal/bootstrap-token`으로 단말 bootstrap 토큰 획득
- `GET /api/v1/terminal/current-mission`을 polling 하며 현재 미션 조회
- 시작 시 `POST /api/v1/terminal/current-mission/claim` 호출
- 이후 사용할 mission terminal token을 로컬에 저장

### Step 7. 본인확인 수행

UI 동작:

1. `/robot/auth` 화면에서 얼굴 촬영
2. 신분증 촬영
3. 제출

시스템 내부 동작:

- 로봇 프론트가 `POST /api/v1/missions/{missionId}/identity-check`로 multipart 업로드
- 백엔드는 `FILE_STORAGE_ROOT` 아래 환자 기준 이미지를 읽음
- 백엔드는 `AI_IDV_URL`로 외부 AI-IDV 호출
- 응답의 얼굴 비교/OCR 결과를 환자 정보와 다시 검증
- 성공 시 이후 환자 토큰 발급에 재사용할 verified 상태를 캐시에 저장

우회 경로:

- `CONSULTATION_IDENTITY_CHECK_BYPASS_ENABLED=true`이면 데모/테스트용 bypass 응답 사용

### Step 8. 활력징후 측정

UI 동작:

1. 아래 순서대로 화면 진행
   - `체온`
   - `혈압`
   - `산소포화도`
   - `심전도`
2. 각 단계 저장 완료 후 자동 다음 단계 이동 확인

시스템 내부 동작:

- 각 화면이 `PUT /api/v1/missions/{missionId}/vitals` 호출
- 백엔드는 mission별 vital을 저장
- 이후 의사 consultation 화면에서 이 값들을 조회 가능

### Step 9. 의사 EMR 접속

UI 동작:

1. 메인 화면으로 돌아가 `EMR 열기` 클릭
2. 의사 계정 `seed_prod_doc_im_01 / qweasd`로 로그인
3. 대시보드에서 해당 예약 row 선택
4. 버튼이 활성화되면 `진료 시작 🎬` 클릭

시스템 내부 동작:

- 의사 대시보드가 assigned case 목록 조회
- `/api/v1/doctors/me/notifications/stream` SSE 구독
- `진료 시작 🎬` 클릭 시 `POST /api/v1/cases/{caseId}/sessions`
- 백엔드가 consultation session 생성 또는 재사용
- doctor LiveKit token 및 room 정보 반환

### Step 10. 양쪽에서 화상진료실 입장

의사 측:

- doctor 프론트가 백엔드에서 받은 doctor token으로 LiveKit room 입장

환자/차량 측:

- robot `Conference` 화면이 아래 endpoint를 polling
  - `GET /api/v1/missions/{missionId}/participants/patient/token`
  - `GET /api/v1/missions/{missionId}/consultation-status`
- patient token이 준비되면 같은 LiveKit room 입장

시스템 내부 동작:

- 실제 음성/영상 전송은 LiveKit 담당
- Spring은 토큰 발급과 room 수명 관리 담당
- LiveKit webhook은 `/api/v1/sessions/webhook/livekit`으로 복귀

### Step 11. 진료 완료 및 요약 저장

UI 동작:

1. 의사가 환자 상세와 활력징후 확인
2. 진료 메모/소견 입력
3. 저장 후 종료

시스템 내부 동작:

- doctor 프론트가 `PUT /api/v1/sessions/{sessionId}/summary` 호출
- 백엔드가 consultation summary 저장
- robot 프론트는 consultation status polling을 통해 완료 상태를 감지하고 `/robot/finish`로 이동

### Step 12. 미션 종료 처리

UI 동작:

1. 데모 환경에서 명시적 종료가 필요하면 다시 `운영 대시보드`로 이동
2. 운영자 수동 처리 또는 텔레메트리 기반 전이에 따라 미션 마감

코드 근거 기반 추정(Assumption based on code):

- `출동`, `도착 처리` 버튼은 프론트 코드에서 명확히 확인된다.
- 반면 consultation 종료 이후 최종 `COMPLETED` 전이를 어떤 운영 절차로 끝낼지는 실제 실행 환경의 telemetry/운영 정책에 따라 달라질 수 있다.

## 5. 발표용 짧은 데모 내러티브

짧게 설명할 때는 아래 순서로 말하면 된다.

1. "환자가 휴대폰 시뮬레이터에서 예약을 생성한다."
2. "백엔드는 예약과 케이스, 차량 미션을 만들고 운영자와 의사에게 알린다."
3. "운영자가 관제 화면에서 차량을 출동시킨다."
4. "ROS와 Unity가 차량 상태와 경로, 카메라 데이터를 MQTT로 올린다."
5. "차량 단말에서 본인확인과 활력징후 측정을 진행한다."
6. "의사가 EMR에서 진료를 시작하면 양쪽이 같은 LiveKit 방에 입장한다."
7. "의사가 진료 요약을 저장하면 원격진료 흐름이 마무리된다."

## 6. ROS/Unity 없이 축약 데모하는 방법

로보틱스 스택이 없어도 대부분의 서비스 데모는 가능하다.

1. 메인 스택만 실행
2. `APP_DEMO_MODE_ENABLED=true`
3. AI-IDV가 없으면 `CONSULTATION_IDENTITY_CHECK_BYPASS_ENABLED=true`
4. 휴대폰 시뮬레이터로 예약 생성
5. 운영자 화면의 `출동`, `도착 처리` 사용
6. 로봇 단말 흐름, 의사 EMR, LiveKit, summary 저장까지 진행

이 축약 데모로도 아래는 충분히 검증 가능하다.

- booking/case/mission 생성
- Kafka/Redis/SSE 알림
- mission terminal 인증
- vital 저장
- LiveKit room 생성 및 입장
- consultation summary 저장

## 7. 선택 확인 단계: 보호자 포털 로그인

보호자 계정까지 함께 시연하려면 아래 단계를 추가할 수 있다.

UI 동작:

1. 메인 화면으로 이동
2. `보호자 로그인` 클릭
3. 보호자 계정 `seed_prod_guardian_wp_059 / qweasd`로 로그인
4. 환자 포털 또는 마이페이지에서 연결된 환자 정보가 보이는지 확인

설명:

- 이 단계는 예약 생성과 화상진료의 핵심 경로는 아니지만, 시연 시 "보호자 포털까지 포함된 전체 사용자 흐름"을 보여줄 때 유용하다.
- 현재 문서에서 사용한 보호자 계정은 waypoint `59` 대상 환자와 연결된 계정으로 제공된 정보인다.
