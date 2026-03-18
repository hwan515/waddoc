import { useState } from 'react';

const CalendarView = ({ records }) => {
    // 달력의 기본값을 현재 연도와 월로 설정
    const [currentDate, setCurrentDate] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1));

    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const firstDayIndex = new Date(year, month, 1).getDay();

    const handlePrevMonth = () => setCurrentDate(new Date(year, month - 1, 1));
    const handleNextMonth = () => setCurrentDate(new Date(year, month + 1, 1));

    const days = ['일', '월', '화', '수', '목', '금', '토'];

    // 그리드 만들기
    const gridCells = [];
    for (let i = 0; i < firstDayIndex; i++) {
        gridCells.push({ empty: true });
    }
    for (let i = 1; i <= daysInMonth; i++) {
        const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
        const dayRecords = records.filter(r => r.date === dateStr);
        gridCells.push({ empty: false, day: i, dateStr, records: dayRecords });
    }
    // 마지막 행을 채우기 위해 남은 셀을 채움
    const totalCells = gridCells.length;
    const remainingCells = (7 - (totalCells % 7)) % 7;
    for (let i = 0; i < remainingCells; i++) {
        gridCells.push({ empty: true });
    }

    return (
        <div className="flex-1 flex flex-col bg-white relative p-4">
            {/* 달력 월 네비게이터 */}
            <div className="flex items-center justify-center gap-4 mb-4">
                <button onClick={handlePrevMonth} className="p-1.5 hover:bg-slate-100 rounded-full transition-colors text-slate-500 hover:text-slate-800">
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
                </button>
                <h2 className="text-xl font-bold text-slate-800 mx-1 tracking-tight w-32 text-center">
                    {year}년 {String(month + 1).padStart(2, '0')}월
                </h2>
                <button onClick={handleNextMonth} className="p-1.5 hover:bg-slate-100 rounded-full transition-colors text-slate-500 hover:text-slate-800">
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" /></svg>
                </button>
            </div>

            {/* 달력 그리드 */}
            <div className="flex-1 flex flex-col max-w-5xl mx-auto w-full">
                <div className="grid grid-cols-7 border border-slate-200 rounded-2xl overflow-hidden bg-slate-50 shadow-sm flex-1">
                    {/* 요일 헤더 */}
                    {days.map((day, idx) => (
                        <div key={idx} className={`h-10 flex items-center justify-center border-b border-r last:border-r-0 border-slate-200 bg-white text-xs font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                            {day}
                        </div>
                    ))}

                    {/* 날짜 그리드 */}
                    {gridCells.map((cell, idx) => (
                        <div key={idx} className={`min-h-[100px] border-b border-r border-slate-200 p-2 flex flex-col transition-colors ${cell.empty ? 'bg-slate-50/50' : 'bg-white hover:bg-slate-50/50'} ${(idx + 1) % 7 === 0 ? 'border-r-0' : ''}`}>
                            {!cell.empty && (
                                <>
                                    <span className={`text-sm font-semibold mb-2 ml-1 ${idx % 7 === 0 ? 'text-red-500' : idx % 7 === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                                        {cell.day}
                                    </span>
                                    <div className="flex flex-col gap-1.5 mt-auto mb-auto">
                                        {cell.records.map((r, i) => (
                                            <div key={i} className={`text-xs font-bold px-2.5 py-1.5 rounded-lg truncate border-l-4 shadow-sm ${r.type === 'upcoming' ? 'bg-[#0353A4]/10 text-[#0353A4] border-[#0353A4]' : 'bg-white border-slate-300 text-slate-600 ring-1 ring-slate-200'}`}>
                                                {r.title}
                                            </div>
                                        ))}
                                    </div>
                                </>
                            )}
                        </div>
                    ))}
                </div>
            </div>

        </div>
    );
};

export default CalendarView;
