const DIRECT_SELECTION_REASON_PREFIX = /^환자가 .+를 직접 선택했습니다\./;

export const sanitizeSelectionReason = (selectionReason, fallback = '') => {
    const normalizedReason = typeof selectionReason === 'string'
        ? selectionReason.trim()
        : '';

    if (!normalizedReason) {
        return fallback;
    }

    if (DIRECT_SELECTION_REASON_PREFIX.test(normalizedReason)) {
        return fallback;
    }

    return normalizedReason;
};
