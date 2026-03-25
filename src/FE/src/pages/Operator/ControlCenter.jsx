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
    COMPLETED: '도착',
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
        completedMissions: 0,
        incidentCount: 0
    });
    const [selectedVehicleId, setSelectedVehicleId] = useState(null);
    const [minimapVehiclePose, setMinimapVehiclePose] = useState(null);
    const [minimapPathPoints, setMinimapPathPoints] = useState([]);
    const [minimapMonitorState, setMinimapMonitorState] = useState(null);
    const [minimapVehicleSpeed, setMinimapVehicleSpeed] = useState(null);

    useEffect(() => {
        const fetchDashboardData = async () => {
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
                const mappedVehicles = rawMissions.map((mission) => {
                    const statusLabel = phaseToMonitorState(mission.phase);
                    const missionSpeed = normalizeVehicleSpeed(mission.speed, statusLabel, 'km/h');

                    return {
                        id: mission.vehicleId,
                        status: statusLabel,
                        location: { lat: 37.4845, lng: 130.9057 },
                        battery: 85,
                        speed: missionSpeed,
                        lastUpdated: mission.updatedAt || new Date().toISOString(),
                        mission
                    };
                });

                setVehicles(mappedVehicles);
                if (mappedVehicles.length > 0) {
                    setSelectedVehicleId((currentVehicleId) => {
                        if (currentVehicleId && mappedVehicles.some((vehicle) => vehicle.id === currentVehicleId)) {
                            return currentVehicleId;
                        }
                        return mappedVehicles[0].id;
                    });
                }

                const todayMissions = rawMissions.filter((mission) => {
                    const dateStr = mission.dispatchedAt || mission.createdAt || mission.updatedAt;
                    if (!dateStr) return false;
                    return new Date(dateStr).toISOString().split('T')[0] === today;
                });

                const mappedMissionsList = todayMissions.map((mission) => {
                    let statusStr = '대기 중';
                    if (['DISPATCHED', 'EN_ROUTE', 'ARRIVED'].includes(mission.phase)) statusStr = '출동 중';
                    if (['VERIFYING', 'CONSULTING'].includes(mission.phase)) statusStr = '진료 중';
                    if (mission.phase === 'INCIDENT') statusStr = '장애 발생';
                    if (['COMPLETED', 'RETURNING'].includes(mission.phase)) statusStr = '종료/복귀';

                    return {
                        id: mission.missionId,
                        patientName: mission.patientName || '환자명 미상',
                        destination: mission.destination || '목적지 미상',
                        vehicleId: mission.vehicleId,
                        status: statusStr,
                        time: mission.dispatchedAt
                            ? new Date(mission.dispatchedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                            : '-'
                    };
                });
                setMissionsList(mappedMissionsList);

                setStatistics({
                    totalMissions: rawMissions.length,
                    activeMissions: rawMissions.filter((mission) => ['DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'VERIFYING', 'CONSULTING'].includes(mission.phase)).length,
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

        fetchDashboardData();
    }, []);

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

                setMinimapVehiclePose(isValidPose(posePayload) ? posePayload : null);
                setMinimapPathPoints(sanitizePathPoints(pathPayload));
                setMinimapMonitorState(nextState);
                setMinimapVehicleSpeed(nextSpeed);
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

    const selectedVehicle = vehicles.find((vehicle) => vehicle.id === selectedVehicleId) || vehicles[0] || null;
    const vehicleState = minimapMonitorState || selectedVehicle?.status || '대기';
    const vehicleSpeed = minimapVehicleSpeed ?? selectedVehicle?.speed ?? null;

    const handleLogout = () => {
        logout();
        navigate('/operator/login');
    };

    return (
        <div className="h-screen bg-[#F5F6F8] flex flex-col font-sans overflow-hidden">
            <header className="h-16 bg-dark text-white flex items-center justify-between px-6 shrink-0 shadow-md z-20">
                <div className="flex items-center gap-8">
                    <div className="flex items-center gap-3">
                        <div className="bg-white/10 p-2 rounded-lg">
                            <Activity className="w-5 h-5 text-secondary" />
                        </div>
                        <span className="font-bold text-xl tracking-tight">
                            Waddoc<span className="text-secondary"> 왔닥</span>
                            <span className="ml-3 pl-3 border-l border-white/20 text-sm font-medium text-slate-300">통합 관제 센터</span>
                        </span>
                    </div>

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
                    />
                )}
                {activeTab === 'patients' && <PatientManagement />}
                {activeTab === 'approvals' && <GuardianApprovals />}
            </main>
        </div>
    );
};

export default ControlCenter;
