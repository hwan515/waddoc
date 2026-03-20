from __future__ import annotations

from typing import Optional


REGISTERED_GREETING_TEMPLATE = "{name} 어르신 안녕하세요. 어디가 불편하신가요?"
ASK_NAME_PROMPT = "안녕하세요. 왓닥입니다. 삐 소리 후 성함을 말씀해 주세요."
RETRY_NAME_PROMPT = "죄송합니다. 다시 한번 성함을 말씀해 주세요."
CONFIRM_NAME_TEMPLATE = "{name} 어르신 맞으신가요? 예 또는 아니오로 말씀해 주세요."
RETRY_YES_NO_PROMPT = "잘 듣지 못했습니다. 예 또는 아니오로 말씀해 주세요."
ASK_SYMPTOM_PROMPT = "어디가 불편하신가요?"
ASK_SYMPTOM_DETAIL_PROMPT = "어디가 아프신가요? 불편한 부위를 말씀해 주세요."
RETRY_SYMPTOM_PROMPT = "죄송합니다. 불편한 부위를 다시 말씀해 주세요."
BOOKING_ASSIST_PROMPT = "최대한 빠르게 예약을 도와드리겠습니다."
SMS_NOTICE_PROMPT = "예약 확인 문자를 보내드리겠습니다."
CLOSING_PROMPT = "따뜻한 왓닥이었습니다. 감사합니다."
HANDOFF_PROMPT = "상담원 연결이 필요합니다. 잠시 후 다시 시도해 주세요."
DEPARTMENT_ASSIGNED_TEMPLATE = "{department}로 배정되셨습니다."


def registered_greeting(patient_name: Optional[str]) -> str:
    name = patient_name.strip() if patient_name and patient_name.strip() else "고객"
    return REGISTERED_GREETING_TEMPLATE.format(name=name)


def confirm_name_prompt(recognized_name: str) -> str:
    cleaned = recognized_name.strip() if recognized_name.strip() else "고객"
    return CONFIRM_NAME_TEMPLATE.format(name=cleaned)


def department_assigned_prompt(department: str) -> str:
    return DEPARTMENT_ASSIGNED_TEMPLATE.format(department=department)
