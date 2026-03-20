from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class ParsedOcrResult:
    name: str | None
    rrn: str | None
    rrn_masked: str | None
    address: str | None
    confidence: float


class IdvOcrParser:
    def parse(self, texts: Iterable[str], scores: Iterable[float]) -> ParsedOcrResult:
        merged_text = "\n".join(text.strip() for text in texts if text and text.strip())
        confidence_values = [float(score) for score in scores]
        confidence = sum(confidence_values) / len(confidence_values) if confidence_values else 0.0

        rrn = self._extract_rrn(merged_text)
        lines = [line.strip() for line in merged_text.splitlines() if line.strip()]
        name = self._extract_name(lines)
        address = self._extract_address(lines)

        return ParsedOcrResult(
            name=name,
            rrn=rrn,
            rrn_masked=self.mask_rrn(rrn),
            address=address,
            confidence=confidence,
        )

    def mask_rrn(self, rrn: str | None) -> str | None:
        if rrn is None:
            return None
        digits = re.sub(r"\D", "", rrn)
        if len(digits) < 7:
            return None
        return f"{digits[:6]}-{digits[6]}******"

    def _extract_rrn(self, text: str) -> str | None:
        match = re.search(r"(\d{6})[- ]?(\d{7})", text)
        if match is None:
            return None
        return f"{match.group(1)}-{match.group(2)}"

    def _extract_name(self, lines: list[str]) -> str | None:
        for index, line in enumerate(lines):
            compact = re.sub(r"\s+", "", line)
            if "성명" in compact or "이름" in compact:
                value = compact.replace("성명", "").replace("이름", "").replace(":", "")
                if value:
                    return value
                if index + 1 < len(lines):
                    return lines[index + 1].replace(" ", "")

        for line in lines:
            if re.fullmatch(r"[가-힣]{2,5}", line.replace(" ", "")):
                return line.replace(" ", "")
        return None

    def _extract_address(self, lines: list[str]) -> str | None:
        address_keywords = ("주소", "거주지", "소재지")
        for index, line in enumerate(lines):
            if any(keyword in line for keyword in address_keywords):
                value = re.sub(r"^(주소|거주지|소재지)\s*[:：]?\s*", "", line).strip()
                if value:
                    return value
                if index + 1 < len(lines):
                    return lines[index + 1].strip()

        for line in lines:
            if any(keyword in line for keyword in ("시", "군", "구", "읍", "면", "동", "로", "길")):
                return line.strip()
        return None
