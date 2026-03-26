from __future__ import annotations

import json
import time
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Optional

from app.config import Settings
from app.logging_utils import get_logger
from app.schemas import (
    ParsedIntent,
    ScenarioSessionInspectResponse,
    ScenarioTurnResult,
    TurnHistoryItem,
)
from app.services.qwen_asr_service import ASRResult, QwenASRService
from app.services.qwen_tts_service import QwenTTSService
from app.state_machine import (
    BranchType,
    DeterministicStateMachine,
    ScenarioState,
    SessionContext,
    TransitionInput,
    initial_prompt_for,
    initial_state_for,
    requires_audio,
)


@dataclass(slots=True)
class TurnRecord:
    turn_index: int
    state_before: ScenarioState
    state_after: ScenarioState
    prompt_text: str | None
    prompt_audio_path: str | None
    transcript: str | None
    parsed_intent: dict | None
    metadata: dict
    audio_input_path: str | None
    created_at: datetime = field(default_factory=lambda: datetime.now(tz=timezone.utc))


@dataclass(slots=True)
class ScenarioSessionRuntime:
    context: SessionContext
    transcript_history: list[TurnRecord] = field(default_factory=list)
    started_perf: float = field(default_factory=time.perf_counter)


class ScenarioService:
    def __init__(
        self,
        settings: Settings,
        state_machine: DeterministicStateMachine,
        tts_service: QwenTTSService,
        asr_service: QwenASRService,
    ) -> None:
        self.settings = settings
        self.state_machine = state_machine
        self.tts_service = tts_service
        self.asr_service = asr_service
        self.logger = get_logger(self.__class__.__name__)
        self._sessions: dict[str, ScenarioSessionRuntime] = {}
        self._lock = Lock()

    def start_session(self, branch: BranchType, patient_name: Optional[str]) -> ScenarioTurnResult:
        session_id = uuid.uuid4().hex
        initial_state = initial_state_for(branch)
        initial_prompt = initial_prompt_for(branch, patient_name)
        now = datetime.now(tz=timezone.utc)
        context = SessionContext(
            session_id=session_id,
            branch=branch,
            patient_name=patient_name,
            current_state=initial_state,
            created_at=now,
            updated_at=now,
        )
        runtime = ScenarioSessionRuntime(context=context)

        prompt_audio_path, prompt_audio_url, tts_metrics = self._maybe_generate_tts(
            session_id=session_id,
            prompt_text=initial_prompt,
            suffix="start",
            enabled=self.settings.auto_tts_on_start,
        )

        metadata = {
            "event": "session_started",
            "branch": branch.value,
            "tts_inference_ms": tts_metrics.get("tts_inference_ms"),
            "tts_error": tts_metrics.get("tts_error"),
        }
        self._append_turn(
            runtime=runtime,
            state_before=initial_state,
            state_after=initial_state,
            prompt_text=initial_prompt,
            prompt_audio_path=prompt_audio_path,
            transcript=None,
            parsed_intent=None,
            metadata=metadata,
            audio_input_path=None,
        )

        with self._lock:
            self._sessions[session_id] = runtime
        self._persist_transcript_history(runtime)
        self.logger.info(
            "Session started",
            extra={"event": "session_started", "session_id": session_id, "state": initial_state.value},
        )
        return ScenarioTurnResult(
            session_id=session_id,
            current_state=initial_state,
            next_state=initial_state,
            prompt_text=initial_prompt,
            prompt_audio_path=prompt_audio_path,
            prompt_audio_url=prompt_audio_url,
            transcript=None,
            parsed_intent=None,
            metadata=metadata,
            created_at=context.created_at,
            updated_at=context.updated_at,
        )

    def advance_session(
        self,
        session_id: str,
        audio_bytes: bytes | None = None,
        audio_filename: str | None = None,
    ) -> ScenarioTurnResult:
        runtime = self._get_session(session_id)
        context = runtime.context
        state_before = context.current_state
        prompt_audio_path: str | None = None
        prompt_audio_url: str | None = None
        transcript: str | None = None
        parsed_intent: ParsedIntent | None = None
        metadata: dict = {}
        audio_input_path: str | None = None

        if state_before == ScenarioState.END:
            metadata = {"event": "already_completed"}
            return ScenarioTurnResult(
                session_id=session_id,
                current_state=ScenarioState.END,
                next_state=ScenarioState.END,
                prompt_text=None,
                prompt_audio_path=None,
                prompt_audio_url=None,
                transcript=None,
                parsed_intent=None,
                metadata=metadata,
                created_at=context.created_at,
                updated_at=context.updated_at,
            )

        transition_input: TransitionInput | None = None
        asr_result: ASRResult | None = None

        if requires_audio(state_before):
            if not audio_bytes:
                prompt_text = self._missing_audio_prompt(state_before)
                prompt_audio_path, prompt_audio_url, tts_meta = self._maybe_generate_tts(
                    session_id=session_id,
                    prompt_text=prompt_text,
                    suffix=f"missing_audio_{len(runtime.transcript_history)}",
                    enabled=self.settings.auto_tts_on_next and prompt_text is not None,
                )
                metadata = {
                    "event": "missing_audio",
                    "state": state_before.value,
                    "tts_inference_ms": tts_meta.get("tts_inference_ms"),
                }
                self._append_turn(
                    runtime=runtime,
                    state_before=state_before,
                    state_after=state_before,
                    prompt_text=prompt_text,
                    prompt_audio_path=prompt_audio_path,
                    transcript=None,
                    parsed_intent=None,
                    metadata=metadata,
                    audio_input_path=None,
                )
                self._persist_transcript_history(runtime)
                return ScenarioTurnResult(
                    session_id=session_id,
                    current_state=state_before,
                    next_state=state_before,
                    prompt_text=prompt_text,
                    prompt_audio_path=prompt_audio_path,
                    prompt_audio_url=prompt_audio_url,
                    transcript=None,
                    parsed_intent=None,
                    metadata=metadata,
                    created_at=context.created_at,
                    updated_at=context.updated_at,
                )

            recording_path = self._save_recording(session_id, audio_bytes, audio_filename)
            audio_input_path = str(recording_path)
            asr_result = self.asr_service.transcribe_file(recording_path)
            transcript = asr_result.transcript
            yes_no_intent, _ = self.asr_service.classify_yes_no(transcript)
            symptom = self.asr_service.extract_symptom(transcript)
            parsed_intent = ParsedIntent(
                yes_no=yes_no_intent,
                symptom_label=symptom["label"],
                symptom_keyword=symptom["matched_keyword"],
            )
            transition_input = TransitionInput(
                transcript=transcript,
                low_confidence=self.asr_service.is_low_confidence(asr_result),
                yes_no_intent=yes_no_intent,
                symptom_label=symptom["label"],
                symptom_keyword=symptom["matched_keyword"],
                confidence=asr_result.confidence,
            )

        transition = self.state_machine.advance(context, transition_input)
        state_after = transition.next_state
        context.current_state = state_after
        context.updated_at = datetime.now(tz=timezone.utc)

        prompt_text = transition.prompt_text
        tts_meta: dict = {}
        if prompt_text:
            prompt_audio_path, prompt_audio_url, tts_meta = self._maybe_generate_tts(
                session_id=session_id,
                prompt_text=prompt_text,
                suffix=f"turn_{len(runtime.transcript_history)}",
                enabled=self.settings.auto_tts_on_next,
            )

        metadata = {
            **transition.metadata,
            "tts_inference_ms": tts_meta.get("tts_inference_ms"),
            "tts_error": tts_meta.get("tts_error"),
            "total_turns": context.total_turns,
            "name_retry_count": context.name_retry_count,
            "yes_no_retry_count": context.yes_no_retry_count,
            "total_session_duration_sec": self._session_duration_sec(runtime),
            "scenario_completed": state_after == ScenarioState.END,
        }
        if asr_result:
            metadata.update(
                {
                    "stt_inference_ms": asr_result.inference_ms,
                    "stt_duration_sec": asr_result.duration_sec,
                    "stt_confidence": asr_result.confidence,
                    "stt_language": asr_result.language,
                }
            )

        self._append_turn(
            runtime=runtime,
            state_before=state_before,
            state_after=state_after,
            prompt_text=prompt_text,
            prompt_audio_path=prompt_audio_path,
            transcript=transcript,
            parsed_intent=parsed_intent.model_dump() if parsed_intent else None,
            metadata=metadata,
            audio_input_path=audio_input_path,
        )
        self._persist_transcript_history(runtime)

        self.logger.info(
            "State transition",
            extra={
                "event": "state_transition",
                "session_id": session_id,
                "state": state_before.value,
                "next_state": state_after.value,
                "transcript": transcript or "",
            },
        )

        return ScenarioTurnResult(
            session_id=session_id,
            current_state=state_before,
            next_state=state_after,
            prompt_text=prompt_text,
            prompt_audio_path=prompt_audio_path,
            prompt_audio_url=prompt_audio_url,
            transcript=transcript,
            parsed_intent=parsed_intent,
            metadata=metadata,
            created_at=context.created_at,
            updated_at=context.updated_at,
        )

    def get_session(self, session_id: str) -> ScenarioSessionInspectResponse:
        runtime = self._get_session(session_id)
        context = runtime.context
        history = [
            TurnHistoryItem(
                turn_index=item.turn_index,
                state_before=item.state_before,
                state_after=item.state_after,
                prompt_text=item.prompt_text,
                prompt_audio_path=item.prompt_audio_path,
                transcript=item.transcript,
                parsed_intent=ParsedIntent(**item.parsed_intent) if item.parsed_intent else None,
                metadata=item.metadata,
                audio_input_path=item.audio_input_path,
                created_at=item.created_at,
            )
            for item in runtime.transcript_history
        ]
        return ScenarioSessionInspectResponse(
            session_id=context.session_id,
            branch=context.branch,
            patient_name=context.patient_name,
            current_state=context.current_state,
            confirmed_name=context.confirmed_name,
            symptom_label=context.symptom_label,
            symptom_keyword=context.symptom_keyword,
            assigned_department=context.assigned_department,
            yes_no=context.yes_no,
            name_retry_count=context.name_retry_count,
            yes_no_retry_count=context.yes_no_retry_count,
            total_turns=context.total_turns,
            total_session_duration_sec=self._session_duration_sec(runtime),
            transcript_history=history,
            created_at=context.created_at,
            updated_at=context.updated_at,
        )

    def _append_turn(
        self,
        runtime: ScenarioSessionRuntime,
        state_before: ScenarioState,
        state_after: ScenarioState,
        prompt_text: str | None,
        prompt_audio_path: str | None,
        transcript: str | None,
        parsed_intent: dict | None,
        metadata: dict,
        audio_input_path: str | None,
    ) -> None:
        turn = TurnRecord(
            turn_index=len(runtime.transcript_history),
            state_before=state_before,
            state_after=state_after,
            prompt_text=prompt_text,
            prompt_audio_path=prompt_audio_path,
            transcript=transcript,
            parsed_intent=parsed_intent,
            metadata=metadata,
            audio_input_path=audio_input_path,
        )
        runtime.transcript_history.append(turn)

    def _persist_transcript_history(self, runtime: ScenarioSessionRuntime) -> None:
        payload = {
            "session_id": runtime.context.session_id,
            "branch": runtime.context.branch.value,
            "created_at": runtime.context.created_at.isoformat(),
            "updated_at": runtime.context.updated_at.isoformat(),
            "total_turns": runtime.context.total_turns,
            "history": [
                {
                    **asdict(item),
                    "state_before": item.state_before.value,
                    "state_after": item.state_after.value,
                    "created_at": item.created_at.isoformat(),
                }
                for item in runtime.transcript_history
            ],
        }
        out_path = self.settings.transcripts_dir / f"{runtime.context.session_id}.json"
        out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _save_recording(self, session_id: str, audio_bytes: bytes, audio_filename: str | None) -> Path:
        suffix = Path(audio_filename or "input.wav").suffix or ".wav"
        index = int(time.time() * 1000)
        target = self.settings.recordings_dir / f"{session_id}_{index}{suffix}"
        target.write_bytes(audio_bytes)
        return target

    def _maybe_generate_tts(
        self,
        session_id: str,
        prompt_text: str | None,
        suffix: str,
        enabled: bool,
    ) -> tuple[str | None, str | None, dict]:
        if not enabled or not prompt_text:
            return None, None, {}
        target = self.settings.tts_output_dir / f"{session_id}_{suffix}.wav"
        try:
            tts_result = self.tts_service.synthesize(prompt_text, str(target))
        except Exception as exc:
            self.logger.warning(
                "TTS generation skipped",
                extra={"event": "tts_skipped", "session_id": session_id},
            )
            return None, None, {"tts_error": str(exc)}
        output_path = str(target)
        output_url = self._to_output_url(target)
        return output_path, output_url, {"tts_inference_ms": tts_result.inference_ms}

    def _missing_audio_prompt(self, state: ScenarioState) -> str:
        if state in {ScenarioState.UNREGISTERED_ASK_NAME, ScenarioState.UNREGISTERED_RETRY_NAME}:
            return "음성이 감지되지 않았습니다. 성함을 다시 말씀해 주세요."
        if state == ScenarioState.UNREGISTERED_CONFIRM_NAME:
            return "음성이 감지되지 않았습니다. 예 또는 아니오로 말씀해 주세요."
        return "음성이 감지되지 않았습니다. 불편한 부위를 말씀해 주세요."

    def _session_duration_sec(self, runtime: ScenarioSessionRuntime) -> float:
        return round(time.perf_counter() - runtime.started_perf, 3)

    def _to_output_url(self, path: Path) -> str:
        rel = path.resolve().relative_to(self.settings.output_dir_path.resolve())
        return "/outputs/" + rel.as_posix()

    def _get_session(self, session_id: str) -> ScenarioSessionRuntime:
        with self._lock:
            runtime = self._sessions.get(session_id)
        if runtime is None:
            raise KeyError(f"Session not found: {session_id}")
        return runtime
