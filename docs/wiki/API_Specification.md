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
>   "timestamp": "2026-03-11T10:00:00+09:00",
>   "correlationId": "corr_case_T7nLp4"
> }
> ```
> `correlationId`는 해당 요청이 속한 케이스/세션 흐름을 전 구간 추적하기 위한 값이다. 감사 로그의 `correlation_id`와 동일한 값을 사용한다.
>
> **상세 검증 에러 시** `details` 필드를 추가로 포함할 수 있다:
> ```json
> {
>   "errorCode": "BOOKING_SLOT_CONFLICT",
>   "message": "이미 예약된 슬롯입니다.",
>   "timestamp": "2026-03-11T10:00:00+09:00",
>   "correlationId": "corr_case_T7nLp4",
>   "details": [
>     { "field": "slotId", "reason": "해당 슬롯은 이미 다른 예약에 확정되었습니다." }
>   ]
> }
> ```
>
> **이중 ID 전략**:
> - DB 내부 PK는 `bigint` 자동 증가이며, 외부 API에는 **`public_id`** (접두사 + nanoid)를 노출한다.
> - API 요청/응답의 모든 ID 필드는 `public_id` 값이다 (예: `userId` → `"usr_V1StGXR8"`, `patientId` → `"pat_Zk3mQ9"`).
> - 접두사 규칙: `usr_` (USER), `pat_` (PATIENT), `doc_` (DOCTOR_PROFILE), `ints_` (INTAKE_SESSION), `turn_` (INTAKE_TURN), `rec_` (RECOMMENDATION), `slot_` (SCHEDULE_SLOT), `bk_` (BOOKING), `case_` (CARE_CASE), `ms_` (MISSION), `vrf_` (VERIFICATION), `ses_` (CONSULTATION_SESSION), `ntf_` (NOTIFICATION), `log_` (AUDIT_LOG), `cns_` (PATIENT_CONSENT), `vit_` (VITAL_RECORD), `evt_` (MISSION_EVENT), `link_` (PATIENT_GUARDIAN_LINK), `sms_` (SMS_LOG), `face_` (PATIENT_FACE_REFERENCE)
> - Path parameter의 ID도 `public_id` 값을 사용한다 (예: `/api/v1/bookings/bk_Abc123`).

---

## 목차

1. [인증 API](#1-인증-api-apiv1auth)
2. [환자 식별 API](#2-환자-식별-api-apiv1patients)
3. [인테이크(문진) API](#3-인테이크문진-api-apiv1intake)
4. [예약 API](#4-예약-api-apiv1bookings)
5. [케이스 API](#5-케이스-api-apiv1cases)
6. [미션(차량 출동) API](#6-미션차량-출동-api-apiv1missions)
7. [본인확인 API](#7-본인확인-api-apiv1verifications)
8. [동의 API](#8-동의-api-apiv1consents)
9. [바이탈(더미) API](#9-바이탈더미-api-apiv1casesvitals)
10. [화상진료 세션 API](#10-화상진료-세션-api-apiv1sessions)
11. [보호자 API](#11-보호자-api-apiv1guardians)
12. [관리자 API](#12-관리자-api-apiv1admin)
13. [알림 API](#13-알림-api-apiv1notifications)
14. [감사 로그 API](#14-감사-로그-api-apiv1audit-logs)
15. [상태 Enum 정의](#15-상태-enum-정의)

---

## 1. 인증 API (`/api/v1/auth`)

> 의사, 관리자, 보호자 전용. 환자는 계정이 없으므로 이 API를 사용하지 않는다.
>
> Refresh Token은 `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` 쿠키로 관리된다.

### 1.1 로그인

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
| 423 | `AUTH_ACCOUNT_LOCKED` | 계정 비활성화 |

---

### 1.2 Access Token 갱신

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
| 401 | `AUTH_TOKEN_REUSE` | 이미 사용된 RT 재사용 (Token Family 전체 무효화) |

---

### 1.3 로그아웃

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/auth/logout` |
| Auth | Bearer Token |

**Response** `204 No Content`
- `refresh_token` 쿠키 삭제 (`Max-Age=0`)
- 서버 측 Refresh Token 및 세션 무효화 처리

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

### 2.3 [Fallback] 이름 + 생년월일로 환자 조회 (3차 식별, STT)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/identify/by-info` |
| Auth | 불필요 |

> 2차 전화번호 식별도 실패 시, 이름 + 생년월일(STT 입력)로 최종 시도한다.

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
| `regionCode` | string | O | 지역 코드 |
| `address` | string | X | 주소 |
| `facePhoto` | file | X | 사전 등록 얼굴 사진 (JPEG/PNG) |

**Response** `201 Created`
```json
{
  "patientId": "pat_R7xNw3",
  "name": "홍길동",
  "birthDate6": "580315",
  "phone": "01012345678",
  "facePhotoRegistered": true
}
```
> `facePhotoRegistered`는 DB 저장 필드가 아닌 **파생 값**이다. 계산 기준: `PATIENT_FACE_REFERENCE`에 `status = APPROVED` **AND** `deleted_at IS NULL`인 레코드가 **1건 이상** 존재하면 `true`. 이 기준은 본인확인 전제조건 판단과 동일하게 적용한다.

---

### 2.5 환자 사전 등록 사진 업로드

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/patients/{patientId}/face-photos` |
| Auth | Bearer Token (ADMIN) |

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `facePhoto` | file | O | 얼굴 사진 (JPEG/PNG) |

**Response** `201 Created`
```json
{
  "faceRefId": "face_Q4rTx9",
  "patientId": "pat_Zk3mQ9",
  "status": "PENDING",
  "photoPath": "patient-reference/9f2c...jpg",
  "uploadedAt": "2026-03-10T09:00:00+09:00"
}
```

---

### 2.6 환자 사전 등록 사진 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/patients/{patientId}/face-photos` |
| Auth | Bearer Token (ADMIN) |

**Response** `200 OK`
```json
{
  "facePhotos": [
    {
      "faceRefId": "face_Q4rTx9",
      "status": "APPROVED",
      "photoPath": "patient-reference/9f2c...jpg",
      "uploadedAt": "2026-03-10T09:00:00+09:00",
      "approvedAt": "2026-03-10T09:05:00+09:00"
    }
  ]
}
```

---

### 2.7 환자 사전 등록 사진 승인

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/patients/{patientId}/face-photos/{faceRefId}/approve` |
| Auth | Bearer Token (ADMIN) |

**Response** `200 OK`
```json
{
  "faceRefId": "face_Q4rTx9",
  "status": "APPROVED",
  "approvedBy": "usr_A3dMn1",
  "approvedAt": "2026-03-10T09:05:00+09:00"
}
```

---

### 2.8 환자 사전 등록 사진 반려

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/patients/{patientId}/face-photos/{faceRefId}/reject` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "reason": "얼굴이 명확하지 않음"
}
```

**Response** `200 OK`
```json
{
  "faceRefId": "face_Q4rTx9",
  "status": "REJECTED"
}
```

---

### 2.9 환자 사전 등록 사진 삭제

| 항목 | 값 |
|------|-----|
| Method | `DELETE` |
| Path | `/api/v1/patients/{patientId}/face-photos/{faceRefId}` |
| Auth | Bearer Token (ADMIN) |

> 소프트 삭제 처리 (`deleted_at` 설정). 삭제된 reference는 본인확인에 사용되지 않는다.

**Response** `204 No Content`

---

## 3. 인테이크(문진) API (`/api/v1/intake`)

> 전화 시뮬레이터에서 증상 수집, 진료과 추천, 예약 슬롯 안내까지의 세션을 관리한다.
>
> 구조: `INTAKE_SESSION → INTAKE_TURN → SYMPTOM_INTAKE → RECOMMENDATION`
>
> **공개 세션 접근 제어**: 공개 인테이크 플로우에서는 `intakeSessionId`(`public_id`)를 세션 접근 식별자(capability token)로 사용한다. 충분히 랜덤한 nanoid로 생성하며, 세션 완료(`COMPLETED`)/만료/폐기(`ABANDONED`, `FAILED`) 후에는 해당 ID로의 상태 변경 요청을 거부한다. 세션 TTL은 서버에서 관리하며, 무활동 상태가 일정 시간 지속되면 자동으로 `ABANDONED` 처리한다.
>
> **운영 정책**:
> - `lastActivityAt` 자동 갱신: 세션 문맥 API 전반 (세션 생성, 환자 바인딩, 턴 기록, 증상 수집, 추천, 예약 등)에서 자동 갱신된다.
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
| Path | `/api/v1/intake/sessions/{intakeSessionId}/bind-patient` |
| Auth | 불필요 |

> 환자 식별 완료 후, 기존 세션에 환자를 바인딩한다.
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
| 400 | `SESSION_STATE_INVALID` | 비활성 세션 (COMPLETED/ABANDONED/FAILED) |
| 409 | `PATIENT_ALREADY_BOUND` | 이미 다른 환자가 바인딩된 세션 |

---

### 3.4 턴(Turn) 기록 — DTMF 입력

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/turns` |
| Auth | 불필요 |

**Request Body**
```json
{
  "dtmfInput": "1",
  "prompt": "새로운 예약을 원하시면 1번, 기존 예약 조회·취소를 원하시면 2번을 눌러주세요.",
  "nextAction": "ASK_SYMPTOM",
  "ttsMessage": "홍길동 어르신, 어디가 불편하신가요?"
}
```

> - `turnType`은 서버가 `DTMF`로 고정하므로 요청에 포함하지 않는다.
> - `dtmfInput`: 숫자, `*`, `#`만 허용 (`^[0-9*#]+$`), 최대 10자.
> - `nextAction`, `ttsMessage`: 프론트(시뮬레이터)에서 전달, 서버는 저장만 수행한다.

**Response** `201 Created`
```json
{
  "turnId": "turn_M4nPq8",
  "turnOrder": 1,
  "intakeSessionId": "ints_R8kxPw",
  "turnType": "DTMF",
  "dtmfInput": "1",
  "nextAction": "ASK_SYMPTOM",
  "ttsMessage": "홍길동 어르신, 어디가 불편하신가요?",
  "createdAt": "2026-03-10T10:01:00+09:00"
}
```

---

### 3.5 턴(Turn) 기록 — 음성(STT) 입력

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/turns/voice` |
| Auth | 불필요 |

> **MVP STT**: 실제 AI STT 호출 없이 Mock/Stub 응답을 반환한다. `audioFile`은 로컬 저장만 수행한다.

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `audioFile` | file | O | 녹음 파일 (webm, wav) |
| `prompt` | string | X | 직전 TTS 안내문 |
| `nextAction` | string | X | 프론트에서 전달하는 다음 행동 |
| `ttsMessage` | string | X | 프론트에서 전달하는 TTS 응답 |

**Response** `200 OK`
```json
{
  "turnId": "turn_W5vBx9",
  "turnOrder": 2,
  "intakeSessionId": "ints_R8kxPw",
  "turnType": "VOICE",
  "sttText": "어제부터 머리가 너무 아프고 열이 많이 나요",
  "sttConfidence": 0.92,
  "exceptionCode": null,
  "nextAction": "RECOMMEND",
  "ttsMessage": "머리가 아프고 열이 나시는군요.",
  "createdAt": "2026-03-10T10:02:00+09:00"
}
```

**예외 코드** (`exceptionCode`)

| 코드 | 설명 | ttsMessage |
|------|------|------------|
| `STT_FAIL` | 음성 인식 실패 | "다시 한번 짧게 말씀해주세요." |
| `NO_INPUT` | 무응답 | "입력이 확인되지 않았습니다." |
| `AMBIGUOUS_SYMPTOM` | 증상 불명확 | "가장 불편한 증상 하나만 다시 말씀해주세요." |
| `EMERGENCY_SUSPECTED` | 응급 의심 | "응급 안내를 우선 제공합니다." |

---

### 3.6 증상 분류 및 진료과/의사 추천

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/recommend` |
| Auth | 불필요 |

**Request Body**
```json
{
  "symptomText": "어제부터 머리가 너무 아프고 열이 많이 나요"
}
```

**Response** `200 OK`
```json
{
  "recommendationId": "rec_H3jLk7",
  "symptomCategory": "두통/발열",
  "department": "INTERNAL_MEDICINE",
  "departmentName": "내과",
  "confidenceLevel": "HIGH",
  "isEmergency": false,
  "reason": "두통 + 발열 증상으로 내과 진료 추천",
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
  "ttsMessage": "내과 김의사 선생님, 3월 11일 오전 10시 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요."
}
```

---

### 3.7 인테이크 세션 종료

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/complete` |
| Auth | 불필요 |

**Request Body**
```json
{
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
> - `slotId`가 해당 세션의 `RECOMMENDATION.availableSlots`에 포함되는지
> - 세션 상태가 예약 생성 가능한 단계인지
> - `patientId`, `recommendationId`, `channel`은 서버가 세션에서 자동 추출하므로 body에 포함하지 않는다.

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

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 409 | `BOOKING_SLOT_CONFLICT` | 이미 예약된 슬롯 |
| 404 | `SLOT_NOT_FOUND` | 유효하지 않은 슬롯 ID |
| 400 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 |
| 400 | `SLOT_NOT_IN_RECOMMENDATION` | 해당 세션의 추천 결과에 포함되지 않은 슬롯 |
| 400 | `SESSION_STATE_INVALID` | 예약 생성 불가한 세션 상태 |

---

### 4.2 환자의 기존 예약 조회 (인테이크 세션 문맥)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/intake/sessions/{intakeSessionId}/existing-bookings` |
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
| 400 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 |

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
| Path | `/api/v1/intake/sessions/{intakeSessionId}/existing-bookings/{bookingId}/cancel` |
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

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 400 | `BOOKING_ALREADY_CANCELLED` | 이미 취소된 예약 |
| 400 | `BOOKING_NOT_CANCELLABLE` | 취소 불가 상태 (진료 중 등) |
| 403 | `PATIENT_MISMATCH` | 세션 환자와 예약 환자 불일치 |
| 400 | `PATIENT_NOT_BOUND` | 세션에 환자가 아직 바인딩되지 않음 |

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
    "birthDate6": "580315"
  },
  "doctor": {
    "doctorId": "doc_P5wMn4",
    "name": "김의사"
  },
  "intakeSummary": {
    "symptomText": "어제부터 머리가 너무 아프고 열이 많이 나요",
    "symptomCategory": "두통/발열",
    "department": "내과",
    "intakeSessionId": "ints_R8kxPw"
  },
  "missionId": "ms_F2gHn6",
  "sessionId": null,
  "verification": {
    "status": "PENDING"
  },
  "consent": {
    "telemedicineAgreed": false
  },
  "createdAt": "2026-03-10T10:05:00+09:00"
}
```

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
      "patientName": "홍길동",
      "symptomCategory": "두통/발열",
      "appointmentDate": "2026-03-11",
      "startTime": "10:00",
      "verificationStatus": "PENDING"
    }
  ],
  "totalCount": 1
}
```

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
      "vehicleId": "v-001",
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
  "vehicleId": "v-001",
  "patientName": "홍길동",
  "destination": "경북 울릉군 울릉읍...",
  "dispatchedAt": "2026-03-11T08:30:00+09:00",
  "estimatedArrivalTime": "2026-03-11T09:45:00+09:00",
  "events": [
    {
      "eventId": "evt_Q3rTx8",
      "eventType": "PHASE_CHANGED",
      "fromPhase": "CREATED",
      "toPhase": "DISPATCHED",
      "timestamp": "2026-03-11T08:30:00+09:00"
    }
  ]
}
```

---

### 6.3 미션 생성 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "caseId": "case_T7nLp4",
  "vehicleId": "v-001",
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
  "vehicleId": "v-001",
  "createdAt": "2026-03-10T14:00:00+09:00"
}
```

---

### 6.4 미션 단계 수동 전환 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/missions/{missionId}/phase` |
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

### 6.5 차량 이벤트 수신 (ROS2 → Spring Boot)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/missions/{missionId}/events` |
| Auth | API Key (내부 서비스) |

**Request Body**
```json
{
  "eventType": "LOCATION_UPDATE",
  "source": "ROS2",
  "sourceEventId": "ros2_evt_abc123",
  "seqNo": 42,
  "vehicleId": "v-001",
  "latitude": 37.4845,
  "longitude": 130.9057,
  "speed": 30.5,
  "heading": 180,
  "timestamp": "2026-03-11T09:15:00+09:00",
  "metadata": {}
}
```

| eventType | 설명 |
|-----------|------|
| `LOCATION_UPDATE` | 위치 업데이트 |
| `PHASE_CHANGED` | 자동 단계 전환 |
| `INCIDENT_REPORTED` | 장애 보고 |
| `INCIDENT_RESOLVED` | 장애 복구 |

**Response** `202 Accepted`

---

## 7. 본인확인 API (`/api/v1/verifications`)

> 사전 등록 사진(Reference)과 현장 촬영 사진(Probe)을 IDV AI로 비교한다.

### 7.1 본인확인 요청

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/verifications` |
| Auth | Bearer Token (ADMIN) 또는 내부 서비스 |

**Request Body** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `missionId` | string | O | 미션 ID |
| `patientId` | string | O | 환자 ID |
| `probeImage` | file | O | 현장 촬영 사진 (JPEG/PNG) |

**Response** `200 OK`
```json
{
  "verificationId": "vrf_B4cKm7",
  "status": "VERIFIED",
  "similarityScore": 0.93,
  "attemptCount": 1,
  "maxAttempts": 3,
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "blurScore": 0.12,
    "brightnessOk": true
  },
  "message": "본인 확인이 완료되었습니다."
}
```

**FAILED 응답 (재시도 가능)**
```json
{
  "verificationId": "vrf_B4cKm7",
  "status": "FAILED",
  "similarityScore": 0.45,
  "attemptCount": 2,
  "maxAttempts": 3,
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "blurScore": 0.65,
    "brightnessOk": false
  },
  "message": "본인 확인에 실패했습니다. 밝은 곳에서 다시 촬영해주세요. (1회 남음)"
}
```

**MANUAL_REVIEW 응답 (3차 실패)**
```json
{
  "verificationId": "vrf_B4cKm7",
  "status": "MANUAL_REVIEW",
  "attemptCount": 3,
  "message": "관리자 확인이 필요합니다."
}
```

---

### 7.2 본인확인 수동 승인 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/verifications/{verificationId}/manual-approve` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "approved": true,
  "reason": "현장 운영자 신원 확인 완료"
}
```

**Response** `200 OK`
```json
{
  "verificationId": "vrf_B4cKm7",
  "status": "VERIFIED",
  "approvedBy": "usr_A3dMn1",
  "approvedAt": "2026-03-11T09:55:00+09:00"
}
```

---

## 8. 동의 API (`/api/v1/consents`)

### 8.1 동의 기록

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/consents` |
| Auth | Bearer Token (ADMIN) 또는 내부 서비스 |

**Request Body**
```json
{
  "patientId": "pat_Zk3mQ9",
  "caseId": "case_T7nLp4",
  "consentTypes": [
    {
      "type": "TELEMEDICINE",
      "agreed": true
    },
    {
      "type": "PRIVACY",
      "agreed": true
    }
  ],
  "channel": "TABLET_TOUCH",
  "witness": "운영자"
}
```

**Response** `201 Created`
```json
{
  "consents": [
    { "consentId": "cns_A1bXq2", "type": "TELEMEDICINE", "agreed": true },
    { "consentId": "cns_C3dYr4", "type": "PRIVACY", "agreed": true }
  ],
  "patientId": "pat_Zk3mQ9",
  "caseId": "case_T7nLp4",
  "allAgreed": true,
  "consentedAt": "2026-03-11T09:50:00+09:00"
}
```

---

### 8.2 동의 현황 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/consents` |
| Auth | Bearer Token (DOCTOR, ADMIN) |

**Query Params**: `?caseId=case_T7nLp4`

**Response** `200 OK`
```json
{
  "consents": [
    {
      "consentId": "cns_A1bXq2",
      "type": "TELEMEDICINE",
      "agreed": true,
      "channel": "TABLET_TOUCH",
      "consentedAt": "2026-03-11T09:50:00+09:00"
    },
    {
      "consentId": "cns_C3dYr4",
      "type": "PRIVACY",
      "agreed": true,
      "channel": "TABLET_TOUCH",
      "consentedAt": "2026-03-11T09:50:00+09:00"
    }
  ]
}
```

---

## 9. 바이탈(더미) API (`/api/v1/cases/vitals`)

> MVP에서는 실기기 연동 없이 더미 데이터를 수동 입력한다.

### 9.1 바이탈 데이터 입력

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/cases/{caseId}/vitals` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "measurements": [
    {
      "type": "HEART_RATE",
      "value": 82,
      "unit": "bpm"
    },
    {
      "type": "SPO2",
      "value": 98,
      "unit": "%"
    },
    {
      "type": "BLOOD_PRESSURE_SYSTOLIC",
      "value": 125,
      "unit": "mmHg"
    },
    {
      "type": "BLOOD_PRESSURE_DIASTOLIC",
      "value": 80,
      "unit": "mmHg"
    },
    {
      "type": "TEMPERATURE",
      "value": 37.2,
      "unit": "°C"
    }
  ],
  "source": "MANUAL",
  "measuredAt": "2026-03-11T09:55:00+09:00"
}
```

**유효한 type 값**: `HEART_RATE`, `SPO2`, `BLOOD_PRESSURE_SYSTOLIC`, `BLOOD_PRESSURE_DIASTOLIC`, `TEMPERATURE`

**Response** `201 Created`
```json
{
  "vitalRecordId": "vit_G5hJk3",
  "caseId": "case_T7nLp4",
  "measurementCount": 5,
  "source": "MANUAL",
  "recordedAt": "2026-03-11T09:55:00+09:00"
}
```

---

### 9.2 바이탈 데이터 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/cases/{caseId}/vitals` |
| Auth | Bearer Token (DOCTOR, ADMIN) |

**Response** `200 OK`
```json
{
  "caseId": "case_T7nLp4",
  "latestMeasuredAt": "2026-03-11T09:55:00+09:00",
  "measurements": [
    { "type": "HEART_RATE", "value": 82, "unit": "bpm", "isNormal": true },
    { "type": "SPO2", "value": 98, "unit": "%", "isNormal": true },
    { "type": "BLOOD_PRESSURE_SYSTOLIC", "value": 125, "unit": "mmHg", "isNormal": true },
    { "type": "BLOOD_PRESSURE_DIASTOLIC", "value": 80, "unit": "mmHg", "isNormal": true },
    { "type": "TEMPERATURE", "value": 37.2, "unit": "°C", "isNormal": true }
  ],
  "source": "MANUAL"
}
```

---

## 10. 화상진료 세션 API (`/api/v1/sessions`)

> LiveKit 기반 1:1 WebRTC 화상진료 세션을 관리한다.
>
> **토큰 발급 정책**: 의사와 환자의 토큰은 **별도 엔드포인트**에서 발급한다.
> - 의사: 세션 생성 시 (10.1) 자신의 토큰만 발급
> - 환자: 본인확인 + 동의 완료 후 차량 태블릿에서 별도 요청 (10.2)

### 10.1 진료 세션 생성 — 의사 토큰 발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/cases/{caseId}/sessions` |
| Auth | Bearer Token (DOCTOR) |

> 의사가 "진료 시작"을 클릭하면 세션을 생성하고 **의사 본인의 LiveKit 토큰만** 발급한다.
> 의사는 이미 Bearer Token으로 인증되어 있으므로, `doctorId`는 Request Body에서 제거하고 **서버가 Access Token의 principal에서 온 `userId` → `DOCTOR_PROFILE`을 조회**한다.

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

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `CASE_NOT_ASSIGNED` | 해당 의사에게 배정되지 않은 케이스 |
| 409 | `SESSION_ALREADY_EXISTS` | 이미 활성 세션이 존재 |

---

### 10.2 환자 토큰 발급 (차량 태블릿 — 관리자 인증)

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/sessions/{sessionId}/participants/patient/token` |
| Auth | Bearer Token (ADMIN) |

> 차량 태블릿은 **운영 단말**로 정의하며, 관리자 계정으로 로그인되어 있다.
> 본인확인(VERIFIED) + 동의 완료된 환자의 WebRTC 토큰을 발급한다.
> 서버는 `sessionId` 기반으로 VERIFICATION + CONSENT 상태를 검증한다.

**Request Body**
```json
{
  "patientId": "pat_Zk3mQ9"
}
```

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
| 403 | `VERIFICATION_NOT_COMPLETED` | 본인확인 미완료 |
| 403 | `CONSENT_NOT_COMPLETED` | 동의 미완료 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 400 | `PATIENT_MISMATCH` | 세션의 케이스 환자 ID와 불일치 |

---

### 10.3 세션 토큰 재발급

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/sessions/{sessionId}/token` |
| Auth | Bearer Token (DOCTOR) — 의사 재발급 시 / 불필요 — 환자 재발급 시 |

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
| `PATIENT` | Bearer Token (ADMIN) 필수 | 차량 태블릿으로 요청. `patientId` 필수 + VERIFICATION 상태 재확인 |

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
| 400 | `SESSION_NOT_IN_PROGRESS` | 세션이 IN_PROGRESS가 아닌 경우 |

---

### 10.4 세션 상태 조회

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

### 10.5 진료 종료 및 요약 기록

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/sessions/{sessionId}/summary` |
| Auth | Bearer Token (DOCTOR) |

**Request Body**
```json
{
  "summaryNote": "편두통 소견. 충분한 수분 섭취 및 휴식 권장. 증상 지속 시 재진료 필요.",
  "isPrescriptionIssued": true,
  "prescriptionNote": "타이레놀 500mg",
  "needsFollowUp": true,
  "followUpNote": "1주일 후 재진 권장"
}
```

**Response** `200 OK`
```json
{
  "sessionId": "ses_L6pQr1",
  "caseId": "case_T7nLp4",
  "status": "COMPLETED",
  "summary": {
    "summaryNote": "편두통 소견. 충분한 수분 섭취 및 휴식 권장.",
    "isPrescriptionIssued": true,
    "prescriptionNote": "타이레놀 500mg",
    "needsFollowUp": true,
    "followUpNote": "1주일 후 재진 권장"
  },
  "endedAt": "2026-03-11T10:25:00+09:00",
  "durationMinutes": 25
}
```

---

### 10.6 LiveKit Webhook 수신 (서버 간)

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

## 11. 보호자 API (`/api/v1/guardians`)

> 보호자는 로그인 후 연결된 환자의 **완료된 진료 요약만 읽기 전용으로** 조회한다.

### 11.1 연결된 환자 목록 조회

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
      "relation": "자녀",
      "linkedAt": "2026-01-15"
    }
  ]
}
```

---

### 11.2 환자의 완료된 진료 요약 목록 조회

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
      "prescriptionNote": "타이레놀 500mg",
      "needsFollowUp": true,
      "followUpNote": "1주일 후 재진 권장"
    }
  ],
  "totalCount": 1
}
```

**Errors**

| Status | errorCode | 설명 |
|--------|-----------|------|
| 403 | `GUARDIAN_NOT_LINKED` | 해당 환자에 연결되지 않은 보호자 |

---

## 12. 관리자 API (`/api/v1/admin`)

### 12.1 예약 전체 목록 조회

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

### 12.2 케이스 전체 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/cases` |
| Auth | Bearer Token (ADMIN) |

**Query Params**: `date`, `status`, `page`, `size`

---

### 12.3 세션 전체 목록 조회

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/sessions` |
| Auth | Bearer Token (ADMIN) |

**Query Params**: `date`, `status`, `page`, `size`

---

### 12.4 환자 목록 조회

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

---

### 12.5 본인확인 실패 건 조회 (MANUAL_REVIEW)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/admin/verifications/manual-review` |
| Auth | Bearer Token (ADMIN) |

**Response** `200 OK`
```json
{
  "verifications": [
    {
      "verificationId": "vrf_D6fNr5",
      "patientName": "박노인",
      "missionId": "ms_G8jKp2",
      "attemptCount": 3,
      "lastAttemptAt": "2026-03-11T09:58:00+09:00",
      "status": "MANUAL_REVIEW"
    }
  ]
}
```

---

### 12.6 환자-보호자 연결 관리

| 항목 | 값 |
|------|-----|
| Method | `POST` |
| Path | `/api/v1/admin/patient-guardian-link` |
| Auth | Bearer Token (ADMIN) |

**Request Body**
```json
{
  "patientId": "pat_Zk3mQ9",
  "guardianUserId": "usr_J2mNp7",
  "relation": "자녀"
}
```

**Response** `201 Created`
```json
{
  "linkId": "link_H9kLm3",
  "patientId": "pat_Zk3mQ9",
  "guardianUserId": "usr_J2mNp7",
  "relation": "자녀",
  "linkedAt": "2026-03-10T15:00:00+09:00"
}
```

---

## 13. 알림 API (`/api/v1/notifications`)

> MVP에서는 웹 내 알림 (SSE 또는 Polling) 방식을 사용한다.

### 13.1 SSE 실시간 알림 구독

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/notifications/subscribe` |
| Auth | Bearer Token |
| Content-Type | `text/event-stream` |

> [!WARNING]
> **표준 `EventSource` API는 커스텀 헤더를 지원하지 않는다.** React에서는 반드시 `@microsoft/fetch-event-source` 폴리필을 사용하여 `Authorization: Bearer` 헤더를 전송해야 한다. 자세한 구현은 `Architecture.md` §8.9 참조.

**SSE 이벤트 예시**
```
event: BOOKING_CREATED
data: {"notificationId":"ntf_Xw2Bq7","title":"새 예약","message":"홍길동 환자 3/11 10:00 내과 예약","createdAt":"2026-03-10T10:05:00+09:00"}

event: SESSION_READY
data: {"notificationId":"ntf_Yz3Cr8","title":"진료 준비 완료","message":"홍길동 환자 본인확인/동의 완료","createdAt":"2026-03-11T09:58:00+09:00"}
```

**알림 이벤트 종류**

| event | 대상 | 설명 |
|-------|------|------|
| `BOOKING_CREATED` | ADMIN, DOCTOR | 예약 생성 |
| `BOOKING_CANCELLED` | ADMIN, DOCTOR | 예약 취소 |
| `MISSION_PHASE_CHANGED` | ADMIN | 미션 단계 변경 |
| `VERIFICATION_COMPLETED` | DOCTOR | 본인확인 완료 |
| `VERIFICATION_MANUAL_REVIEW` | ADMIN | 수동 검토 필요 |
| `SESSION_READY` | DOCTOR | 진료 시작 가능 |
| `SESSION_ABANDONED` | ADMIN | 세션 이탈 |
| `CONSULTATION_COMPLETED` | GUARDIAN | 진료 완료 (보호자) |

---

### 13.2 알림 목록 조회 (Polling 대안)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/notifications` |
| Auth | Bearer Token |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `unreadOnly` | boolean | X | 읽지 않은 알림만 (기본: false) |
| `page` | int | X | 페이지 |
| `size` | int | X | 페이지 크기 |

**Response** `200 OK`
```json
{
  "notifications": [
    {
      "notificationId": "ntf_Xw2Bq7",
      "type": "BOOKING_CREATED",
      "title": "새 예약",
      "message": "홍길동 환자 3/11 10:00 내과 예약",
      "isRead": false,
      "createdAt": "2026-03-10T10:05:00+09:00"
    }
  ],
  "unreadCount": 3,
  "totalCount": 15
}
```

---

### 13.3 알림 읽음 처리

| 항목 | 값 |
|------|-----|
| Method | `PUT` |
| Path | `/api/v1/notifications/{notificationId}/read` |
| Auth | Bearer Token |

**Response** `200 OK`

---

### 13.4 SMS 발송 기록 조회 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/notifications/sms-logs` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `patientId` | string | X | 환자 ID 필터 |
| `sendStatus` | string | X | `SENT`, `FAILED` 등 |
| `page` | int | X | 페이지 |
| `size` | int | X | 페이지 크기 |

**Response** `200 OK`
```json
{
  "smsLogs": [
    {
      "smsLogId": "sms_K3nPq8",
      "notificationId": "ntf_Qw3rTy",
      "recipientPhone": "01012345678",
      "senderPhone": "01000000000",
      "sendStatus": "SENT",
      "vendor": "SOLAPI",
      "sentAt": "2026-03-10T10:05:01+09:00"
    }
  ],
  "totalCount": 5,
  "page": 0,
  "size": 20
}
```

---

## 14. 감사 로그 API (`/api/v1/audit-logs`)

### 14.1 감사 로그 조회 (관리자)

| 항목 | 값 |
|------|-----|
| Method | `GET` |
| Path | `/api/v1/audit-logs` |
| Auth | Bearer Token (ADMIN) |

**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `startDate` | string | X | 시작일 (YYYY-MM-DD) |
| `endDate` | string | X | 종료일 |
| `actorId` | string | X | 행위자 ID |
| `action` | string | X | 행위 종류 필터 |
| `targetType` | string | X | 대상 타입 필터 |
| `page` | int | X | 페이지 |
| `size` | int | X | 페이지 크기 |

**Response** `200 OK`
```json
{
  "logs": [
    {
      "logId": "log_M4nPq8",
      "timestamp": "2026-03-11T10:00:00+09:00",
      "actorId": "usr_K9mXw2",
      "actorRole": "DOCTOR",
      "action": "SESSION_STARTED",
      "targetType": "CONSULTATION_SESSION",
      "targetId": "ses_L6pQr1",
      "correlationId": "corr_case_T7nLp4",
      "detailJson": {
        "caseId": "case_T7nLp4",
        "patientId": "pat_Zk3mQ9"
      }
    }
  ],
  "totalCount": 200,
  "page": 0,
  "size": 20
}
```

**기록 대상 action 목록**

| action | 설명 |
|--------|------|
| `USER_LOGIN` | 로그인 |
| `USER_LOGOUT` | 로그아웃 |
| `BOOKING_CREATED` | 예약 생성 |
| `BOOKING_CANCELLED` | 예약 취소 |
| `CASE_CREATED` | 케이스 생성 |
| `MISSION_PHASE_CHANGED` | 미션 단계 변경 |
| `VERIFICATION_ATTEMPTED` | 본인확인 시도 |
| `VERIFICATION_SUCCEEDED` | 본인확인 성공 |
| `VERIFICATION_FAILED` | 본인확인 실패 |
| `VERIFICATION_MANUAL_APPROVED` | 수동 승인 |
| `CONSENT_RECORDED` | 동의 기록 |
| `SESSION_STARTED` | 진료 세션 시작 |
| `SESSION_COMPLETED` | 진료 세션 완료 |
| `SESSION_ABANDONED` | 진료 세션 이탈 |
| `GUARDIAN_ACCESS` | 보호자 조회 |
| `VITAL_RECORDED` | 바이탈 기록 |
| `INTAKE_SESSION_CREATED` | 인테이크 세션 생성 (`actorRole=SYSTEM`) |
| `INTAKE_PATIENT_BOUND` | 인테이크 세션 환자 바인딩 (`actorRole=SYSTEM`) |
| `INTAKE_TURN_RECORDED` | 인테이크 턴 기록 (DTMF/VOICE) (`actorRole=SYSTEM`) |
| `INTAKE_SESSION_COMPLETED` | 인테이크 세션 종료 (`actorRole=SYSTEM`) |
| `INTAKE_SESSION_TIMEOUT` | 인테이크 세션 타임아웃 (후속 이슈) |

> **`actorRole=SYSTEM` 처리**: 무인증 공개 API(인테이크 세션 등)에서 발생하는 감사 로그는 `actorId="SYSTEM"`, `actorRole="SYSTEM"`으로 기록한다.

---

## 15. 상태 Enum 정의

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

### VERIFICATION.status
```
PENDING → IN_PROGRESS → VERIFIED | FAILED | MANUAL_REVIEW | TIMEOUT
```

### INTAKE_SESSION.status
```
STARTED → IN_PROGRESS → COMPLETED | ABANDONED | FAILED
```

### INTAKE_SESSION.completionReason
```
BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED
```

### CONSENT.type
```
TELEMEDICINE | PRIVACY
```

### VITAL_MEASUREMENT.type
```
HEART_RATE | SPO2 | BLOOD_PRESSURE_SYSTOLIC | BLOOD_PRESSURE_DIASTOLIC | TEMPERATURE
```

### RECOMMENDATION.confidenceLevel
```
HIGH | MEDIUM | LOW
```

### USER.role
```
ADMIN | DOCTOR | GUARDIAN
```

### NOTIFICATION.type
```
BOOKING_CREATED | BOOKING_CANCELLED | MISSION_PHASE_CHANGED | VERIFICATION_COMPLETED |
VERIFICATION_MANUAL_REVIEW | SESSION_READY | SESSION_ABANDONED | CONSULTATION_COMPLETED
```
