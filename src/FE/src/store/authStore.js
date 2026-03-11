import { create } from 'zustand';

// Mock Auth Store
const useAuthStore = create((set) => ({
    user: null, // null means not logged in

    // 로그인 액션 (임시 Mock)
    login: (userData) => {
        // userData는 { email, password, role } 의 형태를 띰
        // 실제로는 API 호출이 들어갈 자리
        set({
            user: {
                id: Math.random().toString(36).substring(7),
                name: userData.email.split('@')[0] || '테스트',
                email: userData.email,
                role: userData.role || 'patient', // 'patient', 'doctor', 'operator'
            }
        });
    },

    // 로그아웃 액션
    logout: () => {
        set({ user: null });
    }
}));

export default useAuthStore;
