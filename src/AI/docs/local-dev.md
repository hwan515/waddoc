# Local Development

## Prerequisites

- Docker Desktop

## Environment Setup

루트에 `.env` 파일을 만들고 [`.env.example`](../.env.example)을 참고해 값을 채웁니다.

필수 값:

- `UPSTAGE_API_KEY`

권장 기본값:

- `UPSTAGE_MODEL=solar-pro2`
- `WHISPER_MODEL=large-v3`
- `WHISPER_DEVICE=cuda`
- `WHISPER_COMPUTE_TYPE=float16`
- `WHISPER_FINAL_BEAM_SIZE=5`
- `WHISPER_FINAL_BEST_OF=5`

## Start

```bash
docker compose up --build
```

접속 주소:

- frontend: `http://localhost:5173`
- main-server: `http://localhost:8080`
- ai-server: `http://localhost:8000`

## Stop

```bash
docker compose down --remove-orphans
```

## Logs

```bash
docker compose logs -f
```

## Notes

- 첫 실행 시 `faster-whisper` 모델 다운로드 때문에 AI 서버 시작이 느릴 수 있습니다.
- RTX 4050 기준으로는 GPU 경로와 `large-v3/float16` 조합을 기본값으로 둔 상태입니다.
- Docker Desktop에서 GPU 지원이 켜져 있고, 호스트에 NVIDIA 드라이버가 정상 설치되어 있어야 합니다.
- 실시간 STT는 `AudioWorklet -> WebSocket -> faster-whisper` 흐름으로 동작합니다.
- `partial` 전사는 빠른 설정을 쓰고, `final` 전사는 더 높은 beam 설정을 씁니다.
