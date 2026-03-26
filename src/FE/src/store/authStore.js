import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useAuthStore = create(
    persist(
        (set) => ({
            user: null, // null means not logged in
            token: null, // Store accessToken

            // 로그인 액션 (실제 API 응답 결과를 받아와서 세팅)
            setAuth: (token, userData) => {
                set({
                    token: token,
                    user: userData
                });
            },

            // 로그아웃 액션
            logout: () => {
                set({ user: null, token: null });
            }
        }),
        {
            name: 'auth-storage', // localStorage key name
        }
    )
);

export default useAuthStore;
