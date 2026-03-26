const readRuntimeConfig = () => {
    if (typeof window === 'undefined') {
        return {};
    }

    return window.__APP_CONFIG__ || {};
};

const trimTrailingSlash = (value) => String(value || '').replace(/\/$/, '');
<<<<<<< HEAD
=======
const truthyValues = new Set(['1', 'true', 'yes', 'on']);
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a

const normalizePath = (path) => {
    const stringPath = String(path || '');
    return stringPath.startsWith('/') ? stringPath : `/${stringPath}`;
};

const uniqueNonEmpty = (values) => [...new Set(values.filter(Boolean))];

const shouldUseRelativeRobotApiPath = () => true;

const getSameHostRobotApiBaseUrl = () => {
    return '';
};

const getConfiguredRobotApiBaseUrl = () => {
    const runtimeConfig = readRuntimeConfig();

    return trimTrailingSlash(
        runtimeConfig.VITE_ROBOT_API_BASE_URL
        || import.meta.env.VITE_ROBOT_API_BASE_URL
        || ''
    );
};

export const getRobotTerminalConfig = () => {
    const runtimeConfig = readRuntimeConfig();

    return {
        terminalId: runtimeConfig.VITE_ROBOT_TERMINAL_ID || import.meta.env.VITE_ROBOT_TERMINAL_ID || '',
        terminalKey: runtimeConfig.VITE_ROBOT_TERMINAL_KEY || import.meta.env.VITE_ROBOT_TERMINAL_KEY || '',
    };
};

export const getRobotApiUrlCandidates = (path) => {
    const normalizedPath = normalizePath(path);
    const configuredBaseUrl = getConfiguredRobotApiBaseUrl();
    const sameHostBaseUrl = trimTrailingSlash(getSameHostRobotApiBaseUrl());

    return uniqueNonEmpty([
        configuredBaseUrl ? `${configuredBaseUrl}${normalizedPath}` : '',
        shouldUseRelativeRobotApiPath() ? normalizedPath : '',
        sameHostBaseUrl ? `${sameHostBaseUrl}${normalizedPath}` : '',
    ]);
};

export const getRobotMinimapApiUrlCandidates = () => {
    const runtimeConfig = readRuntimeConfig();
    const configuredMinimapUrl = runtimeConfig.VITE_MINIMAP_API_URL || import.meta.env.VITE_MINIMAP_API_URL || '';

    return uniqueNonEmpty([
        configuredMinimapUrl,
        ...getRobotApiUrlCandidates('/api/minimap'),
    ]);
};

<<<<<<< HEAD
=======
export const isMonitoringTabEnabled = () => {
    const runtimeConfig = readRuntimeConfig();
    const configuredValue = runtimeConfig.VITE_ENABLE_MONITORING_TAB ?? import.meta.env.VITE_ENABLE_MONITORING_TAB ?? 'false';

    return truthyValues.has(String(configuredValue).trim().toLowerCase());
};

>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
export const getRobotCommandUrlCandidates = (path) => getRobotApiUrlCandidates(path);
