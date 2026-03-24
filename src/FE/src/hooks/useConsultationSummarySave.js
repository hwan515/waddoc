import { useState } from 'react';
import apiClient from '../utils/api';
import { extractConsultationSummaryErrorMessage } from '../utils/consultationSummary';

const DEFAULT_SUCCESS_MESSAGE = '진료 요약을 저장했습니다.';
const DEFAULT_ERROR_MESSAGE = '진료 요약 저장에 실패했습니다.';
const DEFAULT_SAVING_MESSAGE = '진료 요약을 저장하는 중입니다.';
const DEFAULT_DEMO_MESSAGE = '데모 모드에서는 요약 저장이 서버에 반영되지 않습니다.';
const DEFAULT_SESSION_ERROR_MESSAGE = '세션 정보가 없어 진료 요약을 저장할 수 없습니다.';

const useConsultationSummarySave = ({ sessionId, isDemoMode }) => {
    const [isSavingSummary, setIsSavingSummary] = useState(false);
    const [summarySaveStatus, setSummarySaveStatus] = useState({
        type: 'idle',
        message: '',
    });

    const saveSummary = async (
        summaryData,
        {
            successMessage = DEFAULT_SUCCESS_MESSAGE,
            savingMessage = DEFAULT_SAVING_MESSAGE,
            errorMessage = DEFAULT_ERROR_MESSAGE,
            demoMessage = DEFAULT_DEMO_MESSAGE,
        } = {}
    ) => {
        if (!summaryData) {
            return { ok: false };
        }

        if (isDemoMode) {
            setSummarySaveStatus({
                type: 'info',
                message: demoMessage,
            });
            return { ok: true, skipped: true };
        }

        if (!sessionId) {
            setSummarySaveStatus({
                type: 'error',
                message: DEFAULT_SESSION_ERROR_MESSAGE,
            });
            return { ok: false };
        }

        setIsSavingSummary(true);
        setSummarySaveStatus({
            type: 'saving',
            message: savingMessage,
        });

        try {
            const response = await apiClient.put(`/sessions/${sessionId}/summary`, summaryData);
            setSummarySaveStatus({
                type: 'success',
                message: successMessage,
            });
            return {
                ok: true,
                data: response.data,
            };
        } catch (error) {
            setSummarySaveStatus({
                type: 'error',
                message: extractConsultationSummaryErrorMessage(error, errorMessage),
            });
            return {
                ok: false,
                error,
            };
        } finally {
            setIsSavingSummary(false);
        }
    };

    return {
        isSavingSummary,
        summarySaveStatus,
        saveSummary,
        setSummarySaveStatus,
    };
};

export default useConsultationSummarySave;
