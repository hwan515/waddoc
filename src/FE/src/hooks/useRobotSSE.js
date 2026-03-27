import { useEffect, useRef, useState } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import useAuthStore from '../store/authStore';

const safeParseJson = (str) => {
    try {
        return typeof str === 'string' ? JSON.parse(str) : str;
    } catch {
        return null;
    }
};

export const useRobotSSE = () => {
    const token = useAuthStore((state) => state.token);
    const [isConnected, setIsConnected] = useState(false);
    const [minimapData, setMinimapData] = useState(null);
    const [odomData, setOdomData] = useState(null);
    const [stateData, setStateData] = useState(null);
    const [statusData, setStatusData] = useState(null);
    const controllerRef = useRef(null);

    const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

    useEffect(() => {
        if (!token) return;

        controllerRef.current = new AbortController();

        const connect = async () => {
            try {
                await fetchEventSource(`${BASE_URL}/robots/stream`, {
                    method: 'GET',
                    headers: {
                        Authorization: `Bearer ${token}`,
                        Accept: 'text/event-stream',
                    },
                    signal: controllerRef.current.signal,

                    onopen(res) {
                        if (res.ok && res.status === 200) {
                            setIsConnected(true);
                        } else if (res.status >= 400 && res.status < 500 && res.status !== 429) {
                            console.error('Robot SSE auth error:', res.status);
                        }
                    },

                    onmessage(event) {
                        if (event.event === 'connected') {
                            setIsConnected(true);
                            // 서버에서 현재 상태 스냅샷 전송 — 즉시 반영
                            const snapshot = safeParseJson(event.data);
                            if (snapshot) {
                                if (snapshot.minimap) setMinimapData(safeParseJson(snapshot.minimap));
                                if (snapshot.odom)    setOdomData(safeParseJson(snapshot.odom));
                                if (snapshot.state)   setStateData(safeParseJson(snapshot.state));
                            }
                        } else if (event.event === 'minimap') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) setMinimapData(parsed);
                        } else if (event.event === 'odom') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) setOdomData(parsed);
                        } else if (event.event === 'state') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) setStateData(parsed);
                        } else if (event.event === 'status') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) setStatusData(parsed);
                        }
                        // ping은 무시
                    },

                    onclose() {
                        setIsConnected(false);
                    },

                    onerror(err) {
                        setIsConnected(false);
                        console.error('Robot SSE error:', err);
                    },
                });
            } catch (error) {
                console.error('Robot SSE setup error:', error);
            }
        };

        connect();

        return () => {
            controllerRef.current?.abort();
        };
    }, [token, BASE_URL]);

    return { isConnected, minimapData, odomData, stateData, statusData };
};
