import json
from typing import Any

import httpx

from app.core.config import Settings


class UpstageClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def recommend(self, transcript: str, history: list[dict[str, str]]) -> dict[str, Any] | None:
        if not self._settings.upstage_api_key:
            return None

        system_prompt = (
            "당신은 의료 진단이 아니라 추천 진료과 안내만 수행하는 분류기입니다. "
            "환자의 증상 전사문을 보고 하나의 진료과만 선택하세요. "
            "반드시 JSON만 반환하고, 키는 departmentCode, confidence, reason, assistantMessage, ttsText만 사용하세요. "
            "departmentCode는 ENT, INTERNAL_MEDICINE, ORTHOPEDICS, DERMATOLOGY, NEUROLOGY, GENERAL 중 하나만 허용합니다. "
            "assistantMessage와 ttsText는 한국어로 작성하고, 반드시 예약 의사를 묻는 문장으로 끝내세요."
        )

        user_payload = {
            "transcript": transcript,
            "history": history,
        }

        request_body = {
            "model": self._settings.upstage_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False)},
            ],
            "temperature": 0.1,
        }

        url = f"{self._settings.upstage_base_url.rstrip('/')}/chat/completions"

        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(
                url,
                headers={
                    "Authorization": f"Bearer {self._settings.upstage_api_key}",
                    "Content-Type": "application/json",
                },
                json=request_body,
            )
            response.raise_for_status()

        data = response.json()
        content = data["choices"][0]["message"]["content"]
        cleaned = content.replace("```json", "").replace("```", "").strip()
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start == -1 or end == -1:
            raise ValueError("Upstage response did not contain JSON.")

        return json.loads(cleaned[start : end + 1])
