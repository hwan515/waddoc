import { MEDICINE_CATALOG_BY_CODE } from '../constants/medicineCatalog';

const createFallbackItem = (code) => ({
    code,
    name: code,
    category: '카탈로그 미등록',
    dosage: '용법/용량 정보 없음',
    isFallback: true,
});

export const parsePrescriptionNote = (prescriptionNote) => {
    if (typeof prescriptionNote !== 'string') {
        return {
            rawText: '',
            codes: [],
            items: [],
            isStructured: false,
            hasData: false,
        };
    }

    const trimmed = prescriptionNote.trim();
    if (!trimmed) {
        return {
            rawText: '',
            codes: [],
            items: [],
            isStructured: false,
            hasData: false,
        };
    }

    try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) {
            const codes = parsed
                .filter((value) => typeof value === 'string')
                .map((value) => value.trim())
                .filter(Boolean);

            return {
                rawText: trimmed,
                codes,
                items: codes.map((code) => MEDICINE_CATALOG_BY_CODE[code] || createFallbackItem(code)),
                isStructured: true,
                hasData: codes.length > 0,
            };
        }
    } catch {
        // Fallback to legacy free-text prescription content.
    }

    return {
        rawText: trimmed,
        codes: [],
        items: [],
        isStructured: false,
        hasData: true,
    };
};
