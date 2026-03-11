import { useState } from 'react';
import {
    ChevronLeft,
    ChevronRight,
    Calendar as CalendarIcon,
    Check,
    X,
    Clock,
    User,
    Phone,
    CalendarCheck
} from 'lucide-react';

import { useNavigate } from 'react-router-dom';
import { mockNewRequests, mockTodaySchedule } from '../../mockdata/bookings';
import { mockCalendarEvents } from '../../mockdata/calendar';

const DoctorDashboard = () => {
    const navigate = useNavigate();
    const [viewMode, setViewMode] = useState('weekly');
    const [newRequests, setNewRequests] = useState(mockNewRequests);
    const [calendarEvents, setCalendarEvents] = useState(mockCalendarEvents);
    const [todaySchedules] = useState(mockTodaySchedule);
    const [selectedSchedule, setSelectedSchedule] = useState(null); // For modal

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

    // --- HANDLERS ---
    const handleAcceptRequest = (req) => {
        // Add to Calendar
        const newEvent = {
            id: `c_${req.id}`,
            name: req.name,
            type: req.type,
            timeStr: req.timeStr,
            dayIdx: req.dayIdx
        };
        setCalendarEvents([...calendarEvents, newEvent]);
        // Remove from New Requests
        setNewRequests(newRequests.filter(r => r.id !== req.id));
    };

    const handleRejectRequest = (id) => {
        setNewRequests(newRequests.filter(r => r.id !== id));
    };

    const handleScheduleClick = (schedule) => {
        setSelectedSchedule(schedule);
    };

    const formatDisplayTime = (timeStr) => {
        const h = parseInt(timeStr.split(':')[0], 10);
        const m = timeStr.split(':')[1];
        const period = h < 12 ? '오전' : '오후';
        const dispH = h > 12 ? h - 12 : h;
        return `${period} ${dispH.toString().padStart(2, '0')}:${m}`;
    };

    return (
        <div className="flex bg-[#F5F6F8] h-[calc(100vh-64px)] overflow-hidden p-4 gap-4 text-sm min-w-[1200px] relative">

            {/* 1. Left Column: New Requests List */}
            <div className="w-[320px] rounded-xl bg-white shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-slate-200/60 flex flex-col overflow-hidden flex-shrink-0">
                <div className="h-14 border-b border-slate-100 flex items-center px-5 shrink-0 bg-white">
                    <h2 className="font-bold text-slate-800 text-base">신규 접수 내역</h2>
                    <span className="ml-2 bg-red-100 text-red-600 px-2 py-0.5 rounded-full text-[11px] font-bold leading-none">{newRequests.length}</span>
                </div>
                <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar bg-slate-50/50">
                    {newRequests.map((req) => (
                        <div key={req.id} className="bg-white border border-slate-200 rounded-lg p-4 shadow-sm hover:border-primary/30 transition-colors">
                            <div className="flex items-center justify-between mb-3">
                                <div className="font-bold text-slate-800 text-[15px] flex items-end gap-1.5">
                                    {req.name} <span className="text-xs font-medium text-slate-500">{req.age}세</span>
                                </div>
                                <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${req.type === '초진' ? 'text-blue-600 bg-blue-50' : 'text-green-600 bg-green-50'}`}>
                                    {req.type}
                                </span>
                            </div>
                            <div className="space-y-1.5 text-xs text-slate-600 mb-4 font-medium">
                                <div className="flex items-center gap-2">
                                    <span className="text-slate-400 w-12">연락처</span> {req.phone}
                                </div>
                                <div className="flex items-center gap-2">
                                    <span className="text-slate-400 w-12">예약일</span> {req.requestDate}
                                </div>
                                <div className="flex items-center gap-2">
                                    <span className="text-slate-400 w-12">시간</span> <span className="text-primary font-bold">{formatDisplayTime(req.timeStr)}</span>
                                </div>
                            </div>
                            <div className="flex gap-2 text-xs font-bold pt-1">
                                <button
                                    onClick={() => handleRejectRequest(req.id)}
                                    className="flex-1 py-2 rounded-md bg-white border border-slate-300 text-slate-600 hover:bg-slate-50 transition-colors flex items-center justify-center gap-1 shadow-sm"
                                >
                                    <X className="w-3.5 h-3.5" /> 거절
                                </button>
                                <button
                                    onClick={() => handleAcceptRequest(req)}
                                    className="flex-1 py-2 rounded-md bg-primary text-white border border-transparent hover:bg-primary/90 transition-colors flex items-center justify-center gap-1 shadow-sm shadow-primary/20"
                                >
                                    <Check className="w-3.5 h-3.5" /> 수락
                                </button>
                            </div>
                        </div>
                    ))}
                    {newRequests.length === 0 && (
                        <div className="flex flex-col items-center justify-center h-40 text-slate-400">
                            <span className="text-sm font-medium">신규 접수 내역이 없습니다.</span>
                        </div>
                    )}
                </div>
            </div>

            {/* 2. Middle Column: Calendar (Weekly/Monthly) */}
            <div className="flex-1 min-w-[500px] rounded-xl bg-white shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-slate-200/60 flex flex-col overflow-hidden relative">

                {/* Toolbar */}
                <div className="h-[70px] border-b border-slate-100 flex items-center justify-between px-6 shrink-0 bg-white z-10">
                    <div className="flex bg-[#F8F9FA] border border-slate-200 rounded-md overflow-hidden p-0.5">
                        <button
                            className={`px-5 py-1.5 text-[12px] font-bold transition-all rounded-sm ${viewMode === 'weekly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => setViewMode('weekly')}
                        >
                            주 단위
                        </button>
                        <button
                            className={`px-5 py-1.5 text-[12px] font-bold transition-all rounded-sm ${viewMode === 'monthly' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
                            onClick={() => setViewMode('monthly')}
                        >
                            월 단위
                        </button>
                    </div>

                    <div className="flex items-center justify-center gap-2 absolute left-1/2 -translate-x-1/2">
                        <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronLeft className="w-5 h-5 text-slate-600" /></button>
                        <h2 className="text-lg font-bold text-slate-800 mx-2 tracking-tight">2023년 08월 6일 - 08월 12일</h2>
                        <button className="p-1 hover:bg-slate-100 rounded-full transition-colors"><ChevronRight className="w-5 h-5 text-slate-600" /></button>
                    </div>
                </div>

                {/* Grid Area */}
                <div className="flex-1 overflow-hidden flex flex-col bg-white">
                    {viewMode === 'weekly' ? (
                        <div className="flex-1 overflow-y-auto custom-scrollbar flex relative">

                            {/* Sync Scrolled Time Gutter */}
                            <div className="w-16 flex-shrink-0 border-r border-slate-100 flex flex-col bg-white">
                                <div className="h-[46px] sticky top-0 bg-white z-30" /> {/* Spacer for sticky header */}
                                <div className="flex flex-col relative">
                                    {timeSlots.map((time, idx) => (
                                        <div key={idx} className="h-20 flex items-start justify-center text-[11px] text-slate-400 font-medium pt-2 border-b border-transparent">
                                            {time}
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Daily Columns Wrapper */}
                            <div className="flex-1 flex flex-col min-w-0">

                                {/* Headers (Sticky) */}
                                <div className="flex bg-white border-b border-slate-100 h-[46px] shadow-sm sticky top-0 z-30">
                                    {weekDays.map((day, idx) => (
                                        <div key={idx} className="flex-1 border-r border-slate-100 flex flex-col items-center justify-center bg-white">
                                            <span className={`text-[13px] font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                                                {day.date}
                                            </span>
                                        </div>
                                    ))}
                                </div>

                                {/* Grid Content */}
                                <div className="relative flex min-h-[880px]">
                                    {/* Background lines (h-20 each) */}
                                    <div className="absolute inset-0 pointer-events-none flex flex-col z-0">
                                        {timeSlots.map((_, i) => (
                                            <div key={i} className="h-20 border-b border-slate-100 w-full" />
                                        ))}
                                    </div>

                                    {weekDays.map((_, dayIdx) => (
                                        <div key={dayIdx} className="flex-1 border-r border-slate-100 relative z-10">

                                            {/* Render Calendar Events */}
                                            {calendarEvents.filter(ev => ev.dayIdx === dayIdx).map(ev => {
                                                const topOffsetPx = getTopOffset(ev.timeStr);
                                                // Usually events are like 30m or 1h block. Lets set default height 60px.
                                                return (
                                                    <div
                                                        key={ev.id}
                                                        style={{ top: `${topOffsetPx}px`, height: '60px' }}
                                                        className={`absolute inset-x-1.5 p-2 rounded shadow-sm text-xs opacity-90 hover:opacity-100 transition-opacity cursor-pointer border-l-[3px] ${ev.type === '초진' ? 'bg-blue-50 border-primary' : 'bg-green-50 border-green-500'}`}
                                                    >
                                                        <div className="font-bold text-slate-800">{ev.name}</div>
                                                        <div className="text-[10px] text-slate-500 mt-0.5">{ev.type} · {ev.timeStr}</div>
                                                    </div>
                                                );
                                            })}

                                            {/* Current Time Line Indicator (Mock) */}
                                            {dayIdx === 2 && (
                                                <div className="absolute top-[140px] left-0 right-0 h-px bg-red-400 z-20 flex items-center -ml-1">
                                                    <div className="w-2 h-2 rounded-full bg-red-400"></div>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>

                        </div>
                    ) : (
                        <div className="flex-1 flex flex-col items-center justify-center text-slate-400 font-medium bg-slate-50/50">
                            <CalendarIcon className="w-10 h-10 mb-3 text-slate-300" />
                            월 단위 뷰 스케줄이 표기될 공간입니다.
                        </div>
                    )}
                </div>
            </div>

            {/* 3. Right Column: Today's Schedule */}
            <div className="w-[300px] xl:w-[350px] rounded-xl bg-white shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-slate-200/60 flex flex-col overflow-hidden flex-shrink-0 relative">
                <div className="h-14 border-b border-slate-100 flex items-center px-5 shrink-0 bg-white">
                    <div className="flex flex-col">
                        <h2 className="font-bold text-slate-800 text-base leading-tight">오늘의 예약 일정</h2>
                        <span className="text-[11px] font-medium text-slate-500 mt-0.5">2023.08.08 (화)</span>
                    </div>
                </div>

                <div className="flex-1 p-5 overflow-y-auto custom-scrollbar relative bg-slate-50/20">

                    {/* Timeline connecting dots */}
                    <div className="absolute top-8 bottom-8 left-[39px] w-px bg-slate-200 z-0" />

                    <div className="space-y-5 relative z-10">
                        {todaySchedules.map((schedule) => {
                            return (
                                <div
                                    key={schedule.id}
                                    onClick={() => handleScheduleClick(schedule)}
                                    className="flex gap-4 group cursor-pointer relative"
                                >

                                    {/* Time column */}
                                    <div className="w-12 pt-1 text-right flex flex-col items-end">
                                        <span className="text-[12px] font-bold text-slate-700">{schedule.time}</span>
                                    </div>

                                    {/* Dot status indicator */}
                                    <div className="relative flex items-start pt-1.5">
                                        <div className={`w-3 h-3 rounded-full border-2 border-white ring-1 shadow-sm flex-shrink-0 z-10 transition-colors ${schedule.status === '완료' ? 'bg-slate-300 ring-slate-300' : 'bg-primary ring-primary'}`}
                                        />
                                    </div>

                                    {/* Card content */}
                                    <div className={`flex-1 p-3.5 rounded-[10px] border transition-all relative overflow-hidden ${schedule.status === '완료' ? 'bg-slate-50 border-slate-200 opacity-70' : 'bg-white border-primary/20 shadow-sm group-hover:border-primary/50 group-hover:shadow-md'}`}>

                                        {/* Active Indicator bar */}
                                        {schedule.status === '대기' && (
                                            <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary rounded-l-[10px]" />
                                        )}

                                        <div className="flex justify-between items-start mb-1.5">
                                            <span className="font-bold text-slate-800 text-[14px]">{schedule.name}</span>
                                            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${schedule.type === '초진' ? 'text-blue-600 bg-blue-50' : 'text-green-600 bg-green-50'}`}>
                                                {schedule.type}
                                            </span>
                                        </div>

                                        <div className="flex items-center justify-between">
                                            <div className="flex items-center gap-1.5 text-[11px] text-slate-500 font-medium">
                                                <Clock className="w-3.5 h-3.5" />
                                                {schedule.status === '완료' ? '진료 완료' : '대면 진료'}
                                            </div>

                                            {schedule.status === '대기' && (
                                                <span className="text-[10px] font-bold text-primary bg-primary/10 px-1.5 py-0.5 rounded">현재 대기중</span>
                                            )}
                                        </div>

                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {todaySchedules.length === 0 && (
                        <div className="h-full flex flex-col items-center justify-center text-slate-400 text-sm gap-2">
                            <CalendarIcon className="w-8 h-8 text-slate-300" />
                            <span>오늘 예정된 예약이 없습니다.</span>
                        </div>
                    )}
                </div>
            </div>

            {/* MODAL OVERLAY */}
            {selectedSchedule && (
                <div className="absolute inset-0 bg-slate-900/20 backdrop-blur-[2px] z-50 flex items-center justify-center rounded-xl p-4 animate-in fade-in duration-200">
                    <div className="bg-white rounded-xl shadow-xl border border-slate-200 w-[400px] overflow-hidden flex flex-col animate-in slide-in-from-bottom-4 duration-300">
                        {/* Modal Header */}
                        <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50">
                            <h3 className="font-bold text-slate-800 text-base">예약 상세 정보</h3>
                            <button
                                onClick={() => setSelectedSchedule(null)}
                                className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        {/* Modal Body */}
                        <div className="p-6">
                            <div className="flex items-center justify-between mb-6">
                                <div className="flex items-center gap-3">
                                    <div className="w-12 h-12 bg-primary/10 rounded-full flex items-center justify-center text-primary">
                                        <User className="w-6 h-6" />
                                    </div>
                                    <div>
                                        <h4 className="text-xl font-bold text-slate-800 flex items-center gap-2">
                                            {selectedSchedule.name}
                                            <span className="text-sm font-medium text-slate-500">{selectedSchedule.age}세</span>
                                        </h4>
                                        <div className="text-sm text-slate-500 flex items-center gap-1.5 mt-0.5">
                                            <Phone className="w-3.5 h-3.5" />
                                            {selectedSchedule.phone}
                                        </div>
                                    </div>
                                </div>

                                <div className="flex flex-col items-end gap-1.5">
                                    <span className={`text-xs font-bold px-2 py-1 rounded ${selectedSchedule.type === '초진' ? 'text-blue-600 bg-blue-50' : 'text-green-600 bg-green-50'}`}>
                                        {selectedSchedule.type}
                                    </span>
                                    <span className={`text-xs font-bold px-2 py-1 rounded ${selectedSchedule.status === '완료' ? 'text-slate-600 bg-slate-100' : selectedSchedule.status === '대기' ? 'text-primary bg-primary/10' : 'text-orange-600 bg-orange-50'}`}>
                                        {selectedSchedule.status}
                                    </span>
                                </div>
                            </div>

                            <div className="bg-slate-50 border border-slate-100 rounded-lg p-4 space-y-3">
                                <div className="flex items-start gap-3">
                                    <CalendarCheck className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
                                    <div className="flex-1">
                                        <div className="text-xs font-bold text-slate-500 mb-0.5">예약 일시</div>
                                        <div className="text-sm font-medium text-slate-800">2023년 08월 08일 (화) <span className="text-primary font-bold ml-1">{formatDisplayTime(selectedSchedule.time)}</span></div>
                                    </div>
                                </div>
                                <div className="flex items-start gap-3">
                                    <Clock className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
                                    <div className="flex-1">
                                        <div className="text-xs font-bold text-slate-500 mb-0.5">내원 목적 및 특이사항</div>
                                        <div className="text-sm font-medium text-slate-800">{selectedSchedule.memo || '목적 미기재'}</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Modal Footer */}
                        <div className="h-16 border-t border-slate-100 flex items-center px-5 gap-3 bg-slate-50/50 mt-auto">
                            <button
                                onClick={() => setSelectedSchedule(null)}
                                className="flex-1 py-2.5 bg-white border border-slate-300 text-slate-700 font-bold rounded-lg hover:bg-slate-50 transition-colors shadow-sm"
                            >
                                닫기
                            </button>
                            <button
                                onClick={() => navigate(`/doctor/consultation/${selectedSchedule.id}`)}
                                className="flex-1 py-2.5 bg-primary border border-transparent text-white font-bold rounded-lg hover:bg-primary/90 transition-colors shadow-sm shadow-primary/20"
                            >
                                {selectedSchedule.status === '완료' ? '기록 보기' : '진료 시작'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

        </div>
    );
};

export default DoctorDashboard;
