# API v1

모든 공개 API는 `/api/v1`로 버전 관리합니다.

## Public REST API: Spring Boot

### `POST /api/v1/chat/sessions`

새 채팅 세션을 만들고 프론트가 사용할 STT 접속 정보를 반환합니다.

Request:

```json
{
  "locale": "ko-KR"
}
```

Response:

```json
{
  "sessionId": "cs_01",
  "stt": {
    "wsUrl": "ws://localhost:8000/api/v1/stt/streams/cs_01",
    "token": "stt_cs_01",
    "sampleRate": 16000,
    "chunkMs": 200,
    "encoding": "pcm_s16le"
  }
}
```

### `GET /api/v1/chat/sessions/{sessionId}/messages`

세션의 저장된 메시지 목록을 반환합니다.

Response:

```json
{
  "sessionId": "cs_01",
  "messages": [
    {
      "id": "msg_user_01",
      "role": "USER",
      "text": "목이 아프고 기침이 나요."
    },
    {
      "id": "msg_assistant_01",
      "role": "ASSISTANT",
      "text": "이비인후과 진료를 추천합니다. 예약하시겠습니까?"
    }
  ]
}
```

### `POST /api/v1/chat/sessions/{sessionId}/messages`

final transcript 또는 텍스트 입력을 저장하고, 추천 결과까지 한 번에 반환합니다.

Request:

```json
{
  "turnId": "turn_01",
  "inputType": "voice",
  "text": "목이 아프고 기침이 나요.",
  "sttMeta": {
    "engine": "faster-whisper",
    "language": "ko",
    "durationMs": 4200
  }
}
```

Response:

```json
{
  "sessionId": "cs_01",
  "userMessage": {
    "id": "msg_user_01",
    "role": "USER",
    "text": "목이 아프고 기침이 나요."
  },
  "assistantMessage": {
    "id": "msg_assistant_01",
    "role": "ASSISTANT",
    "text": "이비인후과 진료를 추천합니다. 예약하시겠습니까?"
  },
  "recommendation": {
    "departmentCode": "ENT",
    "departmentName": "이비인후과",
    "confidence": 0.88,
    "reason": "인후통과 기침 증상이 상기도 관련 증상과 가장 가깝습니다."
  },
  "ttsText": "이비인후과 진료를 추천합니다. 예약하시겠습니까?"
}
```

### `POST /api/v1/chat/sessions/{sessionId}/reservations/confirm`

추천 후 예약 플로우로 진입하는 엔드포인트입니다.

Request:

```json
{
  "departmentCode": "ENT"
}
```

Response:

```json
{
  "sessionId": "cs_01",
  "status": "PENDING_RESERVATION",
  "departmentCode": "ENT"
}
```

## Internal REST API: FastAPI

### `GET /idv/api/v1/health`

GPU 기반 본인확인(IDV) 프로세스 헬스체크입니다.

Response:

```json
{
  "status": "ok"
}
```

### `POST /idv/api/v1/verify`

Spring이 차량 탑승 환자의 본인확인을 위해 호출하는 내부 API입니다.

Request: `multipart/form-data`

- `verificationId`
- `patientId`
- `verificationMode=FACE_AND_IDCARD`
- `referenceImage`
- `faceImage`
- `idCardImage`

Response:

```json
{
  "verificationId": "vrf_001",
  "status": "SUCCEEDED",
  "matched": true,
  "faceSimilarityScore": 0.94,
  "idCardFaceSimilarityScore": 0.91,
  "reasonCodes": [],
  "ocr": {
    "name": "홍길동",
    "rrnMasked": "580315-1******",
    "address": "경북 울릉군 울릉읍 ..."
  },
  "qualityChecks": {
    "faceDetected": true,
    "singleFace": true,
    "idCardDetected": true,
    "ocrConfidence": 0.97
  },
  "modelVersion": "idv-ocr-face-v1"
}
```

### `GET /api/v1/health`

기본 헬스체크입니다.

Response:

```json
{
  "status": "ok"
}
```

### `POST /api/v1/triage/recommendations`

Spring이 final transcript를 전달하는 내부 API입니다.

Request:

```json
{
  "sessionId": "cs_01",
  "turnId": "turn_01",
  "transcript": "목이 아프고 기침이 나요.",
  "history": []
}
```

Response:

```json
{
  "departmentCode": "ENT",
  "departmentName": "이비인후과",
  "assistantMessage": "이비인후과 진료를 추천합니다. 예약하시겠습니까?",
  "ttsText": "이비인후과 진료를 추천합니다. 예약하시겠습니까?",
  "confidence": 0.88,
  "reason": "인후통과 기침 증상이 상기도 관련 증상과 가장 가깝습니다."
}
```

## Realtime WebSocket API: FastAPI

### `WS /api/v1/stt/streams/{sessionId}/{turnId}?token=...`

프론트는 음성 턴마다 한 번 연결합니다.

Client text frame:

```json
{
  "type": "stt.start",
  "language": "ko",
  "sampleRate": 16000
}
```

Client binary frame:

- raw PCM `s16le` chunks

Client stop frame:

```json
{
  "type": "stt.stop"
}
```

Server events:

```json
{
  "type": "stt.ready"
}
```

```json
{
  "type": "stt.partial",
  "turnId": "turn_01",
  "text": "목이 아프고"
}
```

```json
{
  "type": "stt.final",
  "turnId": "turn_01",
  "text": "목이 아프고 기침이 나요.",
  "confidence": 0.91
}
```

```json
{
  "type": "stt.error",
  "code": "NO_SPEECH",
  "message": "음성이 감지되지 않았습니다. 다시 말씀해 주세요."
}
```

## Error Model

REST 에러 형식:

```json
{
  "timestamp": "2026-03-06T05:00:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "text는 비어 있을 수 없습니다."
}
```

WebSocket 에러 형식:

```json
{
  "type": "stt.error",
  "code": "STT_UNAVAILABLE",
  "message": "STT 엔진을 현재 사용할 수 없습니다."
}
```

## Contract Rules

- Spring만 채팅 세션 상태를 공개적으로 소유합니다.
- FastAPI만 STT와 Upstage 프롬프트 로직을 소유합니다.
- Partial transcript는 저장하거나 분석하지 않습니다.
- Final transcript는 `turnId` 기준으로 멱등하게 처리해야 합니다.
