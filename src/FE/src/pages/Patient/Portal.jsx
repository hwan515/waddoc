import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, User, Calendar as CalendarIcon, List, LogOut } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import CalendarView from '../../components/patient/CalendarView';
import ListView from '../../components/patient/ListView';
import { parsePrescriptionNote } from '../../utils/prescriptionNote';

const PatientPortal = () => {
    const [activeTab, setActiveTab] = useState('list');
    const navigate = useNavigate();
    const logout = useAuthStore(state => state.logout);

    // 초기 상태에서 이름은 하드코딩된 '이름' 대신 임시 텍스트 사용
    const [user, setUser] = useState({ name: '불러오는 중...', stats: { totalVisits: 0, nextReservation: '없음' } });
    const [medicalRecords, setMedicalRecords] = useState([]);
    const [calendarEvents, setCalendarEvents] = useState([]);

    useEffect(() => {
        const fetchPatientData = async () => {
            try {
                // 보호자 API: 로그인된 보호자 계정에 연동된 환자 목록 조회
                const patientsRes = await apiClient.get('/guardians/patients');
                const patients = patientsRes.data.patients || [];

                if (patients.length > 0) {
                    const primaryPatient = patients[0];

                    // 환자의 진료 기록(요약) 조회
                    const summariesRes = await apiClient.get(`/guardians/patients/${primaryPatient.patientId}/summaries`);
                    const summaries = summariesRes.data.summaries || [];
                    
                    console.log("[Portal] 진료 기록(summaries) API 응답 확인:", summariesRes.data);

                    setUser({
                        ...primaryPatient,
                        stats: {
                            totalVisits: summariesRes.data.totalCount || summaries.length,
                            nextReservation: summaries.length > 0 ? '없음' : '예약 없음'
                        }
                    });

                    // 리스트 뷰 & 캘린더 뷰 포맷으로 파싱
                    const mappedRecords = summaries.map((s, index) => {
                        const parsedPrescription = parsePrescriptionNote(s.prescriptionNote);

                        return {
                            id: s.caseId || index,
                            date: s.consultationDate || '',
                            time: '-', // API 명세상 시간은 제공되지 않으므로 임시 대시
                            doctorName: s.doctorName,
                            department: s.departmentName,
                            status: '완료', // summaries API는 완료된 것만 내려줌
                            hasPrescription: Boolean(s.isPrescriptionIssued || parsedPrescription.hasData),
                            hasNote: !!s.summaryNote,
                            summaryNote: s.summaryNote,
                            prescriptionNote: s.prescriptionNote,
                            prescription: parsedPrescription,
                        };
                    });

                    const mappedEvents = summaries.map((s, index) => ({
                        id: s.caseId || index,
                        title: `${s.departmentName} 진료`,
                        date: s.consultationDate, // ex) '2026-03-11'
                        doctor: s.doctorName,
                        type: 'past'
                    }));

                    setMedicalRecords(mappedRecords);
                    setCalendarEvents(mappedEvents);
                } else {
                    setUser((currentUser) => ({
                        ...currentUser,
                        name: '연결된 환자 없음',
                        stats: { totalVisits: 0, nextReservation: '없음' },
                    }));
                }
            } catch (error) {
                console.error("Failed to fetch guardian patient data:", error);
                if (error.response && error.response.status === 401) {
                    console.error("401 Unauthorized: 로그인된 계정이 '보호자(GUARDIAN)' 권한이 없거나 권한 승인이 대기 상태일 수 있습니다.");
                }
                // 에러 발생 시 초기 상태로 대기
                setMedicalRecords([]);
                setCalendarEvents([]);
            }
        };

        if (useAuthStore.getState().user) {
            fetchPatientData();
        }
    }, []);

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
                            Waddoc<span className="text-[#0353A4]"> 왔닥</span>
                        </span>
                    </div>

                    {/* Patient Name */}
                    <div className="flex items-center gap-2 px-4 py-1.5 bg-[#F0F4F8] rounded-full">
                        <User className="w-4 h-4 text-[#0353A4]" />
                        <span className="font-semibold text-slate-700">
                            {user.name === '불러오는 중...' || user.name === '연결된 환자 없음' ? user.name : `${user.name} 환자님`}
                        </span>
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
                        <CalendarView records={calendarEvents} patientName={user.name} />
                    ) : (
                        <ListView records={medicalRecords} patientName={user.name} />
                    )}
                </div>
            </main>
        </div>
    );
};

export default PatientPortal;
