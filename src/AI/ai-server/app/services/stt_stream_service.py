import json
from dataclasses import dataclass, field
from functools import lru_cache

from fastapi import WebSocket

from app.core.config import get_settings
from app.schemas.stt import (
    SttErrorEvent,
    SttFinalEvent,
    SttPartialEvent,
    SttReadyEvent,
    SttStartEvent,
    SttStopEvent,
)
from app.services.whisper_service import WhisperService, get_whisper_service


@dataclass
class StreamState:
    language: str = "ko"
    sample_rate: int = 16000
    audio_buffer: bytearray = field(default_factory=bytearray)
    bytes_since_partial: int = 0
    last_partial_text: str = ""


class SttStreamService:
    def __init__(self, whisper_service: WhisperService) -> None:
        self._whisper_service = whisper_service
        self._settings = get_settings()

    async def handle_connection(self, websocket: WebSocket, session_id: str, turn_id: str) -> None:
        state = StreamState()

        while True:
            message = await websocket.receive()

            if message["type"] == "websocket.disconnect":
                break

            if "text" in message and message["text"] is not None:
                payload = json.loads(message["text"])
                event_type = payload.get("type")

                if event_type == "stt.start":
                    start_event = SttStartEvent.model_validate(payload)
                    state.language = start_event.language
                    state.sample_rate = start_event.sampleRate
                    await websocket.send_json(SttReadyEvent().model_dump())
                    continue

                if event_type == "stt.stop":
                    SttStopEvent.model_validate(payload)
                    await self._emit_final_or_error(websocket, state, turn_id)
                    break

            if "bytes" in message and message["bytes"] is not None:
                chunk = message["bytes"]
                state.audio_buffer.extend(chunk)
                state.bytes_since_partial += len(chunk)

                if state.bytes_since_partial >= self._partial_threshold_bytes(state.sample_rate):
                    state.bytes_since_partial = 0
                    await self._emit_partial(websocket, state, turn_id)

    async def close(self, websocket: WebSocket) -> None:
        try:
            await websocket.close()
        except RuntimeError:
            return

    async def _emit_partial(self, websocket: WebSocket, state: StreamState, turn_id: str) -> None:
        if len(state.audio_buffer) < state.sample_rate * 2:
            return

        try:
            result = await self._whisper_service.transcribe_pcm(
                bytes(state.audio_buffer),
                state.language,
                mode="partial",
            )
        except Exception:
            return

        if not result.text or result.text == state.last_partial_text:
            return

        state.last_partial_text = result.text
        await websocket.send_json(
            SttPartialEvent(
                turnId=turn_id,
                text=result.text,
            ).model_dump()
        )

    async def _emit_final_or_error(self, websocket: WebSocket, state: StreamState, turn_id: str) -> None:
        if len(state.audio_buffer) <= 0:
            await websocket.send_json(
                SttErrorEvent(
                    code="NO_SPEECH",
                    message="음성이 감지되지 않았습니다. 다시 말씀해 주세요.",
                ).model_dump()
            )
            return

        try:
            result = await self._whisper_service.transcribe_pcm(
                bytes(state.audio_buffer),
                state.language,
                mode="final",
            )
        except Exception as error:
            await websocket.send_json(
                SttErrorEvent(
                    code="STT_UNAVAILABLE",
                    message=f"STT 엔진을 사용할 수 없습니다: {error}",
                ).model_dump()
            )
            return

        if not result.text:
            await websocket.send_json(
                SttErrorEvent(
                    code="NO_SPEECH",
                    message="음성이 감지되지 않았습니다. 다시 말씀해 주세요.",
                ).model_dump()
            )
            return

        await websocket.send_json(
            SttFinalEvent(
                turnId=turn_id,
                text=result.text,
                confidence=result.confidence,
            ).model_dump()
        )

    def _partial_threshold_bytes(self, sample_rate: int) -> int:
        bytes_per_second = sample_rate * 2
        interval_seconds = self._settings.whisper_partial_interval_ms / 1000
        return max(int(bytes_per_second * interval_seconds), bytes_per_second)


@lru_cache
def get_stt_stream_service() -> SttStreamService:
    return SttStreamService(get_whisper_service())
