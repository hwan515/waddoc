import { useState, useEffect } from 'react';
import { LogOut, Activity, Map as MapIcon, LayoutDashboard } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import { mockStatistics } from '../../mockdata/operator';
import MapMonitoring from '../../components/operator/MapMonitoring';
import DashboardView from '../../components/operator/DashboardView';

const ControlCenter = () => {
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);

    // '지도' | '대시보드'
    const [activeTab, setActiveTab] = useState('dashboard');

    // 캘린더 모드
    const [calendarMode, setCalendarMode] = useState('weekly');

    // 상태 관리
    const [vehicles, setVehicles] = useState([]);
    const [missionsList, setMissionsList] = useState([]);
    const [calendarEvents, setCalendarEvents] = useState([]);
    const [statistics, setStatistics] = useState({
        totalMissions: 0,
        activeMissions: 0,
        completedMissions: 0,
        incidentCount: 0
    });

    // 현재 선택된 차량 (카메라 뷰 연동)
    const [selectedVehicleId, setSelectedVehicleId] = useState(null);

    // API 호출
    useEffect(() => {
        const fetchDashboardData = async () => {
            try {
                // 오늘 날짜 구하기 (YYYY-MM-DD)
                const today = new Date().toISOString().split('T')[0];

                // 개별 API 실패 시 전체 화면이 멈추는 것을 방지하기 위한 안전 장치
                const fetchSafe = (req) => req.catch(err => {
                    console.error("API Error:", err);
                    return { data: {} };
                });

                const [missionsRes, bookingsRes] = await Promise.all([
                    fetchSafe(apiClient.get('/missions')),
                    fetchSafe(apiClient.get('/admin/bookings', { params: { size: 100 } })) // 전체 달력 일정
                ]);

                console.log("[운영 대시보드] 금일 미션(Missions) API 응답:", missionsRes.data);
                console.log("[운영 대시보드] 예약(Bookings) API 응답:", bookingsRes.data);

                // 1. 차량(Missions) 매핑
                // 상태 변환 (CREATED/DISPATCHED... -> 운행 중 / 대기 중 등)
                const rawMissions = missionsRes.data.missions || [];
                const mappedVehicles = rawMissions.map(m => {
                    let statusLabel = '대기 중';
                    if (['DISPATCHED', 'EN_ROUTE', 'ARRIVED'].includes(m.phase)) statusLabel = '운행 중';
                    if (['VERIFYING', 'CONSULTING'].includes(m.phase)) statusLabel = '진료 중';
                    if (m.phase === 'INCIDENT') statusLabel = '장애';
                    if (m.phase === 'RETURNING') statusLabel = '상황 종료'; // or returning

                    return {
                        id: m.vehicleId,
                        status: statusLabel,
                        location: { lat: 37.4845, lng: 130.9057 }, // 임시 목업
                        battery: 85, // 임시
                        speed: 30, // 임시
                        lastUpdated: m.updatedAt || new Date().toISOString(),
                        mission: m
                    };
                });
                setVehicles(mappedVehicles);
                if (mappedVehicles.length > 0) setSelectedVehicleId(mappedVehicles[0].id);

                // 2. 출동 목록 (금일) 및 통계 (전체) 매핑
                const todayMissions = rawMissions.filter(m => {
                    const dateStr = m.dispatchedAt || m.createdAt || m.updatedAt;
                    if (!dateStr) return false;
                    return new Date(dateStr).toISOString().split('T')[0] === today;
                });

                const mappedMissionsList = todayMissions.map(m => {
                    let statusStr = '대기 중';
                    if (['DISPATCHED', 'EN_ROUTE', 'ARRIVED'].includes(m.phase)) statusStr = '출동 중';
                    if (['VERIFYING', 'CONSULTING'].includes(m.phase)) statusStr = '진료 중';
                    if (m.phase === 'INCIDENT') statusStr = '장애 발생';
                    if (['COMPLETED', 'RETURNING'].includes(m.phase)) statusStr = '종료/복귀';

                    return {
                        id: m.missionId,
                        patientName: m.patientName || '환자명 미상',
                        destination: m.destination || '목적지 미상',
                        vehicleId: m.vehicleId,
                        status: statusStr,
                        time: m.dispatchedAt ? new Date(m.dispatchedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-'
                    };
                });
                setMissionsList(mappedMissionsList);

                // 통계 업데이트 (전체 누적)
                setStatistics({
                    totalMissions: rawMissions.length,
                    activeMissions: rawMissions.filter(m => ['DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'VERIFYING', 'CONSULTING'].includes(m.phase)).length,
                    completedMissions: rawMissions.filter(m => ['COMPLETED', 'RETURNING'].includes(m.phase)).length,
                    incidentCount: rawMissions.filter(m => m.phase === 'INCIDENT').length
                });

                // 3. 캘린더 일정(Bookings) 매핑
                // appointmentDate 기반으로 dayIdx 추출 (임시: 일~토 를 0~6으로 매핑)
                const mappedEvents = (bookingsRes.data.bookings || []).map(b => {
                    const dateObj = new Date(b.appointmentDate);
                    const dayIdx = dateObj.getDay(); // 0: 일, 1: 월 ... 6: 토

                    return {
                        id: b.bookingId,
                        name: b.patientName,
                        type: b.departmentName || '진료', // API에 초진/재진 필드가 없으니 과 이름으로 대체
                        timeStr: b.startTime, // "10:00"
                        dayIdx: dayIdx,
                        fullDate: b.appointmentDate,
                        doctor: b.doctorName || '담당의',
                        status: b.status || 'CONFIRMED'
                    };
                });
                setCalendarEvents(mappedEvents);

            } catch (error) {
                console.error("Dashboard data fetch error:", error);
            }
        };

        fetchDashboardData();
    }, []);

    const handleLogout = () => {
        logout();
        navigate('/operator/login');
    };

    return (
        <div className="h-screen bg-[#F5F6F8] flex flex-col font-sans overflow-hidden">
            {/* 1. 상단 글로벌 네비게이션 바 (Nav Bar) */}
            <header className="h-16 bg-[#061A40] text-white flex items-center justify-between px-6 shrink-0 shadow-md z-20">
                <div className="flex items-center gap-8">
                    {/* Logo Section */}
                    <div className="flex items-center gap-3">
                        <div className="bg-white/10 p-2 rounded-lg">
                            <Activity className="w-5 h-5 text-[#B9D6F2]" />
                        </div>
                        <span className="font-bold text-xl tracking-tight">
                            Waddoc<span className="text-[#B9D6F2]"> 왔닥</span>
                            <span className="ml-3 pl-3 border-l border-white/20 text-sm font-medium text-slate-300">통합 관제 센터</span>
                        </span>
                    </div>

                    {/* Navigation Tabs */}
                    <div className="flex items-center gap-1 bg-[#003559] p-1 rounded-lg">
                        <button
                            onClick={() => setActiveTab('map')}
                            className={`flex items-center gap-2 px-4 py-1.5 rounded-md text-sm font-bold transition-all ${activeTab === 'map'
                                ? 'bg-white text-[#0353A4] shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <MapIcon className="w-4 h-4" />
                            지도 모니터링
                        </button>
                        <button
                            onClick={() => setActiveTab('dashboard')}
                            className={`flex items-center gap-2 px-4 py-1.5 rounded-md text-sm font-bold transition-all ${activeTab === 'dashboard'
                                ? 'bg-white text-[#0353A4] shadow-sm'
                                : 'text-slate-300 hover:text-white hover:bg-white/10'
                                }`}
                        >
                            <LayoutDashboard className="w-4 h-4" />
                            운영 대시보드
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
                        <span className="bg-[#003559] px-2.5 py-1 rounded text-xs mr-2 border border-white/10">관리자</span>
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

            {/* 메인 뷰 영역 (탭에 따라 변경) */}
            <main className="flex-1 overflow-hidden relative">
                {activeTab === 'map' ? (
                    <MapMonitoring
                        vehicles={vehicles}
                        selectedVehicleId={selectedVehicleId}
                        setSelectedVehicleId={setSelectedVehicleId}
                    />
                ) : (
                    <DashboardView
                        calendarMode={calendarMode}
                        setCalendarMode={setCalendarMode}
                        calendarEvents={calendarEvents}
                        missionsList={missionsList}
                        statistics={statistics}
                    />
                )}
            </main>
        </div>
    );
};

export default ControlCenter;
