import axios from 'axios';
import useAuthStore from '../store/authStore';

// Create Axios Instance
const apiClient = axios.create({
    baseURL: '/api/v1',
    withCredentials: true, // For receiving and sending HttpOnly cookies (refresh_token)
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request Interceptor: Attach AccessToken
apiClient.interceptors.request.use(
    (config) => {
        const { token } = useAuthStore.getState();
        const hasExplicitAuthorization =
            Boolean(config.headers?.Authorization) || Boolean(config.headers?.authorization);

        if (token && !hasExplicitAuthorization) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response Interceptor: Handle 401 & Token Refresh
apiClient.interceptors.response.use(
    (response) => {
        return response;
    },
    async (error) => {
        const originalRequest = error.config;
        const { token } = useAuthStore.getState();
        const currentUserAuthorization = token ? `Bearer ${token}` : null;
        const requestAuthorization =
            originalRequest?.headers?.Authorization || originalRequest?.headers?.authorization || null;
        const isNonUserAuthorization =
            Boolean(requestAuthorization) && requestAuthorization !== currentUserAuthorization;
        const isTerminalBootstrapRequest = originalRequest?.url?.includes('/terminal/');
        
        // 401 에러이고 재시도 횟수를 초과하지 않은 경우 (Token 만료 의심)
        if (error.response?.status === 401
            && !originalRequest._retry
            && !isNonUserAuthorization
            && !isTerminalBootstrapRequest) {
            originalRequest._retry = true;

            try {
                // 저장소 혹은 /auth/refresh 로 RT 검증 요청 (API 설정에 따라 RT 자동 송신됨)
                const refreshResponse = await axios.post('/api/v1/auth/refresh', {}, {
                    withCredentials: true
                });

                if (refreshResponse.status === 200) {
                    const newAccessToken = refreshResponse.data.accessToken;
                    const newUserConfig = refreshResponse.data.user;

                    // 새로운 토큰 스토어 갱신
                    useAuthStore.getState().setAuth(newAccessToken, newUserConfig);

                    // 실패했던 원본 요청에 새로운 토큰 헤더 적용 후 재시도
                    originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
                    return apiClient(originalRequest);
                }
            } catch (refreshError) {
                // Refresh 실패시 로그아웃 처리
                useAuthStore.getState().logout();
                return Promise.reject(refreshError);
            }
        }
        
        return Promise.reject(error);
    }
);

export default apiClient;
