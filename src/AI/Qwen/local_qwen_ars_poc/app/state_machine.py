from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional

from app.data import prompt_templates as prompts
from app.data.symptom_dictionary import DEPARTMENT_BY_SYMPTOM


class BranchType(str, Enum):
    REGISTERED = "registered"
    UNREGISTERED = "unregistered"


class ScenarioState(str, Enum):
    REGISTERED_GREETING = "REGISTERED_GREETING"
    UNREGISTERED_ASK_NAME = "UNREGISTERED_ASK_NAME"
    UNREGISTERED_CONFIRM_NAME = "UNREGISTERED_CONFIRM_NAME"
    UNREGISTERED_RETRY_NAME = "UNREGISTERED_RETRY_NAME"
    ASK_SYMPTOM = "ASK_SYMPTOM"
    CONFIRM_BOOKING = "CONFIRM_BOOKING"
    SEND_SMS_NOTICE = "SEND_SMS_NOTICE"
    CLOSING = "CLOSING"
    END = "END"
    ERROR = "ERROR"


@dataclass(slots=True)
class SessionContext:
    session_id: str
    branch: BranchType
    patient_name: str | None
    current_state: ScenarioState
    created_at: datetime = field(default_factory=lambda: datetime.now(tz=timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(tz=timezone.utc))
    candidate_name: str | None = None
    confirmed_name: str | None = None
    symptom_label: str | None = None
    symptom_keyword: str | None = None
    assigned_department: str | None = None
    yes_no: str | None = None
    name_retry_count: int = 0
    yes_no_retry_count: int = 0
    total_turns: int = 0


@dataclass(slots=True)
class TransitionInput:
    transcript: str | None = None
    low_confidence: bool = False
    yes_no_intent: str = "unknown"
    symptom_label: str | None = None
    symptom_keyword: str | None = None
    confidence: float | None = None


@dataclass(slots=True)
class TransitionResult:
    next_state: ScenarioState
    prompt_text: str | None
    parsed_intent: str | None
    metadata: dict[str, Any]


def initial_state_for(branch: BranchType) -> ScenarioState:
    if branch == BranchType.REGISTERED:
        return ScenarioState.REGISTERED_GREETING
    return ScenarioState.UNREGISTERED_ASK_NAME


def initial_prompt_for(branch: BranchType, patient_name: Optional[str]) -> str:
    if branch == BranchType.REGISTERED:
        return prompts.registered_greeting(patient_name)
    return prompts.ASK_NAME_PROMPT


def requires_audio(state: ScenarioState) -> bool:
    return state in {
        ScenarioState.REGISTERED_GREETING,
        ScenarioState.UNREGISTERED_ASK_NAME,
        ScenarioState.UNREGISTERED_CONFIRM_NAME,
        ScenarioState.UNREGISTERED_RETRY_NAME,
        ScenarioState.ASK_SYMPTOM,
    }


class DeterministicStateMachine:
    def __init__(self, max_name_retries: int, max_yes_no_retries: int) -> None:
        self.max_name_retries = max_name_retries
        self.max_yes_no_retries = max_yes_no_retries

    def advance(self, session: SessionContext, user_input: TransitionInput | None) -> TransitionResult:
        session.updated_at = datetime.now(tz=timezone.utc)
        session.total_turns += 1
        user_input = user_input or TransitionInput()

        if session.current_state == ScenarioState.REGISTERED_GREETING:
            return self._handle_symptom_capture(
                session=session,
                user_input=user_input,
                retry_prompt=prompts.RETRY_SYMPTOM_PROMPT,
            )

        if session.current_state == ScenarioState.UNREGISTERED_ASK_NAME:
            return self._handle_name_capture(session=session, user_input=user_input)

        if session.current_state == ScenarioState.UNREGISTERED_RETRY_NAME:
            return self._handle_name_capture(session=session, user_input=user_input)

        if session.current_state == ScenarioState.UNREGISTERED_CONFIRM_NAME:
            return self._handle_name_confirmation(session=session, user_input=user_input)

        if session.current_state == ScenarioState.ASK_SYMPTOM:
            return self._handle_symptom_capture(
                session=session,
                user_input=user_input,
                retry_prompt=prompts.RETRY_SYMPTOM_PROMPT,
            )

        if session.current_state == ScenarioState.CONFIRM_BOOKING:
            session.current_state = ScenarioState.SEND_SMS_NOTICE
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.SMS_NOTICE_PROMPT,
                parsed_intent="auto_progress",
                metadata={"event": "system_notice"},
            )

        if session.current_state == ScenarioState.SEND_SMS_NOTICE:
            session.current_state = ScenarioState.CLOSING
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.CLOSING_PROMPT,
                parsed_intent="auto_progress",
                metadata={"event": "closing"},
            )

        if session.current_state == ScenarioState.CLOSING:
            session.current_state = ScenarioState.END
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=None,
                parsed_intent="completed",
                metadata={"event": "session_completed"},
            )

        if session.current_state == ScenarioState.ERROR:
            session.current_state = ScenarioState.END
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=None,
                parsed_intent="error_end",
                metadata={"event": "session_terminated"},
            )

        return TransitionResult(
            next_state=ScenarioState.ERROR,
            prompt_text=prompts.HANDOFF_PROMPT,
            parsed_intent="invalid_state",
            metadata={"event": "invalid_state"},
        )

    def _handle_name_capture(
        self,
        session: SessionContext,
        user_input: TransitionInput,
    ) -> TransitionResult:
        transcript = (user_input.transcript or "").strip()
        if not transcript or user_input.low_confidence:
            session.name_retry_count += 1
            if session.name_retry_count > self.max_name_retries:
                session.current_state = ScenarioState.ERROR
                return TransitionResult(
                    next_state=session.current_state,
                    prompt_text=prompts.HANDOFF_PROMPT,
                    parsed_intent="name_capture_failed",
                    metadata={"event": "max_name_retries_exceeded"},
                )
            session.current_state = ScenarioState.UNREGISTERED_RETRY_NAME
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.RETRY_NAME_PROMPT,
                parsed_intent="retry_name",
                metadata={"event": "name_retry", "name_retry_count": session.name_retry_count},
            )

        session.candidate_name = transcript
        session.current_state = ScenarioState.UNREGISTERED_CONFIRM_NAME
        session.yes_no_retry_count = 0
        return TransitionResult(
            next_state=session.current_state,
            prompt_text=prompts.confirm_name_prompt(transcript),
            parsed_intent="capture_name",
            metadata={"event": "name_captured", "recognized_name": transcript},
        )

    def _handle_name_confirmation(
        self,
        session: SessionContext,
        user_input: TransitionInput,
    ) -> TransitionResult:
        intent = user_input.yes_no_intent
        if intent == "yes":
            session.confirmed_name = session.candidate_name or session.patient_name
            session.yes_no = "yes"
            session.current_state = ScenarioState.ASK_SYMPTOM
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.ASK_SYMPTOM_PROMPT,
                parsed_intent="confirm_name_yes",
                metadata={"event": "name_confirmed", "confirmed_name": session.confirmed_name},
            )

        if intent == "no":
            session.yes_no = "no"
            session.name_retry_count += 1
            if session.name_retry_count > self.max_name_retries:
                session.current_state = ScenarioState.ERROR
                return TransitionResult(
                    next_state=session.current_state,
                    prompt_text=prompts.HANDOFF_PROMPT,
                    parsed_intent="name_confirmation_failed",
                    metadata={"event": "name_confirmation_failed"},
                )
            session.current_state = ScenarioState.UNREGISTERED_RETRY_NAME
            session.candidate_name = None
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.RETRY_NAME_PROMPT,
                parsed_intent="confirm_name_no",
                metadata={"event": "retry_name_after_no", "name_retry_count": session.name_retry_count},
            )

        session.yes_no_retry_count += 1
        if session.yes_no_retry_count > self.max_yes_no_retries:
            session.current_state = ScenarioState.ERROR
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.HANDOFF_PROMPT,
                parsed_intent="yes_no_ambiguous",
                metadata={"event": "max_yes_no_retries_exceeded"},
            )

        session.current_state = ScenarioState.UNREGISTERED_CONFIRM_NAME
        name = session.candidate_name or "고객"
        return TransitionResult(
            next_state=session.current_state,
            prompt_text=f"{prompts.confirm_name_prompt(name)} {prompts.RETRY_YES_NO_PROMPT}",
            parsed_intent="retry_yes_no",
            metadata={"event": "retry_yes_no", "yes_no_retry_count": session.yes_no_retry_count},
        )

    def _handle_symptom_capture(
        self,
        session: SessionContext,
        user_input: TransitionInput,
        retry_prompt: str,
    ) -> TransitionResult:
        transcript = (user_input.transcript or "").strip()
        if not transcript or user_input.low_confidence:
            session.current_state = ScenarioState.ASK_SYMPTOM
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=retry_prompt,
                parsed_intent="retry_symptom",
                metadata={"event": "symptom_retry"},
            )

        session.symptom_label = user_input.symptom_label or "기타"
        session.symptom_keyword = user_input.symptom_keyword
        if session.symptom_label == "기타":
            session.current_state = ScenarioState.ASK_SYMPTOM
            return TransitionResult(
                next_state=session.current_state,
                prompt_text=prompts.ASK_SYMPTOM_DETAIL_PROMPT,
                parsed_intent="ask_symptom_detail",
                metadata={"event": "symptom_needs_detail"},
            )

        session.assigned_department = DEPARTMENT_BY_SYMPTOM.get(session.symptom_label, "일반외래")
        session.current_state = ScenarioState.CONFIRM_BOOKING
        return TransitionResult(
            next_state=session.current_state,
            prompt_text=prompts.department_assigned_prompt(session.assigned_department),
            parsed_intent="capture_symptom",
            metadata={
                "event": "symptom_captured",
                "symptom_label": session.symptom_label,
                "symptom_keyword": session.symptom_keyword,
                "assigned_department": session.assigned_department,
            },
        )
