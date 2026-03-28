import apiClient from './api';
import useAuthStore from '../store/authStore';

export const logoutSession = async () => {
    try {
        await apiClient.post('/auth/logout');
    } catch (error) {
        console.error('Logout API request failed:', error);
    } finally {
        useAuthStore.getState().logout();
    }
};
