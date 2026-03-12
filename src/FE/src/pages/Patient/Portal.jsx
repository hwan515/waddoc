import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, User, Calendar as CalendarIcon, List, LogOut } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import { mockPatientProfile, mockMedicalRecords, mockCalendarEvents } from '../../mockdata/patient';

const PatientPortal = () => {
    const [activeTab, setActiveTab] = useState('list');
    const navigate = useNavigate();
    const logout = useAuthStore(state => state.logout);

    // In real app, we would get `user` from authStore, but we use mockPatientProfile here.
    const user = mockPatientProfile;

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <div className="min-h-screen flex flex-col bg-white">
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
                            onClick={() => setActiveTab('list')}
                            className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-xl transition-all ${activeTab === 'list' ? 'bg-[#0353A4] text-white shadow-md' : 'text-slate-600 hover:bg-slate-100'}`}
                        >
                            <List className="w-4 h-4" />
                            리스트보기
                        </button>
                        <button
                            onClick={() => setActiveTab('calendar')}
                            className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-xl transition-all ${activeTab === 'calendar' ? 'bg-[#0353A4] text-white shadow-md' : 'text-slate-600 hover:bg-slate-100'}`}
                        >
                            <CalendarIcon className="w-4 h-4" />
                            달력보기
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
            <main className="flex-1 w-full max-w-7xl mx-auto p-4 md:p-6 flex flex-col h-full animate-fade-in-up">
                <div className="bg-white border border-slate-200 shadow-sm rounded-3xl flex-1 flex flex-col overflow-hidden relative">
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
        <div className="flex-1 flex flex-col bg-white animate-fade-in relative p-4">
            {/* Calendar Month Navigation - Pulled up to save space */}
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

            {/* Calendar Grid */}
            <div className="flex-1 flex flex-col max-w-5xl mx-auto w-full">
                <div className="grid grid-cols-7 border border-slate-200 rounded-2xl overflow-hidden bg-slate-50 shadow-sm flex-1">
                    {/* Days Header */}
                    {days.map((day, idx) => (
                        <div key={idx} className={`h-10 flex items-center justify-center border-b border-r last:border-r-0 border-slate-200 bg-white text-xs font-bold ${idx === 0 ? 'text-red-500' : idx === 6 ? 'text-blue-500' : 'text-slate-700'}`}>
                            {day}
                        </div>
                    ))}

                    {/* Days Grid */}
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
        <div className="flex-1 flex flex-col p-6 animate-fade-in w-full bg-white">
            <div className="w-full overflow-hidden border border-slate-200 rounded-2xl shadow-sm bg-white">
                <table className="w-full border-collapse">
                    <thead>
                        <tr className="bg-[#0353A4] text-white">
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">진료일</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">진료과목</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">상태</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">담당의</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">처방전 보기</th>
                            <th className="px-6 py-4 text-center text-sm font-bold">소견서 보기</th>
                        </tr>
                    </thead>
                    <tbody>
                        {records.map((r, i) => (
                            <tr key={i} className={`${i % 2 === 0 ? 'bg-white' : 'bg-[#F8FAFC]'} hover:bg-[#B9D6F2]/20 transition-colors border-b border-slate-200 last:border-b-0`}>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <div className="flex flex-col items-center">
                                        <span className="text-sm font-bold text-slate-700">{r.date.replace(/-/g, '/')}</span>
                                        <span className="text-xs font-semibold text-slate-500 mt-0.5">{r.time}</span>
                                    </div>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-[#0353A4]">{r.department}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <div className="flex justify-center">
                                        <span className={`text-xs px-3 py-1.5 rounded-lg font-bold min-w-[60px] shadow-sm ${
                                            r.status === '완료' 
                                            ? 'bg-slate-100 text-slate-600 border border-slate-200' 
                                            : 'bg-green-50 text-green-600 border border-green-200 ring-1 ring-green-100'
                                        }`}>
                                            {r.status}
                                        </span>
                                    </div>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-slate-700">{r.doctorName}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    {r.hasPrescription ? (
                                        <button className="text-sm font-bold text-[#0353A4] hover:bg-[#0353A4]/5 px-3 py-1.5 rounded-lg transition-colors mx-auto">
                                            처방전 보기
                                        </button>
                                    ) : (
                                        <span className="text-sm font-medium text-slate-300">-</span>
                                    )}
                                </td>
                                <td className="px-6 py-4 text-center">
                                    {r.hasNote ? (
                                        <button className="text-sm font-bold text-[#0353A4] hover:bg-[#0353A4]/5 px-3 py-1.5 rounded-lg transition-colors mx-auto">
                                            소견서 보기
                                        </button>
                                    ) : (
                                        <span className="text-sm font-medium text-slate-300">-</span>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

export default PatientPortal;
