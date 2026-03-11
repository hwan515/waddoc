import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, User, Calendar as CalendarIcon, List, LogOut } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import { mockPatientProfile, mockMedicalRecords, mockCalendarEvents } from '../../mockdata/patient';

const PatientPortal = () => {
    const [activeTab, setActiveTab] = useState('calendar');
    const navigate = useNavigate();
    const logout = useAuthStore(state => state.logout);

    // In real app, we would get `user` from authStore, but we use mockPatientProfile here.
    const user = mockPatientProfile;

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <div className="min-h-screen flex flex-col bg-[#FAF9F6]">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 shadow-sm px-6 py-4 flex items-center justify-between sticky top-0 z-50">
                <div className="flex items-center gap-8">
                    {/* Logo */}
                    <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/')}>
                        <div className="bg-[#0353A4]/10 p-1.5 rounded-lg">
                            <Activity className="w-6 h-6 text-[#0353A4]" strokeWidth={2.5} />
                        </div>
                        <span className="font-bold text-xl text-slate-800 tracking-tight">
                            Vital<span className="text-[#0353A4]">Connect</span>
                        </span>
                    </div>

                    {/* Patient Name */}
                    <div className="flex items-center gap-2 px-4 py-1.5 bg-[#F0F4F8] rounded-full">
                        <User className="w-4 h-4 text-[#0353A4]" />
                        <span className="font-semibold text-slate-700">{user.name} 환자님</span>
                    </div>

                    {/* Tabs */}
                    <nav className="flex items-center gap-2">
                        <button
                            onClick={() => setActiveTab('calendar')}
                            className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-xl transition-all ${activeTab === 'calendar' ? 'bg-[#0353A4] text-white shadow-md' : 'text-slate-600 hover:bg-slate-100'}`}
                        >
                            <CalendarIcon className="w-4 h-4" />
                            달력보기
                        </button>
                        <button
                            onClick={() => setActiveTab('list')}
                            className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-xl transition-all ${activeTab === 'list' ? 'bg-[#0353A4] text-white shadow-md' : 'text-slate-600 hover:bg-slate-100'}`}
                        >
                            <List className="w-4 h-4" />
                            리스트보기
                        </button>
                    </nav>
                </div>

                <div className="flex items-center gap-4 border-l border-slate-200 pl-4">
                    <button
                        onClick={() => navigate('/patient/mypage')}
                        className="text-sm font-medium text-slate-600 hover:text-[#0353A4] transition-colors"
                    >
                        마이페이지
                    </button>
                    <div className="w-1 h-1 rounded-full bg-slate-300 mx-2"></div>
                    <button onClick={handleLogout} className="flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-red-500 transition-colors">
                        <LogOut className="w-4 h-4" />
                        로그아웃
                    </button>
                </div>
            </header>

            {/* Main Content Area */}
            <main className="flex-1 w-full max-w-7xl mx-auto p-4 md:p-8 flex flex-col h-full animate-fade-in-up">
                <div className="bg-white border border-slate-200 shadow-sm rounded-3xl flex-1 flex flex-col overflow-hidden relative min-h-[600px]">
                    {/* Background acccent line */}
                    <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[#0353A4]/20 via-[#B9D6F2] to-[#B9D6F2]/30 z-10"></div>

                    {activeTab === 'calendar' ? (
                        <CalendarView records={mockCalendarEvents} />
                    ) : (
                        <ListView records={mockMedicalRecords} />
                    )}
                </div>
            </main>
        </div>
    );
};

// Component for Calendar View
const CalendarView = ({ records }) => {
    // Basic calendar logic for August 2023 (to match mock data)
    const [currentDate, setCurrentDate] = useState(new Date(2023, 7, 1)); // Month is 0-indexed (7 = Aug)

    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const firstDayIndex = new Date(year, month, 1).getDay();

    const handlePrevMonth = () => setCurrentDate(new Date(year, month - 1, 1));
    const handleNextMonth = () => setCurrentDate(new Date(year, month + 1, 1));

    const days = ['일', '월', '화', '수', '목', '금', '토'];

    // Generate grid cells
    const gridCells = [];
    for (let i = 0; i < firstDayIndex; i++) {
        gridCells.push({ empty: true });
    }
    for (let i = 1; i <= daysInMonth; i++) {
        const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
        const dayRecords = records.filter(r => r.date === dateStr);
        gridCells.push({ empty: false, day: i, dateStr, records: dayRecords });
    }
    // Fill remaining cells to complete the last row
    const totalCells = gridCells.length;
    const remainingCells = (7 - (totalCells % 7)) % 7;
    for (let i = 0; i < remainingCells; i++) {
        gridCells.push({ empty: true });
    }

    return (
        <div className="flex-1 flex flex-col bg-white animate-fade-in relative pt-12 p-8">
            {/* Calendar Header */}
            <div className="flex flex-col items-center justify-center mb-8">
                <div className="flex items-center gap-2 mb-2">
                    <CalendarIcon className="w-6 h-6 text-[#0353A4]" />
                    <h2 className="text-2xl font-extrabold text-slate-800 tracking-tight">예약 및 진료 일정</h2>
                </div>

                <div className="flex items-center justify-center gap-4 mt-2">
                    <button onClick={handlePrevMonth} className="p-2 hover:bg-slate-100 rounded-full transition-colors text-slate-500 hover:text-slate-800">
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
                    </button>
                    <h2 className="text-xl font-bold text-slate-800 mx-2 tracking-tight w-32 text-center">
                        {year}년 {String(month + 1).padStart(2, '0')}월
                    </h2>
                    <button onClick={handleNextMonth} className="p-2 hover:bg-slate-100 rounded-full transition-colors text-slate-500 hover:text-slate-800">
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" /></svg>
                    </button>
                </div>
            </div>

            {/* Calendar Grid */}
            <div className="flex-1 flex flex-col max-w-5xl mx-auto w-full">
                <div className="grid grid-cols-7 border border-slate-200 rounded-2xl overflow-hidden bg-slate-50 shadow-sm flex-1">
                    {/* Days Header */}
                    {days.map((day, idx) => (
                        <div key={idx} className={`h-12 flex items-center justify-center border-b border-r last:border-r-0 border-slate-200 bg-white text-sm font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                            {day}
                        </div>
                    ))}

                    {/* Days Grid */}
                    {gridCells.map((cell, idx) => (
                        <div key={idx} className={`min-h-[120px] border-b border-r border-slate-200 p-2.5 flex flex-col transition-colors ${cell.empty ? 'bg-slate-50/50' : 'bg-white hover:bg-slate-50/50'} ${(idx + 1) % 7 === 0 ? 'border-r-0' : ''}`}>
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

            <style dangerouslySetInnerHTML={{
                __html: `
                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(10px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in { animation: fadeIn 0.4s ease-out forwards; }
                @keyframes fadeInUp {
                    from { opacity: 0; transform: translateY(15px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in-up { animation: fadeInUp 0.5s ease-out forwards; }
            `}} />
        </div>
    );
};

// Component for List View
const ListView = ({ records }) => {
    return (
        <div className="flex-1 flex flex-col items-center justify-start p-8 animate-fade-in w-full bg-white pt-12">
            <List className="w-12 h-12 text-[#0353A4] mb-4" />
            <h2 className="text-2xl font-extrabold text-slate-800 mb-2 tracking-tight">상세 진료 기록</h2>
            <p className="text-slate-500 font-medium text-sm mb-10">환자님의 지난 진료 내역과 다가오는 일정을 리스트 형태로 확인하세요.</p>

            {/* Mock list items */}
            <div className="w-full max-w-4xl space-y-4">
                {records.map((r, i) => (
                    <div key={i} className="bg-white p-5 rounded-2xl border border-slate-200 shadow-[0_2px_10px_rgba(0,0,0,0.03)] flex items-center justify-between hover:border-[#0353A4]/30 hover:shadow-md transition-all group">
                        <div className="flex items-center gap-6">
                            <div className="flex flex-col items-center justify-center bg-slate-50 px-4 py-2 rounded-xl border border-slate-100 group-hover:bg-[#0353A4]/5 group-hover:border-[#0353A4]/10 transition-colors">
                                <span className="text-sm font-bold text-slate-400 mb-0.5">{r.date.substring(0, 4)}</span>
                                <span className="font-extrabold text-[#0353A4] text-xl leading-none">{r.date.substring(5).replace('-', '.')}</span>
                            </div>
                            <div className="flex flex-col">
                                <span className="font-bold text-slate-800 text-lg flex items-center gap-2">
                                    {r.department} 진료
                                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${r.status === '완료' ? 'bg-slate-100 text-slate-600' : 'bg-[#0353A4]/10 text-[#0353A4]'}`}>
                                        {r.status}
                                    </span>
                                </span>
                                <div className="flex items-center gap-3 text-sm text-slate-500 font-medium mt-1">
                                    <span className="flex items-center gap-1"><Activity className="w-3.5 h-3.5" /> {r.doctorName} 전문의</span>
                                    <span className="w-1 h-1 rounded-full bg-slate-300"></span>
                                    <span>{r.time}</span>
                                </div>
                            </div>
                        </div>
                        <div className="flex gap-2">
                            {r.hasPrescription && (
                                <button className="px-4 py-2 bg-[#0353A4]/10 text-[#0353A4] font-bold rounded-xl text-sm border border-[#0353A4]/20 hover:bg-[#0353A4]/20 transition-colors">처방전 보기</button>
                            )}
                            {r.hasNote && (
                                <button className="px-4 py-2 bg-slate-50 text-slate-600 font-bold rounded-xl text-sm border border-slate-200 hover:bg-slate-100 transition-colors">의사소견서 보기</button>
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default PatientPortal;
