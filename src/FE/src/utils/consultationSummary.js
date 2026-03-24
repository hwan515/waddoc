export const hasSummaryNote = (summaryNote) => Boolean(summaryNote?.trim());

export const createConsultationSummaryPayload = ({
    summaryNote,
    prescriptionCodes = [],
    needsFollowUp = false,
}) => ({
    summaryNote: summaryNote.trim(),
    isPrescriptionIssued: prescriptionCodes.length > 0,
    prescriptionNote: JSON.stringify(prescriptionCodes),
    needsFollowUp,
});

export const extractConsultationSummaryErrorMessage = (error, fallbackMessage) => {
    const apiMessage =
        error?.response?.data?.message
        || error?.response?.data?.errorMessage
        || error?.response?.data?.error;

    return apiMessage || fallbackMessage;
};
