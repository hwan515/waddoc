import { Activity, LayoutDashboard, Calendar as CalendarIcon, ChevronLeft, ChevronRight, User, Phone, MapPin } from 'lucide-react';

const DashboardView = ({ calendarMode, setCalendarMode, calendarEvents, todayQueue, statistics }) => {
    // 주간 뷰 시간표 (08:00 ~ 18:00, 11 slots)
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

    // 시간 "14:30" 문자열을 픽셀 상단 오프셋으로 변환하는 헬퍼 함수
    const getTopOffset = (timeStr) => {
        const [h, m] = timeStr.split(':').map(Number);
        const decimalHours = (h - 8) + (m / 60); // 08:00 이후 시간
        // 각 시간 블록은 80px 높이
        return decimalHours * 80;
    };

    return (
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
                {/* 그리드 영역 */}
                <div className="flex-1 overflow-hidden flex flex-col bg-white">
                    {calendarMode === 'weekly' ? (
                        <div className="flex-1 overflow-y-auto custom-scrollbar flex relative">
                            {/* 동기화 스크롤 시간 표시 */}
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
                            {/* 일별 컬럼 */}
                            <div className="flex-1 flex flex-col min-w-0">
                                {/* 헤더 (고정) */}
                                <div className="flex bg-white border-b border-slate-100 h-[50px] shadow-sm sticky top-0 z-30">
                                    {weekDays.map((day, idx) => (
                                        <div key={idx} className="flex-1 border-r border-slate-100 flex flex-col items-center justify-center bg-white">
                                            <span className={`text-[14px] font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                                                {day.date}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                                {/* 그리드 컨텐츠 */}
                                <div className="relative flex min-h-[880px]">
                                    <div className="absolute inset-0 pointer-events-none flex flex-col z-0">
                                        {timeSlots.map((_, i) => (
                                            <div key={i} className="h-20 border-b border-slate-100 w-full" />
                                        ))}
                                    </div>
                                    {weekDays.map((_, dayIdx) => (
                                        <div key={dayIdx} className="flex-1 border-r border-slate-100 relative z-10">
                                            {calendarEvents.filter(ev => ev.dayIdx === dayIdx).map(ev => {
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
                            총 {todayQueue.length}명
                        </span>
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar">
                        {todayQueue.map(q => (
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
                                {statistics.totalPatients}<span className="text-sm font-bold text-slate-400 ml-1">명</span>
                            </div>
                        </div>
                        <div className="bg-[#F8F9FA] rounded-lg border border-slate-200 p-3 flex flex-col justify-center">
                            <span className="text-xs font-bold text-slate-500 mb-1">차량 출동 횟수</span>
                            <div className="text-2xl font-black text-[#0353A4]">
                                {Object.values(statistics.vehicleMissions || {}).reduce((a, b) => a + b, 0)}<span className="text-sm font-bold text-[#0353A4]/60 ml-1">회</span>
                            </div>
                        </div>
                        <div className="bg-slate-50 rounded-lg border border-slate-100 p-3 flex flex-col justify-center">
                            <span className="text-[11px] font-bold text-slate-500 mb-1">평균 대기 시간</span>
                            <div className="text-lg font-bold text-slate-800">
                                {statistics.avgWaitTime}<span className="text-xs font-bold text-slate-400 ml-1">분</span>
                            </div>
                        </div>
                        <div className="bg-slate-50 rounded-lg border border-slate-100 p-3 flex flex-col justify-center">
                            <span className="text-[11px] font-bold text-slate-500 mb-1">평균 진료 시간</span>
                            <div className="text-lg font-bold text-slate-800">
                                {statistics.avgConsultTime}<span className="text-xs font-bold text-slate-400 ml-1">분</span>
                            </div>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};

export default DashboardView;
