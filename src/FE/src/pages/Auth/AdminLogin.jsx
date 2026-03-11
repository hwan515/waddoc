import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, Eye, EyeOff } from 'lucide-react';
import useAuthStore from '../../store/authStore';

const AdminLogin = () => {
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);

    const [formData, setFormData] = useState({
        email: '',
        password: '',
        role: 'operator' // Fixed for admin
    });

    const [showPassword, setShowPassword] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!formData.email || !formData.password) {
            alert('사번(이메일)과 비밀번호를 입력해주세요.');
            return;
        }

        // Auth Store 로그인 (Mock)
        login(formData);

        // 관제 대시보드로 이동
        navigate('/operator/control');
    };

    return (
        <div className="h-screen flex font-sans bg-slate-50 relative overflow-hidden">
            {/* Center Panel - Login Form */}
            <div className="w-full h-full flex items-center justify-center px-4 relative z-10">
                {/* Background Decor */}
                <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-200 rounded-full blur-3xl opacity-20 pointer-events-none" />
                <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-[#0353A4] rounded-full blur-3xl opacity-10 pointer-events-none" />

                <div className="w-full max-w-md bg-white p-10 rounded-3xl shadow-xl shadow-slate-200/50 border border-slate-100 flex flex-col relative z-20">

                    {/* Logo & Headline */}
                    <div className="mb-8 text-center flex flex-col items-center">
                        <div className="flex items-center gap-2 mb-6">
                            <div className="bg-[#0353A4]/10 p-2 rounded-xl">
                                <Activity className="w-6 h-6 text-[#0353A4]" strokeWidth={2.5} />
                            </div>
                            <span className="font-bold text-xl text-slate-800 tracking-tight">
                                Vital<span className="text-[#0353A4]">Connect</span>
                            </span>
                        </div>
                        <h1 className="text-2xl font-bold mb-2 text-slate-900 tracking-tight">운영자 콘솔 로그인</h1>
                        <p className="text-sm text-slate-500 font-medium">관리자 사번(이메일)으로 로그인하세요.</p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        {/* Email Input */}
                        <div>
                            <label className="block text-sm font-bold text-slate-800 mb-2">관리자 이메일</label>
                            <input
                                name="email"
                                type="email"
                                required
                                value={formData.email}
                                onChange={handleChange}
                                className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0353A4] focus:border-transparent transition-all sm:text-sm font-medium bg-slate-50 focus:bg-white"
                                placeholder="admin@vitalconnect.co.kr"
                            />
                        </div>

                        {/* Password Input */}
                        <div>
                            <label className="block text-sm font-bold text-slate-800 mb-2">비밀번호</label>
                            <div className="relative">
                                <input
                                    name="password"
                                    type={showPassword ? "text" : "password"}
                                    required
                                    value={formData.password}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0353A4] focus:border-transparent transition-all sm:text-sm font-medium pr-12 bg-slate-50 focus:bg-white"
                                    placeholder="••••••••"
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-[#0353A4] transition-colors"
                                >
                                    {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                                </button>
                            </div>
                        </div>

                        {/* Submit Button */}
                        <button
                            type="submit"
                            className="w-full py-4 mt-6 bg-[#0353A4] hover:bg-[#061A40] text-white rounded-xl font-bold text-base shadow-lg shadow-[#0353A4]/20 transform hover:-translate-y-0.5 transition-all"
                        >
                            콘솔 입장
                        </button>
                    </form>

                    <div className="mt-8 mb-2 text-center text-sm font-medium flex flex-col gap-2">
                        <span className="text-slate-500">
                            계정이 없으신가요?{' '}
                            <Link to="/admin/signup" className="font-bold text-[#0353A4] hover:underline underline-offset-4">
                                관리자 등록
                            </Link>
                        </span>
                        <div className="pt-4 mt-4 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                            <Link to="/" className="hover:text-slate-600 transition-colors">← 일반 사용자 메인으로</Link>
                            <span>사내 운영 전용 페이지</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default AdminLogin;
