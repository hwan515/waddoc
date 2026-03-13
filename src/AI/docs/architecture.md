# Architecture

## Overview

서비스는 세 개로 분리합니다.

- `frontend`: 마이크 캡처, 실시간 자막 표시, 채팅 UI, 브라우저 TTS
- `main-server`: 세션 관리, 메시지 저장, 예약 플로우 진입
- `ai-server`: 실시간 STT WebSocket, Upstage 추천 어댑터

## Request Flow

1. 프론트가 `POST /api/v1/chat/sessions`로 세션을 생성합니다.
2. Spring이 `sessionId`와 STT 접속 정보를 반환합니다.
3. 프론트가 `WS /api/v1/stt/streams/{sessionId}/{turnId}`로 PCM 청크를 전송합니다.
4. FastAPI가 `faster-whisper`로 주기적 partial 전사를 수행하고 `stt.partial`을 반환합니다.
5. 사용자가 녹음을 멈추면 FastAPI가 final transcript를 확정해 `stt.final`을 반환합니다.
6. 프론트는 final transcript만 `POST /api/v1/chat/sessions/{sessionId}/messages`로 전송합니다.
7. Spring은 메시지를 저장하고 FastAPI `POST /api/v1/triage/recommendations`를 호출합니다.
8. FastAPI는 Upstage에 증상 텍스트를 보내 추천 진료과를 생성하고 정규화합니다.
9. Spring이 봇 메시지를 저장한 뒤 프론트에 반환합니다.
10. 프론트는 추천 문구를 말풍선과 TTS로 동시에 출력합니다.

## Service Boundaries

### Frontend

- 마이크 권한과 오디오 캡처 라이프사이클 담당
- API 키를 보관하지 않음
- partial transcript를 임시 UI 상태로만 유지

### Main Server

- `/api/v1/chat` 공개 REST API 담당
- 세션 생성과 메시지 저장 담당
- final transcript만 받아 FastAPI에 triage를 요청

### AI Server

- `/api/v1/stt` 실시간 WebSocket 담당
- `/api/v1/triage` 추천 API 담당
- STT 엔진과 Upstage 프롬프트 로직을 캡슐화

## Realtime Design Rules

- `partial transcript`는 저장하지 않습니다.
- `final transcript`만 Spring과 Upstage로 전달합니다.
- 실시간 오디오는 Spring을 거치지 않습니다.
- 추천 결과는 코드와 메시지로 정규화해 반환합니다.

## Environment Variables

### Frontend

- `VITE_API_BASE_URL`: Spring base URL, 기본값 `http://localhost:8080`

### Main Server

- `FRONTEND_BASE_URL`: CORS 허용 프론트 URL
- `AI_SERVER_BASE_URL`: FastAPI base URL, 기본값 `http://localhost:8000`
- `STT_WS_BASE_URL`: 브라우저가 접속할 STT WebSocket base URL

### AI Server

- `UPSTAGE_API_KEY`: Upstage API 키
- `UPSTAGE_BASE_URL`: 기본값 `https://api.upstage.ai/v1`
- `UPSTAGE_MODEL`: 기본값 `solar-pro2`
- `WHISPER_MODEL`: 기본값 `small`
- `WHISPER_DEVICE`: 기본값 `cpu`
- `WHISPER_COMPUTE_TYPE`: 기본값 `int8`
- `WHISPER_PARTIAL_INTERVAL_MS`: partial 재전사 주기
- `WHISPER_VAD_MIN_SILENCE_MS`: VAD silence 기준
