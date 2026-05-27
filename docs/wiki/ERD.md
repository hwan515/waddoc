# 도서·산간 방문형 비대면 진료 서비스 — ERD

> Status: Canonical
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Purpose: 도메인 엔티티, 상태 enum, 핵심 관계, DB 제약 조건을 정의하는 데이터 모델 기준 문서입니다.

> **기준 문서**: `MVP_Requirements_v2.md`, `API_Specification.md`, `Architecture.md`
>
> **범위**: 본 문서는 `core-app`의 주 영속 스키마를 중심으로 표현한다. `notification-service`의 별도 DB(`waddoc_notification`)는 4장 메모에서 함께 설명한다.

---

## 1. ERD 다이어그램

```mermaid
erDiagram
    %% ============ 사용자 / 인증 ============
    USER {
        bigint user_id PK
        varchar public_id UK "외부 노출 ID (usr_xxxx)"
        varchar username UK "로그인 ID"
        varchar password_hash "bcrypt 해시"
        varchar name
        enum role "ADMIN | DOCTOR | GUARDIAN"
        boolean is_active "계정 활성화 여부 (승인 완료 후 true)"
        enum approval_status "PENDING | APPROVED | REJECTED"
        timestamp approval_requested_at
        bigint approved_by_user_id FK "USER.user_id 참조 (승인 관리자, nullable)"
        timestamp approved_at
        timestamp created_at
        timestamp updated_at
    }

    %% ============ 환자 ============
    PATIENT {
        bigint patient_id PK
        varchar public_id UK "외부 노출 ID (pat_xxxx)"
        varchar name
        date birth_date "원본 생년월일 (YYYY-MM-DD)"
        char birth_date6 "조회용 6자리 (YYMMDD)"
        enum gender "MALE | FEMALE | UNKNOWN"
        varchar region_code "지역 코드 (ex: ULLEUNG)"
        varchar address
        varchar phone UK "환자 휴대전화 번호 (01012345678)"
        varchar reference_image_path "기준 이미지 상대경로 (nullable)"
        bigint reference_image_uploaded_by_user_id FK "등록 관리자 USER.user_id"
        timestamp reference_image_updated_at
        timestamp created_at
        timestamp updated_at
    }

    PATIENT_GUARDIAN_LINK {
        bigint link_id PK
        varchar public_id UK "외부 노출 ID (link_xxxx)"
        bigint patient_id FK
        bigint guardian_user_id FK
        bigint approved_by_user_id FK "USER.user_id 참조 (승인 관리자, nullable)"
        varchar relation "관계 (자녀, 배우자 등)"
        enum status "PENDING | APPROVED | REJECTED"
        timestamp requested_at
        timestamp approved_at
    }

    %% ============ 의사 / 슬롯 ============
    DOCTOR_PROFILE {
        bigint doctor_profile_id PK
        varchar public_id UK "외부 노출 ID (doc_xxxx)"
        bigint user_id FK "USER 테이블 참조"
        varchar department "진료과 코드 (INTERNAL_MEDICINE 등)"
        varchar department_name "진료과 한글명"
        timestamp created_at
    }

    SCHEDULE_SLOT {
        bigint slot_id PK
        varchar public_id UK "외부 노출 ID (slot_xxxx)"
        bigint doctor_id FK "DOCTOR_PROFILE 참조"
        date slot_date
        time start_time
        time end_time
        boolean is_booked
        timestamp created_at
    }

    %% ============ 인테이크 ============
    INTAKE_SESSION {
        bigint intake_session_id PK
        varchar public_id UK "외부 노출 ID (ints_xxxx)"
        bigint patient_id FK "nullable — 식별 전 세션 생성 허용"
        varchar caller_number "발신번호"
        varchar channel "WEB_SIMULATOR | PHONE"
        enum status "STARTED | IN_PROGRESS | COMPLETED | ABANDONED | FAILED"
        varchar completion_reason "BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED"
        timestamp created_at
        timestamp ended_at
        timestamp last_activity_at "무활동 타임아웃 추적용"
        varchar selected_department "선택된 진료과 코드"
        varchar selected_department_name "선택된 진료과 한글명"
        varchar selection_reason "선택 사유"
        enum selection_confidence_level "HIGH | MEDIUM | LOW"
        boolean selection_is_emergency "응급 여부"
        text offered_slot_ids_json "안내된 슬롯 ID 스냅샷 (JSON 문자열)"
        timestamp selection_updated_at "진료과 선택 갱신 시각"
    }

    %% ============ 예약 ============
    BOOKING {
        bigint booking_id PK
        varchar public_id UK "외부 노출 ID (bk_xxxx)"
        bigint patient_id FK
        bigint intake_session_id FK
        bigint slot_id FK
        bigint doctor_id FK "DOCTOR_PROFILE.doctor_profile_id 참조"
        varchar channel "WEB_SIMULATOR | PHONE"
        date appointment_date
        varchar region_code "예약 시점 환자 권역 스냅샷"
        time start_time
        time end_time
        enum status "CONFIRMED | CANCELLED | COMPLETED | NO_SHOW"
        varchar cancel_reason
        timestamp cancelled_at
        timestamp created_at
        timestamp updated_at
    }

    %% ============ 케이스 ============
    CARE_CASE {
        bigint case_id PK
        varchar public_id UK "외부 노출 ID (case_xxxx)"
        bigint booking_id FK UK "1:1 연결"
        bigint patient_id FK
        bigint doctor_id FK "DOCTOR_PROFILE.doctor_profile_id 참조"
        bigint intake_session_id FK
        enum status "CREATED | PREPARING | IN_PROGRESS | COMPLETED | FAILED | CANCELLED"
        timestamp created_at
        timestamp updated_at
    }

    VEHICLE {
        bigint vehicle_id PK
        varchar public_id UK "외부 노출 ID (veh_xxxx)"
        varchar code UK "차량 코드"
        varchar region_code "권역 코드"
        varchar display_name "차량 표시명"
        boolean is_active "활성 여부"
        enum operational_status "OPERATIONAL | OUT_OF_SERVICE | MAINTENANCE"
        timestamp status_changed_at
        varchar status_reason
        timestamp created_at
        timestamp updated_at
    }

    DISPATCH_OUTBOX {
        bigint outbox_id PK
        bigint case_id FK
        varchar region_code "권역 코드"
        text destination "목적지 주소"
        enum status "PENDING | PUBLISHED | RETRY_PENDING | COMPLETED"
        timestamp created_at
    }

    BUSINESS_EVENT_OUTBOX {
        bigint business_event_outbox_id PK
        varchar event_id UK
        varchar event_type
        varchar aggregate_id "aggregate public_id"
        varchar correlation_id
        varchar producer
        timestamptz occurred_at
        text payload_json
        enum status "PENDING | PUBLISHED"
        timestamp created_at
    }

    %% ============ 미션 (차량 출동) ============
    MISSION {
        bigint mission_id PK
        varchar public_id UK "외부 노출 ID (ms_xxxx)"
        bigint case_id FK
        varchar vehicle_id "차량 public_id (논리 참조)"
        text destination "목적지 주소"
        int target_waypoint_number "MQTT 출동 대상 waypoint 번호 (nullable)"
        timestamp dispatched_at
        timestamp estimated_arrival_time
        enum phase "CREATED | DISPATCHED | EN_ROUTE | ARRIVED | VERIFYING | CONSULTING | RETURNING | COMPLETED | FAILED | INCIDENT(임시)"
        enum previous_phase "이전 단계"
        decimal latitude "현재 위도"
        decimal longitude "현재 경도"
        varchar last_telemetry_source_event_id "최근 telemetry sourceEventId"
        bigint last_telemetry_seq_no "최근 telemetry seqNo"
        timestamp last_telemetry_at "최근 telemetry timestamp"
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
    }

    VITAL_MEASUREMENT {
        bigint vital_measurement_id PK
        bigint case_id FK UK
        decimal temperature "체온"
        int blood_pressure_sys "수축기 혈압"
        int blood_pressure_dia "이완기 혈압"
        int heart_rate "심박수"
        int spo2 "산소포화도"
        jsonb ecg_waveform_json "측정 시점 ECG 샘플 파형"
        int ecg_sampling_hz "ECG 샘플링 주파수"
        int ecg_duration_seconds "ECG 샘플 길이(초)"
        timestamp measured_at "마지막 측정 시각"
        timestamp created_at
        timestamp updated_at
    }

    %% ============ 화상진료 세션 ============
    CONSULTATION_SESSION {
        bigint session_id PK
        varchar public_id UK "외부 노출 ID (ses_xxxx)"
        bigint case_id FK
        enum status "CREATED | READY | IN_PROGRESS | COMPLETED | FAILED | ABANDONED"
        varchar room_id "LiveKit room ID"
        varchar livekit_url
        enum doctor_connection_state "CONNECTED | RECONNECTING | DISCONNECTED"
        enum patient_connection_state "CONNECTED | RECONNECTING | DISCONNECTED"
        timestamp doctor_joined_at
        timestamp patient_joined_at
        timestamp started_at
        timestamp ended_at
        int duration_minutes
        timestamp created_at
    }

    CONSULTATION_SUMMARY {
        bigint summary_id PK
        bigint session_id FK UK
        text summary_note "진료 요약(환자 증상 및 소견서)"
        boolean is_prescription_issued "처방 여부"
        text prescription_note "처방 메모(의약품)"
        boolean needs_follow_up "재진 필요 여부"
        timestamp created_at
    }

    %% ============ 관계 정의 ============
    USER ||--o| DOCTOR_PROFILE : "is doctor"
    USER ||--o{ USER : "approves user"
    USER ||--o{ PATIENT : "uploads reference image"
    USER ||--o{ PATIENT_GUARDIAN_LINK : "guardian links"
    USER ||--o{ PATIENT_GUARDIAN_LINK : "approves guardian link"
    PATIENT ||--o{ PATIENT_GUARDIAN_LINK : "has guardians"
    PATIENT ||--o{ INTAKE_SESSION : "starts intake"
    PATIENT ||--o{ BOOKING : "makes bookings"
    PATIENT ||--o{ CARE_CASE : "has cases"

    DOCTOR_PROFILE ||--o{ SCHEDULE_SLOT : "has slots"
    DOCTOR_PROFILE ||--o{ BOOKING : "assigned bookings"
    DOCTOR_PROFILE ||--o{ CARE_CASE : "assigned cases"

    INTAKE_SESSION ||--o{ BOOKING : "can lead to bookings"

    SCHEDULE_SLOT ||--o| BOOKING : "booked by"

    BOOKING ||--|| CARE_CASE : "creates case 1:1"

    CARE_CASE ||--o{ DISPATCH_OUTBOX : "enqueues dispatch"
    CARE_CASE ||--o| MISSION : "has mission"
    CARE_CASE ||--o| VITAL_MEASUREMENT : "stores latest vitals"
    CARE_CASE ||--o| CONSULTATION_SESSION : "has session"
    VEHICLE ||--o{ MISSION : "serves (logical)"

    CONSULTATION_SESSION ||--o| CONSULTATION_SUMMARY : "produces summary"
```

---

## 2. 엔티티 설명

### 2.1 사용자 / 인증 도메인

| 테이블 | 설명 |
|--------|------|
| `USER` | 시스템 사용자 (의사, 관리자, 보호자). 환자는 계정이 없으므로 별도 `PATIENT` 테이블로 관리. `public_id`로 외부 API 노출. 관리자 계정은 즉시 활성화되며, 의사/보호자 계정은 관리자 승인 전까지 `approval_status=PENDING`, `is_active=false` |

> Refresh Token은 Redis에서 관리한다 (`refresh:{token}` 키). DB 기반 세션 테이블은 사용하지 않는다.

### 2.2 환자 도메인

| 테이블 | 설명 |
|--------|------|
| `PATIENT` | 환자 기본 정보. `birth_date`(원본)와 `birth_date6`(조회용 6자리) 분리. 환자 휴대전화 번호를 직접 보관하며, GPU 본인확인에 사용하는 기준 이미지 경로/등록 관리자/최근 갱신 시각도 함께 관리 |
| `PATIENT_GUARDIAN_LINK` | 보호자 회원가입 시 환자 전화번호로 환자를 식별해 생성되는 연결 요청/승인 정보. 상태: `PENDING → APPROVED \| REJECTED` |

### 2.3 의사 / 슬롯 도메인

| 테이블 | 설명 |
|--------|------|
| `DOCTOR_PROFILE` | 의사 프로필 (진료과). `USER` 테이블과 1:1 |
| `SCHEDULE_SLOT` | 의사별 예약 가능 슬롯. 예약 시 `is_booked = true`로 전환 |

### 2.4 인테이크 도메인

| 테이블 | 설명 |
|--------|------|
| `INTAKE_SESSION` | 전화/웹 시뮬레이터 인테이크 세션. **`patient_id`는 nullable** — 세션 시작 시 환자가 아직 식별되지 않을 수 있으므로, 식별 완료 후 바인딩한다. 채널은 `WEB_SIMULATOR | PHONE`을 사용하며, 과 선택 결과·안내 슬롯 스냅샷을 세션 자체에 저장한다 (`selected_department` 등). 메뉴 선택/식별/추천/예약 흐름은 `last_activity_at` 갱신으로 추적한다 |

### 2.5 예약 / 케이스 / 미션 도메인

| 테이블 | 설명 |
|--------|------|
| `BOOKING` | 예약 정보. 예약 시점 환자의 `region_code` 스냅샷을 함께 저장하며, 채널은 현재 `WEB_SIMULATOR | PHONE`을 사용한다. 상태는 `CONFIRMED → CANCELLED \| COMPLETED \| NO_SHOW` |
| `CARE_CASE` | 진료 케이스. 예약과 1:1. 상태: `CREATED → PREPARING → IN_PROGRESS → COMPLETED \| FAILED \| CANCELLED` |
| `VEHICLE` | 권역별 실제 운행 차량. 운영 상태(`OPERATIONAL`, `OUT_OF_SERVICE`, `MAINTENANCE`)와 최근 상태 변경 시각/사유를 관리 |
| `DISPATCH_OUTBOX` | 예약 확정 후 자동 배차를 위해 적재되는 outbox 테이블. Kafka publish와 DB 트랜잭션 사이를 분리하며 상태는 `PENDING → PUBLISHED → RETRY_PENDING → COMPLETED` |
| `BUSINESS_EVENT_OUTBOX` | 예약 확정/취소 같은 비즈니스 이벤트를 트랜잭션 밖에서 재전송 가능하게 적재하는 generic outbox. `aggregate_id`는 FK가 아니라 aggregate `public_id`를 저장하는 논리 참조다 |
| `MISSION` | 차량 출동. 현재 위치(latitude/longitude), 배차 시각(`dispatched_at`), ETA, 최근 telemetry 메타데이터와 단계(phase)를 직접 관리한다. `vehicle_id`는 현재 `VEHICLE.public_id`를 논리 참조하고, `target_waypoint_number`는 MQTT 토픽(`robot/cmd/dispatch`)으로 전달할 waypoint 번호를 저장한다 |
| `VITAL_MEASUREMENT` | 진료 케이스별 최신 생체데이터 1건. 로봇 측정 단계마다 같은 `case_id` row를 partial upsert 하며, 체온/혈압/심박수/SpO2와 측정 시점 ECG sample, `measured_at`, `created_at`, `updated_at`을 함께 관리 |

### 2.6 화상진료 세션 도메인

| 테이블 | 설명 |
|--------|------|
| `CONSULTATION_SESSION` | LiveKit WebRTC 1:1 세션. 의사/환자 connection_state 분리 관리 |
| `CONSULTATION_SUMMARY` | 진료 요약, 처방 여부, 재진 필요 여부 기록 |

---

## 3. 상태 Enum 정의

| 엔티티 | 상태 흐름 |
|--------|----------|
| `BOOKING.status` | `CONFIRMED → CANCELLED \| COMPLETED \| NO_SHOW` |
| `CARE_CASE.status` | `CREATED → PREPARING → IN_PROGRESS → COMPLETED \| FAILED \| CANCELLED` |
| `MISSION.phase` | `CREATED → DISPATCHED → EN_ROUTE → ARRIVED → VERIFYING → CONSULTING → RETURNING → COMPLETED \| FAILED` ※ `INCIDENT`는 임시 상태 (복구 후 이전 단계 복귀) |
| `VEHICLE.operational_status` | `OPERATIONAL \| OUT_OF_SERVICE \| MAINTENANCE` |
| `DISPATCH_OUTBOX.status` | `PENDING → PUBLISHED → RETRY_PENDING → COMPLETED` |
| `BUSINESS_EVENT_OUTBOX.status` | `PENDING → PUBLISHED` |
| `CONSULTATION_SESSION.status` | `CREATED → READY → IN_PROGRESS → COMPLETED \| FAILED \| ABANDONED` |
| `CONNECTION_STATE` | `CONNECTED \| RECONNECTING \| DISCONNECTED` |
| `INTAKE_SESSION.status` | `STARTED → IN_PROGRESS → COMPLETED \| ABANDONED \| FAILED` |
| `INTAKE_SESSION.selectionConfidenceLevel` | `HIGH \| MEDIUM \| LOW` |
| `USER.role` | `ADMIN \| DOCTOR \| GUARDIAN` |
| `USER.approval_status` | `PENDING → APPROVED \| REJECTED` |
| `PATIENT_GUARDIAN_LINK.status` | `PENDING → APPROVED \| REJECTED` |

> **`doctorId` 참조 규칙**: API의 `doctorId`는 `DOCTOR_PROFILE.public_id` 값을 의미한다. 내부 저장은 `doctor_profile_id`(`bigint` PK)를 사용한다. 사용자 식별이 필요할 때는 별도로 `userId`를 사용한다.
>
> **이중 ID 전략**: 내부 PK는 `bigint` 자동 증가를 사용한다. 외부 API에는 `public_id`를 노출하며, 대부분 접두사 + 생성 ID 패턴을 따르지만 차량처럼 시드/운영 정책에 따라 고정값(`veh_GIMCHEON_01`)이 들어가는 예외도 있다. 따라서 외부 연동은 `public_id` 형식을 엄격한 패턴으로 가정하지 말고 값 자체를 식별자로 취급해야 한다.
>
> **PATIENT.phone 정책**: 전화번호는 전역 unique 제약을 적용한다. 1번호=1환자 원칙이며, 가족 공용번호 사용은 허용하지 않는다. 보호자 회원가입 및 전화 예약 식별은 이 컬럼을 기준으로 환자를 찾는다.
>
> **추가 인증 주체**: `USER.role` 외에 JWT `tokenType` claim으로 `MISSION_TERMINAL`(미션 터미널 — 차량 태블릿)과 `DEVICE_TERMINAL`(디바이스 터미널 — 부트스트랩 인증)을 구분한다. 이들은 DB 엔티티가 아니라 JWT 기반 임시 인증 주체다.

---

## 4. 핵심 관계 요약

```
PATIENT ←1:N→ PATIENT_GUARDIAN_LINK     (보호자 연결)
USER    ←1:N→ PATIENT_GUARDIAN_LINK     (보호자 계정)
USER    ←1:N→ PATIENT_GUARDIAN_LINK     (승인 관리자)
USER    ←1:N→ USER                      (의사/보호자 계정 승인)

PATIENT → INTAKE_SESSION (과 선택·슬롯 스냅샷 포함)    (기본 전화 예약 흐름)

INTAKE_SESSION → BOOKING                                 (현재 DB는 intake_session_id unique를 강제하지 않으므로 1:N 허용)
PATIENT → BOOKING → CARE_CASE → DISPATCH_OUTBOX → MISSION   (예약 확정 → created mission + 출동 트리거)
                 └→ BUSINESS_EVENT_OUTBOX                (예약 확정/취소 이벤트 영속화, FK 없는 논리 연결)
                                → VITAL_MEASUREMENT          (로봇 측정 최신값 저장)
                                → CONSULTATION_SESSION → CONSULTATION_SUMMARY

VEHICLE → MISSION                                           (권역 차량 배정)
USER(DOCTOR) → DOCTOR_PROFILE → SCHEDULE_SLOT → BOOKING     (의사 배정 흐름)
```

> 데모 모드에서는 예약 생성 시 `MISSION(CREATED)`까지 먼저 생성하고, 관리자의 데모 출동 API가 `DISPATCH_OUTBOX`와 `target_waypoint_number`를 사용해 실제 출동 또는 더미 완료를 제어한다.

> 알림 적재는 별도 `notification-service` DB(`waddoc_notification`)에서 수행한다. `core-app`의 `BUSINESS_EVENT_OUTBOX`에 적재된 예약 이벤트가 발행되면 `processed_event`, `notification_log`, `sms_delivery`, `doctor_notification_projection` 같은 알림용 테이블이 채워진다.

> `PATIENT_CONSENT`를 포함한 동의 도메인 ERD 초안은 [archive/P1_Consent_Extension.md](./archive/P1_Consent_Extension.md) 문서를 참조한다.

---

## 5. 주요 DB 제약 조건

| 테이블 | 제약 | 설명 |
|--------|------|------|
| `PATIENT` | `UNIQUE (phone)` | 전역 unique. 1번호=1환자 원칙 |
| `USER` | `INDEX (approval_status)` | 승인 대기/승인/반려 목록 조회 최적화 |
| `PATIENT_GUARDIAN_LINK` | `UNIQUE (patient_id, guardian_user_id)` | 동일 보호자-환자 조합의 중복 가입 이력 방지 |
| `BOOKING` | `UNIQUE (slot_id)` WHERE `status != 'CANCELLED'` | 동일 슬롯 이중 예약 방지 (부분 unique) |
| `BOOKING` | `INDEX (region_code, appointment_date, start_time, end_time)` WHERE `status != 'CANCELLED'` | 권역/시간대 예약 충돌 조회 최적화 |
| `BOOKING` | `UNIQUE (region_code, appointment_date, start_time)` WHERE `status = 'CONFIRMED' AND region_code IS NOT NULL` | 활성 예약 기준 동일 권역 시작 시각 중복 방지 |
| `CARE_CASE` | `UNIQUE (booking_id)` | 예약-케이스 1:1 보장 |
| `VITAL_MEASUREMENT` | `UNIQUE (case_id)` | 케이스별 최신 생체데이터 1건 보장 |
| `VEHICLE` | `UNIQUE (public_id)`, `UNIQUE (code)` | 외부 노출 ID와 운영 코드 유일성 보장 |
| `VEHICLE` | `UNIQUE (region_code)` WHERE `is_active = true` | 동일 권역의 활성 차량 1대 보장 |
| `DISPATCH_OUTBOX` | `INDEX (status, created_at)` WHERE `status = 'PENDING'` | 최초 배차 relay 스캔 최적화 |
| `DISPATCH_OUTBOX` | `INDEX (region_code, status, created_at)` WHERE `status = 'RETRY_PENDING'` | 권역별 재배차 스캔 최적화 |
| `BUSINESS_EVENT_OUTBOX` | `UNIQUE (event_id)`, `INDEX (status, created_at)` | 이벤트 중복 적재 방지 및 relay 스캔 최적화 |
| `CONSULTATION_SUMMARY` | `UNIQUE (session_id)` | 세션당 요약 1건 보장 |
| `public_id` 보유 테이블 | `UNIQUE` | 외부 노출 ID 유일성 보장 (`dispatch_outbox`, `business_event_outbox`, `vital_measurement`, `consultation_summary` 제외) |
