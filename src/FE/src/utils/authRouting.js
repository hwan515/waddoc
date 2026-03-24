export const ROLE_HOME_PATHS = Object.freeze({
    GUARDIAN: '/patient/portal',
    ADMIN: '/operator/control',
    DOCTOR: '/emr/dashboard',
});

export const ROLE_DISPLAY_NAMES = Object.freeze({
    GUARDIAN: '보호자 포털',
    ADMIN: '관리자/관제',
    DOCTOR: '의사 EMR',
});

export const getHomePathForRole = (role) => ROLE_HOME_PATHS[role] ?? null;

export const getRoleDisplayName = (role) => ROLE_DISPLAY_NAMES[role] ?? '알 수 없는 권한';
