const readRuntimeConfig = () => {
    if (typeof window === 'undefined') {
        return {};
    }

    return window.__APP_CONFIG__ || {};
};

export const getRobotTerminalConfig = () => {
    const runtimeConfig = readRuntimeConfig();

    return {
        terminalId: runtimeConfig.VITE_ROBOT_TERMINAL_ID || import.meta.env.VITE_ROBOT_TERMINAL_ID || '',
        terminalKey: runtimeConfig.VITE_ROBOT_TERMINAL_KEY || import.meta.env.VITE_ROBOT_TERMINAL_KEY || '',
    };
};
