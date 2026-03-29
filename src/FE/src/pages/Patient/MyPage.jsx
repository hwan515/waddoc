import { useNavigate } from 'react-router-dom';
import { Activity, User, LogOut, Phone, MapPin, HeartPulse, Stethoscope } from 'lucide-react';
import { useState, useEffect } from 'react';
import apiClient from '../../utils/api';
import { logoutSession } from '../../utils/logout';

const MyPage = () => {
    const navigate = useNavigate();

    const [user, setUser] = useState({ 
        name: '로딩중', phone: '-', address: '-', birthDate: '-', createdAt: '-', referenceImagePath: null
    });
    const [totalVisits, setTotalVisits] = useState(0);
    const [mostVisitedDoctor, setMostVisitedDoctor] = useState('기록 없음');

    useEffect(() => {
        const fetchMyPageData = async () => {
            try {
                const patientsRes = await apiClient.get('/guardians/patients');
                const patients = patientsRes.data.patients || [];
                
                if (patients.length > 0) {
                    const primaryPatient = patients[0];
                    // 전화번호 포맷 정규식 (010-0000-0000)
                    const formatPhoneNumber = (phoneNumberString) => {
                        if (!phoneNumberString) return '-';
                        const cleaned = ('' + phoneNumberString).replace(/\D/g, '');
                        const match = cleaned.match(/^(\d{3})(\d{3,4})(\d{4})$/);
                        if (match) return `${match[1]}-${match[2]}-${match[3]}`;
                        return phoneNumberString;
                    };

                    setUser({
                        ...primaryPatient,
                        phone: formatPhoneNumber(primaryPatient.phone), 
                        address: primaryPatient.address || '-',
                        birthDate: primaryPatient.birthDate || primaryPatient.birthDate6 || '-',
                        createdAt: primaryPatient.approvedAt || primaryPatient.createdAt || '-',
                        referenceImagePath: primaryPatient.referenceImagePath || primaryPatient.reference_image_path || null
                    });

                    // 진료 요약 조회하여 통계 계산
                    const summariesRes = await apiClient.get(`/guardians/patients/${primaryPatient.patientId}/summaries`);
                    const summaries = summariesRes.data.summaries || [];
                    
                    setTotalVisits(summaries.length);
                    
                    const doctorCounts = summaries.reduce((acc, record) => {
                        acc[record.doctorName] = (acc[record.doctorName] || 0) + 1;
                        return acc;
                    }, {});

                    if (Object.keys(doctorCounts).length > 0) {
                        const topDoctor = Object.keys(doctorCounts).reduce((a, b) => doctorCounts[a] > doctorCounts[b] ? a : b);
                        setMostVisitedDoctor(topDoctor);
                    }
                }
            } catch {
                // Keep the default profile state when the fetch fails.
            }
        };

        fetchMyPageData();
    }, []);

    const handleLogout = async () => {
        await logoutSession();
        navigate('/');
    };

    return (
        <div className="min-h-screen flex flex-col bg-slate-50 font-sans">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 shadow-sm px-6 py-4 flex items-center justify-between sticky top-0 z-50">
                <div className="flex items-center gap-8">
                    {/* Logo */}
                    <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/patient/portal')}>
                        <img src="/waddoc-badge-primary.svg" alt="Waddoc logo" className="h-10 w-10" />
                        <span className="font-bold text-xl text-slate-800 tracking-tight">
                            Waddoc<span className="text-primary"> 왔닥</span>
                        </span>
                    </div>

                    {/* Patient Name */}
                    <div className="flex items-center gap-2 px-4 py-1.5 bg-slate-100 rounded-full">
                        <User className="w-4 h-4 text-primary" />
                        <span className="font-semibold text-slate-700">
                            {user.name === '로딩중' || user.name === '연결된 환자 없음' ? user.name : `${user.name} 환자님`}
                        </span>
                    </div>
                </div>

                <div className="flex items-center gap-4 border-l border-slate-200 pl-4">
                    <button
                        onClick={() => navigate('/patient/portal')}
                        className="text-sm font-medium text-slate-600 hover:text-primary transition-colors"
                    >
                        진료 내역
                    </button>
                    <div className="w-1 h-1 rounded-full bg-slate-300 mx-2"></div>
                    <button onClick={handleLogout} className="flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-red-500 transition-colors">
                        <LogOut className="w-4 h-4" />
                        로그아웃
                    </button>
                </div>
            </header>

            {/* Main Content Areas */}
            <main className="flex-1 w-full max-w-4xl mx-auto p-4 md:p-8 flex flex-col gap-6 animate-fade-in-up">

                {/* 1. Profile Overview Card */}
                <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-200/60 relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-64 h-64 bg-linear-to-bl from-primary/5 to-transparent rounded-full -mr-20 -mt-20 pointer-events-none"></div>

                    <div className="flex flex-col md:flex-row items-center md:items-start gap-8 relative z-10">
                        <div className="w-24 h-24 bg-primary/10 rounded-full flex items-center justify-center border-4 border-white shadow-md flex-shrink-0 overflow-hidden">
                            {user.referenceImagePath ? (
                                <img src={user.referenceImagePath} alt="Patient Profile" className="w-full h-full object-cover" />
                            ) : (
                                <User className="w-10 h-10 text-primary" />
                            )}
                        </div>

                        <div className="flex-1 text-center md:text-left">
                            <h1 className="text-3xl font-extrabold text-slate-800 tracking-tight mb-2">
                                {user.name} <span className="text-xl text-slate-500 font-medium ml-1">환자님</span>
                            </h1>
                            <div className="flex flex-wrap items-center justify-center md:justify-start gap-3 mt-4">
                                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-100 text-sm font-medium text-slate-600">
                                    <Phone className="w-4 h-4 text-slate-400" />
                                    {user.phone}
                                </span>
                                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-100 text-sm font-medium text-slate-600">
                                    <MapPin className="w-4 h-4 text-slate-400" />
                                    {user.address}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* 2. Medical Stats / Detailed Info */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

                    {/* Activity Stats */}
                    <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-200/60">
                        <h2 className="text-lg font-bold text-slate-800 mb-6 flex items-center gap-2 border-b border-slate-100 pb-4">
                            <Activity className="w-5 h-5 text-primary" />
                            나의 병원 진료 요약
                        </h2>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="bg-slate-100 rounded-2xl p-6 flex flex-col items-center justify-center text-center transition-transform hover:-translate-y-1">
                                <div className="p-3 bg-white rounded-xl shadow-sm mb-4">
                                    <HeartPulse className="w-6 h-6 text-primary" />
                                </div>
                                <span className="text-sm font-medium text-slate-500 mb-1">총 진료 횟수</span>
                                <div className="text-3xl font-extrabold text-primary tracking-tight">{totalVisits}<span className="text-lg text-slate-400 font-medium ml-1">회</span></div>
                            </div>

                            <div className="bg-orange-50 rounded-2xl p-6 flex flex-col items-center justify-center text-center transition-transform hover:-translate-y-1">
                                <div className="p-3 bg-white rounded-xl shadow-sm mb-4">
                                    <Stethoscope className="w-6 h-6 text-orange-500" />
                                </div>
                                <span className="text-sm font-medium text-slate-500 mb-1">주 진료 의사</span>
                                <div className="text-2xl font-extrabold text-slate-800 tracking-tight mt-1">{mostVisitedDoctor}</div>
                            </div>
                        </div>
                    </div>

                    {/* Basic Patient Details */}
                    <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-200/60">
                        <h2 className="text-lg font-bold text-slate-800 mb-6 flex items-center gap-2 border-b border-slate-100 pb-4">
                            <User className="w-5 h-5 text-primary" />
                            기본 정보
                        </h2>

                        <div className="space-y-5">
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">생년월일</span>
                                <span className="font-bold text-slate-800">{user.birthDate}</span>
                            </div>
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">연락처</span>
                                <span className="font-bold text-slate-800">{user.phone}</span>
                            </div>
                            <div className="flex items-start justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium whitespace-nowrap mr-4">주소</span>
                                <span className="font-bold text-slate-800 text-right break-keep">{user.address}</span>
                            </div>
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">가입일(승인일)</span>
                                <span className="font-bold text-slate-800">{user.createdAt}</span>
                            </div>
                        </div>
                    </div>

                </div>
            </main>

            <style dangerouslySetInnerHTML={{
                __html: `
                @keyframes fadeInUp {
                    from { opacity: 0; transform: translateY(15px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in-up { animation: fadeInUp 0.5s ease-out forwards; }
            `}} />
        </div>
    );
};

export default MyPage;
