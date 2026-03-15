# 도서·산간 방문형 비대면 진료 서비스 — ERD

> **기준 문서**: `MVP_Requirements_v2.md`, `API_Specification.md`, `Architecture.md`

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
        boolean is_active "계정 활성화 여부"
        timestamp created_at
        timestamp updated_at
    }

    AUTH_SESSION {
        bigint session_id PK
        bigint user_id FK
        varchar refresh_token_hash "RT 해시값"
        varchar token_family "탈취 감지용 family ID"
        varchar device_info "기기 정보"
        boolean is_valid
        timestamp issued_at
        timestamp expires_at
    }

    %% ============ 환자 ============
    PATIENT {
        bigint patient_id PK
        varchar public_id UK "외부 노출 ID (pat_xxxx)"
        varchar name
        date birth_date "원본 생년월일 (YYYY-MM-DD)"
        char birth_date6 "조회용 6자리 (YYMMDD)"
        varchar region_code "지역 코드 (ex: ULLEUNG)"
        varchar address
        timestamp created_at
        timestamp updated_at
    }

    PATIENT_FACE_REFERENCE {
        bigint face_ref_id PK
        varchar public_id UK "외부 노출 ID (face_xxxx)"
        bigint patient_id FK
        varchar photo_path "사진 상대경로"
        bigint uploaded_by FK "USER.user_id — 등록 관리자"
        timestamp deleted_at "소프트 삭제"
        timestamp created_at
    }

    PATIENT_PHONE_BINDING {
        bigint binding_id PK
        bigint patient_id FK
        varchar phone UK "전화번호 (01012345678) — 정책: 전역 unique. 1번호=1환자 원칙. 공용번호(가족 공유) 허용 안 함"
        boolean is_primary
        timestamp created_at
    }

    PATIENT_GUARDIAN_LINK {
        bigint link_id PK
        varchar public_id UK "외부 노출 ID (link_xxxx)"
        bigint patient_id FK
        bigint guardian_user_id FK
        varchar relation "관계 (자녀, 배우자 등)"
        timestamp linked_at
    }

    %% ============ 의사 / 슬롯 ============
    DOCTOR_PROFILE {
        bigint doctor_profile_id PK
        varchar public_id UK "외부 노출 ID (doc_xxxx)"
        bigint user_id FK "USER 테이블 참조"
        varchar department "진료과 코드 (INTERNAL_MEDICINE 등)"
        varchar department_name "진료과 한글명"
        varchar specialty "전문 분야"
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
        varchar channel "WEB_SIMULATOR"
        enum status "STARTED | IN_PROGRESS | COMPLETED | ABANDONED | FAILED"
        varchar completion_reason "BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED"
        timestamp created_at
        timestamp ended_at
        timestamp last_activity_at "마지막 활동 시각"
    }

    INTAKE_TURN {
        bigint turn_id PK
        varchar public_id UK "외부 노출 ID (turn_xxxx)"
        bigint intake_session_id FK
        int turn_order
        enum turn_type "VOICE"
        text prompt "TTS 안내문"
        text stt_text "STT 변환 결과"
        decimal stt_confidence
        varchar exception_code "STT_FAIL | NO_INPUT | AMBIGUOUS_SYMPTOM | EMERGENCY_SUSPECTED"
        varchar next_action "ASK_SYMPTOM | RECOMMEND 등"
        text tts_message "응답 TTS"
        varchar audio_file_path "오디오 파일 상대경로"
        timestamp created_at
    }

    SYMPTOM_INTAKE {
        bigint symptom_id PK
        bigint intake_session_id FK
        text symptom_text "증상 원문"
        varchar symptom_category "분류 결과 (두통/발열 등)"
        boolean is_emergency
        timestamp created_at
    }

    RECOMMENDATION {
        bigint recommendation_id PK
        varchar public_id UK "외부 노출 ID (rec_xxxx)"
        bigint intake_session_id FK
        bigint symptom_id FK
        varchar department "추천 진료과 코드 (INTERNAL_MEDICINE 등)"
        varchar department_name "진료과 한글명"
        enum confidence_level "HIGH | MEDIUM | LOW"
        boolean is_emergency
        text reason "추천 근거"
        timestamp created_at
    }

    %% ============ 예약 ============
    BOOKING {
        bigint booking_id PK
        varchar public_id UK "외부 노출 ID (bk_xxxx)"
        bigint patient_id FK
        bigint intake_session_id FK
        bigint recommendation_id FK
        bigint slot_id FK
        bigint doctor_id FK "DOCTOR_PROFILE.doctor_profile_id 참조"
        varchar channel "WEB_SIMULATOR"
        date appointment_date
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

    %% ============ 미션 (차량 출동) ============
    MISSION {
        bigint mission_id PK
        varchar public_id UK "외부 노출 ID (ms_xxxx)"
        bigint case_id FK
        varchar vehicle_id "차량 ID"
        text destination "목적지 주소"
        enum phase "CREATED | DISPATCHED | EN_ROUTE | ARRIVED | VERIFYING | CONSULTING | RETURNING | COMPLETED | FAILED | INCIDENT(임시)"
        varchar previous_phase "INCIDENT 복귀 시 이전 단계"
        timestamp scheduled_time
        timestamp dispatched_at
        timestamp estimated_arrival_time
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
    }

    MISSION_EVENT {
        bigint event_id PK
        bigint mission_id FK
        enum event_type "LOCATION_UPDATE | PHASE_CHANGED | INCIDENT_REPORTED | INCIDENT_RESOLVED"
        varchar source "이벤트 발생 출처 (ROS2, ADMIN 등)"
        varchar source_event_id "원본 이벤트 고유 ID (중복 방지용)"
        int seq_no "미션 내 이벤트 순번"
        varchar vehicle_id
        varchar from_phase
        varchar to_phase
        decimal latitude
        decimal longitude
        decimal speed
        int heading
        jsonb metadata
        timestamp event_timestamp
    }

    %% ============ 본인확인 ============
    VERIFICATION {
        bigint verification_id PK
        varchar public_id UK "외부 노출 ID (vrf_xxxx)"
        bigint mission_id FK
        bigint patient_id FK
        enum status "PENDING | IN_PROGRESS | VERIFIED | FAILED | MANUAL_REVIEW | TIMEOUT"
        varchar probe_image_path "촬영 사진 상대경로"
        decimal similarity_score
        int attempt_count "현재 시도 횟수"
        int max_attempts "최대 시도 횟수 (3)"
        boolean face_detected
        boolean single_face
        decimal blur_score
        boolean brightness_ok
        bigint approved_by FK "수동 승인 관리자 user_id"
        varchar approved_reason
        timestamp approved_at
        timestamp created_at
        timestamp updated_at
    }

    %% ============ 바이탈 ============
    VITAL_RECORD {
        bigint vital_record_id PK
        varchar public_id UK "외부 노출 ID (vit_xxxx)"
        bigint case_id FK
        varchar source "MANUAL"
        timestamp measured_at
        timestamp recorded_at
    }

    VITAL_MEASUREMENT {
        bigint measurement_id PK
        bigint vital_record_id FK
        enum type "HEART_RATE | SPO2 | BLOOD_PRESSURE_SYSTOLIC | BLOOD_PRESSURE_DIASTOLIC | TEMPERATURE"
        decimal value
        varchar unit "bpm, %, mmHg, C"
        boolean is_normal
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
        int reconnect_count
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
        text summary_note "진료 요약"
        boolean is_prescription_issued "처방 여부"
        text prescription_note "처방 메모"
        boolean needs_follow_up "재진 필요 여부"
        text follow_up_note "재진 메모"
        timestamp created_at
    }

    %% ============ 알림 ============
    NOTIFICATION {
        bigint notification_id PK
        varchar public_id UK "외부 노출 ID (ntf_xxxx)"
        bigint user_id FK "수신 대상 USER (nullable, 웹 알림용)"
        bigint patient_id FK "수신 대상 환자 (nullable, SMS용)"
        enum channel "WEB | SMS"
        enum type "BOOKING_CREATED | BOOKING_CANCELLED | MISSION_PHASE_CHANGED | VERIFICATION_COMPLETED | VERIFICATION_MANUAL_REVIEW | SESSION_READY | SESSION_ABANDONED | CONSULTATION_COMPLETED"
        varchar title
        text message
        boolean is_read
        timestamp created_at
    }

    SMS_LOG {
        bigint sms_log_id PK
        varchar public_id UK "외부 노출 ID (sms_xxxx)"
        bigint notification_id FK "NOTIFICATION 참조"
        varchar recipient_phone "수신 전화번호"
        varchar sender_phone "발신 전화번호"
        text sms_body "실제 발송 메시지 본문"
        enum send_status "PENDING | SENT | FAILED"
        varchar vendor "SOLAPI"
        varchar vendor_group_id "외부 발송 그룹 ID"
        varchar failure_reason "실패 사유"
        timestamp sent_at
        timestamp created_at
    }

    %% ============ 감사 로그 ============
    AUDIT_LOG {
        bigint log_id PK
        varchar public_id UK "외부 노출 ID (log_xxxx)"
        timestamp log_timestamp
        varchar actor_id "행위자 ID"
        enum actor_role "ADMIN | DOCTOR | GUARDIAN | SYSTEM"
        varchar action "USER_LOGIN | BOOKING_CREATED 등"
        varchar target_type "BOOKING | CONSULTATION_SESSION 등"
        varchar target_id
        varchar correlation_id "전 구간 흐름 추적용"
        jsonb detail_json "추가 상세 정보"
    }

    %% ============ 관계 정의 ============
    USER ||--o{ AUTH_SESSION : "has sessions"
    USER ||--o| DOCTOR_PROFILE : "is doctor"
    USER ||--o{ PATIENT_GUARDIAN_LINK : "guardian links"
    USER ||--o{ NOTIFICATION : "receives (web)"

    PATIENT ||--o{ PATIENT_PHONE_BINDING : "has phones"
    PATIENT ||--o{ PATIENT_GUARDIAN_LINK : "has guardians"
    PATIENT ||--o{ PATIENT_FACE_REFERENCE : "has face photos"
    PATIENT ||--o{ INTAKE_SESSION : "starts intake"
    PATIENT ||--o{ BOOKING : "makes bookings"
    PATIENT ||--o{ CARE_CASE : "has cases"
    PATIENT ||--o{ VERIFICATION : "verified in"
    PATIENT ||--o{ NOTIFICATION : "receives (sms)"

    DOCTOR_PROFILE ||--o{ SCHEDULE_SLOT : "has slots"
    DOCTOR_PROFILE ||--o{ BOOKING : "assigned bookings"
    DOCTOR_PROFILE ||--o{ CARE_CASE : "assigned cases"

    INTAKE_SESSION ||--o{ INTAKE_TURN : "has turns"
    INTAKE_SESSION ||--o| SYMPTOM_INTAKE : "collects symptom"
    INTAKE_SESSION ||--o{ RECOMMENDATION : "generates recs"
    INTAKE_SESSION ||--o| BOOKING : "leads to booking"

    SYMPTOM_INTAKE ||--o{ RECOMMENDATION : "results in"

    SCHEDULE_SLOT ||--o| BOOKING : "booked by"

    BOOKING ||--|| CARE_CASE : "creates case 1:1"

    CARE_CASE ||--o| MISSION : "has mission"
    CARE_CASE ||--o{ VITAL_RECORD : "has vitals"
    CARE_CASE ||--o| CONSULTATION_SESSION : "has session"

    MISSION ||--o{ MISSION_EVENT : "has events"
    MISSION ||--o| VERIFICATION : "triggers verification"

    VITAL_RECORD ||--|{ VITAL_MEASUREMENT : "contains measurements"

    CONSULTATION_SESSION ||--o| CONSULTATION_SUMMARY : "produces summary"

    NOTIFICATION ||--o| SMS_LOG : "has sms log"
```

---

## 2. 엔티티 설명

### 2.1 사용자 / 인증 도메인

| 테이블 | 설명 |
|--------|------|
| `USER` | 시스템 사용자 (의사, 관리자, 보호자). 환자는 계정이 없으므로 별도 `PATIENT` 테이블로 관리. `public_id`로 외부 API 노출 |
| `AUTH_SESSION` | Refresh Token 관리. Token Rotation과 Token Family 기반 탈취 감지 지원 |

### 2.2 환자 도메인

| 테이블 | 설명 |
|--------|------|
| `PATIENT` | 환자 기본 정보. 얼굴 사진은 `PATIENT_FACE_REFERENCE`에서 관리. `birth_date`(원본)와 `birth_date6`(조회용 6자리) 분리 |
| `PATIENT_FACE_REFERENCE` | 환자 사전 등록 얼굴 사진. MVP에서는 관리자가 직접 등록하며, `deleted_at IS NULL`인 사진이 1건 이상 존재해야 본인확인 가능. 첫 방문 현장 촬영 후 관리자 승인 등록은 P1 |
| `PATIENT_PHONE_BINDING` | 환자 전화번호 바인딩. 1차/2차 식별에 사용. 전역 unique 제약 (1번호=1환자) |
| `PATIENT_GUARDIAN_LINK` | 환자-보호자 연결 (관리자가 사전 등록) |

### 2.3 의사 / 슬롯 도메인

| 테이블 | 설명 |
|--------|------|
| `DOCTOR_PROFILE` | 의사 프로필 (진료과, 전문 분야). `USER` 테이블과 1:1 |
| `SCHEDULE_SLOT` | 의사별 예약 가능 슬롯. 예약 시 `is_booked = true`로 전환 |

### 2.4 인테이크 도메인

| 테이블 | 설명 |
|--------|------|
| `INTAKE_SESSION` | 전화 시뮬레이터 인테이크 세션. **`patient_id`는 nullable** — 세션 시작 시 환자가 아직 식별되지 않을 수 있으므로, 식별 완료 후 바인딩한다 |
| `INTAKE_TURN` | 턴별 VOICE 입력, STT 결과, 예외 코드 기록 |
| `SYMPTOM_INTAKE` | 수집된 증상 원문 및 분류 결과 |
| `RECOMMENDATION` | 진료과/의사 추천 결과 및 가용 슬롯 |

### 2.5 예약 / 케이스 / 미션 도메인

| 테이블 | 설명 |
|--------|------|
| `BOOKING` | 예약 정보. 상태: `CONFIRMED → CANCELLED \| COMPLETED \| NO_SHOW` |
| `CARE_CASE` | 진료 케이스. 예약과 1:1. 상태: `CREATED → PREPARING → IN_PROGRESS → COMPLETED` |
| `MISSION` | 차량 출동. 단계: `CREATED → DISPATCHED → EN_ROUTE → ARRIVED → … → COMPLETED` |
| `MISSION_EVENT` | 차량 이벤트 (위치 업데이트, 단계 변경, 장애 보고/복구). `UNIQUE (source, source_event_id)` 제약으로 중복 수신 방지 |

### 2.6 본인확인 도메인

| 테이블 | 설명 |
|--------|------|
| `VERIFICATION` | 사전 등록 사진 vs 현장 촬영 비교 결과. 최대 3회 재시도. 3차 실패 시 `MANUAL_REVIEW`. 여기서의 수동 개입은 현장 본인확인 실패 처리이며, 기준 사진 최초 등록 승인과는 별개 |

> 동의 도메인(`PATIENT_CONSENT`)은 P1 별도 문서 [P1_Consent_Extension.md](./P1_Consent_Extension.md)에서 관리한다.

> **확장형 본인확인 (P1 선택)**: 신분증 OCR + 얼굴 3자 대조를 도입하는 경우, `VERIFICATION` 확장 컬럼 또는 별도 스냅샷 테이블(`VERIFICATION_EVIDENCE`)에 다음 저장 항목이 추가로 필요하다.
>
> - `face_image_path`
> - `id_card_image_path`
> - `ocr_name`
> - `ocr_rrn_masked`
> - `ocr_address`
> - `ocr_confidence`
> - `live_vs_registered_score`
> - `live_vs_id_card_face_score`
> - `id_card_face_vs_registered_score`
> - `reason_codes_json`
>
> 주민등록번호 전체 원문은 저장하지 않는 것을 원칙으로 한다.

### 2.7 바이탈 도메인

| 테이블 | 설명 |
|--------|------|
| `VITAL_RECORD` | 바이탈 측정 단위 (케이스당). MVP에서는 더미 데이터 수동 입력 |
| `VITAL_MEASUREMENT` | 개별 측정 항목 (심박수, SpO2, 혈압, 체온) |

### 2.8 화상진료 세션 도메인

| 테이블 | 설명 |
|--------|------|
| `CONSULTATION_SESSION` | LiveKit WebRTC 1:1 세션. 의사/환자 connection_state 분리 관리 |
| `CONSULTATION_SUMMARY` | 진료 요약, 처방 여부, 재진 필요 여부 기록 |

### 2.9 알림 / 감사 로그 도메인

| 테이블 | 설명 |
|--------|------|
| `NOTIFICATION` | 알림. `channel`로 WEB/SMS 구분. 웹 알림은 `user_id`(SSE/Polling), 환자 SMS 알림은 `patient_id` 참조 |
| `SMS_LOG` | SMS 발송 기록. SOLAPI 게이트웨이 연동. 발송 상태(`send_status`) 추적 |
| `AUDIT_LOG` | 전체 행위 감사 로그. `correlation_id`로 전 구간 추적 가능 |

---

## 3. 상태 Enum 정의

| 엔티티 | 상태 흐름 |
|--------|----------|
| `BOOKING.status` | `CONFIRMED → CANCELLED \| COMPLETED \| NO_SHOW` |
| `CARE_CASE.status` | `CREATED → PREPARING → IN_PROGRESS → COMPLETED \| FAILED \| CANCELLED` |
| `MISSION.phase` | `CREATED → DISPATCHED → EN_ROUTE → ARRIVED → VERIFYING → CONSULTING → RETURNING → COMPLETED \| FAILED` ※ `INCIDENT`는 임시 상태 (복구 후 이전 단계 복귀) |
| `CONSULTATION_SESSION.status` | `CREATED → READY → IN_PROGRESS → COMPLETED \| FAILED \| ABANDONED` |
| `CONNECTION_STATE` | `CONNECTED \| RECONNECTING \| DISCONNECTED` |
| `VERIFICATION.status` | `PENDING → IN_PROGRESS → VERIFIED \| FAILED \| MANUAL_REVIEW \| TIMEOUT` |
| `INTAKE_SESSION.status` | `STARTED → IN_PROGRESS → COMPLETED \| ABANDONED \| FAILED` |
| `RECOMMENDATION.confidenceLevel` | `HIGH \| MEDIUM \| LOW` |
| `VITAL_MEASUREMENT.type` | `HEART_RATE \| SPO2 \| BLOOD_PRESSURE_SYSTOLIC \| BLOOD_PRESSURE_DIASTOLIC \| TEMPERATURE` |
| `USER.role` | `ADMIN \| DOCTOR \| GUARDIAN` |

> **`doctorId` 참조 규칙**: API의 `doctorId`는 `DOCTOR_PROFILE.public_id` 값을 의미한다. 내부 저장은 `doctor_profile_id`(`bigint` PK)를 사용한다. 사용자 식별이 필요할 때는 별도로 `userId`를 사용한다.
>
> **이중 ID 전략**: 내부 PK는 `bigint` 자동 증가. 외부 API에는 `public_id`(접두사 + nanoid, 예: `pat_V1StGXR8`)를 노출한다. PK 추론을 방지하고 API 가독성을 높인다.
>
> **MISSION_EVENT 중복 방지**: `UNIQUE (source, source_event_id)` 제약을 적용하여 ROS2 등 외부 소스의 이벤트 중복 수신을 방지한다.
>
> **PATIENT_PHONE_BINDING 정책**: 전화번호는 전역 unique 제약을 적용한다. 1번호=1환자 원칙이며, 가족 공용번호 사용은 허용하지 않는다. 보호자가 환자 대신 예약하려면 보호자 본인 계정으로 로그인해야 한다.

---

## 4. 핵심 관계 요약

```
PATIENT ←1:N→ PATIENT_PHONE_BINDING    (전화번호 바인딩)
PATIENT ←1:N→ PATIENT_GUARDIAN_LINK     (보호자 연결)
USER    ←1:N→ PATIENT_GUARDIAN_LINK     (보호자 계정)

PATIENT → INTAKE_SESSION → INTAKE_TURN       (인테이크 흐름)
                         → SYMPTOM_INTAKE
                         → RECOMMENDATION

PATIENT → BOOKING → CARE_CASE → MISSION → MISSION_EVENT     (진료 라이프사이클)
                               → VERIFICATION
                               → VITAL_RECORD → VITAL_MEASUREMENT
                               → CONSULTATION_SESSION → CONSULTATION_SUMMARY

USER(DOCTOR) → DOCTOR_PROFILE → SCHEDULE_SLOT → BOOKING     (의사 배정 흐름)
```

> `PATIENT_CONSENT`를 포함한 동의 도메인 ERD는 [P1_Consent_Extension.md](./P1_Consent_Extension.md) 문서를 참조한다.

---

## 5. 주요 DB 제약 조건

| 테이블 | 제약 | 설명 |
|--------|------|------|
| `PATIENT_PHONE_BINDING` | `UNIQUE (phone)` | 전역 unique. 1번호=1환자 원칙 |
| `MISSION_EVENT` | `UNIQUE (source, source_event_id)` | ROS2 등 외부 소스의 이벤트 중복 수신 방지 |
| `BOOKING` | `UNIQUE (slot_id)` WHERE `status != 'CANCELLED'` | 동일 슬롯 이중 예약 방지 (부분 unique) |
| `CARE_CASE` | `UNIQUE (booking_id)` | 예약-케이스 1:1 보장 |
| `CONSULTATION_SUMMARY` | `UNIQUE (session_id)` | 세션당 요약 1건 보장 |
| `INTAKE_TURN` | `UNIQUE (intake_session_id, turn_order)` | 세션 내 턴 순서 중복 방지 |
| 모든 테이블 `public_id` | `UNIQUE` | 외부 노출 ID 유일성 보장 |
