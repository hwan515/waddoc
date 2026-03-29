import { useEffect, useRef, useState } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import useAuthStore from '../store/authStore';

const getNotificationKey = (notification) => (
    notification?.caseId || notification?.bookingId || notification?.createdAt
);

export const useSSE = () => {
    const token = useAuthStore(state => state.token);
    const [isConnected, setIsConnected] = useState(false);
    const [notifications, setNotifications] = useState([]);
    const controllerRef = useRef(null);

    // API Base URL (환경변수 또는 로컬 프록시)
    const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

    useEffect(() => {
        if (!token) return;

        controllerRef.current = new AbortController();

        const connectSSE = async () => {
            try {
                await fetchEventSource(`${BASE_URL}/doctors/me/notifications/stream`, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Accept': 'text/event-stream',
                    },
                    signal: controllerRef.current.signal,
                    
                    onopen(res) {
                        if (res.ok && res.status === 200) {
                            setIsConnected(true);
                        }
                    },
                    
                    onmessage(event) {
                        if (event.event === 'connected') {
                            setIsConnected(true);
                        } else if (event.event === 'ping') {
                            // ping은 무시
                        } else if (event.event === 'notification') {
                            try {
                                const payload = JSON.parse(event.data);
                                if (payload.type === 'NEW_BOOKING') {
                                    setNotifications(prev => {
                                        const nextKey = getNotificationKey(payload);
                                        if (!nextKey) {
                                            return [payload, ...prev];
                                        }
                                        if (prev.some(notification => getNotificationKey(notification) === nextKey)) {
                                            return prev;
                                        }
                                        return [payload, ...prev];
                                    });
                                }
                            } catch {
                                // Ignore malformed SSE payloads and keep listening for the next event.
                            }
                        }
                    },

                    onclose() {
                        setIsConnected(false);
                    },

                    onerror() {
                        setIsConnected(false);
                        // 에러 시 자동으로 재연결 시도 (기본 동작)
                    }
                });
            } catch {
                // Ignore bootstrap failures and let the next mount attempt reconnect.
            }
        };

        connectSSE();

        return () => {
            if (controllerRef.current) {
                controllerRef.current.abort();
            }
        };
    }, [token, BASE_URL]);

    // 알림 읽음 처리 (UI에서 알림을 닫거나 확인할 때 호출)
    const removeNotification = (notificationKey) => {
        setNotifications(prev => prev.filter(
            notification => getNotificationKey(notification) !== notificationKey
        ));
    };

    return {
        isConnected,
        notifications,
        removeNotification,
        getNotificationKey,
    };
};
