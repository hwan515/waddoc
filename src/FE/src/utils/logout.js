import apiClient from './api';
import useAuthStore from '../store/authStore';

export const logoutSession = async () => {
    try {
        await apiClient.post('/auth/logout');
    } catch {
        // Ignore logout request failures and clear the local session regardless.
    } finally {
        useAuthStore.getState().logout();
    }
};
