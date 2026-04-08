const readRuntimeConfig = () => {
    if (typeof window === 'undefined') {
        return {};
    }

    return window.__APP_CONFIG__ || {};
};

const truthyValues = new Set(['1', 'true', 'yes', 'on']);
const isTruthyFlag = (value) => truthyValues.has(String(value).trim().toLowerCase());

export const getRobotTerminalConfig = () => {
    const runtimeConfig = readRuntimeConfig();

    return {
        terminalId: runtimeConfig.VITE_ROBOT_TERMINAL_ID || import.meta.env.VITE_ROBOT_TERMINAL_ID || '',
        terminalKey: runtimeConfig.VITE_ROBOT_TERMINAL_KEY || import.meta.env.VITE_ROBOT_TERMINAL_KEY || '',
    };
};

export const getActiveOperatorVehicleId = () => {
    const runtimeConfig = readRuntimeConfig();
    const configuredVehicleId = runtimeConfig.VITE_ACTIVE_OPERATOR_VEHICLE_ID
        ?? import.meta.env.VITE_ACTIVE_OPERATOR_VEHICLE_ID
        ?? 'veh_GIMCHEON_01';

    return String(configuredVehicleId).trim() || 'veh_GIMCHEON_01';
};

export const isMonitoringTabEnabled = () => {
    const runtimeConfig = readRuntimeConfig();
    const configuredValue = runtimeConfig.VITE_ENABLE_MONITORING_TAB ?? import.meta.env.VITE_ENABLE_MONITORING_TAB ?? 'false';

    return isTruthyFlag(configuredValue);
};

export const isDemoModeEnabled = () => {
    const runtimeConfig = readRuntimeConfig();
    const configuredValue = runtimeConfig.VITE_DEMO_MODE_ENABLED ?? import.meta.env.VITE_DEMO_MODE_ENABLED ?? 'false';

    return isTruthyFlag(configuredValue);
};
