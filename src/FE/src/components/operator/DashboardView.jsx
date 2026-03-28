import { useState } from 'react';
import { Activity, LayoutDashboard, Calendar as CalendarIcon, ChevronLeft, ChevronRight, MapPin } from 'lucide-react';

const DAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];
const WEEK_START_HOUR = 8;
const WEEK_SLOT_INTERVAL_MINUTES = 30;
const WEEK_SLOT_COUNT = 22;
const WEEK_SLOT_HEIGHT = 40;

const formatDateKey = (date) => (
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
);

const formatHalfHourLabel = (totalMinutes) => {
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;

    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
};

const formatScheduleTime = (timeStr) => {
    if (!timeStr) return '-';

    const [hours, minutes] = String(timeStr).split(':');
    if (hours === undefined || minutes === undefined) {
        return String(timeStr);
    }

    return `${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}`;
};

const formatMissionDateLabel = (dateKey) => {
    const parsedDate = new Date(`${dateKey}T00:00:00`);
    if (Number.isNaN(parsedDate.getTime())) {
        return dateKey;
    }

    return `${parsedDate.getMonth() + 1}월 ${parsedDate.getDate()}일`;
};

const getWeekOfMonth = (date) => {
    const firstDayOfMonth = new Date(date.getFullYear(), date.getMonth(), 1).getDay();
    return Math.floor((date.getDate() + firstDayOfMonth - 1) / 7) + 1;
};

const getMissionStatusBadgeClass = (status) => {
    switch (status) {
        case '출동 중':
            return 'bg-blue-100 text-blue-700 border-blue-200';
        case '도착 완료':
            return 'bg-amber-100 text-amber-700 border-amber-200';
        case '진료 중':
            return 'bg-amber-100 text-amber-800 border-amber-200';
        case '장애 발생':
            return 'bg-red-100 text-red-700 border-red-200';
        case '종료/복귀':
            return 'bg-green-100 text-green-700 border-green-200';
        case '시연 대기':
        case '대기':
        case '대기 중':
        case '추후 서비스 예정':
        default:
            return 'bg-slate-100 text-slate-600 border-slate-200';
    }
};

const getDemoButtonClass = (variant, disabled) => {
    if (disabled) {
        return 'cursor-wait border-slate-200 bg-slate-100 text-slate-400';
    }

    switch (variant) {
        case 'dispatch':
            return 'border-primary/20 bg-primary text-white hover:bg-accent-1';
        case 'arrive':
            return 'border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100';
        case 'complete':
            return 'border-green-200 bg-green-50 text-green-700 hover:bg-green-100';
        default:
            return 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100';
    }
};

const getEventVariant = (event) => {
    switch (event?.missionPhase) {
        case 'DISPATCHED':
        case 'EN_ROUTE':
            return 'dispatching';
        case 'ARRIVED':
            return 'arrived';
        case 'VERIFYING':
        case 'CONSULTING':
            return 'consulting';
        case 'RETURNING':
        case 'COMPLETED':
            return 'complete';
        case 'INCIDENT':
        case 'FAILED':
            return 'incident';
        case 'CREATED':
            return 'confirmed';
        default:
            break;
    }

    switch (event?.status) {
        case 'COMPLETED':
            return 'complete';
        case 'CANCELLED':
        case 'NO_SHOW':
            return 'cancelled';
        case 'CONFIRMED':
            return 'confirmed';
        default:
            return 'default';
    }
};

const DashboardView = ({
    calendarMode,
    setCalendarMode,
    calendarEvents,
    missionsList = [],
    selectedBooking = null,
    selectedBookingId = null,
    onBookingSelect,
    onMissionPanelReset,
    statistics,
    pendingDemoAction = null,
    onDemoDispatch,
    onDemoArrive
}) => {
    const [referenceDate, setReferenceDate] = useState(() => new Date());
    const [expandedMonthlyDate, setExpandedMonthlyDate] = useState(null);

    const currentYear = referenceDate.getFullYear();
    const currentMonth = referenceDate.getMonth(); // 0-indexed
    const todayDateKey = formatDateKey(new Date());

    // 주간 뷰 시간표 (08:00 ~ 19:00, 30분 단위)
    const timeSlots = Array.from({ length: WEEK_SLOT_COUNT }, (_, i) => {
        const totalMinutes = (WEEK_START_HOUR * 60) + (i * WEEK_SLOT_INTERVAL_MINUTES);

        return {
            label: formatHalfHourLabel(totalMinutes),
            isHalfHour: totalMinutes % 60 !== 0
        };
    });

    // 주간 뷰 동적 날짜
    const startOfWeek = new Date(referenceDate);
    startOfWeek.setHours(0, 0, 0, 0);
    startOfWeek.setDate(referenceDate.getDate() - referenceDate.getDay());
    const endOfWeek = new Date(startOfWeek);
    endOfWeek.setDate(startOfWeek.getDate() + 6);

    const currentWeekDays = Array.from({ length: 7 }, (_, i) => {
        const d = new Date(startOfWeek);
        d.setDate(startOfWeek.getDate() + i);
        const fullDate = formatDateKey(d);

        return {
            dateStr: `${d.getDate()}(${DAY_LABELS[i]})`,
            fullDate,
            isToday: fullDate === todayDateKey
        };
    });

    // 시간 "14:30" 문자열을 픽셀 상단 오프셋으로 변환하는 헬퍼 함수
    const getTopOffset = (timeStr) => {
        if (!timeStr) return 0;
        const [h, m] = timeStr.split(':').map(Number);
        const elapsedMinutes = ((h - WEEK_START_HOUR) * 60) + m;
        return (elapsedMinutes / WEEK_SLOT_INTERVAL_MINUTES) * WEEK_SLOT_HEIGHT;
    };

    const handleCalendarShift = (direction) => {
        setExpandedMonthlyDate(null);
        setReferenceDate((current) => {
            const next = new Date(current);

            if (calendarMode === 'weekly') {
                next.setDate(current.getDate() + (direction * 7));
                return next;
            }

            const currentDay = current.getDate();
            next.setDate(1);
            next.setMonth(current.getMonth() + direction);
            const lastDayOfTargetMonth = new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate();
            next.setDate(Math.min(currentDay, lastDayOfTargetMonth));
            return next;
        });
    };

    const handleCalendarModeChange = (mode) => {
        setExpandedMonthlyDate(null);
        setCalendarMode(mode);
    };

    const getEventsForDate = (fullDate) => (
        calendarEvents
            .filter((event) => event.fullDate === fullDate)
            .sort((a, b) => (a.timeStr || '').localeCompare(b.timeStr || ''))
    );

    const currentPeriodLabel = calendarMode === 'weekly'
        ? `${String(currentYear).slice(-2)}년 ${currentMonth + 1}월 ${getWeekOfMonth(referenceDate)}주차`
        : `${currentYear}년 ${currentMonth + 1}월`;

    const previousPeriodLabel = calendarMode === 'weekly' ? '지난주' : '지난달';
    const nextPeriodLabel = calendarMode === 'weekly' ? '다음 주' : '다음 달';
    const currentMonthPrefix = `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}`;

    const handleScheduleMouseDown = (event) => {
        event.preventDefault();
    };

    const handleToggleMonthlyEvents = (fullDate) => {
        setExpandedMonthlyDate((current) => (current === fullDate ? null : fullDate));
    };

    const getEventColor = (event, isMonthly = false) => {
        switch (getEventVariant(event)) {
            case 'complete':
                return isMonthly
                    ? 'bg-green-50 text-green-700 border border-green-100'
                    : 'border-l-green-500 bg-green-50 text-green-900 ring-1 ring-green-100';
            case 'incident':
                return isMonthly
                    ? 'bg-red-50 text-red-700 border border-red-100'
                    : 'border-l-red-500 bg-red-50 text-red-900 ring-1 ring-red-100';
            case 'cancelled':
                return isMonthly
                    ? 'bg-red-50 text-red-700 border border-red-100'
                    : 'border-l-red-500 bg-red-50 text-red-900 ring-1 ring-red-100';
            case 'arrived':
                return isMonthly
                    ? 'bg-amber-50 text-amber-700 border border-amber-100'
                    : 'border-l-amber-500 bg-amber-50 text-amber-900 ring-1 ring-amber-100';
            case 'consulting':
                return isMonthly
                    ? 'bg-amber-50 text-amber-800 border border-amber-100'
                    : 'border-l-amber-500 bg-amber-50 text-amber-900 ring-1 ring-amber-100';
            case 'dispatching':
            case 'confirmed':
                return isMonthly
                    ? 'bg-secondary/30 text-blue-800 border border-blue-100'
                    : 'border-l-primary bg-secondary/35 text-blue-900 ring-1 ring-blue-100';
            default:
                return isMonthly
                    ? 'bg-slate-100 text-slate-600 border border-slate-200'
                    : 'border-l-slate-400 bg-slate-100 text-slate-700 ring-1 ring-slate-200';
        }
    };

    // 첫날 요일(0: 일요일)과 마지막 날짜 계산
    const firstDay = new Date(currentYear, currentMonth, 1).getDay();
    const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate();

    // 빈 칸 포함 격자 35일 또는 42일 생성
    const totalSlots = firstDay + daysInMonth > 35 ? 42 : 35;
    const monthlyRowCount = totalSlots === 42 ? 6 : 5;
    const monthlyRowMinHeight = expandedMonthlyDate ? '9.5rem' : '7rem';
    const calendarDays = Array.from({ length: totalSlots }, (_, i) => {
        const dayNumber = i - firstDay + 1;
        if (dayNumber > 0 && dayNumber <= daysInMonth) return dayNumber;
        return null;
    });

    const totalMissionCount = statistics.totalMissions || 0;
    const dispatchingMissionCount = statistics.dispatchingMissions || 0;
    const consultingMissionCount = statistics.consultingMissions || 0;
    const completedMissionCount = statistics.completedMissions || 0;
    const incidentMissionCount = statistics.incidentCount || 0;
    const missionPanelTitle = '금일 출동 현황';
    const missionPanelDescription = selectedBooking
        ? `${formatMissionDateLabel(selectedBooking.fullDate)} ${formatScheduleTime(selectedBooking.timeStr)} · ${selectedBooking.name}`
        : '왼쪽 예약을 누르면 해당 미션을 확인할 수 있습니다.';
    const missionPanelEmptyMessage = selectedBooking
        ? '선택한 예약에 연결된 미션이 아직 없습니다.'
        : '금일 출동 예정 미션이 없습니다.';

    return (
        <div className="h-full flex p-4 gap-4">
            {/* 좌측: 통합 캘린더 */}
            <div className="flex-7 min-w-150 rounded-xl bg-white shadow-sm border border-slate-200 overflow-hidden flex flex-col relative">
                <div className="relative h-16 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                    <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                        <CalendarIcon className="w-5 h-5 text-primary" /> 진료 예약 현황
                    </h3>
                    <div className="absolute left-1/2 flex -translate-x-1/2 items-center justify-center gap-3">
                        <button
                            type="button"
                            onClick={() => handleCalendarShift(-1)}
                            aria-label={previousPeriodLabel}
                            title={previousPeriodLabel}
                            className="rounded-full p-1 transition-colors hover:bg-white"
                        >
                            <ChevronLeft className="h-6 w-6 text-slate-600" />
                        </button>
                        <h2 className="mx-2 text-xl font-bold tracking-tight text-slate-800">
                            {currentPeriodLabel}
                        </h2>
                        <button
                            type="button"
                            onClick={() => handleCalendarShift(1)}
                            aria-label={nextPeriodLabel}
                            title={nextPeriodLabel}
                            className="rounded-full p-1 transition-colors hover:bg-white"
                        >
                            <ChevronRight className="h-6 w-6 text-slate-600" />
                        </button>
                    </div>
                    <div className="flex bg-slate-50 border border-slate-200 rounded-md overflow-hidden p-0.5">
                        <button
                            className={`px-6 py-2 text-sm font-bold transition-all rounded-sm ${calendarMode === 'weekly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => handleCalendarModeChange('weekly')}
                        >
                            주 단위
                        </button>
                        <button
                            className={`px-6 py-2 text-sm font-bold transition-all rounded-sm ${calendarMode === 'monthly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => handleCalendarModeChange('monthly')}
                        >
                            월 단위
                        </button>
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
                                        <div
                                            key={idx}
                                            className={`h-10 flex items-start justify-center border-b border-transparent pt-1.5 text-[11px] ${time.isHalfHour ? 'text-slate-300' : 'font-medium text-slate-400'}`}
                                        >
                                            {time.label}
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
                                            <div key={i} className="h-10 border-b border-slate-100 w-full" />
                                        ))}
                                    </div>
                                    {currentWeekDays.map((day, dayIdx) => (
                                        <div key={dayIdx} className={`flex-1 border-r border-slate-100 relative z-10 ${day.isToday ? 'bg-blue-50/30' : ''}`}>
                                            {getEventsForDate(day.fullDate).map((ev) => {
                                                const topOffsetPx = getTopOffset(ev.timeStr);
                                                return (
                                                    <button
                                                        key={ev.id}
                                                        type="button"
                                                        onClick={() => onBookingSelect?.(ev)}
                                                        onMouseDown={handleScheduleMouseDown}
                                                        style={{ top: `${topOffsetPx}px`, height: '36px' }}
                                                        title={`${ev.name} / ${ev.doctor || 'Unassigned'}`}
                                                        aria-pressed={selectedBookingId === ev.id}
                                                        className={`absolute inset-x-1.5 flex cursor-pointer items-center overflow-hidden rounded-md border-l-4 px-2 py-1 text-left text-xs transition-colors select-none focus:outline-none focus:ring-2 focus:ring-primary/20 ${selectedBookingId === ev.id ? 'ring-2 ring-inset ring-primary/20' : ''} ${getEventColor(ev, false)}`}
                                                    >
                                                        <div className="min-w-0">
                                                            <div className="truncate text-[11px] font-bold leading-4">
                                                                {ev.name}
                                                            </div>
                                                            <div className="truncate text-[10px] leading-none opacity-80">
                                                                {ev.doctor || 'Unassigned'}
                                                            </div>
                                                        </div>
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar bg-slate-50">
                            <div className="flex min-h-full flex-col">
                                {/* 요일 헤더 */}
                                <div className="sticky top-0 z-20 grid grid-cols-7 border-b border-slate-200 h-10 shrink-0 bg-white">
                                    {['일', '월', '화', '수', '목', '금', '토'].map((day, i) => (
                                        <div key={i} className={`flex items-center justify-center text-sm font-bold border-r border-slate-200 ${i === 0 ? 'text-red-500' : i === 6 ? 'text-blue-500' : 'text-slate-600'}`}>
                                            {day}
                                        </div>
                                    ))}
                                </div>
                                <div
                                    className="grid flex-1 grid-cols-7"
                                    style={{ gridTemplateRows: `repeat(${monthlyRowCount}, minmax(${monthlyRowMinHeight}, 1fr))` }}
                                >
                                    {calendarDays.map((dayNum, idx) => {
                                        const dayFullDate = dayNum ? `${currentMonthPrefix}-${String(dayNum).padStart(2, '0')}` : null;
                                        const isToday = dayFullDate === todayDateKey;
                                        const dayEvents = dayFullDate ? getEventsForDate(dayFullDate) : [];
                                        const isExpandedDay = dayFullDate !== null && expandedMonthlyDate === dayFullDate;
                                        const visibleEvents = isExpandedDay ? dayEvents : dayEvents.slice(0, 3);

                                        return (
                                            <div key={idx} className={`border-b border-r border-slate-200 flex min-h-0 flex-col overflow-hidden transition-colors p-0.75 ${isExpandedDay ? 'bg-slate-50 ring-1 ring-inset ring-primary/15' : isToday ? 'bg-blue-50/40 hover:bg-blue-50/60' : 'bg-white hover:bg-slate-50/50'}`}>
                                                {dayNum !== null && (
                                                    <>
                                                        <div className={`text-xs font-bold pl-1 pt-0.5 ${idx % 7 === 0 ? 'text-red-500' : idx % 7 === 6 ? 'text-blue-500' : isToday ? 'text-primary' : 'text-slate-700'}`}>
                                                            {dayNum}
                                                        </div>
                                                        <div className={`mt-0.5 flex flex-1 flex-col gap-0.75 ${isExpandedDay ? 'overflow-y-auto pr-0.5 custom-scrollbar' : 'overflow-hidden'}`}>
                                                            {visibleEvents.map((ev) => (
                                                                <button
                                                                    key={ev.id}
                                                                    type="button"
                                                                    onClick={() => onBookingSelect?.(ev)}
                                                                    aria-pressed={selectedBookingId === ev.id}
                                                                    className={`flex items-center justify-between overflow-hidden whitespace-nowrap rounded px-1.5 py-0.75 text-left text-[10px] leading-tight shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-primary/20 ${selectedBookingId === ev.id ? 'ring-2 ring-inset ring-primary/20' : ''} ${getEventColor(ev, true)}`}
                                                                >
                                                                    <span className="mr-1 truncate font-bold">{ev.name}</span>
                                                                    <span className="shrink-0 text-[9px] font-medium opacity-80">{formatScheduleTime(ev.timeStr)}</span>
                                                                </button>
                                                            ))}
                                                            {dayEvents.length > 3 && (
                                                                <button
                                                                    type="button"
                                                                    onClick={() => handleToggleMonthlyEvents(dayFullDate)}
                                                                    className="mt-auto rounded bg-slate-50/80 py-0.5 text-center text-[10px] font-bold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
                                                                >
                                                                    {isExpandedDay ? '접기' : `+${dayEvents.length - 3}건 더보기`}
                                                                </button>
                                                            )}
                                                        </div>
                                                    </>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* 우측: 현황 및 통계 패널 */}
            <div className="w-[clamp(22rem,28vw,26rem)] flex flex-col gap-4 shrink-0">
                {/* 우측 상단: 금일 미션 (출동) 현황 */}
                <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                    <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                            <Activity className="w-5 h-5 text-primary" /> {missionPanelTitle}
                        </h3>
                        <span className="bg-primary/10 text-primary px-2.5 py-1 rounded-full text-xs font-bold">
                            총 {missionsList.length}건
                        </span>
                    </div>
                    <div className="border-t border-slate-100 bg-white px-4 py-2">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="min-w-0">
                                <div className="truncate text-xs font-medium leading-4 text-slate-700">
                                    {missionPanelDescription}
                                </div>
                            </div>
                            {selectedBooking && (
                                <button
                                    type="button"
                                    onClick={onMissionPanelReset}
                                    className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-bold text-slate-600 transition-colors hover:bg-slate-50"
                                >
                                    <ChevronLeft className="h-4 w-4" />
                                    오늘 목록으로
                                </button>
                            )}
                        </div>
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar">
                        {missionsList.length === 0 ? (
                            <div className="flex h-full min-h-48 items-center justify-center rounded-lg border border-dashed border-slate-200 bg-slate-50 text-sm font-medium text-slate-500">
                                {missionPanelEmptyMessage}
                            </div>
                        ) : missionsList.map(m => (
                            <div
                                key={m.id}
                                className={`border rounded-lg p-4 shadow-sm transition-colors ${m.isPrimaryServiceVehicle
                                    ? 'bg-white border-slate-200 hover:border-primary/30'
                                    : 'bg-slate-50/80 border-slate-200 cursor-default opacity-75'
                                    }`}
                            >
                                <div className="flex justify-between items-start mb-3">
                                    <div className="font-bold text-slate-800 text-base">
                                        {m.isPrimaryServiceVehicle ? `${m.patientName} 환자` : m.patientName}
                                    </div>
                                    <div className={`text-xs px-2.5 py-1 rounded-md border font-bold ${getMissionStatusBadgeClass(m.status)}`}>
                                        {m.status}
                                    </div>
                                </div>
                                <div className={`space-y-2 text-sm text-slate-600 font-medium p-3 rounded-md border ${m.isPrimaryServiceVehicle
                                    ? 'bg-slate-50 border-slate-100'
                                    : 'bg-white/80 border-slate-200/80'
                                    }`}>
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
                                    <div className="flex items-center justify-between gap-2 border-t border-slate-200/80 pt-2 text-xs">
                                        <span className="text-slate-500">현재 단계</span>
                                        <span className="font-bold text-slate-700">{m.phaseLabel}</span>
                                    </div>
                                </div>
                                {(m.canDispatch || m.canArrive) && (
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        {m.canDispatch && (
                                            <button
                                                type="button"
                                                onClick={() => onDemoDispatch?.(m.id)}
                                                disabled={pendingDemoAction !== null}
                                                className={`rounded-md border px-3 py-2 text-xs font-bold transition-colors ${getDemoButtonClass(
                                                    'dispatch',
                                                    pendingDemoAction !== null
                                                )}`}
                                            >
                                                {pendingDemoAction?.missionId === m.id && pendingDemoAction?.action === 'dispatch'
                                                    ? '출동 처리 중...'
                                                    : '시연 출동'}
                                            </button>
                                        )}
                                        {m.canArrive && (
                                            <button
                                                type="button"
                                                onClick={() => onDemoArrive?.(m.id)}
                                                disabled={pendingDemoAction !== null}
                                                className={`rounded-md border px-3 py-2 text-xs font-bold transition-colors ${getDemoButtonClass(
                                                    'arrive',
                                                    pendingDemoAction !== null
                                                )}`}
                                            >
                                                {pendingDemoAction?.missionId === m.id && pendingDemoAction?.action === 'arrive'
                                                    ? '도착 처리 중...'
                                                    : '도착 처리'}
                                            </button>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                </div>

                {/* 우측 하단: 총 미션 현황 */}
                <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                    <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                            <LayoutDashboard className="w-5 h-5 text-primary" /> 총 미션 통계
                        </h3>
                        <span className="bg-primary/10 text-primary px-2.5 py-1 rounded-full text-xs font-bold">
                            총 {totalMissionCount}건
                        </span>
                    </div>
                    <div className="flex-1 p-4 grid grid-cols-2 gap-3 overflow-y-auto custom-scrollbar">
                        <div className="bg-blue-50 rounded-lg border border-blue-100 p-4 flex flex-col justify-center">
                            <span className="text-xs font-bold text-blue-700 mb-1">출동 중</span>
                            <div className="text-2xl font-black text-blue-900">
                                {dispatchingMissionCount}<span className="text-sm font-bold text-blue-700/70 ml-1">건</span>
                            </div>
                        </div>
                        <div className="rounded-lg border border-amber-100 bg-amber-50 p-4 flex flex-col justify-center">
                            <span className="text-xs font-bold text-amber-800 mb-1">진료 중</span>
                            <div className="text-2xl font-black text-amber-900">
                                {consultingMissionCount}<span className="text-sm font-bold text-amber-700/70 ml-1">건</span>
                            </div>
                        </div>
                        <div className="bg-green-50 rounded-lg border border-green-100 p-4 flex flex-col justify-center">
                            <span className="text-xs font-bold text-green-700 mb-1">종료 / 복귀</span>
                            <div className="text-lg font-bold text-green-800">
                                {completedMissionCount}<span className="text-xs font-bold text-green-600/60 ml-1">건</span>
                            </div>
                        </div>
                        <div className="bg-red-50 rounded-lg border border-red-100 p-4 flex flex-col justify-center">
                            <span className="text-xs font-bold text-red-700 mb-1">장애 / 지연</span>
                            <div className="text-lg font-bold text-red-800">
                                {incidentMissionCount}<span className="text-xs font-bold text-red-600/60 ml-1">건</span>
                            </div>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};

export default DashboardView;
