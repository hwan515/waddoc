import { useEffect, useRef, useState } from 'react';
import { LogOut, Activity, Map as MapIcon, LayoutDashboard, Users, UserCheck } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import MapMonitoring from '../../components/operator/MapMonitoring';
import DashboardView from '../../components/operator/DashboardView';
import PatientManagement from '../../components/operator/PatientManagement';
import GuardianApprovals from '../../components/operator/GuardianApprovals';
import { getRobotMinimapApiUrlCandidates } from '../../utils/runtimeConfig';
const MINIMAP_POLL_INTERVAL_MS = 100;
const ACTIVE_OPERATOR_VEHICLE_ID = 'veh_GIMCHEON_01';

const MONITOR_STATE_LABELS = {
    DISPATCHED: '출발',
    START: '출발',
    DEPARTURE: '출발',
    '출발': '출발',
    EN_ROUTE: '주행 중',
    DRIVING: '주행 중',
    MOVING: '주행 중',
    RETURNING: '주행 중',
    '운행 중': '주행 중',
    '주행 중': '주행 중',
    ARRIVED: '도착',
    COMPLETED: '대기',
    '도착': '도착',
    VERIFYING: '진료중',
    CONSULTING: '진료중',
    CONSULTATION: '진료중',
    '진료 중': '진료중',
    '진료중': '진료중',
    INCIDENT: '긴급 정지',
    ESTOP: '긴급 정지',
    E_STOP: '긴급 정지',
    EMERGENCY_STOP: '긴급 정지',
    '긴급정지': '긴급 정지',
    '긴급 정지': '긴급 정지',
    '장애': '긴급 정지',
    WAITING: '대기',
    STANDBY: '대기',
    IDLE: '대기',
    '대기 중': '대기',
    '대기': '대기'
};

const isFiniteNumber = (value) => typeof value === 'number' && Number.isFinite(value);

const isValidPose = (pose) => (
    pose
    && isFiniteNumber(pose.x)
    && isFiniteNumber(pose.z)
);

const sanitizePathPoints = (points) => {
    if (!Array.isArray(points)) return [];

    return points.filter((point) => (
        point
        && isFiniteNumber(point.x)
        && isFiniteNumber(point.z)
    ));
};

const toFiniteNumber = (value) => {
    const parsed = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : null;
};

const normalizeMonitorState = (value) => {
    if (typeof value !== 'string') return null;

    const trimmed = value.trim();
    if (!trimmed) return null;

    return MONITOR_STATE_LABELS[trimmed.toUpperCase()]
        || MONITOR_STATE_LABELS[trimmed]
        || trimmed;
};

const phaseToMonitorState = (phase) => normalizeMonitorState(phase) || '대기';

const MOVING_MONITOR_STATES = new Set(['출발', '주행 중']);

const convertMetersPerSecondToKilometersPerHour = (value) => value * 3.6;

const normalizeVehicleSpeed = (value, state, unit = 'm/s') => {
    const parsed = toFiniteNumber(value);

    if (!MOVING_MONITOR_STATES.has(state)) return 0;
    if (parsed === null || parsed <= 0) return 0;

    if (unit === 'km/h') return parsed;
    return convertMetersPerSecondToKilometersPerHour(parsed);
};

const createVehicleLocation = (latitudeValue, longitudeValue) => {
    const lat = toFiniteNumber(latitudeValue);
    const lng = toFiniteNumber(longitudeValue);

    if (lat === null || lng === null) {
        return null;
    }

    return { lat, lng };
};

const extractVehicleLocation = (...candidates) => {
    for (const candidate of candidates) {
        if (!candidate || typeof candidate !== 'object') {
            continue;
        }

        const nextLocation = createVehicleLocation(
            candidate.latitude ?? candidate.lat,
            candidate.longitude ?? candidate.lng
        );

        if (nextLocation) {
            return nextLocation;
        }
    }

    return null;
};

const getMissionRecencyValue = (mission) => {
    const timestamp = mission?.updatedAt || mission?.dispatchedAt || mission?.createdAt;
    const parsed = timestamp ? Date.parse(timestamp) : Number.NaN;

    return Number.isFinite(parsed) ? parsed : 0;
};

const getMissionStatusLabel = (phase) => {
    switch (phase) {
        case 'CREATED':
            return '시연 대기';
        case 'DISPATCHED':
        case 'EN_ROUTE':
            return '출동 중';
        case 'ARRIVED':
            return '도착 완료';
        case 'VERIFYING':
        case 'CONSULTING':
            return '진료 중';
        case 'RETURNING':
        case 'COMPLETED':
            return '종료/복귀';
        case 'INCIDENT':
        case 'FAILED':
            return '장애 발생';
        default:
            return '대기 중';
    }
};

const getMissionPhaseLabel = (phase) => {
    switch (phase) {
        case 'CREATED':
            return '미션 생성';
        case 'DISPATCHED':
            return '출동 지시';
        case 'EN_ROUTE':
            return '이동 중';
        case 'ARRIVED':
            return '현장 도착';
        case 'VERIFYING':
            return '본인 확인';
        case 'CONSULTING':
            return '진료 중';
        case 'RETURNING':
            return '복귀 중';
        case 'COMPLETED':
            return '처리 완료';
        case 'INCIDENT':
            return '장애 발생';
        case 'FAILED':
            return '실패';
        default:
            return '대기';
    }
};

const getDemoActionAvailability = (phase) => ({
    canDispatch: phase === 'CREATED',
    canArrive: ['DISPATCHED', 'EN_ROUTE'].includes(phase),
    canComplete: ['ARRIVED', 'VERIFYING', 'CONSULTING'].includes(phase),
});

const getErrorMessage = (error, fallbackMessage) => (
    error?.response?.data?.message
    || error?.message
    || fallbackMessage
);

const loadDashboardSnapshot = async ({
    setVehicles,
    setSelectedVehicleId,
    setMissionsList,
    setStatistics,
    setCalendarEvents
}) => {
    try {
        const today = new Date().toISOString().split('T')[0];

        const fetchSafe = (req) => req.catch(err => {
            console.error('API Error:', err);
            return { data: {} };
        });

        const [missionsRes, bookingsRes] = await Promise.all([
            fetchSafe(apiClient.get('/missions')),
            fetchSafe(apiClient.get('/admin/bookings', { params: { size: 100 } }))
        ]);

        const rawMissions = missionsRes.data.missions || [];
        const missionDetailResponses = await Promise.all(
            rawMissions.map((mission) => fetchSafe(apiClient.get(`/missions/${mission.missionId}`)))
        );
        const missionDetailsById = new Map(
            rawMissions.map((mission, index) => [mission.missionId, missionDetailResponses[index]?.data || {}])
        );
        const latestMissionByVehicleId = rawMissions.reduce((accumulator, mission) => {
            const currentMission = accumulator.get(mission.vehicleId);

            if (!currentMission || getMissionRecencyValue(mission) >= getMissionRecencyValue(currentMission)) {
                accumulator.set(mission.vehicleId, mission);
            }

            return accumulator;
        }, new Map());

        const mappedVehicles = Array.from(latestMissionByVehicleId.values())
            .map((mission) => {
                const statusLabel = phaseToMonitorState(mission.phase);
                const missionSpeed = normalizeVehicleSpeed(mission.speed, statusLabel, 'km/h');
                const missionDetail = missionDetailsById.get(mission.missionId);
                const location = extractVehicleLocation(
                    mission.currentLocation,
                    missionDetail?.currentLocation,
                    mission.location,
                    mission
                );
                const isPrimaryServiceVehicle = mission.vehicleId === ACTIVE_OPERATOR_VEHICLE_ID;

                return {
                    id: mission.vehicleId,
                    status: isPrimaryServiceVehicle ? statusLabel : '추후 서비스 예정',
                    location: isPrimaryServiceVehicle ? location : null,
                    battery: isPrimaryServiceVehicle ? 85 : null,
                    speed: isPrimaryServiceVehicle ? missionSpeed : 0,
                    lastUpdated: mission.updatedAt || new Date().toISOString(),
                    mission,
                    isPrimaryServiceVehicle,
                    isFutureService: !isPrimaryServiceVehicle
                };
            })
            .sort((a, b) => {
                if (a.isPrimaryServiceVehicle !== b.isPrimaryServiceVehicle) {
                    return a.isPrimaryServiceVehicle ? -1 : 1;
                }

                return a.id.localeCompare(b.id);
            });

        setVehicles(mappedVehicles);
        if (mappedVehicles.length > 0) {
            const primaryVehicle = mappedVehicles.find((vehicle) => vehicle.isPrimaryServiceVehicle);
            setSelectedVehicleId(primaryVehicle?.id ?? mappedVehicles[0].id);
        }

        const todayMissions = rawMissions.filter((mission) => {
            const dateStr = mission.dispatchedAt || mission.createdAt || mission.updatedAt;
            if (!dateStr) return false;
            return new Date(dateStr).toISOString().split('T')[0] === today;
        });

        const mappedMissionsList = todayMissions
            .map((mission) => {
                const isPrimaryServiceVehicle = mission.vehicleId === ACTIVE_OPERATOR_VEHICLE_ID;
                const demoActionAvailability = getDemoActionAvailability(mission.phase);

                return {
                    id: mission.missionId,
                    patientName: isPrimaryServiceVehicle ? (mission.patientName || '환자명 미상') : '추후 서비스 예정',
                    destination: isPrimaryServiceVehicle ? (mission.destination || '목적지 미상') : '서비스 준비 중',
                    vehicleId: mission.vehicleId,
                    status: isPrimaryServiceVehicle ? getMissionStatusLabel(mission.phase) : '추후 서비스 예정',
                    phase: mission.phase,
                    phaseLabel: isPrimaryServiceVehicle ? getMissionPhaseLabel(mission.phase) : '서비스 준비 중',
                    time: isPrimaryServiceVehicle && mission.dispatchedAt
                        ? new Date(mission.dispatchedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                        : '-',
                    isPrimaryServiceVehicle,
                    ...demoActionAvailability
                };
            })
            .sort((a, b) => {
                if (a.isPrimaryServiceVehicle !== b.isPrimaryServiceVehicle) {
                    return a.isPrimaryServiceVehicle ? -1 : 1;
                }

                return a.vehicleId.localeCompare(b.vehicleId);
            });
        setMissionsList(mappedMissionsList);

        setStatistics({
            totalMissions: rawMissions.length,
            activeMissions: rawMissions.filter((mission) => ['DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'VERIFYING', 'CONSULTING'].includes(mission.phase)).length,
            dispatchingMissions: rawMissions.filter((mission) => ['DISPATCHED', 'EN_ROUTE', 'ARRIVED'].includes(mission.phase)).length,
            consultingMissions: rawMissions.filter((mission) => ['VERIFYING', 'CONSULTING'].includes(mission.phase)).length,
            completedMissions: rawMissions.filter((mission) => ['COMPLETED', 'RETURNING'].includes(mission.phase)).length,
            incidentCount: rawMissions.filter((mission) => mission.phase === 'INCIDENT').length
        });

        const mappedEvents = (bookingsRes.data.bookings || []).map((booking) => {
            const dateObj = new Date(booking.appointmentDate);
            const dayIdx = dateObj.getDay();

            return {
                id: booking.bookingId,
                name: booking.patientName,
                type: booking.departmentName || '진료',
                timeStr: booking.startTime,
                dayIdx,
                fullDate: booking.appointmentDate,
                doctor: booking.doctorName || '담당의',
                status: booking.status || 'CONFIRMED'
            };
        });
        setCalendarEvents(mappedEvents);
    } catch (error) {
        console.error('Dashboard data fetch error:', error);
    }
};

const ControlCenter = () => {
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);
    const minimapRequestInFlightRef = useRef(false);
    const minimapErrorLoggedAtRef = useRef(0);

    // '지도' | '대시보드'
    const [activeTab, setActiveTab] = useState('map');

    // 캘린더 모드
    const [calendarMode, setCalendarMode] = useState('weekly');
    const [vehicles, setVehicles] = useState([]);
    const [missionsList, setMissionsList] = useState([]);
    const [calendarEvents, setCalendarEvents] = useState([]);
    const [statistics, setStatistics] = useState({
        totalMissions: 0,
        activeMissions: 0,
        dispatchingMissions: 0,
        consultingMissions: 0,
        completedMissions: 0,
        incidentCount: 0
    });
    const [selectedVehicleId, setSelectedVehicleId] = useState(null);
    const [minimapVehiclePose, setMinimapVehiclePose] = useState(null);
    const [minimapPathPoints, setMinimapPathPoints] = useState([]);
    const [minimapMonitorState, setMinimapMonitorState] = useState(null);
    const [minimapVehicleSpeed, setMinimapVehicleSpeed] = useState(null);
    const [pendingDemoAction, setPendingDemoAction] = useState(null);

    useEffect(() => {
        loadDashboardSnapshot({
            setVehicles,
            setSelectedVehicleId,
            setMissionsList,
            setStatistics,
            setCalendarEvents
        });
    }, []);

    const handleDemoMissionAction = async (missionId, action) => {
        const actionPathByType = {
            dispatch: 'dispatch',
            arrive: 'arrive',
            complete: 'complete'
        };
        const fallbackMessageByType = {
            dispatch: '시연 출동 처리에 실패했습니다.',
            arrive: '도착 처리에 실패했습니다.',
            complete: '진료 종료 처리에 실패했습니다.'
        };
        const nextActionPath = actionPathByType[action];

        if (!nextActionPath || pendingDemoAction) {
            return;
        }

        setPendingDemoAction({ missionId, action });

        try {
            const response = await apiClient.post(`/admin/demo/missions/${missionId}/${nextActionPath}`);
            const nextPhase = response?.data?.phase;

            if (response?.data?.vehicleId === ACTIVE_OPERATOR_VEHICLE_ID && nextPhase) {
                setMinimapMonitorState(phaseToMonitorState(nextPhase));

                if (['ARRIVED', 'VERIFYING', 'CONSULTING', 'RETURNING', 'COMPLETED'].includes(nextPhase)) {
                    setMinimapVehicleSpeed(0);
                }
            }

            await loadDashboardSnapshot({
                setVehicles,
                setSelectedVehicleId,
                setMissionsList,
                setStatistics,
                setCalendarEvents
            });
        } catch (error) {
            console.error(`Demo mission ${action} error:`, error);
            window.alert(getErrorMessage(error, fallbackMessageByType[action]));
        } finally {
            setPendingDemoAction(null);
        }
    };

    useEffect(() => {
        let isMounted = true;

        const fetchMinimapState = async () => {
            if (!isMounted || minimapRequestInFlightRef.current) {
                return;
            }

            minimapRequestInFlightRef.current = true;

            try {
                let data = null;
                let lastError = null;

                for (const apiUrl of getRobotMinimapApiUrlCandidates()) {
                    try {
                        const response = await fetch(apiUrl, {
                            method: 'GET',
                            headers: {
                                Accept: 'application/json'
                            },
                            cache: 'no-store'
                        });

                        if (!response.ok) {
                            throw new Error(`Minimap API error: ${response.status}`);
                        }

                        data = await response.json();
                        break;
                    } catch (error) {
                        lastError = error;
                    }
                }

                if (data === null) {
                    throw lastError || new Error('Minimap API unavailable');
                }

                if (!isMounted) return;

                const posePayload = data?.vehiclePose ?? data?.current_pose ?? data?.currentPose;
                const pathPayload = data?.pathPoints ?? data?.trajectory;
                const nextState = normalizeMonitorState(
                    data?.state
                    ?? data?.vehicleState
                    ?? data?.missionState
                    ?? data?.status
                );
                const nextSpeedKmh = toFiniteNumber(
                    data?.speedKmh
                    ?? data?.vehicleSpeedKmh
                );
                const nextSpeedMs = toFiniteNumber(
                    data?.speedMs
                    ?? data?.vehicleSpeedMs
                    ?? data?.speed
                );
                const nextSpeed = nextSpeedKmh !== null
                    ? normalizeVehicleSpeed(nextSpeedKmh, nextState, 'km/h')
                    : normalizeVehicleSpeed(nextSpeedMs, nextState, 'm/s');
                const nextLocation = extractVehicleLocation(
                    data?.currentLocation,
                    data?.vehicleLocation,
                    data?.location,
                    data,
                    {
                        latitude: data?.latitude,
                        longitude: data?.longitude
                    }
                );

                setMinimapVehiclePose(isValidPose(posePayload) ? posePayload : null);
                setMinimapPathPoints(sanitizePathPoints(pathPayload));
                setMinimapMonitorState(nextState);
                setMinimapVehicleSpeed(nextSpeed);
                if (nextLocation) {
                    setVehicles((currentVehicles) => currentVehicles.map((vehicle) => (
                        vehicle.id === ACTIVE_OPERATOR_VEHICLE_ID
                            ? { ...vehicle, location: nextLocation }
                            : vehicle
                    )));
                }
            } catch (error) {
                if (isMounted) {
                    const now = Date.now();
                    if (now - minimapErrorLoggedAtRef.current >= 2000) {
                        console.error('Minimap polling error:', error);
                        minimapErrorLoggedAtRef.current = now;
                    }
                }
            } finally {
                minimapRequestInFlightRef.current = false;
            }
        };

        fetchMinimapState();
        const intervalId = window.setInterval(fetchMinimapState, MINIMAP_POLL_INTERVAL_MS);

        return () => {
            isMounted = false;
            window.clearInterval(intervalId);
        };
    }, []);

    const selectedVehicle = vehicles.find((vehicle) => vehicle.id === ACTIVE_OPERATOR_VEHICLE_ID)
        || vehicles.find((vehicle) => vehicle.id === selectedVehicleId)
        || vehicles[0]
        || null;
    const vehicleState = minimapMonitorState || selectedVehicle?.status || '대기';
    const vehicleSpeed = minimapVehicleSpeed ?? selectedVehicle?.speed ?? null;
    const vehicleLocation = selectedVehicle?.location || null;

    const handleLogout = () => {
        logout();
        navigate('/operator/login');
    };

    const handleGoHome = () => {
        navigate('/');
    };

    return (
        <div className="h-screen bg-slate-100 flex flex-col font-sans overflow-hidden">
            <header className="h-16 bg-dark text-white flex items-center justify-between px-6 shrink-0 shadow-md z-20">
                <div className="flex items-center gap-8">
                    <button
                        type="button"
                        onClick={handleGoHome}
                        className="flex items-center gap-3 text-left transition-opacity hover:opacity-90"
                    >
                        <div className="bg-white/10 p-2 rounded-lg">
                            <Activity className="w-5 h-5 text-secondary" />
                        </div>
                        <span className="font-bold text-xl tracking-tight">
                            Waddoc<span className="text-secondary"> 왔닥</span>
                            <span className="ml-3 pl-3 border-l border-white/20 text-sm font-medium text-slate-300">통합 관제 센터</span>
                        </span>
                    </button>

                    <div className="flex items-center gap-1 bg-accent-2 p-1 rounded-lg">
                        <button
                            onClick={() => setActiveTab('map')}
                            className={`flex justify-center items-center gap-2 px-4 py-1.5 w-36 rounded-md text-sm font-bold transition-all ${activeTab === 'map'
                                ? 'bg-white text-primary shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <MapIcon className="w-4 h-4" />
                            지도 모니터링
                        </button>
                        <button
                            onClick={() => setActiveTab('dashboard')}
                            className={`flex justify-center items-center gap-2 px-4 py-1.5 w-36 rounded-md text-sm font-bold transition-all ${activeTab === 'dashboard'
                                ? 'bg-white text-primary shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <LayoutDashboard className="w-4 h-4" />
                            운영 대시보드
                        </button>
                        <button
                            onClick={() => setActiveTab('patients')}
                            className={`flex justify-center items-center gap-2 px-4 py-1.5 w-36 rounded-md text-sm font-bold transition-all ${activeTab === 'patients'
                                ? 'bg-white text-primary shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <Users className="w-4 h-4" />
                            환자 관리
                        </button>
                        <button
                            onClick={() => setActiveTab('approvals')}
                            className={`flex justify-center items-center gap-2 px-4 py-1.5 w-36 rounded-md text-sm font-bold transition-all ${activeTab === 'approvals'
                                ? 'bg-white text-primary shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <UserCheck className="w-4 h-4" />
                            가입 승인
                        </button>
                    </div>
                </div>

                <div className="flex items-center gap-5">
                    <div className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                        <span className="text-slate-300 font-medium">시스템 정상</span>
                    </div>
                    <div className="w-px h-5 bg-white/20"></div>
                    <div className="text-sm font-medium flex items-center">
                        <span className="bg-accent-2 px-2.5 py-1 rounded text-xs mr-2 border border-white/10">관리자</span>
                        operator님
                    </div>
                    <button
                        onClick={handleLogout}
                        className="flex items-center gap-2 text-sm text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 px-3 py-1.5 rounded transition-colors"
                    >
                        <LogOut className="w-4 h-4" /> 로그아웃
                    </button>
                </div>
            </header>

            <main className="flex-1 overflow-hidden relative">
                {activeTab === 'map' && (
                    <MapMonitoring
                        vehicles={vehicles}
                        selectedVehicle={selectedVehicle}
                        selectedVehicleId={selectedVehicleId}
                        setSelectedVehicleId={setSelectedVehicleId}
                        minimapVehiclePose={minimapVehiclePose}
                        minimapPathPoints={minimapPathPoints}
                        vehicleState={vehicleState}
                        vehicleSpeed={vehicleSpeed}
                        vehicleLocation={vehicleLocation}
                        updateIntervalMs={MINIMAP_POLL_INTERVAL_MS}
                        useMockMinimapData={false}
                    />
                )}
                {activeTab === 'dashboard' && (
                    <DashboardView
                        calendarMode={calendarMode}
                        setCalendarMode={setCalendarMode}
                        calendarEvents={calendarEvents}
                        missionsList={missionsList}
                        statistics={statistics}
                        pendingDemoAction={pendingDemoAction}
                        onDemoDispatch={(missionId) => handleDemoMissionAction(missionId, 'dispatch')}
                        onDemoArrive={(missionId) => handleDemoMissionAction(missionId, 'arrive')}
                        onDemoComplete={(missionId) => handleDemoMissionAction(missionId, 'complete')}
                    />
                )}
                {activeTab === 'patients' && <PatientManagement />}
                {activeTab === 'approvals' && <GuardianApprovals />}
            </main>
        </div>
    );
};

export default ControlCenter;
