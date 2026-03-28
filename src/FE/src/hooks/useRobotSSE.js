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

const toFiniteNumber = (value) => {
    const parsed = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : null;
};

const firstDefined = (...values) => values.find((value) => value !== undefined && value !== null);

const firstString = (...values) => {
    for (const value of values) {
        if (typeof value === 'string' && value.trim()) {
            return value;
        }
    }
    return null;
};

const firstNumber = (...values) => {
    for (const value of values) {
        const parsed = toFiniteNumber(value);
        if (parsed !== null) {
            return parsed;
        }
    }
    return null;
};

const firstBoolean = (...values) => values.find((value) => typeof value === 'boolean') ?? null;

const normalizePose = (candidate) => {
    if (!candidate || typeof candidate !== 'object') {
        return null;
    }

    const x = toFiniteNumber(candidate.x);
    const z = toFiniteNumber(candidate.z);
    if (x === null || z === null) {
        return null;
    }

    return {
        x,
        z,
        yaw: toFiniteNumber(candidate.yaw)
    };
};

const extractPose = (...candidates) => {
    for (const candidate of candidates) {
        const pose = normalizePose(candidate);
        if (pose) {
            return pose;
        }
    }
    return null;
};

const normalizeLocation = (candidate) => {
    if (!candidate || typeof candidate !== 'object') {
        return null;
    }

    const lat = firstNumber(candidate.lat, candidate.latitude);
    const lng = firstNumber(candidate.lng, candidate.longitude);
    if (lat === null || lng === null) {
        return null;
    }

    return { lat, lng };
};

const extractLocation = (...candidates) => {
    for (const candidate of candidates) {
        const location = normalizeLocation(candidate);
        if (location) {
            return location;
        }
    }
    return null;
};

const sanitizePointArray = (points, withId = false) => {
    if (!Array.isArray(points)) {
        return [];
    }

    return points
        .map((point) => {
            if (!point || typeof point !== 'object') {
                return null;
            }

            const x = toFiniteNumber(point.x);
            const z = toFiniteNumber(point.z);
            if (x === null || z === null) {
                return null;
            }

            return withId
                ? { id: typeof point.id === 'string' ? point.id : null, x, z }
                : { x, z };
        })
        .filter(Boolean);
};

const normalizeBounds = (bounds) => {
    if (!bounds || typeof bounds !== 'object') {
        return null;
    }

    const nextBounds = {
        minX: firstNumber(bounds.minX, bounds.min_x),
        maxX: firstNumber(bounds.maxX, bounds.max_x),
        minZ: firstNumber(bounds.minZ, bounds.min_z),
        maxZ: firstNumber(bounds.maxZ, bounds.max_z),
        width: firstNumber(bounds.width),
        height: firstNumber(bounds.height),
    };

    return Object.values(nextBounds).some((value) => value !== null)
        ? nextBounds
        : null;
};

const parseTimestamp = (value) => {
    if (typeof value !== 'string' || !value.trim()) {
        return null;
    }

    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? new Date(parsed).toISOString() : null;
};

const latestTimestamp = (...values) => values
    .map(parseTimestamp)
    .filter(Boolean)
    .sort()
    .at(-1) ?? null;

const buildFallbackSnapshot = ({ minimapData, odomData, stateData, statusData }) => {
    if (!minimapData && !odomData && !stateData && !statusData) {
        return null;
    }

    return {
        telemetry: {
            vehicleId: firstString(
                odomData?.vehicleId,
                minimapData?.vehicleId,
                stateData?.vehicleId,
                statusData?.vehicleId
            ),
            missionId: firstString(
                odomData?.missionId,
                minimapData?.missionId,
                stateData?.missionId
            ),
            online: firstBoolean(statusData?.online),
            state: firstString(
                stateData?.state,
                odomData?.state,
                odomData?.vehicleState,
                odomData?.missionState,
                odomData?.status,
                minimapData?.state,
                minimapData?.vehicleState,
                minimapData?.missionState,
                minimapData?.status
            ),
            batterySoc: firstNumber(
                minimapData?.batterySoc,
                minimapData?.battery_soc,
                odomData?.batterySoc,
                odomData?.battery_soc
            ),
            speedMs: firstNumber(
                odomData?.speedMs,
                odomData?.speed_ms,
                odomData?.speed,
                minimapData?.speedMs,
                minimapData?.speed_ms,
                minimapData?.speed
            ),
            speedKmh: firstNumber(
                odomData?.speedKmh,
                odomData?.speed_kmh,
                minimapData?.speedKmh,
                minimapData?.speed_kmh
            ),
            pose: extractPose(
                odomData?.minimap_pose,
                odomData?.minimapPose,
                odomData?.vehiclePose,
                odomData?.current_pose,
                odomData?.currentPose,
                minimapData?.current_pose,
                minimapData?.currentPose,
                minimapData?.vehiclePose,
                minimapData?.minimap_pose,
                minimapData?.minimapPose
            ),
            location: extractLocation(
                odomData?.currentLocation,
                odomData?.vehicleLocation,
                odomData?.location,
                odomData,
                {
                    latitude: odomData?.latitude,
                    longitude: odomData?.longitude,
                },
                minimapData?.currentLocation,
                minimapData?.vehicleLocation,
                minimapData?.location,
                minimapData,
                {
                    latitude: minimapData?.latitude,
                    longitude: minimapData?.longitude,
                }
            ),
            updatedAt: latestTimestamp(
                odomData?.updatedAt,
                odomData?.updated_at,
                minimapData?.updatedAt,
                minimapData?.updated_at,
                minimapData?.generated_at,
                stateData?.updatedAt,
                stateData?.updated_at,
                statusData?.updatedAt,
                statusData?.updated_at
            )
        },
        navigation: {
            goalWaypointId: firstString(
                minimapData?.goalWaypointId,
                minimapData?.goal_waypoint_id
            ),
            targetWaypointValue: firstNumber(
                minimapData?.targetWaypointValue,
                minimapData?.target_waypoint_value
            ),
            pathWaypoints: sanitizePointArray(
                firstDefined(minimapData?.pathWaypoints, minimapData?.path_waypoints),
                true
            ),
            fullPathWaypoints: sanitizePointArray(
                firstDefined(minimapData?.fullPathWaypoints, minimapData?.full_path_waypoints),
                true
            ),
            trajectory: sanitizePointArray(minimapData?.trajectory),
            fullTrajectory: sanitizePointArray(firstDefined(minimapData?.fullTrajectory, minimapData?.full_trajectory)),
            bounds: normalizeBounds(minimapData?.bounds),
            cleared: firstBoolean(minimapData?.cleared),
            clearReason: firstString(
                minimapData?.clearReason,
                minimapData?.clear_reason
            )
        }
    };
};

export const useRobotSSE = () => {
    const token = useAuthStore((state) => state.token);
    const [isConnected, setIsConnected] = useState(false);
    const [snapshotData, setSnapshotData] = useState(null);
    const [minimapData, setMinimapData] = useState(null);
    const [odomData, setOdomData] = useState(null);
    const [stateData, setStateData] = useState(null);
    const [statusData, setStatusData] = useState(null);
    const controllerRef = useRef(null);
    const rawDataRef = useRef({
        minimapData: null,
        odomData: null,
        stateData: null,
        statusData: null,
    });

    const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

    useEffect(() => {
        if (!token) return;

        controllerRef.current = new AbortController();

        const connect = async () => {
            const syncFallbackSnapshot = () => {
                setSnapshotData(buildFallbackSnapshot(rawDataRef.current));
            };

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
                            const payload = safeParseJson(event.data);
                            if (payload) {
                                const parsedSnapshot = safeParseJson(payload.snapshot);
                                if (parsedSnapshot) {
                                    setSnapshotData(parsedSnapshot);
                                }

                                const parsedMinimap = safeParseJson(payload.minimap);
                                const parsedOdom = safeParseJson(payload.odom);
                                const parsedState = safeParseJson(payload.state);
                                const parsedStatus = safeParseJson(payload.status);

                                if (parsedMinimap) {
                                    rawDataRef.current.minimapData = parsedMinimap;
                                    setMinimapData(parsedMinimap);
                                }
                                if (parsedOdom) {
                                    rawDataRef.current.odomData = parsedOdom;
                                    setOdomData(parsedOdom);
                                }
                                if (parsedState) {
                                    rawDataRef.current.stateData = parsedState;
                                    setStateData(parsedState);
                                }
                                if (parsedStatus) {
                                    rawDataRef.current.statusData = parsedStatus;
                                    setStatusData(parsedStatus);
                                }

                                if (!parsedSnapshot) {
                                    syncFallbackSnapshot();
                                }
                            }
                        } else if (event.event === 'snapshot') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) setSnapshotData(parsed);
                        } else if (event.event === 'minimap') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) {
                                rawDataRef.current.minimapData = parsed;
                                setMinimapData(parsed);
                                syncFallbackSnapshot();
                            }
                        } else if (event.event === 'odom') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) {
                                rawDataRef.current.odomData = parsed;
                                setOdomData(parsed);
                                syncFallbackSnapshot();
                            }
                        } else if (event.event === 'state') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) {
                                rawDataRef.current.stateData = parsed;
                                setStateData(parsed);
                                syncFallbackSnapshot();
                            }
                        } else if (event.event === 'status') {
                            const parsed = safeParseJson(event.data);
                            if (parsed) {
                                rawDataRef.current.statusData = parsed;
                                setStatusData(parsed);
                                syncFallbackSnapshot();
                            }
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

    return { isConnected, snapshotData, minimapData, odomData, stateData, statusData };
};
