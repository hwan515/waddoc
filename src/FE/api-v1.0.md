
# RuralCare MVP API Specification
Version: v1.0.0-draft  
Date: 2026-03-09  
Target backend: Spring Boot Control Plane + FastAPI AI Plane  
Transport: HTTPS(JSON), WebSocket, WebRTC signaling via LiveKit

---

## 1. 문서 목적

이 문서는 **도서·산간 방문형 비대면 진료 자율주행 서비스 MVP**의 서버 API 명세를 정의한다.
설계 기준은 다음의 도메인 흐름을 따른다.

- `INTAKE_SESSION -> INTAKE_TURN -> SYMPTOM_INTAKE -> RECOMMENDATION -> BOOKING`
- `BOOKING -> CARE_CASE -> MISSION -> CONSULTATION_SESSION -> PRESCRIPTION`
- 보호자는 단순 조회자가 아니라 `GuardianAccessGrant`를 기반으로 조회 및 화상 참여가 가능한 actor이다.
- 미션 상태는 `phase / step / hold_reason / outcome` 4축으로 추적한다.
- 비동기 상태 이벤트는 `MISSION_EVENT` append-only 저장 + current state projection 전략을 따른다.

---

## 2. API 분류

### 2.1 Public / External API
사용 주체:
- IVR Gateway
- Operator Console
- Doctor UI
- Guardian Portal
- Patient Cabin UI
- Admin Backoffice

Base URL:
- `https://api.{env}.ruralcare.example.com/api/v1`

### 2.2 Internal Service API
사용 주체:
- Spring Boot ↔ FastAPI
- Spring Boot ↔ LiveKit Webhook
- Telephony Adapter / SMS Provider / Robot Bridge

Base URL:
- `https://internal.{env}.ruralcare.example.com/internal/v1`

### 2.3 WebSocket
- `wss://api.{env}.ruralcare.example.com/ws/...`

---

## 3. 인증 / 인가

| 대상 | 방식 | 비고 |
| --- | --- | --- |
| ADMIN / OPERATOR / DOCTOR | Bearer JWT (OIDC or internal IAM) + MFA | Spring Security RBAC |
| GUARDIAN | OTP challenge → short-lived access token 또는 session cookie | 읽기 전용 범위 기본 |
| ROBOT_BRIDGE / IVR / SMS / LiveKit webhook | Service JWT 또는 mTLS | 내부망/allowlist 권장 |
| PATIENT Cabin UI | short-lived mission-scoped token | 탑승 세션 범위 최소화 |

### 3.1 Role enum
- `ADMIN`
- `OPERATOR`
- `DOCTOR`
- `GUARDIAN`
- `ROBOT`
- `IVR`
- `SYSTEM`

### 3.2 Scope 예시
- `booking:read`
- `booking:write`
- `mission:write`
- `mission:command`
- `consultation:write`
- `consultation:token:issue`
- `guardian:case:read`
- `audit:read`
- `master:write`

---

## 4. 공통 헤더

| Header | Required | 설명 |
| --- | --- | --- |
| `Authorization` | Y(보호된 API) | `Bearer <token>` |
| `Content-Type` | Y | `application/json` |
| `X-Request-Id` | N | 클라이언트 요청 추적 ID |
| `X-Correlation-Id` | N | case 단위 추적 ID. 생성 후에는 `case_id` 사용 권장 |
| `Idempotency-Key` | POST/PATCH command 계열 권장, 일부 필수 | 중복 생성/명령 방지 |
| `X-Client-Version` | N | UI 또는 bridge 버전 식별 |
| `X-Actor-Type` | Internal API 권장 | `OPERATOR`, `ROBOT`, `IVR` 등 |

---

## 5. 공통 응답 규약

### 5.1 Success Envelope

```json
{
  "meta": {
    "request_id": "req_01J...",
    "correlation_id": "case_2f5d...",
    "timestamp": "2026-03-09T10:21:33Z"
  },
  "data": {},
  "error": null
}
```

### 5.2 Error Envelope

```json
{
  "meta": {
    "request_id": "req_01J...",
    "correlation_id": "case_2f5d...",
    "timestamp": "2026-03-09T10:21:33Z"
  },
  "data": null,
  "error": {
    "code": "BOOKING_SLOT_CONFLICT",
    "message": "Requested provider_slot is no longer available.",
    "details": {
      "provider_slot_id": "pslot_123"
    },
    "retriable": false
  }
}
```

### 5.3 HTTP Status 규칙

| Status | 사용 기준 |
| --- | --- |
| `200 OK` | 조회/명령 성공 |
| `201 Created` | 신규 리소스 생성 |
| `202 Accepted` | 비동기 처리 수락 |
| `204 No Content` | 본문 없는 성공 |
| `400 Bad Request` | 필수 필드 누락/형식 오류 |
| `401 Unauthorized` | 인증 실패 |
| `403 Forbidden` | 권한 부족 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 슬롯 충돌/상태 충돌/멱등 키 충돌 |
| `422 Unprocessable Entity` | 도메인 규칙 위반 |
| `429 Too Many Requests` | rate limit |
| `500/502/503` | 서버/연동 장애 |

---

## 6. 멱등성 / 이벤트 정렬 규칙

### 6.1 Idempotency-Key 적용 대상
아래 API는 `Idempotency-Key`를 **필수**로 둔다.

- `POST /ivr/bookings`
- `POST /bookings/{bookingId}/cancel`
- `POST /missions/generate`
- `POST /missions/{missionId}/dispatch`
- `POST /operator/missions/{missionId}/estop`
- `POST /operator/missions/{missionId}/teleop`
- `POST /operator/missions/{missionId}/recover`
- `POST /verifications`
- `POST /consents`
- `POST /vitals`
- `POST /consultations`
- `POST /consultations/{sessionId}/guardian-invites`
- `POST /consultations/{sessionId}/complete`
- `POST /prescriptions`
- `POST /prescriptions/{rxId}/send`
- `PATCH /prescriptions/{rxId}/status`
- `POST /followups`

### 6.2 Mission Event 정렬
`POST /missions/{missionId}/events` 는 다음을 만족해야 한다.

- `event_id` 단위 멱등 처리
- `(mission_id, source, seq_no)` unique
- `source_ts` 와 `seq_no` 기반 역순 이벤트 탐지
- 오래된 이벤트는 raw store(`MISSION_EVENT`)에는 저장 가능하지만 current state projection에는 반영하지 않음
- 응답에 `deduplicated`, `stale`, `current_phase`, `current_step` 반환

---

## 7. 주요 Enum 카탈로그

### 7.1 Intake
- `flow_type`: `FOLLOWUP`, `NEW_SYMPTOM`, `LOOKUP_CANCEL`
- `input_mode`: `VOICE`, `SYSTEM`
- `exception_code`: `NO_INPUT`, `NO_MATCH`, `ASR_FAIL`, `AMBIGUOUS_SYMPTOM`, `EMERGENCY_SUSPECTED`, `IDENTITY_MISMATCH`

### 7.2 Booking
- `visit_type`: `FOLLOWUP`, `NEW_SYMPTOM`
- `booking_status`: `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`

### 7.3 Mission
- `phase`: `CREATED`, `APPROVED`, `DISPATCHED`, `ENROUTE`, `ARRIVED`, `VERIFYING`, `CONSULTING`, `COMPLETING`, `CLOSED`
- `step`: `NAVIGATION`, `BOARDING`, `ID_CHECK`, `CONSENT_CAPTURE`, `VITAL_MEASUREMENT`, `RTC_CONNECTING`, `RTC_IN_CALL`, `FOLLOWUP_BOOKING`, `PRESCRIPTION_HANDOFF`
- `hold_reason`: `VERIFY_HOLD`, `SAFE_STOP`, `NET_DEGRADED`, `RTC_RETRYING`, `SENSOR_RECOVERY`, `CONSENT_PENDING`, `PATIENT_DELAY`
- `outcome`: `CLOSED_SUCCESS`, `CLOSED_CANCELLED_PATIENT`, `CLOSED_CANCELLED_OPERATOR`, `CLOSED_CANCELLED_EMERGENCY`, `CLOSED_FAILED_AUTONOMY`, `CLOSED_FAILED_NETWORK`, `CLOSED_FAILED_VERIFICATION`, `CLOSED_FAILED_DEVICE`

### 7.4 Consultation
- `session_mode`: `TWO_PARTY`, `THREE_PARTY`
- `consultation_status`: `CREATED`, `CONNECTING`, `IN_CALL`, `RECONNECTING`, `COMPLETED`, `FAILED`
- `bandwidth_profile`: `HIGH`, `MEDIUM`, `LOW`, `AUDIO_PRIORITY`

### 7.5 Consent
- `consent_type`: `TELEMEDICINE`, `PRIVACY`, `GUARDIAN_PARTICIPATION`
- `consent_result`: `GRANTED`, `DECLINED`, `PENDING`

### 7.6 Verification
- `verification_result`: `VERIFIED`, `VERIFY_HOLD`, `VERIFICATION_FAILED`, `EMERGENCY_DIVERT`

### 7.7 Prescription
- `rx_status`: `DRAFT`, `RX_SENT`, `PREPARING`, `READY`, `COMPLETED`, `FAILED`

### 7.8 Guardian participation
- `guardian_participation_status`: `NONE`, `REQUESTED`, `CONSENTED`, `INVITED`, `JOINED`, `DECLINED`, `FAILED`

---

## 8. 공통 객체 스키마

### 8.1 Mission Event Envelope

```json
{
  "schema_version": "v1",
  "event_id": "9e9f9f59-5fe5-4c37-a711-3e47e7729d2b",
  "source": "robot",
  "source_ts": "2026-03-09T10:20:01Z",
  "seq_no": 34,
  "correlation_id": "4b3c53e7-9fbf-4b90-b80c-1e7e2e6380f3",
  "case_id": "4b3c53e7-9fbf-4b90-b80c-1e7e2e6380f3",
  "mission_id": "0e96b1e8-4d4c-4e28-9f58-6d50895d10d7",
  "phase": "ENROUTE",
  "step": "NAVIGATION",
  "hold_reason": null,
  "outcome": null,
  "event_type": "MISSION_STATE_CHANGED",
  "error_code": null,
  "payload": {
    "pose": {
      "lat": 34.123,
      "lon": 127.456,
      "heading_deg": 92.4
    },
    "eta_sec": 220,
    "speed_mps": 3.5
  }
}
```

### 8.2 Pagination

Query params:
- `page` (default 1)
- `size` (default 20, max 100)
- `sort` (`created_at,desc`)

Response:

```json
{
  "meta": {
    "request_id": "req_123",
    "timestamp": "2026-03-09T10:21:33Z",
    "page": 1,
    "size": 20,
    "total_elements": 124,
    "total_pages": 7
  },
  "data": [...]
}
```

---

## 9. API 상세 명세

# 9.1 Auth / IAM

### 9.1.1 POST `/auth/login`
설명: 운영자/의료진/관리자 로그인  
Auth: 없음  
Owner: `identity-access`

Request
```json
{
  "username": "doctor01",
  "password": "********"
}
```

Response
```json
{
  "access_token": "jwt",
  "refresh_token": "jwt",
  "expires_in": 3600,
  "role": "DOCTOR",
  "mfa_required": true
}
```

### 9.1.2 POST `/auth/token/refresh`
설명: refresh token 재발급  
Auth: refresh token

### 9.1.3 POST `/auth/otp/challenges`
설명: 보호자 OTP 또는 MFA challenge 생성

Request
```json
{
  "purpose": "GUARDIAN_LOGIN",
  "target_value": "+821012345678"
}
```

Response
```json
{
  "otp_challenge_id": "otp_123",
  "expires_at": "2026-03-09T10:26:33Z",
  "masked_target": "+82******5678"
}
```

### 9.1.4 POST `/auth/otp/verifications`
설명: OTP 검증 및 guardian access token 발급

Request
```json
{
  "otp_challenge_id": "otp_123",
  "otp_code": "493821"
}
```

Response
```json
{
  "access_token": "guardian-jwt",
  "expires_in": 900,
  "role": "GUARDIAN",
  "scopes": ["guardian:case:read"]
}
```

### 9.1.5 POST `/auth/logout`
설명: auth session 종료

---

# 9.2 Master / Setup API (Admin only)

### 9.2.1 POST `/admin/regions`
설명: 서비스 지역 생성

Request
```json
{
  "name": "Demo Rural Village A",
  "service_hours": "09:00-18:00",
  "max_one_way_km": 5,
  "max_one_way_min": 20,
  "weather_policy": "CLEAR_ONLY",
  "service_area_geojson": {
    "type": "Polygon",
    "coordinates": [[[127.1,34.1],[127.2,34.1],[127.2,34.2],[127.1,34.2],[127.1,34.1]]]
  }
}
```

### 9.2.2 POST `/admin/providers`
설명: 의료진 등록

### 9.2.3 POST `/admin/provider-slots/bulk`
설명: provider slot 일괄 등록

Request
```json
{
  "provider_id": "provider_001",
  "region_id": "region_001",
  "slot_unit_min": 10,
  "slots": [
    {
      "start_at": "2026-03-10T10:00:00+09:00",
      "end_at": "2026-03-10T10:10:00+09:00",
      "capacity": 1
    },
    {
      "start_at": "2026-03-10T10:10:00+09:00",
      "end_at": "2026-03-10T10:20:00+09:00",
      "capacity": 1
    }
  ]
}
```

### 9.2.4 POST `/admin/vehicles`
설명: 차량 등록

### 9.2.5 POST `/admin/mission-windows/bulk`
설명: vehicle mission window 일괄 등록

### 9.2.6 POST `/admin/patients`
설명: 환자 사전 등록

Request
```json
{
  "name": "홍길동",
  "dob": "1954-03-11",
  "region_id": "region_001",
  "default_provider_id": "provider_001",
  "address": "전남 ...",
  "home_point": {
    "lat": 34.12345,
    "lon": 127.45678
  },
  "risk_flag": "LOW"
}
```

Response
```json
{
  "patient_id": "patient_001",
  "dob6_key": "540311",
  "active": true
}
```

### 9.2.7 POST `/admin/patients/{patientId}/phone-bindings`
설명: 전화번호 바인딩 추가

### 9.2.8 POST `/admin/guardians`
설명: 보호자 등록

### 9.2.9 POST `/admin/patient-guardian-links`
설명: 환자-보호자 관계 등록

### 9.2.10 POST `/admin/guardian-access-grants`
설명: 보호자 조회/참여 grant 생성

Request
```json
{
  "patient_guardian_link_id": "pgl_001",
  "scope": ["CASE_READ", "RTC_JOIN"],
  "consent_source": "INSTITUTION_PRECONSENT",
  "valid_from": "2026-03-09T00:00:00+09:00",
  "valid_to": "2026-03-31T23:59:59+09:00"
}
```

---

# 9.3 IVR Intake / Booking API

### 9.3.1 POST `/ivr/sessions`
설명: IVR 인테이크 세션 시작  
Auth: Service(`IVR`)  
Owner: `ivr-intake-booking`

Request
```json
{
  "entry_channel": "PSTN",
  "flow_type": "FOLLOWUP",
  "caller_number": "+821012345678",
  "input_name": "홍길동",
  "input_dob6": "540311"
}
```

Response
```json
{
  "intake_session_id": "intake_001",
  "patient_match": {
    "matched": true,
    "patient_id": "patient_001",
    "match_strategy": "CALLER_AND_DOB6"
  },
  "status": "ACTIVE",
  "next_prompt_code": "MAIN_MENU"
}
```

도메인 규칙
- 사전 등록 환자 여부 확인
- `caller_number + dob6_key` 우선 매칭
- 실패 시 `IDENTITY_MISMATCH` 또는 수동 콜백 흐름

### 9.3.2 POST `/ivr/sessions/{intakeSessionId}/turns`
설명: IVR voice turn 기록(ASR/no-input/no-match 포함)

Request
```json
{
  "turn_no": 2,
  "prompt_code": "SYMPTOM_CAPTURE",
  "input_mode": "VOICE",
  "recognized_text": "기침이 있고 어지러워요",
  "asr_confidence": 0.88,
  "exception_code": null
}
```

Response
```json
{
  "intake_turn_id": "turn_002",
  "next_prompt_code": "CONFIRM_SYMPTOM"
}
```

### 9.3.3 POST `/ivr/sessions/{intakeSessionId}/classify`
설명: symptom intake + recommendation 생성  
Auth: Service(`IVR`) or internal orchestrated by Spring  
Owner: `ivr-intake-booking`

Request
```json
{
  "raw_text": "며칠 전부터 기침이 심하고 몸살이 있어요",
  "requested_dept_code": null,
  "requested_visit_type": "FOLLOWUP"
}
```

Response
```json
{
  "symptom_intake_id": "sym_001",
  "primary_symptom": "COUGH",
  "structured_symptoms": {
    "duration_days": 3,
    "symptoms": ["COUGH", "MYALGIA"]
  },
  "emergency_flag": false,
  "ambiguous_flag": false,
  "recommendations": [
    {
      "recommendation_id": "rec_001",
      "dept_code": "IM",
      "provider_id": "provider_001",
      "recommendation_type": "PRIMARY"
    }
  ]
}
```

### 9.3.4 GET `/ivr/sessions/{intakeSessionId}`
설명: 인테이크 세션 조회

### 9.3.5 POST `/ivr/bookings`
설명: 인테이크 기반 예약 생성  
Auth: Service(`IVR`) / Admin / Operator  
Owner: `ivr-intake-booking`, `scheduling`, `case-management`

Request
```json
{
  "intake_session_id": "intake_001",
  "patient_id": "patient_001",
  "region_id": "region_001",
  "selected_recommendation_id": "rec_001",
  "provider_slot_ids": ["pslot_1000"],
  "mission_window_id": "mwin_2000",
  "visit_type": "FOLLOWUP",
  "booking_channel": "IVR",
  "guardian_requested": true
}
```

Response
```json
{
  "booking_id": "booking_001",
  "case_id": "case_001",
  "assigned_provider_id": "provider_001",
  "provider_slot_ids": ["pslot_1000"],
  "mission_window_id": "mwin_2000",
  "status": "CONFIRMED",
  "booked_at": "2026-03-09T10:24:31Z",
  "notifications": [
    {
      "template_code": "BOOKING_CONFIRMED",
      "delivery_status": "QUEUED"
    }
  ]
}
```

검증 규칙
- 동일 `provider_slot` 중복 배정 금지
- 동일 차량의 `mission_window` 중복 금지
- 예약 생성 성공 시 `CARE_CASE`까지 같은 transaction에서 연계 생성
- follow-up 20분 배정은 `provider_slot_ids` 2개 허용

### 9.3.6 POST `/ivr/bookings/lookup`
설명: 전화번호 + 생년월일 기반 활성 예약 조회/취소용 조회  
Auth: Service(`IVR`)

Request
```json
{
  "caller_number": "+821012345678",
  "dob": "1954-03-11"
}
```

Response
```json
{
  "active_bookings": [
    {
      "booking_id": "booking_001",
      "status": "CONFIRMED",
      "scheduled_at": "2026-03-10T10:00:00+09:00",
      "provider_name": "김의사",
      "department": "내과",
      "planned_departure": "2026-03-10T09:35:00+09:00"
    }
  ]
}
```

### 9.3.7 POST `/bookings/{bookingId}/cancel`
설명: 예약 취소  
Auth: IVR / Operator / Admin  
Owner: `ivr-intake-booking`

Request
```json
{
  "reason": "PATIENT_REQUEST",
  "actor": {
    "type": "IVR",
    "ref_id": "ivr_gateway"
  }
}
```

Response
```json
{
  "booking_id": "booking_001",
  "status": "CANCELLED",
  "cancelled_at": "2026-03-09T11:01:12Z"
}
```

검증 규칙
- `planned_departure` 30분 전까지 IVR 취소 허용
- 이후에는 `BOOKING_CANCEL_AFTER_CUTOFF`로 거절하고 운영자 절차 안내

### 9.3.8 GET `/bookings/{bookingId}`
설명: 예약 상세 조회

---

# 9.4 Case API

### 9.4.1 GET `/cases/{caseId}`
설명: 케이스 종합 조회  
Auth: ADMIN / OPERATOR / DOCTOR / authorized GUARDIAN(scope 제한)  
Owner: `case-management`

Response
```json
{
  "case_id": "case_001",
  "booking": {
    "booking_id": "booking_001",
    "status": "CONFIRMED",
    "visit_type": "FOLLOWUP"
  },
  "patient": {
    "patient_id": "patient_001",
    "name_masked": "홍**",
    "dob": "1954-03-11"
  },
  "guardian_participation_status": "REQUESTED",
  "mission": {
    "mission_id": "mission_001",
    "phase": "ENROUTE",
    "step": "NAVIGATION"
  },
  "consultation": null,
  "prescription": null
}
```

---

# 9.5 Mission / Dispatch API

### 9.5.1 POST `/missions/generate`
설명: 예약 기반 mission 생성  
Auth: SYSTEM / SCHEDULER / OPERATOR  
Owner: `mission-orchestration`

Request
```json
{
  "booking_id": "booking_001"
}
```

Response
```json
{
  "case_id": "case_001",
  "mission_id": "mission_001",
  "vehicle_id": "vehicle_001",
  "mission_window_id": "mwin_2000",
  "planned_departure": "2026-03-10T09:35:00+09:00",
  "phase": "CREATED"
}
```

### 9.5.2 POST `/missions/{missionId}/dispatch`
설명: 출동 시작 승인  
Auth: SYSTEM / OPERATOR  
Owner: `mission-orchestration`

Request
```json
{
  "triggered_by": {
    "type": "SCHEDULER",
    "ref_id": "daily_dispatch_job"
  }
}
```

Response
```json
{
  "mission_id": "mission_001",
  "phase": "DISPATCHED",
  "dispatch_at": "2026-03-10T09:35:03+09:00"
}
```

### 9.5.3 POST `/missions/{missionId}/events`
설명: robot-bridge 또는 내부 시스템이 미션 상태 이벤트를 수집  
Auth: ROBOT / SYSTEM / OPERATOR  
Owner: `mission-orchestration`

Request
```json
{
  "schema_version": "v1",
  "event_id": "evt_001",
  "source": "robot",
  "source_ts": "2026-03-10T09:41:15Z",
  "seq_no": 120,
  "correlation_id": "case_001",
  "case_id": "case_001",
  "mission_id": "mission_001",
  "phase": "ENROUTE",
  "step": "NAVIGATION",
  "hold_reason": null,
  "outcome": null,
  "event_type": "MISSION_STATE_CHANGED",
  "error_code": null,
  "payload": {
    "pose": {
      "lat": 34.123,
      "lon": 127.456
    },
    "eta_sec": 143
  }
}
```

Response
```json
{
  "accepted": true,
  "deduplicated": false,
  "stale": false,
  "current_phase": "ENROUTE",
  "current_step": "NAVIGATION"
}
```

오류 예시
- `MISSION_EVENT_DUPLICATE`
- `MISSION_EVENT_STALE`
- `MISSION_PHASE_REGRESSION_NOT_ALLOWED`

### 9.5.4 GET `/missions/{missionId}`
설명: 미션 current state 조회

### 9.5.5 GET `/missions/{missionId}/timeline`
설명: mission step / raw event 타임라인 조회

### 9.5.6 GET `/missions/{missionId}/alerts`
설명: mission alert 조회

---

# 9.6 Operator Command API

### 9.6.1 POST `/operator/missions/{missionId}/estop`
설명: 원격 E-stop 발령/해제  
Auth: OPERATOR  
Owner: `mission-orchestration`

Request
```json
{
  "operator_id": "operator_001",
  "action": "LATCH",
  "reason": "OBSTACLE_DETECTED"
}
```

Response
```json
{
  "mission_id": "mission_001",
  "estop": true,
  "latched": true,
  "hold_reason": "SAFE_STOP",
  "effective_at": "2026-03-10T09:43:01Z"
}
```

제어 우선순위
- `E-stop > teleop > autonomy`

### 9.6.2 POST `/operator/missions/{missionId}/teleop`
설명: teleop 시작/종료  
Auth: OPERATOR

Request
```json
{
  "operator_id": "operator_001",
  "action": "START",
  "reason": "BYPASS_PARKED_CAR"
}
```

Response
```json
{
  "mission_id": "mission_001",
  "teleop_state": "ACTIVE",
  "lock_owner": "operator_001",
  "autonomy_blocked": true
}
```

규칙
- 단일 operator lock만 허용
- E-stop active 중에는 START 불가
- 종료 후 `RESUME_AUTONOMY` 명시 명령 전까지 autonomy 복귀 금지

### 9.6.3 POST `/operator/missions/{missionId}/recover`
설명: 재시도/취소/자율주행 복귀  
Auth: OPERATOR

Request
```json
{
  "operator_id": "operator_001",
  "action": "RESUME_AUTONOMY",
  "note": "Obstacle cleared"
}
```

Response
```json
{
  "mission_id": "mission_001",
  "phase": "ENROUTE",
  "hold_reason": null,
  "status": "RECOVERY_APPLIED"
}
```

---

# 9.7 Verification / Consent / Vitals API

### 9.7.1 POST `/verifications`
설명: 탑승 후 본인확인 기록  
Auth: OPERATOR / CABIN_UI / SYSTEM  
Owner: `verification-consent`

Request
```json
{
  "mission_id": "mission_001",
  "input_name": "홍길동",
  "input_dob6": "540311",
  "method": "ID_CARD_VISUAL",
  "result": "VERIFIED",
  "evidence_ref": "s3://evidence/verify_001.jpg",
  "verified_by": {
    "type": "OPERATOR",
    "ref_id": "operator_001"
  }
}
```

Response
```json
{
  "verification_id": "ver_001",
  "result": "VERIFIED",
  "hold_reason": null,
  "verified_at": "2026-03-10T10:01:20Z"
}
```

### 9.7.2 POST `/consents`
설명: 동의 수집  
Auth: OPERATOR / CABIN_UI / SYSTEM  
Owner: `verification-consent`

Request
```json
{
  "case_id": "case_001",
  "consent_type": "TELEMEDICINE",
  "channel": "VOICE",
  "result": "GRANTED",
  "actor": {
    "type": "PATIENT",
    "ref_id": "patient_001"
  },
  "captured_at": "2026-03-10T10:02:10Z"
}
```

Response
```json
{
  "consent_id": "consent_001",
  "status": "RECORDED"
}
```

### 9.7.3 POST `/vitals`
설명: vital 업로드  
Auth: ROBOT / OPERATOR / SYSTEM  
Owner: `vital-management`

Request
```json
{
  "mission_id": "mission_001",
  "spo2": 97,
  "bp_sys": 128,
  "bp_dia": 82,
  "temp": 36.7,
  "measured_at": "2026-03-10T10:03:05Z",
  "source": "BLE_BP_DEVICE",
  "validity_flag": "VALID"
}
```

Response
```json
{
  "vital_record_id": "vital_001",
  "accepted_at": "2026-03-10T10:03:07Z",
  "reflected_to_doctor": true
}
```

### 9.7.4 GET `/cases/{caseId}/vitals/latest`
설명: 최신 vital 조회

---

# 9.8 Consultation / WebRTC API

### 9.8.1 POST `/consultations`
설명: 화상진료 세션 생성 + LiveKit room 준비  
Auth: DOCTOR / SYSTEM  
Owner: `consultation`

Request
```json
{
  "case_id": "case_001",
  "mission_id": "mission_001",
  "provider_id": "provider_001",
  "session_mode": "TWO_PARTY",
  "bandwidth_profile": "MEDIUM",
  "participants": [
    {
      "actor_type": "DOCTOR",
      "actor_ref_id": "provider_001",
      "role": "HOST",
      "can_publish": true,
      "can_subscribe": true
    },
    {
      "actor_type": "PATIENT",
      "actor_ref_id": "patient_001",
      "role": "PATIENT",
      "can_publish": true,
      "can_subscribe": true
    }
  ]
}
```

Response
```json
{
  "session_id": "session_001",
  "media_room_name": "case_001",
  "status": "CREATED",
  "retry_policy": {
    "max_retry": 2,
    "fallback_profiles": ["LOW", "AUDIO_PRIORITY"]
  }
}
```

생성 전 체크
- 본인확인 성공
- `TELEMEDICINE`, `PRIVACY` 동의 완료
- 필수 vital 존재 또는 예외 플래그 존재

### 9.8.2 POST `/consultations/{sessionId}/tokens`
설명: LiveKit room join token 발급  
Auth: DOCTOR / SYSTEM / authorized GUARDIAN  
Owner: `consultation`

Request
```json
{
  "actor_type": "DOCTOR",
  "actor_ref_id": "provider_001"
}
```

Response
```json
{
  "participant_id": "part_001",
  "room_name": "case_001",
  "token": "livekit-jwt",
  "expires_at": "2026-03-10T10:25:00Z",
  "permissions": {
    "can_publish": true,
    "can_subscribe": true
  }
}
```

### 9.8.3 POST `/consultations/{sessionId}/guardian-invites`
설명: 보호자 화상 참여 초대 생성  
Auth: DOCTOR / OPERATOR  
Owner: `consultation`, `guardian-access`

Request
```json
{
  "guardian_id": "guardian_001",
  "grant_id": "grant_001",
  "invite_channel": "SMS"
}
```

Response
```json
{
  "invite_id": "invite_001",
  "status": "ACTIVE",
  "expires_at": "2026-03-10T10:30:00Z"
}
```

검증 규칙
- `GuardianAccessGrant.status=ACTIVE`
- 유효 기간 확인
- `GUARDIAN_PARTICIPATION` consent 존재
- ACTIVE invite partial unique

### 9.8.4 GET `/consultations/{sessionId}`
설명: consultation 세션 상세 조회

### 9.8.5 POST `/consultations/{sessionId}/complete`
설명: 진료 종료 처리  
Auth: DOCTOR  
Owner: `consultation`, `prescription-followup`

Request
```json
{
  "summary_min": "상기도 감염 의심. 1주 후 재진 권고.",
  "followup_needed": true,
  "rx_needed": true
}
```

Response
```json
{
  "session_id": "session_001",
  "status": "COMPLETED",
  "next_actions": {
    "can_create_followup": true,
    "can_create_prescription": true
  },
  "completed_at": "2026-03-10T10:18:44Z"
}
```

---

# 9.9 Prescription / Follow-up API

### 9.9.1 POST `/prescriptions`
설명: 처방 객체 생성  
Auth: DOCTOR  
Owner: `prescription-followup`

Request
```json
{
  "case_id": "case_001",
  "session_id": "session_001",
  "destination_type": "PHARMACY",
  "destination_id": "pharmacy_001"
}
```

Response
```json
{
  "rx_id": "rx_001",
  "status": "DRAFT"
}
```

### 9.9.2 POST `/prescriptions/{rxId}/send`
설명: 처방 전송  
Auth: DOCTOR / SYSTEM

Request
```json
{
  "destination_type": "PHARMACY",
  "destination_id": "pharmacy_001",
  "actor": {
    "type": "DOCTOR",
    "ref_id": "provider_001"
  }
}
```

Response
```json
{
  "rx_id": "rx_001",
  "rx_status": "RX_SENT",
  "sent_at": "2026-03-10T10:19:30Z"
}
```

### 9.9.3 PATCH `/prescriptions/{rxId}/status`
설명: 조제 상태 변경  
Auth: OPERATOR / SYSTEM / PHARMACY_INTEGRATION

Request
```json
{
  "status": "READY",
  "note": "수령 가능",
  "actor": {
    "type": "SYSTEM",
    "ref_id": "pharmacy_adapter"
  }
}
```

Response
```json
{
  "rx_id": "rx_001",
  "current_status": "READY",
  "updated_at": "2026-03-10T10:45:00Z"
}
```

### 9.9.4 POST `/followups`
설명: 재진 예약 생성  
Auth: DOCTOR  
Owner: `prescription-followup`, `scheduling`

Request
```json
{
  "patient_id": "patient_001",
  "provider_id": "provider_001",
  "region_id": "region_001",
  "provider_slot_ids": ["pslot_1010"],
  "mission_window_id": "mwin_2010",
  "followup_from_session_id": "session_001"
}
```

Response
```json
{
  "booking_id": "booking_002",
  "case_id": "case_002",
  "notification_status": "QUEUED"
}
```

---

# 9.10 Guardian API

### 9.10.1 GET `/guardian/cases/{caseId}`
설명: 보호자용 케이스 조회  
Auth: GUARDIAN  
Owner: `guardian-access`

Response
```json
{
  "case_id": "case_001",
  "booking": {
    "status": "CONFIRMED",
    "scheduled_at": "2026-03-10T10:00:00+09:00"
  },
  "mission": {
    "phase": "CONSULTING",
    "step": "RTC_IN_CALL",
    "eta_sec": 0,
    "arrived": true
  },
  "vitals_summary": {
    "spo2": 97,
    "bp": "128/82",
    "temp": 36.7
  },
  "consultation_summary_min": "진료 진행 중",
  "rx_status": "RX_SENT"
}
```

권한 규칙
- 유효한 guardian access grant 필요
- grant 범위 밖의 필드는 마스킹 또는 누락
- 원본 음성/영상, 상세 진료 전문, 운영자 내부 코멘트 미노출

---

# 9.11 Audit / Notification API

### 9.11.1 GET `/audit/logs`
설명: 감사 로그 조회  
Auth: ADMIN / limited OPERATOR / DOCTOR  
Owner: `audit-kpi-alert`

Query params
- `case_id`
- `mission_id`
- `session_id`
- `actor_type`
- `action`
- `from`
- `to`

### 9.11.2 GET `/notifications`
설명: 알림 발송 이력 조회

---

## 10. Internal AI API (FastAPI)

> 이 구간은 외부 공개 API가 아니라 Spring Boot가 호출하는 내부 API이다.
> FastAPI는 inference only이며 최종 영속화는 Spring Boot가 수행한다.

### 10.1 POST `/internal/v1/ai/stt/transcriptions`
설명: Whisper 기반 STT  
Auth: Service JWT  
Owner: `ai-stt`

Content-Type: `multipart/form-data`

Form fields
- `audio_file`
- `language` (`ko`, optional)
- `task` (`transcribe`)
- `session_ref`
- `turn_no`

Response
```json
{
  "text": "기침이 있고 어지러워요",
  "language": "ko",
  "confidence": 0.88,
  "duration_ms": 4200,
  "segments": [
    {
      "start_ms": 0,
      "end_ms": 1600,
      "text": "기침이 있고"
    }
  ]
}
```

### 10.2 POST `/internal/v1/ai/triage/classifications`
설명: 증상 분류 및 red-flag 탐지  
Auth: Service JWT  
Owner: `ai-triage`

Request
```json
{
  "raw_text": "며칠 전부터 기침이 심하고 몸살이 있어요",
  "patient_context": {
    "age": 72,
    "risk_flag": "LOW",
    "visit_type": "FOLLOWUP"
  }
}
```

Response
```json
{
  "primary_symptom": "COUGH",
  "structured_symptoms": {
    "symptoms": ["COUGH", "MYALGIA"],
    "duration_days": 3
  },
  "emergency_flag": false,
  "ambiguous_flag": false,
  "dept_code": "IM",
  "recommendation_type": "PRIMARY",
  "rationale_payload": {
    "matched_keywords": ["기침", "몸살"]
  }
}
```

### 10.3 POST `/internal/v1/ai/summaries/consultations`
설명: 최소 진료 요약 생성(선택)

Request
```json
{
  "transcript_ref": "obj://consult/session_001/transcript.json",
  "output_style": "MINIMAL_CLINICAL"
}
```

Response
```json
{
  "summary_min": "상기도 감염 의심. 수분 섭취 및 휴식 권고. 1주 후 재진."
}
```

---

## 11. Integration Webhook API

### 11.1 POST `/internal/v1/integrations/livekit/webhooks`
설명: LiveKit room/participant 이벤트 수신  
Auth: webhook signature verify  
Owner: `consultation`

지원 이벤트 예시
- `room_started`
- `participant_joined`
- `participant_left`
- `track_published`
- `egress_started`
- `egress_ended`

Response
```json
{
  "accepted": true
}
```

### 11.2 POST `/internal/v1/integrations/telephony/events`
설명: PSTN/SIP provider call lifecycle event 수신  
Owner: `ivr-intake-booking`

### 11.3 POST `/internal/v1/integrations/sms/delivery-reports`
설명: SMS delivery status webhook  
Owner: `notification`

---

## 12. WebSocket 채널 명세

### 12.1 `ws/operator/missions/{missionId}`
용도: 관제용 미션 상태 스트림

Payload
```json
{
  "schema_version": "v1",
  "event_id": "evt_120",
  "source": "backend",
  "source_ts": "2026-03-10T09:41:15Z",
  "seq_no": 120,
  "correlation_id": "case_001",
  "mission_id": "mission_001",
  "phase": "ENROUTE",
  "step": "NAVIGATION",
  "hold_reason": null,
  "outcome": null,
  "error_code": null,
  "payload": {
    "eta_sec": 143,
    "pose": {
      "lat": 34.123,
      "lon": 127.456
    }
  }
}
```

### 12.2 `ws/operator/vehicles/{vehicleId}/health`
Payload
```json
{
  "vehicle_id": "vehicle_001",
  "speed": 3.5,
  "battery": 87,
  "network": "GOOD",
  "sensor_health": "OK",
  "estop": false,
  "teleop_lock_owner": null
}
```

### 12.3 `ws/doctor/consultations/{sessionId}/events`
Payload
```json
{
  "session_id": "session_001",
  "rtc_state": "RECONNECTING",
  "retry_count": 1,
  "connection_quality": "POOR",
  "bandwidth_profile": "LOW",
  "vital_update": null
}
```

### 12.4 `ws/guardian/cases/{caseId}`
Payload
```json
{
  "case_id": "case_001",
  "booking_status": "CONFIRMED",
  "phase": "CONSULTING",
  "step": "RTC_IN_CALL",
  "eta": 0,
  "arrived": true,
  "consulting": true,
  "closed": false,
  "rx_status": "RX_SENT"
}
```

---

## 13. 대표 에러 코드

### 13.1 Booking / Scheduling
- `PATIENT_NOT_PRE_REGISTERED`
- `PATIENT_PHONE_BINDING_NOT_FOUND`
- `IDENTITY_MISMATCH`
- `BOOKING_SLOT_CONFLICT`
- `MISSION_WINDOW_CONFLICT`
- `BOOKING_CANCEL_AFTER_CUTOFF`
- `BOOKING_VISIT_TYPE_NOT_ALLOWED`

### 13.2 Mission / Robot
- `NAV-001`
- `NAV-002`
- `SEN-001`
- `SEN-002`
- `NET-001`
- `BAT-001`
- `OPS-001`
- `MISSION_EVENT_DUPLICATE`
- `MISSION_EVENT_STALE`
- `MISSION_PHASE_REGRESSION_NOT_ALLOWED`

### 13.3 Verification / Consent / Vitals
- `ID-001`
- `CON-001`
- `VIT-001`
- `REQUIRED_VITAL_MISSING`

### 13.4 Consultation
- `RTC-001`
- `RTC-002`
- `LIVEKIT_TOKEN_ISSUE_FAILED`
- `GUARDIAN_GRANT_NOT_ACTIVE`
- `GUARDIAN_INVITE_ALREADY_ACTIVE`

### 13.5 Prescription
- `RX-001`
- `RX_STATUS_TRANSITION_INVALID`

---

## 14. 권장 트랜잭션 경계

### 14.1 하나의 DB transaction으로 처리
- `ivr/bookings` → `BOOKING`, `BOOKING_PROVIDER_SLOT`, `CARE_CASE`
- `missions/generate` → `MISSION`
- `verifications` → `VERIFICATION` + mission current state 업데이트
- `consents` → `CONSENT`
- `vitals` → `VITAL_RECORD`
- `consultations` → `CONSULTATION_SESSION`, `CONSULTATION_PARTICIPANT`
- `prescriptions` → `PRESCRIPTION`

### 14.2 Outbox/Event 후처리
- SMS 발송
- guardian portal push
- operator console websocket push
- audit fan-out
- AI summary post-processing
- KPI aggregation

---

## 15. 상태 전이 규칙

1. `phase`는 원칙적으로 단조 증가한다.  
2. `ARRIVED`는 `target geofence 진입 + speed=0 + safe stop` 만족 시에만 허용한다.  
3. `CONSULTING` 진입 전 `VERIFIED + required consents + required vitals(or exception)`가 선행되어야 한다.  
4. `CLOSED` 진입 시 `outcome`은 필수다.  
5. `VERIFY_HOLD`, `RTC_RETRYING` 등은 상위 상태가 아니라 `hold_reason`으로만 표현한다.  

---

## 16. 권장 구현 메모

- Spring Boot는 source of truth, FastAPI는 inference only
- 내부 서비스 간 인증은 service JWT 또는 mTLS
- 감사 로그에는 상세 민감정보를 그대로 중복 저장하지 않고 masked/reference 저장
- `consultation_participant (session_id, actor_type, actor_ref_id, role)` unique
- `mission_event (mission_id, source, seq_no)` unique
- `guardian_invite (session_id, guardian_id, status='ACTIVE')` partial unique
- `booking_provider_slot(provider_slot_id)` unique(capacity=1 기준)

---

## 17. 구현 우선순위(P0)

1. `/ivr/sessions`
2. `/ivr/sessions/{id}/turns`
3. `/ivr/sessions/{id}/classify`
4. `/ivr/bookings`
5. `/ivr/bookings/lookup`
6. `/bookings/{id}/cancel`
7. `/missions/generate`
8. `/missions/{id}/dispatch`
9. `/missions/{id}/events`
10. `/operator/missions/{id}/estop`
11. `/operator/missions/{id}/teleop`
12. `/operator/missions/{id}/recover`
13. `/verifications`
14. `/consents`
15. `/vitals`
16. `/consultations`
17. `/consultations/{id}/tokens`
18. `/consultations/{id}/complete`
19. `/prescriptions`
20. `/prescriptions/{id}/send`
21. `/followups`
22. `/guardian/cases/{id}`

---

## 18. 다음 구현 단계 제안

1. 이 명세 기준으로 OpenAPI 3.1 YAML 확정  
2. Spring Boot 패키지/모듈 구조 매핑  
3. DB DDL + enum + unique index 정의  
4. API 계약 테스트(Pact or Spring Cloud Contract) 작성  
5. Docker Compose에서 `backend`, `ai-stt`, `ai-triage`, `livekit`, `coturn`, `postgres`, `redis`, `rabbitmq` 연결  
