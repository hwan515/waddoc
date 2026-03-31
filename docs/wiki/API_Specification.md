# 도서·산간 방문형 비대면 진료 서비스 — API 명세서 v1 (MVP)

> **기준 문서**: `MVP_Requirements_v2.md`, `Architecture.md`
>
> **Base URL**: `/api/v1`
>
> **인증**: 별도 표기가 없으면 `Authorization: Bearer {AccessToken}` 헤더 필수
>
> **공통 에러 응답 형식**:
> ```json
> {
>   "errorCode": "ERR_XXX",
>   "message": "사람이 읽을 수 있는 에러 메시지",
>   "timestamp": "2026-03-11T10:00:00"
> }
> ```
> `timestamp`는 서버 KST 기준 `LocalDateTime` 문자열이다.
> 상관관계 ID(`correlationId`)는 서버 내부 운영 로그에서만 사용하며 현재 에러 응답 바디에는 포함되지 않는다.
>
> **상세 검증 에러 시** `details` 필드를 추가로 포함할 수 있다:
> ```json
> {
>   "errorCode": "BOOKING_SLOT_CONFLICT",
>   "message": "이미 예약된 슬롯입니다.",
>   "timestamp": "2026-03-11T10:00:00",
>   "details": [
>     { "field": "slotId", "reason": "해당 슬롯은 이미 다른 예약에 확정되었습니다." }
>   ]
> }
> ```
>
> **HTTP 메서드 사용 규칙**:
> - **리소스 CRUD**: `GET`(조회), `POST`(생성), `PUT`(전체 교체), `PATCH`(부분 수정), `DELETE`(삭제)를 기본으로 한다.
> - **핵심 도메인 상태 변경**(세션 상태 전이, 미션 단계 전환, 환자 바인딩 등)은 **리소스 기반 `PATCH`**를 우선 사용하고, URL에 동사를 포함하지 않는다.
> - **액션형 `POST` 허용 범위**: 인증/토큰 발급, 환자 식별·검증, 추천·매칭, 예약 취소(`/cancel`), 승인·반려(`/approve`, `/reject`), Webhook, Telemetry 등 부수 효과가 크거나 리소스 수정으로 표현하기 어려운 경우에 한해 `POST` + 행위 경로를 허용한다.
>
> **이중 ID 전략**:
> - DB 내부 PK는 `bigint` 자동 증가이며, 외부 API에는 **`public_id`** (접두사 + nanoid)를 노출한다.
> - API 요청/응답의 모든 ID 필드는 `public_id` 값이다 (예: `userId` → `"usr_V1StGXR8"`, `patientId` → `"pat_Zk3mQ9"`).
> - 접두사 규칙: `usr_` (USER), `pat_` (PATIENT), `doc_` (DOCTOR_PROFILE), `ints_` (INTAKE_SESSION), `slot_` (SCHEDULE_SLOT), `bk_` (BOOKING), `case_` (CARE_CASE), `ms_` (MISSION), `ses_` (CONSULTATION_SESSION), `link_` (PATIENT_GUARDIAN_LINK)
> - Path parameter의 ID도 `public_id` 값을 사용한다 (예: `/api/v1/bookings/bk_Abc123`).

---

## 목차

1. [인증 API](#1-인증-api-apiv1auth)
2. [환자 식별 API](#2-환자-식별-api-apiv1intakesessionsintakesessionididentify)
3. [인테이크(문진) API](#3-인테이크문진-api-apiv1intake)
4. [예약 API](#4-예약-api-apiv1bookings)
5. [케이스 API](#5-케이스-api-apiv1cases)
6. [미션(차량 출동) API](#6-미션차량-출동-api-apiv1missions)
7. [동의 API (P1 별도 문서)](#7-동의-api-p1)
8. [실시간 알림 API](#8-실시간-알림-api-apiv1doctorsmenotifications)
9. [화상진료 세션 API](#9-화상진료-세션-api)
10. [보호자 API](#10-보호자-api-apiv1guardians)
11. [관리자 API](#11-관리자-api-apiv1admin)
12. [로봇 운영 API](#12-로봇-운영-api-apiv1robots)
13. [상태 Enum 정의](#13-상태-enum-정의)

---

## 1. 인증 API (`/api/v1/auth`)

> 의사, 관리자, 보호자의 인증/계정 관련 API다. 환자는 계정이 없으므로 로그인/토큰 API를 사용하지 않는다.
>
> Refresh Token은 `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` 쿠키로 관리된다.
>
> 의사/보호자 회원가입 계정은 모두 관리자 승인 전까지 `approval_status=PENDING`, `is_active=false` 상태다.
> 보호자 회원가입은 `patientPhone`으로 대상 환자를 식별해 신청한다.

### 1.1 보호자 회원가입 신청

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/auth/guardians/signup` |
| Auth | 불필요 |

**Request Body** (`application/json`)
```json
{
  "username": "guardian_lee",
  "password": "Passw0rd!",
  "name": "이보호",
  "patientPhone": "01012345678",
  "relation": "자녀"
}
```

> 서버는 `patientPhone`으로 `PATIENT.phone`을 조회해 대상 환자를 식별하고, `USER(role=GUARDIAN, approval_status=PENDING, is_active=false)`와 `PATIENT_GUARDIAN_LINK(status=PENDING)`를 함께 생성한다.

**Response** `202 Accepted`
```json
{
  "userId": "usr_J2mNp7",
  "linkId": "link_H9kLm3",
  "status": "PENDING",
  "patient": {
    "nameMasked": "홍*동",
    "birthDate6Masked": "5803**"
  },
  "message": "가입 요청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다."
}
```

> 비인증 API이므로 환자 개인정보를 최소화하여 마스킹된 값만 반환한다. `patientId`는 응답에 포함하지 않는다.

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `GUARDIAN_SIGNUP_FAILED` | 가입 요청 처리 실패 (환자 미존재 여부를 외부에 노출하지 않음) |
| 409 | `AUTH_USERNAME_CONFLICT` | 이미 사용 중인 로그인 ID |
| 409 | `GUARDIAN_LINK_ALREADY_EXISTS` | 동일 환자에 대한 보호자 가입 이력이 이미 존재 |

---

### 1.2 로그인

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/auth/login` |
| Auth | 불필요 |

**Request Body** (`application/json`)
```json
{
  "username": "doctor_kim",
  "password": "Passw0rd!"
}
```

> 의사/보호자 계정은 관리자 승인으로 활성화된 후에만 로그인할 수 있다.

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "user": {
    "userId": "usr_K9mXw2",
    "name": "김의사",
    "role": "DOCTOR"
  }
}
```
- **Set-Cookie**: `refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 401 | `AUTH_INVALID_CREDENTIALS` | 아이디/비밀번호 불일치 |
| 401 | `AUTH_ACCOUNT_PENDING_APPROVAL` | 의사/보호자 계정이 아직 관리자 승인 대기 상태 |
| 423 | `AUTH_ACCOUNT_LOCKED` | 계정 비활성화 |

---

### 1.3 Access Token 갱신

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/auth/refresh` |
| Auth | 불필요 (쿠키 자동 전송) |

**Request**: Body 없음 (쿠키의 `refresh_token` 자동 전송)

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...(새 토큰)",
  "expiresIn": 900,
  "user": {
    "userId": "usr_K9mXw2",
    "name": "김의사",
    "role": "DOCTOR"
  }
}
```
- 새 Refresh Token 쿠키 발급 (Token Rotation)

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 401 | `AUTH_REFRESH_EXPIRED` | Refresh Token 만료 |
| 401 | `AUTH_TOKEN_REUSE` | 이미 사용된 RT 재사용 (해당 토큰 폐기). 사용자 상태 변경이 감지되면 별도로 전체 세션 무효화 수행 |

---

### 1.4 로그아웃

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/auth/logout` |
| Auth | Bearer Token |

**Response** `204 No Content`
- `refresh_token` 쿠키 삭제 (`Max-Age=0`)
- Redis 기반 Refresh Token 무효화 처리

---

## 2. 환자 식별 API (`/api/v1/intake/sessions/{intakeSessionId}/identify`)

> 전화 시뮬레이터에서 환자를 단계별로 식별하는 API이다.
> 환자는 로그인 계정이 없으므로 시뮬레이터 내부에서 호출한다.
> **모든 공개 식별 API는 인테이크 세션 문맥 내에서 호출**하며, 서버는 세션 상태와 식별 시도 이력을 연결하여 관리한다.

### 2.1 발신번호로 환자 조회 (1차 식별)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/identify/by-caller-number` |
| Auth | 불필요 (시뮬레이터) |

> 전화번호가 query string에 포함되면 웹서버 access log, 프록시 log, 브라우저 히스토리 등에 노출될 수 있으므로 POST body로 전달한다.

**Request Body**
```json
{
  "callerNumber": "01012345678"
}
```

**Response** `200 OK` (환자 있음)
```json
{
  "identified": true,
  "patient": {
    "patientId": "pat_Zk3mQ9",
    "name": "홍길동",
    "birthDate6": "580315",
    "regionCode": "ULLEUNG"
  }
}
```

**Response** `200 OK` (환자 없음)
```json
{
  "identified": false,
  "patient": null
}
```

---

### 2.2 전화번호 직접 입력으로 환자 조회 (2차 식별)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/identify/by-phone` |
| Auth | 불필요 |

> 1차 발신번호 식별 실패 시, 환자가 다이얼 패드로 직접 입력한 전화번호로 재시도한다. 세션 문맥 내에서 식별 시도 이력이 기록된다.

**Request Body**
```json
{
  "phone": "01098765432"
}
```

**Response**: 2.1과 동일 구조

---

### 2.3 [보조 조회] 이름 + 생년월일로 환자 조회

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/identify/by-info` |
| Auth | 불필요 |

> 현재 기본 전화 예약 흐름은 발신번호/전화번호 입력까지만 사용한다.
> 이 API는 운영 보조 경로나 별도 수기 입력 시나리오를 위해 유지한다.
> 이름 + 생년월일 조회 결과가 0건이거나 다건인 경우에는 임의 선택하지 않고 `identified=false, patient=null` 을 반환한다.

**Request Body**
```json
{
  "name": "홍길동",
  "birthDate6": "580315"
}
```

**Response**: 2.1과 동일 구조

---

### 2.4 환자 등록 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/patients` |
| Auth | Bearer Token (ADMIN) |

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `name` | string | O | 환자명 |
| `birthDate` | string | O | 생년월일 (YYYY-MM-DD) |
| `phone` | string | O | 전화번호 |
| `gender` | string | O | 환자 성별 (`MALE` | `FEMALE` | `UNKNOWN`) |
| `regionCode` | string | O | 지역 코드 |
| `address` | string | X | 주소 |
| `referenceImage` | file | X | 환자 기준 얼굴 이미지 (JPEG/PNG). 환자별 1건 관리 |

**Response** `201 Created`
```json
{
  "patientId": "pat_R7xNw3",
  "name": "홍길동",
  "birthDate6": "580315",
  "phone": "01012345678",
  "gender": "MALE",
  "referenceImageRegistered": true
}
```
> 기준 이미지는 `PATIENT.reference_image_path` 등 환자 컬럼과 파일 스토리지로 함께 관리한다. DB에는 환자별 기준 이미지 경로와 등록 관리자만 저장하고, 실제 이미지 바이트는 파일 스토리지에 보관한다.

---

## 3. 인테이크(문진) API (`/api/v1/intake`)

> 전화 시뮬레이터에서 진료과 선택 결과 저장, 의사 매칭, 예약 슬롯 안내까지의 세션을 관리한다.
>
> 구조: `INTAKE_SESSION(selected_department, selected_department_name, selection_reason, selection_confidence_level, offered_slot_ids_json) → BOOKING`
>
> **공개 세션 접근 제어**: 공개 인테이크 플로우에서는 `intakeSessionId`(`public_id`)를 세션 접근 식별자(capability token)로 사용한다. 충분히 랜덤한 nanoid로 생성하며, 세션 완료(`COMPLETED`)/만료/폐기(`ABANDONED`, `FAILED`) 후에는 해당 ID로의 상태 변경 요청을 거부한다. 세션 TTL은 서버에서 관리하며, 무활동 상태가 일정 시간 지속되면 자동으로 `ABANDONED` 처리한다.
>
> **운영 정책**:
> - `lastActivityAt` 자동 갱신: 세션 문맥 API 전반 (세션 생성, 환자 바인딩, 진료과 선택, 슬롯 안내, 예약 등)에서 자동 갱신된다.
> - 감사 로그: 무인증 공개 API이므로 `actorId="SYSTEM"`, `actorRole="SYSTEM"`. `correlationId`는 `corr_ints_<publicId>` 형식.
> - 타임아웃 자동 ABANDONED 처리: 후속 이슈로 구현 예정.

### 3.1 인테이크 세션 생성

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions` |
| Auth | 불필요 |

**Request Body**
```json
{
  "callerNumber": "01012345678",
  "channel": "WEB_SIMULATOR"
}
```
> - `callerNumber`: 9~20자리 숫자 (필수)
> - `channel`: `WEB_SIMULATOR` | `PHONE`. 생략 시 `WEB_SIMULATOR` 기본값.

**Response** `201 Created`
```json
{
  "intakeSessionId": "ints_R8kxPw",
  "status": "STARTED",
  "channel": "WEB_SIMULATOR",
  "createdAt": "2026-03-10T10:00:00+09:00"
}
```

---

### 3.2 인테이크 세션 상태 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}` |
| Auth | 불필요 |

**Response** `200 OK`
```json
{
  "intakeSessionId": "ints_R8kxPw",
  "patientId": null,
  "callerNumber": "01012345678",
  "channel": "WEB_SIMULATOR",
  "status": "STARTED",
  "completionReason": null,
  "selectedDepartment": null,
  "selectedDepartmentName": null,
  "selectionReason": null,
  "selectionConfidenceLevel": null,
  "offeredSlotIds": [],
  "createdAt": "2026-03-10T10:00:00+09:00",
  "endedAt": null,
  "lastActivityAt": "2026-03-10T10:00:00+09:00"
}
```
> `@JsonInclude(ALWAYS)` — `patientId`, `endedAt`, `completionReason`이 `null`이어도 응답에 포함된다.

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `INTAKE_SESSION_NOT_FOUND` | 해당 세션 ID 없음 |

---

### 3.3 인테이크 세션에 환자 바인딩

| 항목 | 값 |
|------|-----|
| Method | `PATCH` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}` |
| Auth | 불필요 |

> 환자 식별 완료 후, 세션 리소스의 `patientId`를 부분 수정하여 환자를 바인딩한다.
>
> **`PATCH /intake/sessions/{id}` 허용 케이스 규칙**:
> - **환자 바인딩** (3.3): `patientId`만 포함
> - **세션 종료** (3.6): `status`, `completionReason`만 포함
> - 서로 다른 목적의 필드를 혼합한 요청은 `400 INVALID_PATCH_REQUEST`로 거부한다.
>
> **멱등성**: 같은 환자를 재바인딩하면 `200 OK` (멱등). 다른 환자를 바인딩 시도하면 `409 PATIENT_ALREADY_BOUND`.

**Request Body**
```json
{
  "patientId": "pat_Zk3mQ9"
}
```

**Response** `200 OK`
```json
{
  "intakeSessionId": "ints_R8kxPw",
  "patientId": "pat_Zk3mQ9",
  "status": "IN_PROGRESS",
  "lastActivityAt": "2026-03-10T10:01:00+09:00"
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `INTAKE_SESSION_NOT_FOUND` | 해당 세션 ID 없음 |
| 404 | `PATIENT_NOT_FOUND` | 해당 환자 ID 없음 |
| 400 | `INVALID_PATCH_REQUEST` | 허용되지 않는 필드 조합 |
| 409 | `SESSION_STATE_INVALID` | 비활성 세션 (COMPLETED/ABANDONED/FAILED) |
| 409 | `PATIENT_ALREADY_BOUND` | 이미 다른 환자가 바인딩된 세션 |

---

### 3.4 메뉴 선택 처리 (웹 시뮬레이터)

웹 전화 시뮬레이터에서는 별도 키패드 입력 턴을 저장하지 않는다.
화면에서 `1`, `2` 버튼을 누르면 별도 `/turns` 저장 API 없이 다음 도메인 API를 직접 호출한다.

예시:

- 신규 예약 버튼(`1`) 클릭 → 환자 식별 API 호출 → 진료과 메뉴 선택 → 추천 API 호출
- 기존 예약 조회/취소 버튼(`2`) 클릭 → 환자 식별 후 기존 예약 조회 API 호출
- 예약 확정 버튼(`1`) 클릭 → 예약 생성 API 호출
- 다른 시간 버튼(`2`) 클릭 → 프론트에서 다음 후보 슬롯으로 진행
- 다시 듣기 버튼(`0`) 클릭 → 현재 메뉴 또는 슬롯 안내 반복

버튼 선택 자체는 별도 턴 엔티티로 저장하지 않고, 후속 API의 감사 로그와 세션 갱신으로 추적한다.

---

### 3.5 진료과 선택 결과 저장 및 슬롯 안내

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/recommend` |
| Auth | 불필요 |

**Request Body**
```json
{
  "departmentCode": "INTERNAL_MEDICINE"
}
```

> `departmentCode`는 전화 시뮬레이터의 DTMF 입력과 매핑된다. 기본 메뉴는 `INTERNAL_MEDICINE`, `DERMATOLOGY`, `ORTHOPEDICS`, `NEUROLOGY`, `OPHTHALMOLOGY`를 사용한다.
> 현재 MVP 기본 예약 흐름은 `departmentCode` 기반이다.

**Response** `200 OK`
```json
{
  "department": "INTERNAL_MEDICINE",
  "departmentName": "내과",
  "confidenceLevel": "HIGH",
  "isEmergency": false,
  "reason": "환자가 내과를 직접 선택했습니다. 같은 진료과의 최근 담당 의사를 우선 매칭했습니다.",
  "availableSlots": [
    {
      "slotId": "slot_T9qRx2",
      "doctorId": "doc_P5wMn4",
      "doctorName": "김의사",
      "department": "INTERNAL_MEDICINE",
      "departmentName": "내과",
      "date": "2026-03-11",
      "startTime": "10:00",
      "endTime": "10:30"
    },
    {
      "slotId": "slot_Y6sVb3",
      "doctorId": "doc_P5wMn4",
      "doctorName": "김의사",
      "department": "INTERNAL_MEDICINE",
      "departmentName": "내과",
      "date": "2026-03-11",
      "startTime": "11:00",
      "endTime": "11:30"
    }
  ],
  "ttsMessage": "내과 김의사 선생님, 3월 11일 오전 10시 진료가 가능합니다. 예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다."
}
```

> 별도 추천 리소스를 생성하지 않고, 응답에 포함된 선택 결과와 `availableSlots` 스냅샷을 동일한 `INTAKE_SESSION`에 인라인 저장한다.
> `availableSlots`에는 같은 지역 차량 용량과 당일 현재 시각 이후 조건이 함께 반영된다. 기준 시각은 서버 KST(`Asia/Seoul`)이며, 오늘 날짜 슬롯은 아직 시작되지 않은 시간만 노출된다.
> `ttsMessage`는 `availableSlots`의 첫 번째 슬롯만 읽어 주며, 나머지 후보는 `다른 시간은 2번` 흐름에서 사용한다.

---

### 3.6 인테이크 세션 종료

| 항목 | 값 |
|------|-----|
| Method | `PATCH` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}` |
| Auth | 불필요 |

**Request Body**
```json
{
  "status": "COMPLETED",
  "completionReason": "BOOKING_CREATED"
}
```

> `completionReason`은 enum으로 관리되며, 아래 값만 허용된다.

| completionReason | 설명 |
|------------------|------|
| `BOOKING_CREATED` | 예약 완료 후 정상 종료 |
| `NO_INPUT_TIMEOUT` | 무응답 타임아웃 |
| `USER_HANGUP` | 사용자 종료 |
| `EXISTING_BOOKING_CHECKED` | 기존 예약 조회/취소 후 종료 |

**Response** `200 OK`
```json
{
  "intakeSessionId": "ints_R8kxPw",
  "status": "COMPLETED",
  "endedAt": "2026-03-10T10:08:00+09:00"
}
```

---

## 4. 예약 API (`/api/v1/bookings`)

### 4.1 예약 생성 (인테이크 세션 문맥 — 시뮬레이터)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/bookings` |
| Auth | 불필요 (시뮬레이터에서 호출) |

> 공개 플로우에서 예약 생성은 반드시 인테이크 세션 문맥 내에서 수행한다.
> 서버는 다음을 모두 검증한다:
> - `intakeSession.patient_id` 존재 (환자 바인딩 완료)
> - `slotId`가 해당 세션에서 안내된 슬롯 목록에 포함되는지
> - 세션 상태가 예약 생성 가능한 단계인지
> - 당일 슬롯이면 현재 시각 이후 시작 슬롯인지
> - 동일 `regionCode`에서는 같은 시간대 예약을 1건만 허용하는지
> - `patientId`, `channel`은 서버가 세션에서 자동 추출하므로 body에 포함하지 않는다.

**Request Body**
```json
{
  "slotId": "slot_T9qRx2"
}
```

**Response** `201 Created`
```json
{
  "bookingId": "bk_H8qWm2",
  "status": "CONFIRMED",
  "caseId": "case_T7nLp4",
  "patient": {
    "patientId": "pat_Zk3mQ9",
    "name": "홍길동"
  },
  "doctor": {
    "doctorId": "doc_P5wMn4",
    "name": "김의사",
    "department": "INTERNAL_MEDICINE",
    "departmentName": "내과"
  },
  "appointmentDate": "2026-03-11",
  "startTime": "10:00",
  "endTime": "10:30",
  "createdAt": "2026-03-10T10:05:00+09:00",
  "ttsMessage": "3월 11일 오전 10시 내과 김의사 선생님 진료가 예약되었습니다. 감사합니다."
}
```

> 예약 생성 성공 후 시스템은 환자의 기본 휴대전화 번호로 예약 확정 SMS를 비동기 발송한다. SMS 발송 실패는 예약 생성을 롤백하지 않으며, 운영 로그와 재시도 정책으로 후속 처리한다.
>
> 추가로 서버는 같은 트랜잭션 문맥에서 다음 리소스를 함께 준비한다.
> - `CARE_CASE` 생성
> - `MISSION` 생성 (`phase=CREATED`, `vehicleId=null`)
> - `DISPATCH_OUTBOX` 생성
>
> 주소가 waypoint 매핑 대상이면 `MISSION.targetWaypointNumber`에 저장하고, 운영/데모 환경에서 이후 출동 트리거에 사용한다.
> 이 시점에는 차량이 아직 배정되지 않았으므로 `MISSION.vehicleId`는 `null`이며, 실제 차량 `public_id`는 이후 배차 성공 시점에 채워진다.

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 409 | `BOOKING_SLOT_CONFLICT` | 이미 예약된 슬롯 |
| 409 | `BOOKING_SLOT_EXPIRED` | 이미 시작되었거나 지난 시간의 슬롯 |
| 409 | `BOOKING_VEHICLE_CONFLICT` | 같은 지역 차량에 이미 같은 시간대 예약이 존재 |
| 404 | `SLOT_NOT_FOUND` | 유효하지 않은 슬롯 ID |
| 400 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 (워크플로 단계 충돌) |
| 400 | `SLOT_NOT_IN_RECOMMENDATION` | 해당 세션에서 안내되지 않은 슬롯 |
| 400 | `SESSION_STATE_INVALID` | 예약 생성 불가한 세션 상태 |

> 추천 응답의 `availableSlots`는 같은 지역 차량 용량을 반영해 필터링된 결과다. 지역 용량 계산에서는 `status = CANCELLED`만 제외되며, `COMPLETED` 예약은 같은 지역/시간대 재예약을 계속 막는다.
> 예약 취소 후에는 `status = CANCELLED` 예약이 지역 용량 계산에서 제외되므로 해당 시간대가 다시 노출될 수 있다.

---

### 4.2 환자의 기존 예약 조회 (인테이크 세션 문맥)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/bookings` |
| Auth | 불필요 (시뮬레이터) |

> 공개 키오스크/시뮬레이터에서 예약을 조회할 때는 반드시 인테이크 세션 문맥 내에서 수행한다.
> 서버는 `intakeSession.patient_id`로 해당 환자의 예약만 조회한다.

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `status` | string | X | 필터 (`CONFIRMED`, `CANCELLED` 등) |

**Response** `200 OK`
```json
{
  "bookings": [
    {
      "bookingId": "bk_H8qWm2",
      "status": "CONFIRMED",
      "doctorName": "김의사",
      "departmentName": "내과",
      "appointmentDate": "2026-03-11",
      "startTime": "10:00",
      "endTime": "10:30",
      "createdAt": "2026-03-10T10:05:00+09:00"
    }
  ],
  "totalCount": 1
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 409 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 (워크플로 단계 충돌) |

---

### 4.3 예약 상세 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/bookings/{bookingId}` |
| Auth | Bearer Token |

**Response** `200 OK`
```json
{
  "bookingId": "bk_H8qWm2",
  "status": "CONFIRMED",
  "patient": {
    "patientId": "pat_Zk3mQ9",
    "name": "홍길동",
    "phone": "01012345678",
    "regionCode": "ULLEUNG"
  },
  "doctor": {
    "doctorId": "doc_P5wMn4",
    "name": "김의사",
    "department": "INTERNAL_MEDICINE",
    "departmentName": "내과"
  },
  "appointmentDate": "2026-03-11",
  "startTime": "10:00",
  "endTime": "10:30",
  "caseId": "case_T7nLp4",
  "intakeSessionId": "ints_R8kxPw",
  "channel": "WEB_SIMULATOR",
  "createdAt": "2026-03-10T10:05:00+09:00"
}
```

---

### 4.4 예약 취소 (인테이크 세션 문맥 — 시뮬레이터)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/bookings/{bookingId}/cancel` |
| Auth | 불필요 (시뮬레이터) |

> 서버는 반드시 `booking.patient_id == intakeSession.patient_id`를 검증하고, 세션 상태가 취소 가능한 단계인지 확인한다.

**Request Body**
```json
{
  "cancelReason": "환자 사정으로 취소"
}
```

**Response** `200 OK`
```json
{
  "bookingId": "bk_H8qWm2",
  "status": "CANCELLED",
  "cancelledAt": "2026-03-10T11:00:00+09:00",
  "ttsMessage": "예약이 취소되었습니다."
}
```

> 예약 취소 성공 후 시스템은 환자의 기본 휴대전화 번호로 취소 결과 SMS를 비동기 발송한다. SMS 발송 실패는 취소 처리를 롤백하지 않는다.

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 409 | `BOOKING_ALREADY_CANCELLED` | 이미 취소된 예약 |
| 409 | `BOOKING_NOT_CANCELLABLE` | 취소 불가 상태 (진료 중 등) |
| 403 | `PATIENT_MISMATCH` | 세션 환자와 예약 환자 불일치 |
| 409 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 (워크플로 단계 충돌) |

### 4.5 예약 취소 (관리자/의사 — 인증 기반)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/bookings/{bookingId}/cancel` |
| Auth | Bearer Token (ADMIN, DOCTOR) |

**Request Body**
```json
{
  "cancelReason": "관리자 판단에 의한 취소"
}
```

**Response**: 4.4와 동일 구조

---

## 5. 케이스 API (`/api/v1/cases`)

> 예약 생성 시 자동으로 1:1 케이스가 함께 생성된다. 진료 전체 라이프사이클을 추적한다.

### 5.1 케이스 상세 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/cases/{caseId}` |
| Auth | Bearer Token (DOCTOR, ADMIN) |

**Response** `200 OK`
```json
{
  "caseId": "case_T7nLp4",
  "status": "PREPARING",
  "bookingId": "bk_H8qWm2",
  "patient": {
    "patientId": "pat_Zk3mQ9",
    "name": "홍길동",
    "birthDate6": "580315",
    "birthDate": "1958-03-15",
    "gender": "MALE",
    "phone": "01012345678",
    "address": "경북 울릉군 울릉읍 ..."
  },
  "doctor": {
    "doctorId": "doc_P5wMn4",
    "name": "김의사"
  },
  "intakeSummary": {
    "department": "INTERNAL_MEDICINE",
    "departmentName": "내과",
    "selectionReason": "환자가 내과를 직접 선택했고 최근 동일 진료과 담당 의사를 우선 매칭했습니다.",
    "selectionConfidenceLevel": "HIGH",
    "intakeSessionId": "ints_R8kxPw"
  },
  "missionId": "ms_F2gHn6",
  "sessionId": null,
  "vitals": {
    "caseId": "case_T7nLp4",
    "temperature": 36.7,
    "bloodPressureSys": 128,
    "bloodPressureDia": 82,
    "heartRate": 72,
    "spO2": 98,
    "ecgWaveform": [0.12, 0.18, 0.11, -0.05, 0.45, 1.10],
    "ecgSamplingHz": 25,
    "ecgDurationSeconds": 8,
    "measuredAt": "2026-03-23T14:23:10+09:00",
    "createdAt": "2026-03-23T14:15:00",
    "updatedAt": "2026-03-23T14:23:10"
  },
  "createdAt": "2026-03-10T10:05:00+09:00"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `patient.patientId` | string | 환자 공개 ID |
| `patient.name` | string | 환자 이름 |
| `patient.birthDate6` | string | 생년월일 6자리 |
| `patient.birthDate` | string | 생년월일 (`YYYY-MM-DD`) |
| `patient.gender` | string | 환자 성별 (`MALE`, `FEMALE`, `UNKNOWN`) |
| `patient.phone` | string | 환자 전화번호 |
| `patient.address` | string | 환자 주소 |
| `vitals` | object \| null | 현재 케이스 기준 최신 생체데이터. 아직 측정 전이면 `null` |
| `vitals.caseId` | string | 생체데이터가 연결된 케이스 공개 ID |
| `vitals.temperature` | number \| null | 체온 |
| `vitals.bloodPressureSys` | integer \| null | 수축기 혈압 |
| `vitals.bloodPressureDia` | integer \| null | 이완기 혈압 |
| `vitals.heartRate` | integer \| null | 심박수 |
| `vitals.spO2` | integer \| null | 산소포화도 |
| `vitals.ecgWaveform` | number[] \| null | 측정 시점 ECG sample waveform. 실시간 스트림 아님 |
| `vitals.ecgSamplingHz` | integer \| null | ECG 샘플링 주파수 |
| `vitals.ecgDurationSeconds` | integer \| null | ECG 샘플 길이(초) |
| `vitals.measuredAt` | string \| null | 마지막 측정 시각 (`OffsetDateTime`, KST) |
| `vitals.createdAt` | string \| null | 해당 케이스 생체데이터 row 생성 시각 (`LocalDateTime`, KST) |
| `vitals.updatedAt` | string \| null | 마지막 partial upsert 시각 (`LocalDateTime`, KST) |

---

### 5.2 의사 본인의 케이스 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/cases` |
| Auth | Bearer Token (DOCTOR) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `status` | string | X | 케이스 상태 필터 |
| `date` | string | X | 날짜 필터 (YYYY-MM-DD) |

**Response** `200 OK`
```json
{
  "cases": [
    {
      "caseId": "case_T7nLp4",
      "status": "PREPARING",
      "patientId": "pat_Zk3mQ9",
      "patientName": "홍길동",
      "patientGender": "MALE",
      "departmentName": "내과",
      "appointmentDate": "2026-03-11",
      "startTime": "10:00",
      "missionPhase": "VERIFYING",
      "sessionId": "ses_L6pQr1",
      "sessionStatus": "READY"
    }
  ],
  "totalCount": 1
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `cases[].caseId` | string | 케이스 공개 ID |
| `cases[].status` | string | 케이스 상태 |
| `cases[].patientId` | string | 환자 공개 ID |
| `cases[].patientName` | string | 환자 이름 |
| `cases[].patientGender` | string | 환자 성별 (`MALE`, `FEMALE`, `UNKNOWN`) |
| `cases[].departmentName` | string | 진료과명 |
| `cases[].appointmentDate` | string | 예약 날짜 (`YYYY-MM-DD`) |
| `cases[].startTime` | string | 예약 시작 시간 |
| `cases[].missionPhase` | string | 연결된 미션 단계 |
| `cases[].sessionId` | string \| null | 연결된 화상진료 세션 ID |
| `cases[].sessionStatus` | string \| null | 연결된 화상진료 세션 상태 (`CREATED`, `READY`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `ABANDONED`) |
| `totalCount` | int | 조회된 케이스 수 |

---

## 6. 미션(차량 출동) API (`/api/v1/missions`)

> 차량 출동 및 현장 운영을 미션 단위로 관리한다.

### 6.1 미션 목록 조회 (관리자 대시보드)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/missions` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `date` | string | X | 날짜 필터 |
| `phase` | string | X | 미션 상태 필터 |

**Response** `200 OK`
```json
{
  "missions": [
    {
      "missionId": "ms_F2gHn6",
      "caseId": "case_T7nLp4",
      "patientName": "홍길동",
      "phase": "DISPATCHED",
      "vehicleId": "veh_00000001",
      "destination": "경북 울릉군 울릉읍...",
      "dispatchedAt": "2026-03-11T08:30:00+09:00",
      "estimatedArrivalTime": "2026-03-11T09:45:00+09:00"
    }
  ],
  "totalCount": 1
}
```

---

### 6.2 미션 상세 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/missions/{missionId}` |
| Auth | Bearer Token (ADMIN) |

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "phase": "EN_ROUTE",
  "vehicleId": "veh_00000001",
  "patientName": "홍길동",
  "destination": "경북 울릉군 울릉읍...",
  "dispatchedAt": "2026-03-11T08:30:00+09:00",
  "estimatedArrivalTime": "2026-03-11T09:45:00+09:00",
  "currentLocation": {
    "latitude": 37.4845,
    "longitude": 130.9057,
    "timestamp": "2026-03-11T09:15:00+09:00"
  },
  "updatedAt": "2026-03-11T09:45:00+09:00"
}
```

---

### 6.3 미션 생성 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions` |
| Auth | Bearer Token (ADMIN) |

> 이 API는 관리자 수동 생성/보정용이다. 일반 예약 확정 흐름에서는 `dispatch_outbox`와 Kafka 소비를 통해 미션이 자동 생성된다.

**Request Body**
```json
{
  "caseId": "case_T7nLp4",
  "vehicleId": "veh_00000001",
  "destination": "경북 울릉군 울릉읍...",
  "scheduledTime": "2026-03-11T08:30:00+09:00"
}
```

**Response** `201 Created`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "phase": "CREATED",
  "vehicleId": "veh_00000001",
  "createdAt": "2026-03-10T14:00:00+09:00"
}
```

---

### 6.4 미션 단계 수동 전환 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `PATCH` |
| Path | `/api/v1/missions/{missionId}` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "phase": "ARRIVED",
  "reason": "현장 도착 확인"
}
```

**유효한 phase 값**: `CREATED` → `DISPATCHED` → `EN_ROUTE` → `ARRIVED` → `VERIFYING` → `CONSULTING` → `RETURNING` → `COMPLETED` | `FAILED`
※ `INCIDENT`는 어느 단계에서든 진입 가능한 임시 상태이며, 복구 후 이전 단계로 복귀한다.

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "phase": "ARRIVED",
  "previousPhase": "EN_ROUTE",
  "updatedAt": "2026-03-11T09:45:00+09:00"
}
```

---

### 6.5 차량 위치/상태 수신 (ROS2 → Spring Boot)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions/{missionId}/telemetry` |
| Auth | API Key (내부 서비스) |

**Request Body**
```json
{
  "source": "ROS2",
  "sourceEventId": "ros2_msg_abc123",
  "seqNo": 42,
  "vehicleId": "veh_00000001",
  "phase": "EN_ROUTE",
  "latitude": 37.4845,
  "longitude": 130.9057,
  "speed": 30.5,
  "heading": 180,
  "timestamp": "2026-03-11T09:15:00+09:00",
  "metadata": {}
}
```

> HTTP 진입점은 API Key 검증 후 `mission.telemetry` Kafka 토픽에 메시지를 적재하고 즉시 `202 Accepted`를 반환한다.
>
> 실제 `MISSION.phase`, `MISSION.latitude`, `MISSION.longitude` 갱신은 `MissionTelemetryConsumer`가 비동기로 처리한다. 별도 이벤트 리소스는 생성하지 않는다.
>
> **관리자 PATCH와의 충돌 방지 규칙**:
> - `seqNo` 또는 `timestamp` 기준으로 마지막 반영값보다 오래된 이벤트는 무시한다.
> - 이미 상위 단계로 전환된 `MISSION.phase`를 하위 단계로 역전이시키지 않는다 (예: ARRIVED 이후 EN_ROUTE 수신 시 phase 갱신 무시, 위치 정보만 갱신).
> - `sourceEventId`가 중복 수신되면 멱등 처리한다.

**Response** `202 Accepted`

---

## 7. 동의 API (P1)

> 동의 UI 및 동의 기록은 MVP 제외 범위다.
>
> 상세 API 초안은 [P1_Consent_Extension.md](./P1_Consent_Extension.md) 문서를 참조한다.

---

## 8. 실시간 알림 API (`/api/v1/doctors/me/notifications`)

> 의사 EMR 화면에서 신규 예약 알림을 실시간으로 수신하기 위한 **Server-Sent Events (SSE)** API다.
>
> 단방향 서버 push 전용이며, 의사 로그인 후 대시보드 진입 시 연결한다.
>
> 동일 의사의 다중 탭 연결을 허용한다.

### 8.1 의사 알림 스트림 구독

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/doctors/me/notifications/stream` |
| Auth | Bearer Token (DOCTOR) |
| Accept | `text/event-stream` |
| Response Content-Type | `text/event-stream` |

> 브라우저 기본 `EventSource`는 `Authorization` 헤더를 추가할 수 없으므로, 프론트엔드는 `fetch` 기반 SSE 클라이언트를 사용한다.
>
> 서버는 연결 성공 시 즉시 `connected` 이벤트를 1회 전송하고, 이후 신규 예약 발생 시 `notification` 이벤트를 같은 스트림으로 전달한다.
>
> 서버 스트림 timeout은 1시간이고, `retry: 3000`을 내려 클라이언트 재연결 기준값을 안내한다.

**연결 직후 이벤트 예시**
```text
id: 0d4f4a38-2e8a-4e2d-a7c1-32cf7c6d53f0
event: connected
retry: 3000
data: {"connectedAt":"2026-03-19T17:20:00+09:00"}
```

**신규 예약 알림 이벤트 예시**
```text
id: 73a8f5a7-8df7-4f08-b52e-0d0cb3e0a2f5
event: notification
data: {"type":"NEW_BOOKING","bookingId":"bk_H8qWm2","caseId":"case_T7nLp4","doctorId":"doc_P5wMn4","doctorName":"김도현","departmentName":"내과","patientId":"pat_Zk3mQ9","patientName":"박순자","patientGender":"FEMALE","patientBirthDate":"1958-03-15","patientPhone":"01012345678","appointmentDate":"2026-03-24","startTime":"14:30:00","location":"경북 김천시 증산면 장전1길 69","createdAt":"2026-03-19T17:25:10+09:00"}
```

> `location`은 현재 구조상 환자 주소(`PATIENT.address`)를 사용한다.
>
> `notification` 이벤트는 예약과 케이스 생성 트랜잭션이 정상 커밋된 뒤 발행된다. 활성 SSE 연결이 없더라도 예약 생성 자체는 실패하지 않는다.
>
> 내부적으로는 `doctor.notifications` Kafka 토픽을 통해 전달되며, 활성 SSE 연결이 없는 의사는 이벤트를 소비하더라도 push를 생략한다.

**`notification` payload**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | string | O | 현재는 `NEW_BOOKING` |
| `bookingId` | string | O | 예약 ID (`bk_...`) |
| `caseId` | string | O | 생성된 케이스 ID (`case_...`) |
| `doctorId` | string | O | 담당 의사 ID (`doc_...`) |
| `doctorName` | string | O | 담당 의사명 |
| `departmentName` | string | O | 진료과명 |
| `patientId` | string | O | 환자 ID (`pat_...`) |
| `patientName` | string | O | 환자명 |
| `patientGender` | string | O | 환자 성별 (`MALE` | `FEMALE` | `UNKNOWN`) |
| `patientBirthDate` | string | O | 환자 생년월일 (`YYYY-MM-DD`) |
| `patientPhone` | string | O | 환자 전화번호 |
| `appointmentDate` | string | O | 예약 날짜 (`YYYY-MM-DD`) |
| `startTime` | string | O | 예약 시작 시간 (`HH:mm:ss`) |
| `location` | string | O | 환자 주소 |
| `createdAt` | string | O | 알림 생성 시각 (`OffsetDateTime`) |

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 401 | `AUTH_UNAUTHORIZED` | Bearer Token 누락 또는 인증 실패 |
| 403 | `AUTH_FORBIDDEN` | DOCTOR 권한이 아님 |
| 403 | `AUTH_DOCTOR_PROFILE_REQUIRED` | 의사 프로필이 연결되지 않은 계정 |

---

<a id="9-화상진료-세션-api"></a>
## 9. 진료 진입/화상진료 세션 API (`/api/v1/missions`, `/api/v1/sessions`)

> 차량 도착 후 환자 본인 확인부터 LiveKit 기반 1:1 WebRTC 화상진료 세션 입장까지의 진입 흐름을 관리한다.
>
> **토큰 발급 정책**: 의사와 환자의 토큰은 **별도 엔드포인트**에서 발급한다.
> - 의사: 세션 생성 시 (9.1) 자신의 토큰만 발급
> - 차량 단말: 미션에 바인딩된 제한 토큰 발급 (9.2)
> - 환자: 차량 단말이 `MISSION_TERMINAL` 토큰으로 **본인 확인** 요청 (9.3), 이후 **활력징후 단계 완료 후, 의사 세션이 준비되면** **환자 토큰 발급** 요청 (9.4)

### 9.1 진료 세션 생성 — 의사 토큰 발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/cases/{caseId}/sessions` |
| Auth | Bearer Token (DOCTOR) |

> 의사가 "진료 시작"을 클릭하면 세션을 생성하고 **의사 본인의 LiveKit 토큰만** 발급한다.
> 의사는 이미 Bearer Token으로 인증되어 있으므로, `doctorId`는 Request Body에서 제거하고 **서버가 Access Token의 principal에서 온 `userId` → `DOCTOR_PROFILE`을 조회**한다.
> 동일 케이스에 `CREATED`, `READY`, `IN_PROGRESS` 상태의 활성 세션이 이미 있으면 새 세션을 만들지 않고 **기존 세션을 재사용**하며, 의사의 **재참여용 LiveKit 토큰을 재발급**한다.

**Request Body**: 없음

**Response** `201 Created`
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "READY",
  "room": {
    "roomId": "room_ses_L6pQr1",
    "livekitUrl": "wss://<DOMAIN>/livekit"
  },
  "doctorToken": "eyJhbGci...",
  "createdAt": "2026-03-11T10:00:00+09:00"
}
```

**Response** `200 OK` (기존 활성 세션 재사용 / 재참여)
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "IN_PROGRESS",
  "room": {
    "roomId": "room_ses_L6pQr1",
    "livekitUrl": "wss://<DOMAIN>/livekit"
  },
  "doctorToken": "eyJhbGci...(reissued)",
  "createdAt": "2026-03-11T10:00:00+09:00"
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `CASE_NOT_ASSIGNED` | 해당 의사에게 배정되지 않은 케이스 |

---

### 9.2 차량 단말 bootstrap 토큰 발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/terminal/bootstrap-token` |
| Auth | 없음 |

> 차량 태블릿은 관리자/의사 브라우저 로그인에 의존하지 않고, 환경변수로 주입된 단말 credential로 `DEVICE_TERMINAL` 토큰을 먼저 발급받는다.
> 서버는 `ROBOT_TERMINAL_REGISTRY`에 등록된 엔트리와 `terminalId`, `terminalKey`를 대조해 `vehicleId`, `regionCode` 바인딩 정보를 함께 토큰에 싣는다.
> 이 토큰은 후보 조회와 mission claim에만 사용할 수 있다.

**Request Body**
```json
{
  "terminalId": "robot-terminal-01",
  "terminalKey": "<BOOTSTRAP_SECRET>"
}
```

**Response** `200 OK`
```json
{
  "terminalId": "robot-terminal-01",
  "vehicleId": "veh_GIMCHEON_01",
  "regionCode": "GIMCHEON",
  "deviceTerminalToken": "eyJhbGci...",
  "expiresIn": 1800,
  "scopes": [
    "terminal:check-in-candidates",
    "terminal:claim-mission"
  ]
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 401 | `AUTH_INVALID_CREDENTIALS` | 단말 bootstrap credential 불일치 |
| 503 | `AUTH_TERMINAL_BOOTSTRAP_DISABLED` | 서버에 차량 단말 bootstrap credential 미설정 |

---

### 9.2a 차량 진료 대상 후보 조회

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/terminal/check-in/candidates` |
| Auth | Bearer Token (`DEVICE_TERMINAL`) |

> 환자가 입력한 `전화번호 뒤 4자리 + 생년월일 6자리`를 기준으로 차량 진료 가능한 mission 후보를 조회한다.
> 서버는 `DEVICE_TERMINAL` 토큰에 바인딩된 `regionCode`와 이미 배정된 `vehicleId`를 함께 확인해, 현재 단말이 접근 가능한 mission만 반환한다.
> 응답에는 마스킹된 이름과 예약 시간만 포함한다.

**Request Body**
```json
{
  "phoneLast4": "3720",
  "birthDate6": "580315"
}
```

**Response** `200 OK`
```json
{
  "candidates": [
    {
      "missionId": "ms_F2gHn6",
      "patientMaskedName": "홍*동",
      "appointmentDate": "2026-03-20",
      "appointmentTime": "14:30",
      "doctorMaskedName": "이*종",
      "missionPhase": "ARRIVED"
    }
  ],
  "totalCount": 1
}
```

---

### 9.2b 차량 mission claim 및 미션 단말 토큰 발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/terminal/missions/{missionId}/claim` |
| Auth | Bearer Token (`DEVICE_TERMINAL`) |

> 차량 태블릿은 선택한 mission과 환자 입력값을 서버에 다시 전달해 claim을 요청한다.
> 서버는 같은 날짜/환자 정보/mission phase를 재검증한 뒤, 단말의 `vehicleId`/`regionCode`와 mission을 다시 대조한다.
> 즉시 진료처럼 `mission.vehicleId`가 아직 비어 있으면 첫 claim 시점에 현재 단말의 `vehicleId`로 고정한 뒤, 해당 mission 범위로 제한된 `MISSION_TERMINAL` 토큰을 발급한다.
> claim 성공 후 차량 태블릿은 `current_mission_id`와 `terminalToken`을 저장해, 이후 본인 확인/활력징후 저장/환자 참가 토큰 발급까지 같은 토큰을 재사용한다.

**Request Body**
```json
{
  "phoneLast4": "3720",
  "birthDate6": "580315"
}
```

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "terminalToken": "eyJhbGci...",
  "expiresIn": 1800,
  "scopes": [
    "mission:identity-check",
    "session:issue-patient-token",
    "mission:vitals-write"
  ]
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `MISSION_NOT_FOUND` | 미션 없음 |
| 403 | `TERMINAL_MISSION_CLAIM_FORBIDDEN` | 입력한 접수 정보로 해당 미션을 시작할 수 없음 |

---

### 9.3 환자 본인 확인 (차량 태블릿 — 미션 단말 토큰)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions/{missionId}/identity-check` |
| Auth | Bearer Token (MISSION_TERMINAL, ADMIN) |

> 차량 태블릿 플로우에서는 `missionId`에 바인딩된 `MISSION_TERMINAL` 토큰으로 호출하며, 운영/테스트 목적으로 `ADMIN` 호출도 허용한다.
> `진료 시작` 버튼은 환자 세션 생성이 아니라, 미션 단계 전환과 본인 확인/현장 진료 준비 시작을 의미한다.
> 서버는 `missionId -> case -> patient`로 대상 환자를 조회한 뒤 `PATIENT.reference_image_path`를 확인하고, 기준 이미지가 있으면 함께, 없으면 `referenceImage` 없이 차량에서 촬영한 `faceImage`, `idCardImage`만 GPU 서버로 전송한다.
> GPU 서버 내부 응답 필수 필드: `matched`, `faceSimilarityScore`, `idCardFaceSimilarityScore`, `reasonCodes`, `ocr.name`, `ocr.rrn`, `ocr.address`
> Spring Boot는 `ocr.rrn`에서 생년월일을 추출해 `PATIENT.birthDate6`와 비교하고, `ocr.name`, `ocr.address`도 함께 검증한다. `matched=true`이고 OCR 이름, 생년월일 6자리, 주소 중 하나 이상이 환자 정보와 일치하면 최종 통과로 판정한다. 주민등록번호 원문은 외부 API 응답에 그대로 노출하지 않는다.
> 검증 성공 시 서버는 별도 테이블 대신 TTL 캐시에 최근 본인 확인 성공 상태를 저장하고, 차량 태블릿은 활력징후 단계로 이동한다.
> FE는 얼굴 이미지를 원본 방향 전체 프레임으로 업로드하고, 신분증 이미지는 가이드 영역만 crop한 PNG로 업로드한다.
> 이후 차량 태블릿은 같은 `MISSION_TERMINAL` 토큰으로 `PUT /api/v1/missions/{missionId}/vitals`를 단계별 반복 호출한다.

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `faceImage` | file | O | 차량에서 촬영한 환자 얼굴 이미지 |
| `idCardImage` | file | O | 차량에서 촬영한 신분증 이미지. FE는 가이드 영역 crop 결과를 PNG로 업로드 |

**Response** `200 OK`
```json
{
  "missionId": "ms_K9pQr1",
  "patientId": "pat_T7nLp4",
  "status": "VERIFIED",
  "verifiedAt": "2026-03-11T09:58:00+09:00",
  "expiresInSeconds": 600,
  "identityCheck": {
    "matched": true,
    "faceSimilarityScore": 0.94,
    "idCardFaceSimilarityScore": 0.91,
    "reasonCodes": [],
    "ocr": {
      "name": "홍길동",
      "rrnMasked": "580315-1******",
      "address": "경북 울릉군 울릉읍..."
    }
  },
  "nextStep": "VITALS"
}
```

> `identityCheck.faceSimilarityScore`는 기준 이미지가 없는 경로에서는 `null`일 수 있다.
> `identityCheck.ocr.rrnMasked`는 GPU 서버가 반환한 주민등록번호 원문을 백엔드에서 마스킹한 값이다. 원문은 영속 저장하지 않는다.

**본인 확인 실패 응답 예시** `403 Forbidden`
```json
{
  "errorCode": "IDENTITY_CHECK_FAILED",
  "message": "본인 확인에 실패했습니다. 다시 촬영해주세요.",
  "timestamp": "2026-03-11T09:58:00",
  "details": [
    { "field": "identityCheck.faceSimilarityScore", "reason": "임계값 미만" }
  ]
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `MISSION_NOT_FOUND` | 미션 없음 |
| 403 | `MISSION_NOT_READY` | 미션이 본인 확인 가능한 준비 상태가 아님 |
| 403 | `IDENTITY_CHECK_FAILED` | GPU 본인 확인 실패 |
| 502 | `AI_IDV_REQUEST_FAILED` | 본인 확인 AI 서버 호출 실패 |

---

### 9.4 활력징후 저장 (차량 태블릿 — 미션 단말 토큰)

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/missions/{missionId}/vitals` |
| Auth | Bearer Token (MISSION_TERMINAL, ADMIN) |

> 차량 태블릿 플로우에서는 mission claim에서 발급받은 같은 `MISSION_TERMINAL` 토큰으로 체온/혈압/심박수/SpO2/ECG sample을 단계별 저장하며, 운영/테스트 목적으로 `ADMIN` 호출도 허용한다.
> 서버는 `missionId -> case -> vital_measurement` 순서로 대상을 해석하며, 첫 저장이면 row를 생성하고 이후에는 같은 `case_id` row를 partial upsert 한다.
> 허용 미션 phase는 `ARRIVED`, `VERIFYING`, `CONSULTING` 이다.
> `measuredAt`은 선택 입력이며, 생략하면 서버 현재 시각(KST)을 사용한다.
> `ecgWaveform`은 측정 시점 sample waveform이며 실시간 스트림이 아니다.

**Request Body**
```json
{
  "temperature": 36.7,
  "bloodPressureSys": 128,
  "bloodPressureDia": 82,
  "heartRate": 72,
  "spO2": 98,
  "ecgWaveform": [0.12, 0.18, 0.11, -0.05, 0.45, 1.10],
  "ecgSamplingHz": 25,
  "ecgDurationSeconds": 8,
  "measuredAt": "2026-03-23T14:23:10"
}
```

> 각 측정 단계에서는 필요한 필드만 보내도 된다. 예를 들어 체온 단계에서는 `{ "temperature": 36.7 }`, ECG 단계에서는 waveform 관련 필드만 보내는 식으로 같은 endpoint를 반복 호출한다.

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "vitals": {
    "caseId": "case_T7nLp4",
    "temperature": 36.7,
    "bloodPressureSys": 128,
    "bloodPressureDia": 82,
    "heartRate": 72,
    "spO2": 98,
    "ecgWaveform": [0.12, 0.18, 0.11, -0.05, 0.45, 1.10],
    "ecgSamplingHz": 25,
    "ecgDurationSeconds": 8,
    "measuredAt": "2026-03-23T14:23:10+09:00",
    "createdAt": "2026-03-23T14:15:00",
    "updatedAt": "2026-03-23T14:23:10"
  }
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 400 | `INVALID_INPUT` | 빈 body이거나 저장 가능한 측정 필드가 없음 |
| 403 | `AUTH_FORBIDDEN` | 미션 범위가 맞지 않거나 `mission:vitals-write` scope가 없음 |
| 403 | `MISSION_NOT_READY` | 미션이 활력징후 저장 가능한 준비 상태가 아님 |
| 404 | `MISSION_NOT_FOUND` | 미션 없음 |

---

### 9.5 환자 토큰 발급 (차량 태블릿 — 미션 단말 토큰)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions/{missionId}/participants/patient/token` |
| Auth | Bearer Token (MISSION_TERMINAL, ADMIN) |

> 차량 태블릿 플로우에서는 **활력징후 단계 완료 후, 의사 세션이 준비되면** 환자 참가 토큰을 요청하며, 운영/테스트 목적으로 `ADMIN` 호출도 허용한다.
> 세션 ID를 직접 아는 경우 `POST /api/v1/sessions/{sessionId}/participants/patient/token`으로도 동일한 환자 토큰을 발급받을 수 있다.
> 이 API는 `missionId -> case -> consultationSession -> patient` 순서로 대상 세션을 서버에서 해석하고, 요청에 사용한 단말 토큰이 같은 미션에 바인딩되어 있는지 확인한 뒤 이미 성공한 본인 확인 상태를 검증하고 환자용 LiveKit 토큰만 발급한다.

**Response** `200 OK`
```json
{
  "sessionId": "ses_L6pQr1",
  "patientToken": "eyJhbGci...",
  "expiresIn": 7200,
  "room": {
    "roomId": "room_ses_L6pQr1",
    "livekitUrl": "wss://<DOMAIN>/livekit"
  }
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `MISSION_NOT_READY` | 미션이 환자 참가 가능한 준비 상태가 아님 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 403 | `IDENTITY_CHECK_NOT_CONFIRMED` | 최근 본인 확인 성공 상태가 없거나 만료됨 |

---

### 9.6 세션 토큰 재발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/sessions/{sessionId}/token` |
| Auth | Bearer Token (DOCTOR) — 의사 재발급 시 / Bearer Token (ADMIN) — 환자 재발급 시 |

> 토큰 만료 시 재발급. 세션 상태가 `IN_PROGRESS`인 경우에만 허용.

**Request Body**
```json
{
  "participantType": "DOCTOR",
  "patientId": null
}
```

| participantType | Auth | 추가 검증 |
|-----------------|------|----------|
| `DOCTOR` | Bearer Token 필수 | 해당 세션의 담당 의사인지 확인 |
| `PATIENT` | Bearer Token (ADMIN) 필수 | 차량 태블릿으로 요청. `patientId` 필수 + `patientJoinedAt` 존재 + 연결 상태가 `RECONNECTING` 또는 `DISCONNECTED` 인 동일 환자인지 재검증 |

**Response** `200 OK`
```json
{
  "token": "eyJhbGci...(새 토큰)",
  "expiresIn": 7200
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 409 | `SESSION_NOT_IN_PROGRESS` | 세션이 IN_PROGRESS가 아닌 경우 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 403 | `AUTH_FORBIDDEN` | 재발급 권한이 없는 사용자 |
| 403 | `PATIENT_MISMATCH` | 세션의 케이스 환자 ID와 불일치 |
| 409 | `PATIENT_NOT_JOINED_SESSION` | 동일 환자이지만 아직 세션 입장 이력이 없는 경우 |
| 409 | `PATIENT_NOT_RECONNECTABLE` | 환자 연결 상태가 재입장 가능한 상태가 아닌 경우 |
| 400 | `INVALID_INPUT` | `PATIENT` 재발급 시 `patientId` 누락 |

---

### 9.7 세션 상태 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/sessions/{sessionId}` |
| Auth | Bearer Token (DOCTOR, ADMIN) |

**Response** `200 OK`
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "IN_PROGRESS",
  "room": {
    "roomId": "room_ses_L6pQr1",
    "livekitUrl": "wss://<DOMAIN>/livekit"
  },
  "doctor": {
    "doctorId": "doc_P5wMn4",
    "name": "김의사",
    "connectionState": "CONNECTED",
    "joinedAt": "2026-03-11T10:00:30+09:00"
  },
  "patient": {
    "patientId": "pat_Zk3mQ9",
    "name": "홍길동",
    "connectionState": "CONNECTED",
    "joinedAt": "2026-03-11T10:01:00+09:00"
  },
  "reconnectCount": 0,
  "startedAt": "2026-03-11T10:00:00+09:00"
}
```

---

### 9.8 진료 요약 저장 및 진료 종료 기록

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/sessions/{sessionId}/summary` |
| Auth | Bearer Token (DOCTOR) |

> 이 API는 의사 화면에서 **`진료 완료` 버튼을 눌렀을 때만** 호출한다.
> 진료 중 작성한 경과 기록지, 재진 여부, 처방 내역은 프론트 로컬 상태로만 유지되며 서버에 중간 저장하지 않는다.
> `PUT` 의미를 유지하기 위해 클라이언트는 진료 종료 시점의 최신 진료 요약 상태를 전체 필드로 전송한다.

**Request Body**
```json
{
  "summaryNote": "편두통 소견. 충분한 수분 섭취 및 휴식 권장. 증상 지속 시 재진료 필요.",
  "isPrescriptionIssued": true,
  "prescriptionNote": "[\"M001\",\"M005\"]",
  "needsFollowUp": true
}
```

**Field Rules**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `summaryNote` | string | O | 진료 종료 시 저장하는 경과 기록지 |
| `isPrescriptionIssued` | boolean | O | 최종 처방 내역에 약품 코드가 1개 이상 있으면 `true`, 없으면 `false` |
| `prescriptionNote` | string | O | 약품 코드 배열의 JSON 문자열. 예: `"[\"M001\",\"M005\"]"` |
| `needsFollowUp` | boolean | O | 재진 필요 여부 |

> `prescriptionNote`는 현재 문자열 필드를 재사용하므로, **약품 코드 리스트를 JSON 문자열로 직렬화한 값**을 저장한다.
> 처방이 없을 경우 `prescriptionNote`는 `"[]"`를 권장한다.
> 하위 호환을 위해 기존 자유 텍스트 처방 문자열도 조회 API에서 그대로 반환될 수 있다.

**Response** `200 OK`
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "COMPLETED",
  "summary": {
    "summaryNote": "편두통 소견. 충분한 수분 섭취 및 휴식 권장.",
    "isPrescriptionIssued": true,
    "prescriptionNote": "[\"M001\",\"M005\"]",
    "needsFollowUp": true
  },
  "endedAt": "2026-03-11T10:25:00+09:00",
  "durationMinutes": 25
}
```

---

### 9.9 LiveKit Webhook 수신 (서버 간)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/sessions/webhook/livekit` |
| Auth | LiveKit Webhook Signature |

> LiveKit에서 전송하는 이벤트를 수신하여 세션 상태를 자동 관리한다.

**수신 이벤트 목록**

| 이벤트 | 처리 |
|--------|------|
| `participant_joined` | connection_state → `CONNECTED` |
| `participant_left` | connection_state → `DISCONNECTED`, 30초 타이머 시작 |
| `room_finished` | 세션 종료 처리 |

---

## 10. 보호자 API (`/api/v1/guardians`)

> 관리자 승인을 완료한 보호자 계정만 접근할 수 있다.
> 보호자는 로그인 후 연결된 환자의 **완료된 진료 요약만 읽기 전용으로** 조회한다.

### 10.1 연결된 환자 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/guardians/patients` |
| Auth | Bearer Token (GUARDIAN) |

**Response** `200 OK`
```json
{
  "patients": [
    {
      "patientId": "pat_Zk3mQ9",
      "name": "홍길동",
      "birthDate6": "580315",
      "phone": "01012345678",
      "regionCode": "ULLEUNG",
      "address": "경북 울릉군 울릉읍 ...",
      "gender": "MALE",
      "relation": "자녀",
      "approvedAt": "2026-01-15"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `patients[].patientId` | string | 환자 공개 ID |
| `patients[].name` | string | 환자 이름 |
| `patients[].birthDate6` | string | 생년월일 6자리 |
| `patients[].phone` | string | 전화번호 |
| `patients[].regionCode` | string | 지역 코드 |
| `patients[].address` | string | 주소 |
| `patients[].gender` | string | 환자 성별 (`MALE`, `FEMALE`, `UNKNOWN`) |
| `patients[].relation` | string | 보호자와 환자의 관계 |
| `patients[].approvedAt` | date | 연결 승인 일자 |

---

### 10.2 환자의 완료된 진료 요약 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/guardians/patients/{patientId}/summaries` |
| Auth | Bearer Token (GUARDIAN) |

**Response** `200 OK`
```json
{
  "patientName": "홍길동",
  "summaries": [
    {
      "caseId": "case_T7nLp4",
      "consultationDate": "2026-03-11",
      "departmentName": "내과",
      "doctorName": "김의사",
      "summaryNote": "편두통 소견. 충분한 수분 섭취 및 휴식 권장.",
      "isPrescriptionIssued": true,
      "prescriptionNote": "[\"M001\",\"M005\"]",
      "needsFollowUp": true
    }
  ],
  "totalCount": 1
}
```

> `prescriptionNote`는 다음 두 형식 중 하나로 조회될 수 있다.
> 1. 최신 형식: 약품 코드 배열의 JSON 문자열. 예: `"[\"M001\",\"M005\"]"`
> 2. 레거시 형식: 자유 텍스트 처방 문자열
>
> 클라이언트는 먼저 `prescriptionNote`를 JSON 배열(`string[]`)로 파싱 시도하고, 성공하면 공통 `MEDICINE_CATALOG` 기준으로 약품명, 분류, 용법/용량을 매핑해 렌더링한다.
> JSON 파싱에 실패하면 레거시 자유 텍스트 처방전으로 간주하고 원문을 그대로 표시한다.

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `GUARDIAN_NOT_LINKED` | 해당 환자에 연결되지 않은 보호자 |

---

## 11. 관리자 API (`/api/v1/admin`)

> 운영 monitoring 접근 제어용 내부 API를 포함한다.
> `/actuator/health`, `/actuator/prometheus`는 Spring 앱 내부 경로로만 사용하며 public nginx 경로를 제공하지 않는다.

### 11.0 Monitoring 세션 발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/monitoring/session` |
| Auth | Bearer Token (ADMIN) |

**Response** `204 No Content`
- `Set-Cookie`: `monitoring_access=...; HttpOnly; Secure; SameSite=Lax; Path=/grafana/; Max-Age=28800`

---

### 11.0.1 Monitoring 세션 제거

| 항목 | 값 |
|------|-----|
| Method | `DELETE` |
| Path | `/api/v1/admin/monitoring/session` |
| Auth | 불필요 |

**Response** `204 No Content`
- `monitoring_access` 쿠키 삭제 (`Max-Age=0`)

---

### 11.0.2 Monitoring 접근 검증

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/monitoring/authorize` |
| Auth | 불필요 (`monitoring_access` 쿠키 검증) |

**Response** `204 No Content`

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 401 | `AUTH_UNAUTHORIZED` | monitoring 쿠키가 없거나 유효하지 않음 |
| 403 | `AUTH_FORBIDDEN` | monitoring 쿠키는 있으나 ADMIN 권한이 아님 |

---
### 11.1 예약 전체 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/bookings` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `date` | string | X | 예약 날짜 |
| `status` | string | X | 상태 필터 |
| `page` | int | X | 페이지 (기본: 0) |
| `size` | int | X | 페이지 크기 (기본: 20) |

**Response** `200 OK`
```json
{
  "bookings": [
    {
      "bookingId": "bk_H8qWm2",
      "status": "CONFIRMED",
      "patientName": "홍길동",
      "doctorName": "김의사",
      "departmentName": "내과",
      "appointmentDate": "2026-03-11",
      "startTime": "10:00",
      "caseId": "case_T7nLp4",
      "missionPhase": "DISPATCHED"
    }
  ],
  "totalCount": 50,
  "page": 0,
  "size": 20
}
```

---

### 11.2 케이스 전체 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/cases` |
| Auth | Bearer Token (ADMIN) |

**Query Params**: `date`, `status`, `page`, `size`

---

### 11.3 세션 전체 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/sessions` |
| Auth | Bearer Token (ADMIN) |

**Query Params**: `date`, `status`, `page`, `size`

---

### 11.4 환자 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/patients` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `name` | string | X | 환자명 검색 |
| `phone` | string | X | 전화번호 검색 |
| `page` | int | X | 페이지 |
| `size` | int | X | 페이지 크기 |

**Response** `200 OK`
```json
{
  "patients": [
    {
      "patientId": "pat_Zk3mQ9",
      "name": "홍길동",
      "birthDate6": "580315",
      "phone": "01012345678",
      "regionCode": "ULLEUNG",
      "address": "경북 울릉군 울릉읍 ...",
      "gender": "MALE"
    }
  ],
  "totalCount": 1,
  "page": 0,
  "size": 20
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `patients[].patientId` | string | 환자 공개 ID |
| `patients[].name` | string | 환자 이름 |
| `patients[].birthDate6` | string | 생년월일 6자리 |
| `patients[].phone` | string | 전화번호 |
| `patients[].regionCode` | string | 지역 코드 |
| `patients[].address` | string | 주소 |
| `patients[].gender` | string | 환자 성별 (`MALE`, `FEMALE`, `UNKNOWN`) |
| `totalCount` | int | 전체 환자 수 |
| `page` | int | 현재 페이지 |
| `size` | int | 페이지 크기 |

---

### 11.5 보호자 가입 요청 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/guardian-link-requests` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `status` | string | X | `PENDING`, `APPROVED`, `REJECTED` |
| `page` | int | X | 페이지 |
| `size` | int | X | 페이지 크기 |

**Response** `200 OK`
```json
{
  "requests": [
    {
      "linkId": "link_H9kLm3",
      "status": "PENDING",
      "guardianUserId": "usr_J2mNp7",
      "guardianName": "이보호",
      "patientId": "pat_Zk3mQ9",
      "patientName": "홍길동",
      "patientPhone": "01012345678",
      "relation": "자녀",
      "requestedAt": "2026-03-10T14:55:00+09:00"
    }
  ],
  "totalCount": 1,
  "page": 0,
  "size": 20
}
```

---

### 11.6 보호자 가입 승인

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/guardian-link-requests/{linkId}/approve` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "comment": "환자 전화번호 일치 확인 후 승인"
}
```

**Response** `200 OK`
```json
{
  "linkId": "link_H9kLm3",
  "status": "APPROVED",
  "patientId": "pat_Zk3mQ9",
  "guardianUserId": "usr_J2mNp7",
  "relation": "자녀",
  "approvedByUserId": "usr_A1bCd2",
  "approvedAt": "2026-03-10T15:00:00+09:00"
}
```

> 승인 시 보호자 `USER.approval_status`를 `APPROVED`, `USER.is_active`를 `true`로 전환한다.

---

### 11.7 보호자 가입 반려

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/guardian-link-requests/{linkId}/reject` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "reason": "환자 정보 불일치"
}
```

**Response** `200 OK`
```json
{
  "linkId": "link_H9kLm3",
  "status": "REJECTED",
  "processedByUserId": "usr_A1bCd2",
  "processedAt": "2026-03-10T15:05:00+09:00"
}
```

> 반려된 보호자 계정은 로그인할 수 없으며, 관리자 확인 전까지 비활성 상태로 유지된다.

**Errors** (`11.6`, `11.7` 공통)

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `GUARDIAN_LINK_REQUEST_NOT_FOUND` | 가입 요청을 찾을 수 없음 |
| 409 | `GUARDIAN_LINK_ALREADY_PROCESSED` | 이미 승인 또는 반려된 요청 |

---

### 11.8 차량 상세 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/vehicles/{vehicleId}` |
| Auth | Bearer Token (ADMIN) |

**Response** `200 OK`
```json
{
  "vehicleId": "veh_00000001",
  "code": "GIMCHEON-01",
  "regionCode": "GIMCHEON_JEUNGSAN",
  "displayName": "김천증산 1호차",
  "active": true,
  "operationalStatus": "OPERATIONAL",
  "statusChangedAt": "2026-03-20T09:00:00+09:00",
  "statusReason": null,
  "createdAt": "2026-03-20T08:00:00+09:00",
  "updatedAt": "2026-03-20T09:00:00+09:00"
}
```

---

### 11.9 차량 운영 상태 변경

| 항목 | 값 |
|------|-----|
| Method | `PATCH` |
| Path | `/api/v1/admin/vehicles/{vehicleId}` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "operationalStatus": "OUT_OF_SERVICE",
  "statusReason": "배터리 점검"
}
```

**유효한 `operationalStatus` 값**: `OPERATIONAL` | `OUT_OF_SERVICE` | `MAINTENANCE`

**Response** `200 OK`
```json
{
  "vehicleId": "veh_00000001",
  "code": "GIMCHEON-01",
  "regionCode": "GIMCHEON_JEUNGSAN",
  "displayName": "김천증산 1호차",
  "active": true,
  "operationalStatus": "OUT_OF_SERVICE",
  "statusChangedAt": "2026-03-20T10:15:00+09:00",
  "statusReason": "배터리 점검",
  "createdAt": "2026-03-20T08:00:00+09:00",
  "updatedAt": "2026-03-20T10:15:00+09:00"
}
```

> `OUT_OF_SERVICE` 또는 `MAINTENANCE` 상태의 차량은 신규 배차 대상에서 제외된다.
>
> 차량 상태가 다시 `OPERATIONAL`로 전환되면 같은 권역(`regionCode`)의 `RETRY_PENDING` 배차를 Kafka 재평가 흐름으로 다시 깨운다.

**Errors** (`11.8`, `11.9` 공통)

| Status | errorCode | 설명 |
|--------|-----------|------|
| 404 | `VEHICLE_NOT_FOUND` | 차량을 찾을 수 없음 |

---

### 11.10 데모 미션 출동

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/demo/missions/{missionId}/dispatch` |
| Auth | Bearer Token (ADMIN) |

> 데모 모드에서만 사용할 수 있는 관리자 제어 API다.
> - `MISSION.targetWaypointNumber`가 있으면 Spring이 MQTT 토픽 `robot/cmd/dispatch`로 waypoint 명령을 publish한다.
> - 같은 차량에 남아 있는 다른 활성 demo mission(`DISPATCHED`~`RETURNING`)은 새 출동 전에 `COMPLETED`로 정리한다.
> - 호출 성공 후 mission은 `EN_ROUTE`로 전이된다.
> - waypoint 매핑이 없는 주소는 로봇 호출 없이 더미 완료 mission으로 처리된다.
> - 처리 완료 후 연결된 `DISPATCH_OUTBOX`는 `COMPLETED`로 정리한다.

**Response** `200 OK`
```json
{
  "missionId": "ms_demo_01",
  "phase": "EN_ROUTE",
  "previousPhase": "CREATED",
  "vehicleId": "veh_GIMCHEON_01",
  "targetWaypointNumber": 59,
  "waypointCommandSent": true,
  "dummyCompleted": false
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `DEMO_MODE_DISABLED` | 데모 모드가 비활성화되어 있음 |
| 404 | `MISSION_NOT_FOUND` | 미션을 찾을 수 없음 |
| 400 | `MISSION_PHASE_TRANSITION_INVALID` | `CREATED` 상태가 아닌 미션에 출동 요청 |
| 502 | `ROBOT_COMMAND_REQUEST_FAILED` | MQTT waypoint 명령 publish 실패 |

---

### 11.11 데모 미션 도착 처리

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/demo/missions/{missionId}/arrive` |
| Auth | Bearer Token (ADMIN) |

> 데모 모드에서 `DISPATCHED` 또는 `EN_ROUTE` 상태의 mission을 `ARRIVED`까지 전이시킨다.

**Response** `200 OK`
```json
{
  "missionId": "ms_demo_01",
  "phase": "ARRIVED",
  "previousPhase": "DISPATCHED",
  "vehicleId": "veh_GIMCHEON_01",
  "targetWaypointNumber": 59,
  "waypointCommandSent": false,
  "dummyCompleted": false
}
```

---

### 11.12 데모 미션 종료 처리

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/demo/missions/{missionId}/complete` |
| Auth | Bearer Token (ADMIN) |

> 데모 모드에서 진행 중 mission을 `COMPLETED`까지 전이시킨다.

**Response** `200 OK`
```json
{
  "missionId": "ms_demo_01",
  "phase": "COMPLETED",
  "previousPhase": "ARRIVED",
  "vehicleId": "veh_GIMCHEON_01",
  "targetWaypointNumber": 59,
  "waypointCommandSent": false,
  "dummyCompleted": false
}
```

**Errors** (`11.10`, `11.11`, `11.12` 공통)

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `DEMO_MODE_DISABLED` | 데모 모드가 비활성화되어 있음 |
| 404 | `MISSION_NOT_FOUND` | 미션을 찾을 수 없음 |
| 400 | `MISSION_PHASE_TRANSITION_INVALID` | 현재 상태에서 허용되지 않는 데모 전이 |

---

## 12. 로봇 운영 API (`/api/v1/robots`)

> 운영 콘솔에서 로봇 텔레메트리 모니터링과 명령 전달에 사용하는 API다.
> 로봇 통신은 MQTT 브로커(Mosquitto)를 통해 이루어지며, Spring Boot가 MQTT 메시지를 중계한다.

### 12.1 로봇 텔레메트리 스트림 구독

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/robots/stream` |
| Auth | Bearer Token (ADMIN, DOCTOR) |
| Accept | `text/event-stream` |

> Spring Boot가 MQTT 토픽(`robot/odom`, `robot/minimap`, `robot/state`, `robot/status`)에서 수신한 로봇 텔레메트리를 SSE로 중계한다.
> 운영 콘솔의 지도 모니터링과 미니맵 패널에서 사용한다.

**Response**: SSE 스트림

---

### 12.2 로봇 웨이포인트 명령

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/robots/cmd/waypoint/{n}` |
| Auth | Bearer Token (ADMIN, DOCTOR) |

> MQTT 토픽 `robot/cmd/waypoint`로 waypoint 번호를 publish한다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `n` | int (path) | waypoint 번호 |

**Response** `202 Accepted`

---

### 12.3 로봇 비상정지 명령

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/robots/cmd/estop/{state}` |
| Auth | Bearer Token (ADMIN, DOCTOR) |

> MQTT 토픽 `robot/cmd/estop`로 비상정지 상태를 publish한다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `state` | int (path) | `1` = 비상정지 활성화, `0` = 비상정지 해제 |

**Response** `202 Accepted`

---

### 12.4 차량 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/vehicles` |
| Auth | Bearer Token (ADMIN) |

> 전체 차량 목록을 조회한다. 운영 콘솔의 차량 관리 패널에서 사용한다.

**Response** `200 OK`
```json
[
  {
    "vehicleId": "veh_00000001",
    "code": "GIMCHEON-01",
    "regionCode": "GIMCHEON_JEUNGSAN",
    "displayName": "김천증산 1호차",
    "active": true,
    "operationalStatus": "OPERATIONAL",
    "statusChangedAt": "2026-03-20T09:00:00+09:00",
    "statusReason": null,
    "createdAt": "2026-03-20T08:00:00+09:00",
    "updatedAt": "2026-03-20T09:00:00+09:00"
  }
]
```

---

### 12.5 차량 현재 미션 조회 (차량 단말)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/terminal/current-mission` |
| Auth | Bearer Token (`DEVICE_TERMINAL`) |

> 차량 단말이 현재 자신에게 배정된 미션을 조회한다. `DEVICE_TERMINAL` 토큰에 바인딩된 `vehicleId`와 `regionCode` 기준으로 해당 차량의 활성 미션을 반환한다.

**Response** `200 OK` (미션 있음)
```json
{
  "hasMission": true,
  "missionId": "ms_F2gHn6",
  "patientName": "홍길동",
  "appointmentDate": "2026-03-20",
  "appointmentTime": "14:30",
  "phase": "ARRIVED",
  "vehicleId": "veh_GIMCHEON_01",
  "targetWaypointNumber": 59
}
```

**Response** `200 OK` (미션 없음)
```json
{
  "hasMission": false
}
```

---

### 12.6 현재 미션 자동 claim (차량 단말)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/terminal/current-mission/claim` |
| Auth | Bearer Token (`DEVICE_TERMINAL`) |

> `GET /api/v1/terminal/current-mission`으로 조회된 미션을 별도 입력 없이 바로 claim한다. 응답은 9.2b와 동일한 `IssueMissionTerminalTokenResponse` 구조다.

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "terminalToken": "eyJhbGci...",
  "expiresIn": 1800,
  "scopes": [
    "mission:identity-check",
    "session:issue-patient-token",
    "mission:vitals-write"
  ]
}
```

---

### 12.7 미션 단말 토큰 발급 (의사/관리자)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions/{missionId}/terminal/token` |
| Auth | Bearer Token (DOCTOR, ADMIN) |

> 의사 또는 관리자가 특정 미션에 대한 `MISSION_TERMINAL` 토큰을 발급받는다. 운영/테스트 목적으로 차량 단말 부트스트랩 없이 직접 미션 토큰을 획득할 때 사용한다.

**Response** `200 OK`
```json
{
  "missionId": "ms_F2gHn6",
  "caseId": "case_T7nLp4",
  "terminalToken": "eyJhbGci...",
  "expiresIn": 1800,
  "scopes": [
    "mission:identity-check",
    "session:issue-patient-token",
    "mission:vitals-write"
  ]
}
```

---

### 12.8 미션별 진료 세션 상태 조회 (차량 단말)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/missions/{missionId}/consultation-status` |
| Auth | Bearer Token (ADMIN, MISSION_TERMINAL) |

> 차량 단말이 현재 미션에 연결된 화상진료 세션의 상태를 폴링한다. 세션이 `COMPLETED`, `FAILED`, `ABANDONED` 상태가 되면 차량 단말은 종료 화면으로 전환한다.

**Response** `200 OK`
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "IN_PROGRESS",
  "room": {
    "roomId": "room_ses_L6pQr1",
    "livekitUrl": "wss://<DOMAIN>/livekit"
  }
}
```

---

## 13. 상태 Enum 정의

> **`doctorId` 참조 규칙**: API의 `doctorId`는 `DOCTOR_PROFILE.public_id` 값을 의미한다. 내부 저장은 `doctor_profile_id`(`bigint` PK)를 사용한다. 사용자 식별이 필요할 때는 별도로 `userId`를 사용한다.
>
> **이중 ID 전략**: 모든 API 요청/응답의 ID 필드는 `public_id` 값이다. DB 내부 `bigint` PK는 외부에 노출하지 않는다.

### BOOKING.status
```
CONFIRMED → CANCELLED | COMPLETED | NO_SHOW
```

### CARE_CASE.status
```
CREATED → PREPARING → IN_PROGRESS → COMPLETED | FAILED | CANCELLED
```

### MISSION.phase
```
CREATED → DISPATCHED → EN_ROUTE → ARRIVED → VERIFYING → CONSULTING → RETURNING → COMPLETED | FAILED
※ INCIDENT는 어느 단계에서든 진입 가능한 임시 상태이며, 복구 후 이전 단계로 복귀한다.
```

### CONSULTATION_SESSION.status
```
CREATED → READY → IN_PROGRESS → COMPLETED | FAILED | ABANDONED
```

### CONNECTION_STATE (참가자별)
```
CONNECTED | RECONNECTING | DISCONNECTED
```

### INTAKE_SESSION.status
```
STARTED → IN_PROGRESS → COMPLETED | ABANDONED | FAILED
```

### INTAKE_SESSION.completionReason
``` 
BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED
```

### VEHICLE.operationalStatus
```
OPERATIONAL | OUT_OF_SERVICE | MAINTENANCE
```

### INTAKE_SESSION.selectionConfidenceLevel
```
HIGH | MEDIUM | LOW
```

### USER.role
```
ADMIN | DOCTOR | GUARDIAN
```

### PATIENT.gender
```
MALE | FEMALE | UNKNOWN
```

### PATIENT_GUARDIAN_LINK.status
```
PENDING → APPROVED | REJECTED
```
