import { useState } from 'react';
import { LogOut, Activity, Map as MapIcon, LayoutDashboard, Navigation, Video, AlertCircle, CheckCircle2, Truck, Calendar as CalendarIcon, ChevronLeft, ChevronRight, User, Phone, MapPin } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import { mockVehicles, mockTotalCalendarEvents, mockTodayQueue, mockStatistics } from '../../mockdata/operator';

const ControlCenter = () => {
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);

    // 'map' | 'dashboard'
    const [activeTab, setActiveTab] = useState('dashboard');

    // 캘린더 모드
    const [calendarMode, setCalendarMode] = useState('weekly');

    // 현재 선택된 차량 (카메라 뷰 연동)
    const [selectedVehicleId, setSelectedVehicleId] = useState(mockVehicles[0]?.id || null);

    // Weekly View Timeslots (08:00 ~ 18:00, 11 slots)
    const timeSlots = Array.from({ length: 11 }, (_, i) => {
        const hour = i + 8;
        const period = hour < 12 ? '오전' : '오후';
        const displayHour = hour > 12 ? hour - 12 : hour;
        return `${period} ${displayHour.toString().padStart(2, '0')}:00`;
    });

    const weekDays = [
        { date: '6(일)' }, { date: '7(월)' }, { date: '8(화)' }, { date: '9(수)' },
        { date: '10(목)' }, { date: '11(금)' }, { date: '12(토)' }
    ];

    // Helper to convert time "14:30" string to pixel top offset
    const getTopOffset = (timeStr) => {
        const [h, m] = timeStr.split(':').map(Number);
        const decimalHours = (h - 8) + (m / 60); // hours since 08:00
        // Each hour block is 80px high
        return decimalHours * 80;
    };

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    // 상태에 따른 배지 색상 결정 헬퍼 함수
    const getStatusBadge = (status) => {
        switch (status) {
            case '운행 중': return 'bg-blue-100 text-blue-700 border-blue-200';
            case '대기 중': return 'bg-green-100 text-green-700 border-green-200';
            case '진료 중': return 'bg-purple-100 text-purple-700 border-purple-200';
            case '점검 중': return 'bg-yellow-100 text-yellow-700 border-yellow-200';
            case '장애': return 'bg-red-100 text-red-700 border-red-200';
            default: return 'bg-slate-100 text-slate-700 border-slate-200';
        }
    };

    return (
        <div className="h-screen bg-[#F5F6F8] flex flex-col font-sans overflow-hidden">
            {/* 1. 상단 글로벌 네비게이션 바 (Nav Bar) */}
            <header className="h-16 bg-[#061A40] text-white flex items-center justify-between px-6 shrink-0 shadow-md z-20">
                <div className="flex items-center gap-3">
                    <div className="bg-white/10 p-2 rounded-lg">
                        <Activity className="w-5 h-5 text-[#B9D6F2]" />
                    </div>
                    <span className="font-bold text-xl tracking-tight">
                        Vital<span className="text-[#B9D6F2]">Connect</span>
                        <span className="ml-3 pl-3 border-l border-white/20 text-sm font-medium text-slate-300">통합 관제 센터</span>
                    </span>
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
                    // --- 지도(Map) 모니터링 뷰 ---
                    <div className="h-full flex p-4 gap-4">

                        {/* 좌측: 디지털 트윈 (전체 맵 영역) */}
                        <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col relative">
                            <div className="absolute top-4 left-4 z-10 bg-white/90 backdrop-blur-sm px-4 py-2 rounded-lg shadow border border-slate-200">
                                <h2 className="font-bold text-slate-800 flex items-center gap-2">
                                    <MapIcon className="w-4 h-4 text-[#006DAA]" /> 디지털 트윈 모니터링
                                </h2>
                                <p className="text-xs text-slate-500 mt-1">평소: 전체 Map / 차량 선택 시: 해당 차량 중심 뷰</p>
                            </div>

                            {/* TODO: Three.js 또는 카카오/네이버 지도 연동 영역 */}
                            <div className="flex-1 bg-[#E8F0F8] flex items-center justify-center relative">
                                <div className="absolute inset-0" style={{
                                    backgroundImage: `radial-gradient(#CBD5E1 1px, transparent 1px)`,
                                    backgroundSize: '24px 24px',
                                    opacity: 0.5
                                }}></div>
                                <div className="text-center z-10 p-8 bg-white/80 backdrop-blur rounded-2xl shadow-xl border border-white mt-10">
                                    <div className="w-16 h-16 bg-[#0353A4]/10 rounded-full flex items-center justify-center mx-auto mb-4">
                                        <MapIcon className="w-8 h-8 text-[#0353A4]" />
                                    </div>
                                    <h3 className="text-xl font-bold text-slate-800 mb-2">디지털 트윈 Map (추후 구현)</h3>
                                    <p className="text-slate-500 text-sm">자율주행 모빌리티의 실시간 위치와 상태를 3D/2D 맵으로 렌더링 할 영역입니다.</p>
                                </div>
                            </div>
                        </div>

                        {/* 우측: 사이드 패널 (차량 리스트 + 카메라) */}
                        <div className="w-[400px] flex flex-col gap-4 shrink-0">

                            {/* 상단: 차량 리스트 */}
                            <div className="flex-[3] bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                                <div className="h-12 border-b border-slate-100 flex items-center px-4 bg-slate-50/50 shrink-0">
                                    <h3 className="font-bold text-slate-800 text-[15px] flex items-center gap-2">
                                        <Truck className="w-4 h-4 text-[#0353A4]" /> 운영 차량 리스트
                                    </h3>
                                    <span className="ml-auto bg-[#0353A4] text-white px-2 py-0.5 rounded-full text-xs font-bold">
                                        {mockVehicles.length}대
                                    </span>
                                </div>
                                <div className="flex-1 overflow-y-auto p-3 space-y-2 custom-scrollbar">
                                    {mockVehicles.map(v => (
                                        <div
                                            key={v.id}
                                            onClick={() => setSelectedVehicleId(v.id)}
                                            className={`p-3 rounded-lg border cursor-pointer transition-all ${selectedVehicleId === v.id
                                                ? 'border-[#0353A4] bg-[#F0F7FF] shadow-sm'
                                                : 'border-slate-200 hover:border-[#006DAA]/30 hover:bg-slate-50'
                                                }`}
                                        >
                                            <div className="flex items-center justify-between mb-2">
                                                <div className="font-bold text-slate-800 text-[15px]">{v.id}</div>
                                                <div className={`text-xs px-2 py-0.5 rounded-md border font-bold ${getStatusBadge(v.status)}`}>
                                                    {v.status}
                                                </div>
                                            </div>
                                            <div className="space-y-1.5 text-xs text-slate-600 font-medium">
                                                <div className="flex items-center gap-2">
                                                    <Navigation className="w-3.5 h-3.5 text-slate-400" />
                                                    <span className="font-mono">{v.location.lat.toFixed(4)}, {v.location.lng.toFixed(4)}</span>
                                                </div>
                                                <div className="flex items-center justify-between mt-2 pt-2 border-t border-slate-200/50">
                                                    <span className="flex items-center gap-1.5">
                                                        배터리 <span className="font-bold text-slate-800">{v.battery}%</span>
                                                    </span>
                                                    <span className="flex items-center gap-1.5">
                                                        속도 <span className="font-bold text-slate-800">{v.speed} km/h</span>
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* 하단: 선택 차량 카메라 화면 */}
                            <div className="flex-[2] bg-slate-900 rounded-xl shadow-sm border border-slate-800 overflow-hidden relative flex flex-col">
                                <div className="absolute top-3 left-3 z-10 bg-black/50 backdrop-blur-sm px-3 py-1.5 rounded text-white text-xs font-bold flex items-center gap-2 border border-white/10">
                                    <Video className="w-3.5 h-3.5 text-red-400" />
                                    {selectedVehicleId ? `${selectedVehicleId} 카메라` : '차량을 선택하세요'}
                                    <span className="ml-1 w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse"></span>
                                </div>

                                {/* TODO: 실제 카메라 영상 스트리밍 영역 */}
                                <div className="flex-1 flex items-center justify-center relative overflow-hidden">
                                    {selectedVehicleId ? (
                                        <>
                                            {/* 카메라 목업 배경 */}
                                            <div className="absolute inset-0 bg-[#1a1c23]">
                                                {/* HUD 라인 */}
                                                <div className="absolute inset-0 opacity-20" style={{
                                                    backgroundImage: `linear-gradient(rgba(255, 255, 255, 0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.1) 1px, transparent 1px)`,
                                                    backgroundSize: '40px 40px'
                                                }}></div>
                                                <div className="absolute top-1/2 left-1/4 right-1/4 h-px bg-green-500/30"></div>
                                                <div className="absolute left-1/2 top-1/4 bottom-1/4 w-px bg-green-500/30"></div>
                                            </div>
                                            <div className="relative z-10 text-center">
                                                <Video className="w-10 h-10 text-slate-500 mx-auto mb-2 opacity-50" />
                                                <p className="text-slate-400 text-sm font-medium">실시간 주행 카메라 (추후 연동)</p>
                                            </div>
                                        </>
                                    ) : (
                                        <p className="text-slate-500 text-sm font-medium">리스트에서 차량을 선택해주세요.</p>
                                    )}
                                </div>
                            </div>

                        </div>
                    </div>
                ) : (
                    // --- 전체 대시보드(Dashboard) 뷰 ---
                    <div className="h-full flex p-4 gap-4">

                        {/* 좌측: 통합 캘린더 */}
                        <div className="flex-[7] min-w-[600px] rounded-xl bg-white shadow-sm border border-slate-200 overflow-hidden flex flex-col relative">
                            {/* Toolbar */}
                            <div className="h-16 border-b border-slate-100 flex items-center justify-between px-6 shrink-0 bg-white z-10">
                                <div className="flex bg-[#F8F9FA] border border-slate-200 rounded-md overflow-hidden p-0.5">
                                    <button
                                        className={`px-6 py-2 text-[13px] font-bold transition-all rounded-sm ${calendarMode === 'weekly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                                        onClick={() => setCalendarMode('weekly')}
                                    >
                                        주 단위
                                    </button>
                                    <button
                                        className={`px-6 py-2 text-[13px] font-bold transition-all rounded-sm ${calendarMode === 'monthly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                                        onClick={() => setCalendarMode('monthly')}
                                    >
                                        월 단위
                                    </button>
                                </div>
                                <div className="flex items-center justify-center gap-3 absolute left-1/2 -translate-x-1/2">
                                    <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronLeft className="w-6 h-6 text-slate-600" /></button>
                                    <h2 className="text-xl font-bold text-slate-800 mx-2 tracking-tight">2023년 08월 6일 - 08월 12일</h2>
                                    <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronRight className="w-6 h-6 text-slate-600" /></button>
                                </div>
                            </div>
                            {/* Grid Area */}
                            <div className="flex-1 overflow-hidden flex flex-col bg-white">
                                {calendarMode === 'weekly' ? (
                                    <div className="flex-1 overflow-y-auto custom-scrollbar flex relative">
                                        {/* Sync Scrolled Time Gutter */}
                                        <div className="w-20 flex-shrink-0 border-r border-slate-100 flex flex-col bg-white">
                                            <div className="h-[50px] sticky top-0 bg-white z-30" />
                                            <div className="flex flex-col relative">
                                                {timeSlots.map((time, idx) => (
                                                    <div key={idx} className="h-20 flex items-start justify-center text-[12px] text-slate-400 font-medium pt-2 border-b border-transparent">
                                                        {time}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                        {/* Daily Columns Wrapper */}
                                        <div className="flex-1 flex flex-col min-w-0">
                                            {/* Headers (Sticky) */}
                                            <div className="flex bg-white border-b border-slate-100 h-[50px] shadow-sm sticky top-0 z-30">
                                                {weekDays.map((day, idx) => (
                                                    <div key={idx} className="flex-1 border-r border-slate-100 flex flex-col items-center justify-center bg-white">
                                                        <span className={`text-[14px] font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                                                            {day.date}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                            {/* Grid Content */}
                                            <div className="relative flex min-h-[880px]">
                                                <div className="absolute inset-0 pointer-events-none flex flex-col z-0">
                                                    {timeSlots.map((_, i) => (
                                                        <div key={i} className="h-20 border-b border-slate-100 w-full" />
                                                    ))}
                                                </div>
                                                {weekDays.map((_, dayIdx) => (
                                                    <div key={dayIdx} className="flex-1 border-r border-slate-100 relative z-10">
                                                        {mockTotalCalendarEvents.filter(ev => ev.dayIdx === dayIdx).map(ev => {
                                                            const topOffsetPx = getTopOffset(ev.timeStr);
                                                            return (
                                                                <div
                                                                    key={ev.id}
                                                                    style={{ top: `${topOffsetPx}px`, height: '60px' }}
                                                                    className={`absolute inset-x-2 p-2 rounded shadow-sm text-xs opacity-90 hover:opacity-100 transition-opacity cursor-pointer border-l-[4px] ${ev.type === '초진' ? 'bg-blue-50 border-primary' : 'bg-green-50 border-green-500'}`}
                                                                >
                                                                    <div className="font-bold text-slate-800 text-[13px]">{ev.name} <span className="text-[11px] font-normal text-slate-500 ml-1">({ev.doctor})</span></div>
                                                                    <div className="text-[11px] text-slate-500 mt-1">{ev.type} · {ev.timeStr}</div>
                                                                </div>
                                                            );
                                                        })}
                                                        {dayIdx === 2 && (
                                                            <div className="absolute top-[140px] left-0 right-0 h-px bg-red-400 z-20 flex items-center -ml-1">
                                                                <div className="w-2.5 h-2.5 rounded-full bg-red-400"></div>
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="flex-1 flex flex-col items-center justify-center text-slate-400 font-medium bg-slate-50/50">
                                        <CalendarIcon className="w-12 h-12 mb-4 text-slate-300" />
                                        월 단위 뷰 스케줄이 표기될 공간입니다.
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 우측: 현황 및 통계 패널 */}
                        <div className="w-[450px] flex flex-col gap-4 shrink-0">

                            {/* 우측 상단: 금일 대기 현황 */}
                            <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                                <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                                    <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                                        <Activity className="w-5 h-5 text-[#0353A4]" /> 금일 대기 현황
                                    </h3>
                                    <span className="bg-red-100 text-red-600 px-2.5 py-1 rounded-full text-xs font-bold">
                                        총 {mockTodayQueue.length}명
                                    </span>
                                </div>
                                <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar">
                                    {mockTodayQueue.map(q => (
                                        <div key={q.id} className="bg-white border border-slate-200 rounded-lg p-4 shadow-sm hover:border-[#0353A4]/30 transition-colors">
                                            <div className="flex justify-between items-start mb-3">
                                                <div className="font-bold text-slate-800 text-base">{q.name} 환자</div>
                                                <div className={`text-xs px-2.5 py-1 rounded-md border font-bold ${q.status === '진료중' ? 'bg-blue-100 text-blue-700 border-blue-200' :
                                                    q.status === '대기중' ? 'bg-yellow-100 text-yellow-700 border-yellow-200' :
                                                        'bg-slate-100 text-slate-600 border-slate-200'
                                                    }`}>
                                                    {q.status}
                                                </div>
                                            </div>
                                            <div className="space-y-2 text-sm text-slate-600 font-medium bg-slate-50 p-3 rounded-md border border-slate-100">
                                                <div className="flex items-start gap-2.5">
                                                    <MapPin className="w-4 h-4 text-slate-400 mt-0.5 shrink-0" />
                                                    <span className="leading-tight">{q.address}</span>
                                                </div>
                                                <div className="flex items-center gap-2.5">
                                                    <Phone className="w-4 h-4 text-slate-400 shrink-0" />
                                                    <span>{q.phone}</span>
                                                </div>
                                                <div className="flex items-center gap-2.5">
                                                    <User className="w-4 h-4 text-slate-400 shrink-0" />
                                                    <span>담당의: <span className="font-bold text-slate-700">{q.doctorName}</span></span>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* 우측 하단: 통계 정보 */}
                            <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                                <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                                    <div className="flex items-center gap-4">
                                        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                                            <LayoutDashboard className="w-5 h-5 text-[#0353A4]" /> 통계 정보
                                        </h3>
                                        <span className="text-xs font-semibold text-slate-500 bg-white border px-2 py-0.5 rounded-full">TODAY</span>
                                    </div>
                                </div>
                                <div className="flex-1 p-4 grid grid-cols-2 gap-3 overflow-y-auto custom-scrollbar">
                                    <div className="bg-[#F8F9FA] rounded-lg border border-slate-200 p-3 flex flex-col justify-center">
                                        <span className="text-xs font-bold text-slate-500 mb-1">총 진료 환자</span>
                                        <div className="text-2xl font-black text-slate-800">
                                            {mockStatistics.totalPatients}<span className="text-sm font-bold text-slate-400 ml-1">명</span>
                                        </div>
                                    </div>
                                    <div className="bg-[#F8F9FA] rounded-lg border border-slate-200 p-3 flex flex-col justify-center">
                                        <span className="text-xs font-bold text-slate-500 mb-1">차량 출동 횟수</span>
                                        <div className="text-2xl font-black text-[#0353A4]">
                                            {Object.values(mockStatistics.vehicleMissions).reduce((a, b) => a + b, 0)}<span className="text-sm font-bold text-[#0353A4]/60 ml-1">회</span>
                                        </div>
                                    </div>
                                    <div className="bg-slate-50 rounded-lg border border-slate-100 p-3 flex flex-col justify-center">
                                        <span className="text-[11px] font-bold text-slate-500 mb-1">평균 대기 시간</span>
                                        <div className="text-lg font-bold text-slate-800">
                                            {mockStatistics.avgWaitTime}<span className="text-xs font-bold text-slate-400 ml-1">분</span>
                                        </div>
                                    </div>
                                    <div className="bg-slate-50 rounded-lg border border-slate-100 p-3 flex flex-col justify-center">
                                        <span className="text-[11px] font-bold text-slate-500 mb-1">평균 진료 시간</span>
                                        <div className="text-lg font-bold text-slate-800">
                                            {mockStatistics.avgConsultTime}<span className="text-xs font-bold text-slate-400 ml-1">분</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>
                )}
            </main>

            {/* 3. 하단 탭 네비게이션 (Fixed Bottom Bar) */}
            <div className="h-16 bg-white border-t border-slate-200 flex items-center justify-center shrink-0 z-20 shadow-[0_-4px_10px_rgba(0,0,0,0.02)] relative">
                <div className="flex items-center gap-2 bg-slate-100 p-1.5 rounded-xl absolute bottom-3 shadow-inner">
                    <button
                        onClick={() => setActiveTab('map')}
                        className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-bold transition-all ${activeTab === 'map'
                            ? 'bg-white text-[#0353A4] shadow-sm ring-1 ring-black/5'
                            : 'text-slate-500 hover:text-slate-700 hover:bg-slate-200/50'
                            }`}
                    >
                        <MapIcon className="w-4 h-4" />
                        지도 모니터링
                    </button>
                    <button
                        onClick={() => setActiveTab('dashboard')}
                        className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-bold transition-all ${activeTab === 'dashboard'
                            ? 'bg-white text-[#0353A4] shadow-sm ring-1 ring-black/5'
                            : 'text-slate-500 hover:text-slate-700 hover:bg-slate-200/50'
                            }`}
                    >
                        <LayoutDashboard className="w-4 h-4" />
                        운영 대시보드
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ControlCenter;
