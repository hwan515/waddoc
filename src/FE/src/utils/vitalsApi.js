import apiClient from './api';

const ROBOT_MISSION_ID_KEY = 'current_mission_id';
const ROBOT_MISSION_TERMINAL_TOKEN_KEY = 'robot_mission_terminal_token';

const randomBetween = (min, max) => Math.random() * (max - min) + min;

const randomIntBetween = (min, max) => Math.round(randomBetween(min, max));

const roundToOneDecimal = (value) => Math.round(value * 10) / 10;

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

export const extractVitalsApiErrorMessage = (error, fallbackMessage) => {
    const detail = error?.response?.data?.detail;
    const message = error?.response?.data?.message;
    return detail || message || fallbackMessage;
};

export const getRobotMissionContext = () => {
    const missionId = localStorage.getItem(ROBOT_MISSION_ID_KEY);
    const terminalToken = localStorage.getItem(ROBOT_MISSION_TERMINAL_TOKEN_KEY);

    if (!missionId) {
        throw new Error('선택된 미션이 없습니다. 진료 시작 화면으로 돌아가 다시 진행해주세요.');
    }

    if (!terminalToken) {
        throw new Error('차량 단말 인증 정보가 없습니다. 진료 시작 화면으로 돌아가 다시 진행해주세요.');
    }

    return { missionId, terminalToken };
};

export const upsertMissionVitals = async (payload) => {
    const { missionId, terminalToken } = getRobotMissionContext();

    const response = await apiClient.put(`/missions/${missionId}/vitals`, payload, {
        headers: {
            Authorization: `Bearer ${terminalToken}`,
        },
    });

    return response.data;
};

export const createTemperatureMeasurement = () => ({
    temperature: roundToOneDecimal(randomBetween(36.3, 37.2)),
});

export const createBloodMeasurement = () => {
    const heartRate = randomIntBetween(64, 86);
    const bloodPressureSys = randomIntBetween(114, 132);
    const bloodPressureDia = clamp(
        randomIntBetween(72, 86),
        60,
        bloodPressureSys - 28
    );

    return {
        bloodPressureSys,
        bloodPressureDia,
        heartRate,
    };
};

export const createSpO2Measurement = () => ({
    spO2: randomIntBetween(96, 99),
});

export const createEcgWaveform = (durationSeconds = 8, samplingHz = 25) => {
    const totalSamples = durationSeconds * samplingHz;

    return Array.from({ length: totalSamples }, (_, index) => {
        const phase = index % samplingHz;
        let value = Math.sin(index / 9) * 0.05;

        if (phase === 3) value += 0.12;
        if (phase === 4) value -= 0.16;
        if (phase === 5) value += 1.05;
        if (phase === 6) value -= 0.24;
        if (phase === 11) value += 0.28;

        value += randomBetween(-0.015, 0.015);
        return Number(value.toFixed(3));
    });
};

export const createEcgMeasurement = () => {
    const ecgSamplingHz = 25;
    const ecgDurationSeconds = 8;

    return {
        ecgWaveform: createEcgWaveform(ecgDurationSeconds, ecgSamplingHz),
        ecgSamplingHz,
        ecgDurationSeconds,
    };
};
