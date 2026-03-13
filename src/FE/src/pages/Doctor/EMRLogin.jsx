import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, ShieldCheck } from 'lucide-react';
import useAuthStore from '../../store/authStore';

const EMRLogin = () => {
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);

    const [formData, setFormData] = useState({
        medId: '',
        password: '',
        role: 'doctor' // EMR is exclusively for doctors
    });

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!formData.medId || !formData.password) {
            alert('사번과 비밀번호를 입력해주세요.');
            return;
        }

        // Mock Login
        login({ email: formData.medId, role: formData.role });
        navigate('/emr/dashboard');
    };

    return (
        <div className="h-screen bg-slate-50 flex items-center justify-center p-4">
            <div className="max-w-md w-full">

                {/* Header Section */}
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-[#0353A4] mb-4 shadow-lg shadow-blue-900/20">
                        <Activity className="w-8 h-8 text-white" />
                    </div>
                    <h1 className="text-2xl font-bold text-slate-800 tracking-tight">MediCloud EMR</h1>
                    <p className="text-slate-500 mt-2 text-sm">통합 전자의무기록 시스템 로그인</p>
                </div>

                {/* Login Card */}
                <div className="bg-white rounded-2xl shadow-xl shadow-slate-200/50 p-8 border border-slate-100">
                    <form onSubmit={handleSubmit} className="space-y-6">

                        <div>
                            <label className="block text-sm font-semibold text-slate-700 mb-2">의료진 사번 (ID)</label>
                            <input
                                name="medId"
                                type="text"
                                required
                                value={formData.medId}
                                onChange={handleChange}
                                className="w-full px-4 py-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-[#0353A4] focus:border-[#0353A4] transition-all bg-slate-50 focus:bg-white"
                                placeholder="사번을 입력하세요 (예: D10023)"
                            />
                        </div>

                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <label className="block text-sm font-semibold text-slate-700">비밀번호</label>
                                <a href="#" className="text-sm font-semibold text-[#0353A4] hover:underline">비밀번호 찾기</a>
                            </div>
                            <input
                                name="password"
                                type="password"
                                required
                                value={formData.password}
                                onChange={handleChange}
                                className="w-full px-4 py-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-[#0353A4] focus:border-[#0353A4] transition-all bg-slate-50 focus:bg-white"
                                placeholder="비밀번호를 입력하세요"
                            />
                        </div>

                        <div className="flex items-center gap-2 text-sm text-slate-600 bg-blue-50/50 p-3 rounded-lg border border-blue-100">
                            <ShieldCheck className="w-4 h-4 text-[#0353A4]" />
                            <span>의료법에 의거, 비인가자의 접근은 처벌받을 수 있습니다.</span>
                        </div>

                        <button
                            type="submit"
                            className="w-full py-3.5 bg-[#0353A4] hover:bg-blue-800 text-white font-bold rounded-xl transition-colors shadow-md shadow-blue-900/20"
                        >
                            로그인
                        </button>
                    </form>
                </div>

                <div className="text-center mt-8 text-sm text-slate-400 font-medium whitespace-pre-line">
                    ⓒ 2026 MediCloud Healthcare Solutions. All Rights Reserved.
                </div>
            </div>
        </div>
    );
};

export default EMRLogin;
