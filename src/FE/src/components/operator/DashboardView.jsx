import { Activity, LayoutDashboard, Calendar as CalendarIcon, ChevronLeft, ChevronRight, MapPin } from 'lucide-react';

const DashboardView = ({ calendarMode, setCalendarMode, calendarEvents, missionsList = [], statistics }) => {
    // 월간 달력 계산 (현재 년/월 기준)
    const today = new Date();
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth(); // 0-indexed
    const currentDate = today.getDate();

    // 주간 뷰 시간표 (08:00 ~ 18:00, 11 slots)
    const timeSlots = Array.from({ length: 11 }, (_, i) => {
        const hour = i + 8;
        const period = hour < 12 ? '오전' : '오후';
        const displayHour = hour > 12 ? hour - 12 : hour;
        return `${period} ${displayHour.toString().padStart(2, '0')}:00`;
    });

    // 주간 뷰 동적 날짜 (이번 주)
    const startOfWeek = new Date(today);
    startOfWeek.setDate(today.getDate() - today.getDay());

    const currentWeekDays = Array.from({ length: 7 }, (_, i) => {
        const d = new Date(startOfWeek);
        d.setDate(startOfWeek.getDate() + i);
        return {
            dateStr: `${d.getDate()}(${['일', '월', '화', '수', '목', '금', '토'][i]})`,
            fullDate: `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`,
            isToday: d.getDate() === currentDate && d.getMonth() === currentMonth && d.getFullYear() === currentYear
        };
    });

    // 시간 "14:30" 문자열을 픽셀 상단 오프셋으로 변환하는 헬퍼 함수
    const getTopOffset = (timeStr) => {
        if (!timeStr) return 0;
        const [h, m] = timeStr.split(':').map(Number);
        const decimalHours = (h - 8) + (m / 60); // 08:00 이후 시간
        // 각 시간 블록은 80px 높이
        return decimalHours * 80;
    };

    // 이벤트 상태별 색상 헬퍼
    const getEventColor = (status, isMonthly = false) => {
        switch (status) {
            case 'CONFIRMED': return isMonthly ? 'bg-blue-50 text-blue-700 border border-blue-100' : 'bg-blue-50 border-blue-500';
            case 'COMPLETED': return isMonthly ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-green-50 border-green-500 text-green-700';
            case 'CANCELLED': return isMonthly ? 'bg-red-50 text-red-700 border border-red-100' : 'bg-red-50 border-red-500 text-red-700';
            default: return isMonthly ? 'bg-slate-100 text-slate-600 border border-slate-200' : 'bg-slate-100 border-slate-400 text-slate-600';
        }
    };

    // 첫날 요일(0: 일요일)과 마지막 날짜 계산
    const firstDay = new Date(currentYear, currentMonth, 1).getDay();
    const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate();

    // 빈 칸 포함 격자 35일 또는 42일 생성
    const totalSlots = firstDay + daysInMonth > 35 ? 42 : 35;
    const calendarDays = Array.from({ length: totalSlots }, (_, i) => {
        const dayNumber = i - firstDay + 1;
        if (dayNumber > 0 && dayNumber <= daysInMonth) return dayNumber;
        return null;
    });

    return (
        <div className="h-full flex p-4 gap-4">
            {/* 좌측: 통합 캘린더 */}
            <div className="flex-7 min-w-150 rounded-xl bg-white shadow-sm border border-slate-200 overflow-hidden flex flex-col relative">
                {/* Toolbar */}
                <div className="h-16 border-b border-slate-100 flex items-center justify-between px-6 shrink-0 bg-white z-10">
                    <div className="flex bg-[#F8F9FA] border border-slate-200 rounded-md overflow-hidden p-0.5">
                        <button
                            className={`px-6 py-2 text-sm font-bold transition-all rounded-sm ${calendarMode === 'weekly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => setCalendarMode('weekly')}
                        >
                            주 단위
                        </button>
                        <button
                            className={`px-6 py-2 text-sm font-bold transition-all rounded-sm ${calendarMode === 'monthly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => setCalendarMode('monthly')}
                        >
                            월 단위
                        </button>
                    </div>
                    <div className="flex items-center justify-center gap-3 absolute left-1/2 -translate-x-1/2">
                        <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronLeft className="w-6 h-6 text-slate-600" /></button>
                        <h2 className="text-xl font-bold text-slate-800 mx-2 tracking-tight">
                            {calendarMode === 'weekly' ? '이번 주 스케줄' : `${currentYear}년 ${currentMonth + 1}월`}
                        </h2>
                        <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronRight className="w-6 h-6 text-slate-600" /></button>
                    </div>
                </div>
                {/* 그리드 영역 */}
                <div className="flex-1 overflow-hidden flex flex-col bg-white">
                    {calendarMode === 'weekly' ? (
                        <div className="flex-1 overflow-y-auto custom-scrollbar flex relative">
                            {/* 동기화 스크롤 시간 표시 */}
                            <div className="w-20 shrink-0 border-r border-slate-100 flex flex-col bg-white">
                                <div className="h-12.5 sticky top-0 bg-white z-30" />
                                <div className="flex flex-col relative">
                                    {timeSlots.map((time, idx) => (
                                        <div key={idx} className="h-20 flex items-start justify-center text-xs text-slate-400 font-medium pt-2 border-b border-transparent">
                                            {time}
                                        </div>
                                    ))}
                                </div>
                            </div>
                            {/* 일별 컬럼 */}
                            <div className="flex-1 flex flex-col min-w-0">
                                {/* 헤더 (고정) */}
                                <div className="flex bg-white border-b border-slate-100 h-12.5 shadow-sm sticky top-0 z-30">
                                    {currentWeekDays.map((day, idx) => (
                                        <div key={idx} className={`flex-1 border-r border-slate-100 flex flex-col items-center justify-center ${day.isToday ? 'bg-blue-50/70' : 'bg-white'}`}>
                                            <span className={`text-sm font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'} ${day.isToday ? 'text-primary' : ''}`}>
                                                {day.dateStr}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                                {/* 그리드 컨텐츠 */}
                                <div className="relative flex min-h-220">
                                    <div className="absolute inset-0 pointer-events-none flex flex-col z-0">
                                        {timeSlots.map((_, i) => (
                                            <div key={i} className="h-20 border-b border-slate-100 w-full" />
                                        ))}
                                    </div>
                                    {currentWeekDays.map((day, dayIdx) => (
                                        <div key={dayIdx} className={`flex-1 border-r border-slate-100 relative z-10 ${day.isToday ? 'bg-blue-50/30' : ''}`}>
                                            {calendarEvents.filter(ev => ev.fullDate === day.fullDate).map(ev => {
                                                const topOffsetPx = getTopOffset(ev.timeStr);
                                                return (
                                                    <div
                                                        key={ev.id}
                                                        style={{ top: `${topOffsetPx}px`, height: '60px' }}
                                                        className={`absolute inset-x-2 p-2 rounded shadow-sm text-xs opacity-90 hover:opacity-100 transition-opacity cursor-pointer border-l-4 ${getEventColor(ev.status, false)}`}
                                                    >
                                                        <div className="font-bold text-slate-800 text-sm">{ev.name}
                                                            <br /><span className="text-xs font-normal text-slate-500 ml-1">({ev.doctor})</span>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                            {dayIdx === 2 && (
                                                <div className="absolute top-35 left-0 right-0 h-px bg-red-400 z-20 flex items-center -ml-1">
                                                    <div className="w-2.5 h-2.5 rounded-full bg-red-400"></div>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="flex-1 flex flex-col h-full bg-slate-50">
                            {/* 요일 헤더 */}
                            <div className="grid grid-cols-7 border-b border-slate-200 h-10 shrink-0 bg-white">
                                {['일', '월', '화', '수', '목', '금', '토'].map((day, i) => (
                                    <div key={i} className={`flex items-center justify-center text-sm font-bold border-r border-slate-200 ${i === 0 ? 'text-red-500' : i === 6 ? 'text-blue-500' : 'text-slate-600'}`}>
                                        {day}
                                    </div>
                                ))}
                            </div>
                            {/* 날짜 그리드 (스크롤 없이 꽉 채우기) */}
                            <div className={`flex-1 grid grid-cols-7 ${totalSlots === 42 ? 'grid-rows-6' : 'grid-rows-5'}`}>
                                {calendarDays.map((dayNum, idx) => {
                                    const isToday = dayNum === currentDate;
                                    const dayEvents = dayNum ? calendarEvents.filter(ev => {
                                        if (!ev.fullDate) return false;
                                        const evDate = new Date(ev.fullDate);
                                        return evDate.getFullYear() === currentYear && evDate.getMonth() === currentMonth && evDate.getDate() === dayNum;
                                    }).sort((a, b) => (a.timeStr || '').localeCompare(b.timeStr || '')) : [];

                                    return (
                                        <div key={idx} className={`border-b border-r border-slate-200 flex flex-col overflow-hidden transition-colors p-0.75 ${isToday ? 'bg-blue-50/40 hover:bg-blue-50/60' : 'bg-white hover:bg-slate-50/50'}`}>
                                            {dayNum !== null && (
                                                <>
                                                    <div className={`text-xs font-bold pl-1 pt-0.5 ${idx % 7 === 0 ? 'text-red-500' : idx % 7 === 6 ? 'text-blue-500' : isToday ? 'text-primary' : 'text-slate-700'}`}>
                                                        {dayNum}
                                                    </div>
                                                    <div className="flex-1 overflow-hidden mt-0.5 flex flex-col gap-0.75">
                                                        {dayEvents.slice(0, 3).map(ev => (
                                                            <div key={ev.id} className={`px-1.5 py-0.75 rounded text-[10px] leading-tight flex justify-between items-center whitespace-nowrap overflow-hidden shadow-sm ${getEventColor(ev.status, true)}`}>
                                                                <span className="font-bold truncate mr-1">{ev.name}</span>
                                                                <span className="shrink-0 text-[9px] font-medium opacity-80">{ev.timeStr}</span>
                                                            </div>
                                                        ))}
                                                        {dayEvents.length > 3 && (
                                                            <div className="text-[10px] text-slate-400 font-bold text-center bg-slate-50/80 rounded py-0.5 mt-auto">
                                                                +{dayEvents.length - 3}건 더보기
                                                            </div>
                                                        )}
                                                    </div>
                                                </>
                                            )}
                                        </div>
                                    )
                                })}
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* 우측: 현황 및 통계 패널 */}
            <div className="w-112.5 flex flex-col gap-4 shrink-0">
                {/* 우측 상단: 금일 미션 (출동) 현황 */}
                <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                    <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                            <Activity className="w-5 h-5 text-primary" /> 금일 출동 현황
                        </h3>
                        <span className="bg-primary/10 text-primary px-2.5 py-1 rounded-full text-xs font-bold">
                            총 {missionsList.length}건
                        </span>
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar">
                        {missionsList.map(m => (
                            <div key={m.id} className="bg-white border border-slate-200 rounded-lg p-4 shadow-sm hover:border-primary/30 transition-colors">
                                <div className="flex justify-between items-start mb-3">
                                    <div className="font-bold text-slate-800 text-base">{m.patientName} 환자</div>
                                    <div className={`text-xs px-2.5 py-1 rounded-md border font-bold ${m.status === '출동 중' || m.status === '진료 중' ? 'bg-blue-100 text-blue-700 border-blue-200' :
                                        m.status === '대기 중' ? 'bg-slate-100 text-slate-600 border-slate-200' :
                                            m.status === '장애 발생' ? 'bg-red-100 text-red-700 border-red-200' :
                                                'bg-green-100 text-green-700 border-green-200'
                                        }`}>
                                        {m.status}
                                    </div>
                                </div>
                                <div className="space-y-2 text-sm text-slate-600 font-medium bg-slate-50 p-3 rounded-md border border-slate-100">
                                    <div className="flex items-start gap-2.5">
                                        <MapPin className="w-4 h-4 text-slate-400 mt-0.5 shrink-0" />
                                        <span className="leading-tight">{m.destination}</span>
                                    </div>
                                    <div className="flex items-center gap-2.5">
                                        <LayoutDashboard className="w-4 h-4 text-slate-400 shrink-0" />
                                        <span>차량 ID: <span className="font-bold text-slate-700">{m.vehicleId}</span></span>
                                    </div>
                                    <div className="flex items-center gap-2.5">
                                        <CalendarIcon className="w-4 h-4 text-slate-400 shrink-0" />
                                        <span>배차/출동: <span className="font-bold text-slate-700">{m.time}</span></span>
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
                                <LayoutDashboard className="w-5 h-5 text-primary" /> 통계 정보
                            </h3>
                            <span className="text-xs font-semibold text-slate-500 bg-white border px-2 py-0.5 rounded-full">TOTAL</span>
                        </div>
                    </div>
                    <div className="flex-1 p-4 grid grid-cols-2 gap-3 overflow-y-auto custom-scrollbar">
                        <div className="bg-[#F8F9FA] rounded-lg border border-slate-200 p-3 flex flex-col justify-center">
                            <span className="text-xs font-bold text-slate-500 mb-1">총 미션 수</span>
                            <div className="text-2xl font-black text-slate-800">
                                {statistics.totalMissions || 0}<span className="text-sm font-bold text-slate-400 ml-1">건</span>
                            </div>
                        </div>
                        <div className="bg-secondary/30 rounded-lg border border-blue-100 p-3 flex flex-col justify-center">
                            <span className="text-xs font-bold text-blue-800 mb-1">진행 중 (출동/진료)</span>
                            <div className="text-2xl font-black text-primary">
                                {statistics.activeMissions || 0}<span className="text-sm font-bold text-primary/60 ml-1">건</span>
                            </div>
                        </div>
                        <div className="bg-green-50 rounded-lg border border-green-100 p-3 flex flex-col justify-center">
                            <span className="text-xs font-bold text-green-700 mb-1">종료 / 복귀</span>
                            <div className="text-lg font-bold text-green-800">
                                {statistics.completedMissions || 0}<span className="text-xs font-bold text-green-600/60 ml-1">건</span>
                            </div>
                        </div>
                        <div className="bg-red-50 rounded-lg border border-red-100 p-3 flex flex-col justify-center">
                            <span className="text-xs font-bold text-red-700 mb-1">장애 및 지연</span>
                            <div className="text-lg font-bold text-red-800">
                                {statistics.incidentCount || 0}<span className="text-xs font-bold text-red-600/60 ml-1">건</span>
                            </div>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};

export default DashboardView;
