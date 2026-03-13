from app.schemas.triage import TriageRecommendationResponse


class DepartmentMapper:
    _catalog = {
        "ENT": "이비인후과",
        "INTERNAL_MEDICINE": "내과",
        "ORTHOPEDICS": "정형외과",
        "DERMATOLOGY": "피부과",
        "NEUROLOGY": "신경과",
        "GENERAL": "일반내과",
    }

    def recommend_from_keywords(self, transcript: str) -> TriageRecommendationResponse:
        lowered = transcript.lower()

        if any(token in lowered for token in ("목", "기침", "코", "귀", "인후", "콧물", "코막힘", "throat", "cough", "nose", "ear")):
            code = "ENT"
            reason = "인후통, 기침, 코막힘 등 상기도 증상이 있어 이비인후과와 가장 가깝습니다."
        elif any(token in lowered for token in ("피부", "발진", "가려", "두드러기", "rash", "itch", "skin")):
            code = "DERMATOLOGY"
            reason = "피부 발진이나 가려움 관련 표현이 있어 피부과와 가장 가깝습니다."
        elif any(token in lowered for token in ("두통", "어지", "저림", "마비", "headache", "dizzy", "numb")):
            code = "NEUROLOGY"
            reason = "두통, 어지럼, 저림 등 신경학적 증상 표현이 있어 신경과와 가장 가깝습니다."
        elif any(token in lowered for token in ("무릎", "허리", "어깨", "발목", "관절", "근육", "knee", "back", "shoulder", "ankle")):
            code = "ORTHOPEDICS"
            reason = "허리, 무릎, 어깨 등 근골격계 통증 표현이 있어 정형외과와 가장 가깝습니다."
        elif any(token in lowered for token in ("열", "복통", "설사", "소화", "몸살", "fever", "stomach", "diarrhea")):
            code = "INTERNAL_MEDICINE"
            reason = "전신 증상이나 소화기 증상 표현이 있어 내과와 가장 가깝습니다."
        else:
            code = "GENERAL"
            reason = "특정 진료과로 강하게 분류되지 않아 일반내과에서 먼저 확인하는 것이 안전합니다."

        department_name = self._catalog[code]
        message = self._build_message(code, department_name)

        return TriageRecommendationResponse(
            departmentCode=code,
            departmentName=department_name,
            assistantMessage=message,
            ttsText=message,
            confidence=0.55 if code == "GENERAL" else 0.82,
            reason=reason,
        )

    def normalize(self, payload: dict[str, object], transcript: str) -> TriageRecommendationResponse:
        code = str(payload.get("departmentCode", "")).upper()
        if code not in self._catalog:
            return self.recommend_from_keywords(transcript)

        department_name = self._catalog[code]
        assistant_message = str(
            payload.get(
                "assistantMessage",
                self._build_message(code, department_name),
            )
        )
        tts_text = str(payload.get("ttsText", assistant_message))
        confidence = float(payload.get("confidence", 0.75))
        reason = str(payload.get("reason", "AI 응답을 정규화해 추천 결과를 구성했습니다."))

        return TriageRecommendationResponse(
            departmentCode=code,
            departmentName=department_name,
            assistantMessage=assistant_message,
            ttsText=tts_text,
            confidence=confidence,
            reason=reason,
        )

    @staticmethod
    def _build_message(code: str, department_name: str) -> str:
        if code == "GENERAL":
            return "정확한 분류가 어려워 일반내과 진료를 먼저 추천합니다. 예약하시겠습니까?"

        return f"{department_name} 진료를 추천합니다. 예약하시겠습니까?"
