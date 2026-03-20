# Reservation Voice Triage

음성 기반 진료과 추천과 예약 진입 흐름을 위한 3개 서비스 프로젝트입니다.

- `frontend`: React + Vite 채팅 UI, 실시간 STT 표시, 브라우저 TTS
- `main-server`: Spring Boot 공개 API, 세션/메시지 저장, 예약 진입점
- `ai-server`: FastAPI 실시간 STT WebSocket, Upstage 추천 API 어댑터

핵심 규칙:

- 실시간 오디오는 프론트에서 AI 서버로 바로 전송합니다.
- `partial transcript`는 화면에만 표시하고 저장하지 않습니다.
- `final transcript`만 Spring으로 보내 저장하고 Upstage 분석에 사용합니다.
- 모든 API는 `/api/v1`로 버전 관리합니다.

문서:

- [API 명세](docs/api-v1.md)
- [아키텍처](docs/architecture.md)
- [로컬 실행](docs/local-dev.md)

## 빠른 시작

1. 루트에 `.env`를 만들고 [`.env.example`](.env.example)을 참고해 `UPSTAGE_API_KEY`를 설정합니다.
2. Bash에서 `docker compose up --build`를 실행합니다.
3. 브라우저에서 `http://localhost:5173`에 접속합니다.

중지:

- `docker compose down --remove-orphans`
