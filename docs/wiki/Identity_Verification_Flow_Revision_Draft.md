# Identity Verification Flow Revision Draft

## 1. 목적

이 문서는 차량 탑승 이후 본인확인 플로우를 다음 기준으로 재정렬하기 위한 수정안 초안이다.

- 차량 UX: `탑승 대기 -> 진료 시작 -> 얼굴 촬영 -> 신분증 촬영 -> 본인인증 결과 -> 활력징후 -> 진료실 입장`
- GPU 서버: FastAPI 기반 AI 추론 서버
- 호출 경계: 차량 FE는 GPU 서버를 직접 호출하지 않고, 반드시 Spring Boot를 경유
- 기준 이미지: 환자 등록 시 저장된 `PATIENT.reference_image_path`를 우선 사용하되, 없으면 no-reference 경로로 처리
- 보안 원칙: 주민등록번호 원문과 촬영 원본은 최소 보관

이 초안은 기존 문서와 충돌하는 구간을 먼저 정리하는 용도다. 개별 문서 반영 전까지는 아래 규칙을 우선 기준으로 본다.

## 2. 현재 문서와의 비교 요약

### 2.1 일치하는 내용

- 차량 태블릿은 운영 단말이며 `MISSION_TERMINAL` 또는 관리자 권한으로 요청한다.
- Spring Boot가 환자 기준 이미지를 조회하고 GPU 서버로 multipart 요청을 보낸다. 기준 이미지가 없으면 `referenceImage` 없이 호출한다.
- GPU 서버 응답에는 얼굴 점수, 신분증 얼굴 점수, OCR 결과, reason codes가 포함된다.
- Spring Boot는 OCR 이름, 주소, 주민등록번호 기반 생년월일을 환자 정보와 다시 대조한다.
- FE는 GPU 서버를 직접 호출하지 않는다.

### 2.2 수정이 필요한 내용

- 기존 `POST /api/v1/sessions/{sessionId}/participants/patient/token`은 본인확인과 환자 토큰 발급을 한 번에 처리한다.
  요청한 UX는 `본인확인 결과`와 `진료실 입장` 사이에 `활력징후` 단계가 있으므로, 본인확인과 환자 토큰 발급을 분리해야 한다.
- 일부 문서에는 `동의` 단계가 본인확인 뒤에 들어가 있다.
  이번 수정안에서는 `동의`를 본인확인 선행 조건에서 제외하고 후속 확장 범위로 분리한다.
- `활력징후` 단계는 Project 문서에는 존재하지만, MVP 문서에는 별도 저장 도메인이 제외돼 있다.
  따라서 MVP 범위에서는 활력징후 단계를 UX에는 포함하되, 측정값 영속 저장은 선택 또는 더미 fallback으로 제한한다.
- AI 문서는 현재 STT 중심이며 IDV FastAPI API가 정의돼 있지 않다.

### 2.3 최근 구현 반영 사항

- `S14P21A603-430`: 기준 이미지 없는 본인확인 경로 반영 완료
  - FastAPI `/idv/api/v1/verify`에서 `referenceImage` optional 처리
  - 기준 이미지가 없으면 `live face ↔ id card face + OCR` 경로로 `matched` 계산
  - Spring Boot는 OCR 이름, 생년월일 6자리, 주소 중 하나 이상 일치하면 최종 통과 판정
  - FastAPI 응답 요약 로그를 백엔드에 남김
- `S14P21A603-431`: 차량 본인확인 신분증 촬영 품질 개선 반영 완료
  - FE가 전체 프레임 대신 신분증 가이드 영역만 crop해 업로드
  - `object-cover` 기준 source 좌표 보정 적용
  - 신분증 이미지를 PNG로 업로드
  - 업로드 이미지는 좌우 반전 없이 원본 방향 유지

## 3. 목표 플로우

### 3.1 운영 플로우

1. 차량이 도착하면 `MISSION.phase = ARRIVED`
2. 환자 탑승 완료를 운영자가 확인
3. 차량 태블릿에서 `진료 시작` 버튼 클릭
4. 서버는 `MISSION.phase = VERIFYING` 으로 전환
5. 차량 태블릿은 얼굴 촬영
6. 차량 태블릿은 신분증 촬영
7. 차량 태블릿은 Spring Boot에 본인확인 요청
8. Spring Boot는 기준 이미지가 있으면 함께, 없으면 `referenceImage` 없이 FastAPI `/idv/api/v1/verify` 호출
9. Spring Boot는 AI 결과와 환자 원본 정보를 재검증한 뒤 본인확인 결과 반환
10. 성공 시 차량 태블릿은 활력징후 단계로 이동
11. 활력징후 단계 완료 후, 의사 세션이 준비되면 환자 토큰 발급 요청
12. 환자 토큰 발급 성공 시 진료실 입장
13. 의사/환자 모두 입장하면 `MISSION.phase = CONSULTING`

### 3.2 FE 상태 모델

차량 FE는 아래 상태를 명시적으로 가져간다.

- `ARRIVED_WAIT`
- `START_READY`
- `FACE_CAPTURE`
- `IDCARD_CAPTURE`
- `VERIFYING`
- `VERIFIED`
- `VERIFY_FAILED`
- `VITALS_PENDING`
- `WAITING_DOCTOR_SESSION`
- `WAITING_JOIN`
- `IN_CONSULTATION`

주의:

- `진료 시작`은 세션 생성 액션이 아니다.
- 차량 단말은 환자 세션을 생성하지 않는다.
- 차량 단말은 본인확인과 환자 참가 준비의 시작점만 담당한다.

## 4. 제안 API 수정안

## 4.1 신규 API: 본인확인 전용

### `POST /api/v1/missions/{missionId}/identity-check`

- Auth: `Bearer Token (MISSION_TERMINAL | ADMIN)`
- Content-Type: `multipart/form-data`
- 목적: 얼굴 촬영 + 신분증 촬영 결과를 사용해 본인확인만 수행
- 전제 조건:
  - `MISSION.phase == ARRIVED` 또는 `MISSION.phase == VERIFYING`
  - `missionId`로 연결된 케이스 환자가 존재

Request parts:

- `faceImage`: file
- `idCardImage`: file

Spring 내부 처리:

1. `missionId -> case -> patient`로 대상 환자 조회
2. `MISSION.phase`를 `VERIFYING`으로 전환
3. `PATIENT.reference_image_path` 조회
4. 기준 이미지가 있으면 `referenceImage + faceImage + idCardImage`, 없으면 `faceImage + idCardImage`만 FastAPI `/idv/api/v1/verify`로 전달
5. OCR 이름, 주소, 주민등록번호 기반 생년월일을 환자 정보와 재검증
6. AI 응답 `matched=true`이고 OCR 이름, 생년월일 6자리, 주소 중 하나 이상이 환자 정보와 일치하면 성공 처리
7. 성공 시 Redis 등 휘발성 저장소에 `verified` 상태를 TTL 기반으로 저장

Response `200 OK` 예시:

```json
{
  "missionId": "mis_123",
  "patientId": "pat_123",
  "status": "VERIFIED",
  "verifiedAt": "2026-03-19T13:20:00+09:00",
  "expiresInSeconds": 600,
  "identityCheck": {
    "matched": true,
    "faceSimilarityScore": 0.94,
    "idCardFaceSimilarityScore": 0.91,
    "reasonCodes": [],
    "ocr": {
      "name": "홍길동",
      "rrnMasked": "580315-1******",
      "address": "경북 울릉군 울릉읍 ..."
    }
  },
  "nextStep": "VITALS"
}
```

실패 정책:

- 재촬영 허용 횟수: 3회 권장
- 초과 시 `MANUAL_REVIEW_REQUIRED` 또는 운영자 확인 안내 반환

### 상태 저장 방식

별도 `VERIFICATION` 테이블은 만들지 않는다.

- 저장 위치: Redis 또는 동등한 TTL 캐시
- key 예시: `identity-check:{missionId}:{patientId}`
- TTL 예시: 10분
- 저장 값 예시:
  - `status=VERIFIED`
  - `verifiedAt`
  - `reasonCodes`
  - 최소한의 correlation 정보

이유:

- 문서 기준상 별도 본인확인 이력 리소스가 필수는 아니다.
- 활력징후 단계와 진료실 입장 사이에서만 참조하면 되므로 영속 저장보다 TTL 캐시가 단순하다.

## 4.2 기존 API 역할 축소: 환자 토큰 발급

### `POST /api/v1/sessions/{sessionId}/participants/patient/token`

기존 역할:

- 본인확인 + 환자 토큰 발급

수정 후 역할:

- 이미 성공한 본인확인 상태를 확인한 뒤 환자 LiveKit 토큰만 발급

검증 규칙:

1. `sessionId` 유효
2. `sessionId -> case -> patient`로 대상 환자 조회
3. `MISSION.phase`가 `VERIFYING` 또는 `CONSULTING`
4. `identity-check:{missionId}:{patientId}` 가 `VERIFIED`
5. 필요 시 verified TTL이 유효

Request body는 사용하지 않는다.

주의:

- 이 API에서는 더 이상 `faceImage`, `idCardImage`를 받지 않는다.
- 본인확인 이미지 업로드 책임은 `mission identity-check` API로 이동한다.

## 4.3 의사 세션 생성 API 유지

### `POST /api/v1/cases/{caseId}/sessions`

이 API는 그대로 유지한다.

- 세션 생성 주체는 의사
- 의사 본인의 토큰만 발급
- 차량 단말은 세션 생성 권한이 없음

차량 단말은 아래 두 상태를 처리해야 한다.

- 활성 세션 없음: `WAITING_DOCTOR_SESSION`
- 활성 세션 있음 + verified 완료: 환자 토큰 발급 후 입장

## 5. FastAPI IDV API 초안

## 5.1 신규 엔드포인트

### `GET /idv/api/v1/health`

목적:

- GPU IDV 프로세스 상태 확인

### `POST /idv/api/v1/verify`

Request parts:

- `verificationId`
- `patientId`
- `verificationMode=FACE_AND_IDCARD`
- `referenceImage` (optional)
- `faceImage`
- `idCardImage`

Response fields:

- `verificationId`
- `status`
- `matched`
- `faceSimilarityScore`
- `idCardFaceSimilarityScore`
- `reasonCodes`
- `ocr.name`
- `ocr.rrnMasked` 또는 `ocr.rrn`
- `ocr.address`
- `qualityChecks.faceDetected`
- `qualityChecks.singleFace`
- `qualityChecks.idCardDetected`
- `qualityChecks.ocrConfidence`
- `modelVersion`

현재 구현 파일:

- `app/api/v1/routes/idv.py`
- `app/services/idv_service.py`
- `app/services/idv_model_registry.py`
- `app/services/idv_ocr_parser.py`
- `app/services/idv_quality_service.py`
- `app/services/idv_similarity.py`

`idv_service.py`가 얼굴 검출, embedding, OCR, 품질 검사, 최종 응답 조립을 오케스트레이션한다.

## 5.2 Spring과 FastAPI의 책임 경계

FastAPI 책임:

- 얼굴 매칭 모델 실행
- 신분증 OCR
- 신분증 얼굴 추출 및 라이브 얼굴 비교
- 품질 검사와 score 산출
- 기준 이미지가 없을 때 no-reference 경로로 `matched` 계산

Spring 책임:

- 관리자 또는 미션 단말 권한 검증
- 미션/세션/환자 정합성 검증
- 기준 이미지 조회
- FastAPI 호출
- OCR 이름/주소/생년월일 재검증
- OCR 이름, 생년월일 6자리, 주소 중 하나 이상 일치 여부 판정
- FastAPI 응답 요약 로그 기록
- verified 상태 캐시
- 환자 토큰 발급

## 5.3 FE 캡처 입력 정책

현재 차량 FE 입력 정책:

- 얼굴 촬영:
  - 전체 비디오 프레임을 원본 방향으로 업로드
  - 좌우 반전은 미리보기 CSS에만 적용
- 신분증 촬영:
  - 전체 프레임 업로드 금지
  - 화면 가이드 박스 기준 영역만 crop 후 업로드
  - `object-cover`로 인한 잘림을 보정한 실제 source 좌표 사용
  - 신분증 이미지는 PNG 업로드

현재 crop 기준:

- `x=0.25`
- `y=0.30`
- `width=0.50`
- `height=0.40`

구현 위치:

- FE: `src/FE/src/pages/Robot/AuthStep.jsx`

## 6. 활력징후 단계 정리

활력징후 단계는 UX에 포함한다. 다만 기존 MVP 문서와 정합성을 맞추기 위해 범위를 제한한다.

MVP 기준:

- 차량 태블릿은 본인확인 성공 후 `VITALS_PENDING` 화면으로 이동
- 체온/혈압/산소포화도/심전도는 더미 표시 가능
- 측정값 저장은 필수 범위가 아님
- 의사 화면 반영도 1차 범위에서는 선택 구현 가능

즉, 이번 수정안의 필수 범위는 다음까지다.

1. 본인확인 성공
2. 활력징후 단계 진입
3. 세션 준비 후 진료실 입장

활력징후 데이터 영속 저장은 별도 확장 범위로 분리한다.

## 7. 동의 단계 처리 원칙

기존 문서 일부에는 `본인확인 -> 동의 -> 활력징후 -> 진료` 흐름이 있다.

이번 수정안에서는:

- `동의`를 본인확인 선행 조건에서 제외
- `동의`는 P1 또는 별도 확장 문서에서 관리
- 현재 플로우의 blocker로 취급하지 않음

이유:

- 사용자 요청 플로우에는 동의 단계가 포함되지 않았다.
- 현 시점에서 본인확인과 진료실 입장 연결을 먼저 안정화하는 것이 우선이다.

## 8. 저장 및 보안 정책 수정안

### 8.1 주민등록번호

- GPU 응답에 원문 `rrn`이 있더라도 Spring 로그, DB, API 응답에는 저장하지 않는다.
- 외부 응답은 `rrnMasked`만 허용한다.

### 8.2 촬영 원본

기본 정책:

- 얼굴 이미지, 신분증 이미지는 요청 처리 후 즉시 폐기

예외 정책:

- 실패 케이스 또는 운영자 수동 보존 요청 시에만 `verification-probe/` 저장
- 저장 시 UUID 파일명 사용
- TTL 7일 후 정리

즉, 기존 `verification-probe 7일 보관` 규칙은 "기본 저장"이 아니라 "예외 저장 시 7일 TTL"로 수정한다.

## 9. 문서별 반영 포인트

### `docs/wiki/API_Specification.md`

- `10.2`를 본인확인 전용 API로 분리
- 환자 토큰 발급 API를 다음 번호로 분리
- 환자 토큰 발급 요청 body에서 `faceImage`, `idCardImage` 제거
- identity-check 성공 후 활력징후 단계로 이동하는 응답 예시 추가

### `src/FE/api.md`

- 위 API 변경을 FE 기준으로 동일 반영
- 차량 FE 상태 흐름 추가

### `docs/wiki/Architecture.md`

- 시퀀스 다이어그램을 `진료 시작 -> identity-check -> vitals -> patient token -> join room`으로 수정
- `verification-probe` 저장 규칙을 예외 저장으로 수정
- FE 직접 GPU 호출 금지 원칙 유지

### `docs/wiki/MVP_Requirements_v2.md`

- Step 6~8을 본 수정안 기준으로 재정렬
- 활력징후는 UX 포함, 저장 도메인은 선택 범위라고 명시
- 차량 단말은 세션 생성이 아니라 본인확인과 참가 준비 주체라고 재강조

### `docs/wiki/Projectinfo.md`

- 본인확인/활력징후 파이프라인을 새 순서로 정리
- 동의 단계는 후속 확장 범위라고 명시

### `docs/wiki/Detailed.md`

- `PIN/QR/신분증` 대체 서술을 실제 MVP 기준인 얼굴 + 신분증 + 기준 이미지 비교로 정리
- 실패 시 운영자 개입 경로를 구체화
- 기준 이미지 없는 경우 `live face + id card face + OCR` 경로도 함께 명시

### `docs/wiki/Infrastructure_Setup.md`

- GPU 서버 reverse proxy 경로에 `/idv/...` 헬스체크와 verify 경로 추가 명시
- FastAPI IDV 프로세스 운영 포트와 health check 예시 추가

### `src/AI/docs/api-v1.md`

- Internal REST API에 `/idv/api/v1/health`, `/idv/api/v1/verify` 추가
- multipart request/response 예시 추가

### `src/AI/docs/architecture.md`

- AI Server 책임에 IDV API 추가
- STT 외에 IDV orchestration 경로 추가

## 10. 구현 순서

1. 문서 초안 확정
2. Spring 신규 `mission identity-check` API 추가
3. FastAPI `/idv/api/v1/verify` 추가
4. 차량 FE를 2-step 캡처 흐름으로 교체
5. 신분증 crop 및 PNG 업로드 반영
6. 환자 토큰 발급 API를 verified 상태 기반으로 단순화
7. 활력징후 화면 연결
8. 운영 검증 및 장애 케이스 테스트

## 11. 검증 시나리오

- 정상 환자 얼굴 + 정상 신분증
- 타인 얼굴 + 정상 신분증
- 환자 얼굴 + 타인 신분증
- 얼굴 품질 저하 또는 흔들림
- 신분증 미검출
- OCR 이름 불일치
- OCR 주소 불일치
- 주민등록번호 기반 생년월일 불일치
- FastAPI timeout
- verified 완료 후 의사 세션 미생성
- verified TTL 만료 후 환자 토큰 발급 요청

## 12. 결정이 필요한 항목

- 활력징후 결과를 MVP에서 저장할지 여부
- 본인확인 실패 시 최대 재촬영 횟수
- 실패 이미지 저장을 기본 미저장으로 둘지, 실패 케이스만 저장할지 여부
- 동의 단계를 이번 범위에서 완전히 제외할지, placeholder 화면만 둘지 여부
