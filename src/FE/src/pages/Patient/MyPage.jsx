import { useNavigate } from 'react-router-dom';
import { Activity, User, LogOut, Phone, MapPin, HeartPulse, Stethoscope, ChevronLeft } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import { mockPatientProfile, mockMedicalRecords } from '../../mockdata/patient';

const MyPage = () => {
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);

    const user = mockPatientProfile;

    // 통계 계산
    const totalVisits = mockMedicalRecords.filter(r => r.status === '완료').length;

    // 가장 많이 진료를 본 의사 계산
    const doctorCounts = mockMedicalRecords.reduce((acc, record) => {
        if (record.status === '완료') {
            acc[record.doctorName] = (acc[record.doctorName] || 0) + 1;
        }
        return acc;
    }, {});

    // 가장 많이 방문한 의사명 찾기
    const mostVisitedDoctor = Object.keys(doctorCounts).reduce((a, b) => doctorCounts[a] > doctorCounts[b] ? a : b, '기록 없음');

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <div className="min-h-screen flex flex-col bg-[#FAF9F6] font-sans">
            {/* Header (공통 스타일 유지, 뒤로가기 버튼 추가) */}
            <header className="bg-white border-b border-slate-200 shadow-sm px-6 py-4 flex items-center justify-between sticky top-0 z-50">
                <div className="flex items-center gap-6">
                    {/* Back Button */}
                    <button
                        onClick={() => navigate(-1)}
                        className="p-2 -ml-2 rounded-xl text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition-colors"
                        title="뒤로 가기"
                    >
                        <ChevronLeft className="w-6 h-6" />
                    </button>

                    {/* Logo */}
                    <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/patient/portal')}>
                        <div className="bg-[#0353A4]/10 p-1.5 rounded-lg">
                            <Activity className="w-6 h-6 text-[#0353A4]" strokeWidth={2.5} />
                        </div>
                        <span className="font-bold text-xl text-slate-800 tracking-tight hidden sm:block">
                            Vital<span className="text-[#0353A4]">Connect</span>
                        </span>
                    </div>

                    <div className="h-6 w-px bg-slate-200 hidden sm:block"></div>
                    <span className="font-bold text-slate-800 hidden sm:block">마이페이지</span>
                </div>

                <div className="flex items-center gap-4">
                    <button onClick={handleLogout} className="flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-red-500 transition-colors px-3 py-1.5 rounded-lg hover:bg-red-50">
                        <LogOut className="w-4 h-4" />
                        로그아웃
                    </button>
                </div>
            </header>

            {/* Main Content Areas */}
            <main className="flex-1 w-full max-w-4xl mx-auto p-4 md:p-8 flex flex-col gap-6 animate-fade-in-up">

                {/* 1. Profile Overview Card */}
                <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-200/60 relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-[#0353A4]/5 to-transparent rounded-full -mr-20 -mt-20 pointer-events-none"></div>

                    <div className="flex flex-col md:flex-row items-center md:items-start gap-8 relative z-10">
                        <div className="w-24 h-24 bg-[#0353A4]/10 rounded-full flex items-center justify-center border-4 border-white shadow-md flex-shrink-0">
                            <User className="w-10 h-10 text-[#0353A4]" />
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
                            <Activity className="w-5 h-5 text-[#0353A4]" />
                            나의 병원 진료 요약
                        </h2>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="bg-[#F0F4F8] rounded-2xl p-6 flex flex-col items-center justify-center text-center transition-transform hover:-translate-y-1">
                                <div className="p-3 bg-white rounded-xl shadow-sm mb-4">
                                    <HeartPulse className="w-6 h-6 text-[#0353A4]" />
                                </div>
                                <span className="text-sm font-medium text-slate-500 mb-1">총 진료 횟수</span>
                                <div className="text-3xl font-extrabold text-[#0353A4] tracking-tight">{totalVisits}<span className="text-lg text-slate-400 font-medium ml-1">회</span></div>
                            </div>

                            <div className="bg-[#FDF4EE] rounded-2xl p-6 flex flex-col items-center justify-center text-center transition-transform hover:-translate-y-1">
                                <div className="p-3 bg-white rounded-xl shadow-sm mb-4">
                                    <Stethoscope className="w-6 h-6 text-[#D97757]" />
                                </div>
                                <span className="text-sm font-medium text-slate-500 mb-1">주 진료 의사</span>
                                <div className="text-2xl font-extrabold text-slate-800 tracking-tight mt-1">{mostVisitedDoctor}</div>
                            </div>
                        </div>
                    </div>

                    {/* Medical Details */}
                    <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-200/60">
                        <h2 className="text-lg font-bold text-slate-800 mb-6 flex items-center gap-2 border-b border-slate-100 pb-4">
                            <User className="w-5 h-5 text-[#0353A4]" />
                            상세 건강 정보
                        </h2>

                        <div className="space-y-5">
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">연령/성별</span>
                                <span className="font-bold text-slate-800">{user.age}세 / {user.gender === 'M' ? '남성' : '여성'}</span>
                            </div>
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">혈액형</span>
                                <span className="font-bold text-red-500 bg-red-50 px-3 py-1 rounded-lg">{user.bloodType}</span>
                            </div>
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">알레르기 보유</span>
                                <div className="flex gap-2 text-right">
                                    {user.allergies.length > 0
                                        ? user.allergies.map((a, i) => <span key={i} className="font-semibold text-slate-700 bg-slate-100 px-3 py-1 rounded-lg text-sm">{a}</span>)
                                        : <span className="font-medium text-slate-400">없음</span>
                                    }
                                </div>
                            </div>
                            <div className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors">
                                <span className="text-slate-500 font-medium">만성 질환</span>
                                <div className="flex gap-2 text-right">
                                    {user.chronicDiseases.length > 0
                                        ? user.chronicDiseases.map((a, i) => <span key={i} className="font-semibold text-slate-700 bg-slate-100 px-3 py-1 rounded-lg text-sm">{a}</span>)
                                        : <span className="font-medium text-slate-400">없음</span>
                                    }
                                </div>
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
