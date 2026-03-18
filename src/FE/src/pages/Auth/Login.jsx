import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, Eye, EyeOff } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';

const Login = () => {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        id: '',
        password: '',
        role: 'patient' // 고정
    });

    const [showPassword, setShowPassword] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    // const handleRoleSelect = (role) => {
    //     setFormData({ ...formData, role });
    // };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!formData.id || !formData.password) {
            alert('아이디와 비밀번호를 입력해주세요.');
            return;
        }

        try {
            const response = await apiClient.post('/auth/login', {
                username: formData.id,
                password: formData.password
            });

            const { accessToken, user } = response.data;
            // 의사/관리자라도 로그인 할 수는 있겠으나 우선 저장
            useAuthStore.getState().setAuth(accessToken, user);
            navigate('/patient/portal');
        } catch (error) {
            console.error('Login Failed:', error);
            alert('로그인에 실패했습니다. 아이디와 비밀번호를 다시 확인해주세요.');
        }
    };

    return (
        <div className="h-screen flex font-sans bg-white relative overflow-hidden">

            {/* Left Panel - Login Form */}
            <div className="w-full lg:w-[45%] h-full flex flex-col px-8 sm:px-16 xl:px-24 py-8 relative z-10 bg-white overflow-y-auto custom-scrollbar">

                {/* Logo */}
                <div className="flex items-center gap-3">
                    <div className="bg-primary/10 p-2 rounded-xl">
                        <Activity className="w-8 h-8 text-primary" strokeWidth={2.5} />
                    </div>
                    <span className="font-bold text-2xl text-dark tracking-tight">
                        Waddoc<span className="text-primary"> 왔닥</span>
                    </span>
                </div>

                {/* Form Container */}
                <div className="w-full max-w-sm mx-auto flex-1 flex flex-col justify-center py-6">
                    <div className="mb-8 text-center lg:text-left">
                        <h1 className="text-3xl font-bold mb-3 text-slate-900 tracking-tight">환영합니다</h1>
                        <p className="text-sm text-slate-500 font-medium">서비스 이용을 위해 계정에 로그인해주세요.</p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">

                        {/* Role Selector Removes (Patient Only) */}

                        {/* ID Input */}
                        <div>
                            <label className="block text-sm font-bold text-slate-800 mb-2">아이디</label>
                            <input
                                name="id"
                                type="text"
                                required
                                value={formData.id}
                                onChange={handleChange}
                                className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-all sm:text-sm font-medium"
                                placeholder="아이디를 입력해주세요"
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
                                    className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-all sm:text-sm font-medium pr-12"
                                    placeholder="모의 테스트용 비밀번호를 입력해주세요"
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-primary transition-colors"
                                >
                                    {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                                </button>
                            </div>
                        </div>

                        {/* Remember & Forgot Password */}
                        <div className="flex items-center justify-between pt-2">
                            <label className="flex items-center text-sm text-slate-500 font-medium cursor-pointer group">
                                <div className="relative flex items-center justify-center w-5 h-5 mr-3 border-2 border-slate-300 rounded group-hover:border-primary transition-colors">
                                    <input type="checkbox" className="opacity-0 absolute inset-0 cursor-pointer peer" />
                                    <div className="peer-checked:bg-primary absolute inset-0 rounded-[2px] transition-colors flex items-center justify-center">
                                        <svg className="w-3.5 h-3.5 text-white opacity-0 peer-checked:opacity-100 transition-opacity" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                        </svg>
                                    </div>
                                </div>
                                로그인 상태 유지
                            </label>
                            <a href="#" className="text-sm text-primary font-bold hover:text-accent-1 transition-colors">비밀번호를 잊으셨나요?</a>
                        </div>

                        {/* Submit Button */}
                        <button
                            type="submit"
                            className="w-full py-4 mt-4 bg-primary hover:bg-accent-1 text-white rounded-xl font-bold text-base shadow-lg shadow-primary/30 transform hover:-translate-y-0.5 transition-all"
                        >
                            로그인
                        </button>

                        {/* Divider */}
                        {/* <div className="relative my-6">
                            <div className="absolute inset-0 flex items-center">
                                <div className="w-full border-t border-slate-200"></div>
                            </div>
                            <div className="relative flex justify-center text-sm font-medium">
                                <span className="px-4 bg-white text-slate-400">또는 다음으로 로그인</span>
                            </div>
                        </div> */}

                        {/* Social Logins */}
                        {/* <div className="grid grid-cols-2 gap-4">
                            <button type="button" className="flex items-center justify-center gap-3 py-3 border-2 border-slate-100 rounded-xl hover:bg-slate-50 transition-colors">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M22.56 12.25C22.56 11.47 22.49 10.71 22.36 9.98H12V14.28H17.92C17.659 15.65 16.89 16.82 15.71 17.61V20.39H19.28C21.36 18.47 22.56 15.63 22.56 12.25Z" fill="#4285F4" />
                                    <path d="M12 23C14.97 23 17.46 22.01 19.28 20.39L15.71 17.61C14.73 18.27 13.47 18.66 12 18.66C9.15002 18.66 6.74002 16.73 5.86002 14.15H2.18002V17.02C4.01002 20.64 7.70002 23 12 23Z" fill="#34A853" />
                                    <path d="M5.85998 14.15C5.62998 13.48 5.49998 12.76 5.49998 12C5.49998 11.24 5.62998 10.52 5.85998 9.85V6.98H2.17998C1.42998 8.48 0.999985 10.19 0.999985 12C0.999985 13.81 1.42998 15.52 2.17998 17.02L5.85998 14.15Z" fill="#FBBC05" />
                                    <path d="M12 5.34C13.62 5.34 15.06 5.9 16.2 6.99L19.35 3.84C17.45 2.08 14.96 1 12 1C7.70002 1 4.01002 3.36 2.18002 6.98L5.86002 9.85C6.74002 7.27 9.15002 5.34 12 5.34Z" fill="#EA4335" />
                                </svg>
                                <span className="text-sm font-bold text-slate-700">Google</span>
                            </button>
                            <button type="button" className="flex items-center justify-center gap-3 py-3 border-2 border-slate-100 rounded-xl hover:bg-slate-50 transition-colors">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M16.5921 7.15243C17.3879 6.18667 17.9252 4.8812 17.7774 3.52C16.634 3.56711 15.2227 4.288 14.3986 5.27581C13.6666 6.13407 13.0602 7.464 13.242 8.78848C14.5085 8.87877 15.8078 8.11475 16.5921 7.15243ZM21.1378 19.344C20.6552 20.768 19.006 23.328 17.4764 23.3524C15.9926 23.3768 15.4984 22.4593 13.7845 22.4593C12.0468 22.4593 11.4883 23.328 10.0519 23.3524C8.61548 23.4013 7.16439 21.0567 5.98144 19.344C3.52628 15.7725 1.83155 10.596 4.35414 7.1044C5.60256 5.38531 7.44297 4.28286 9.42169 4.25844C10.8581 4.234 12.2222 5.23961 13.1162 5.23961C14.0102 5.23961 15.6883 4.016 17.4042 4.016C18.1519 4.016 20.6865 4.088 22.3551 6.55627C22.2073 6.65401 19.3567 8.36531 19.3806 11.6661C19.4046 15.5532 22.7142 16.8247 22.786 16.8736C22.7142 17.0691 22.187 18.9042 21.1378 19.344Z" fill="black" />
                                </svg>
                                <span className="text-sm font-bold text-slate-700">Apple</span>
                            </button>
                        </div> */}
                    </form>

                    <div className="mt-6 mb-4 lg:mb-0 text-center text-sm font-medium">
                        <span className="text-slate-500">계정이 없으신가요? </span>
                        <Link to="/signup" className="font-bold text-primary hover:text-accent-1 hover:underline underline-offset-4">
                            회원가입
                        </Link>
                    </div>
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between text-xs sm:text-sm text-slate-400 font-medium">
                    <span>Copyright © 2026 VitalConnect.</span>
                    <a href="#" className="hover:text-slate-600 transition-colors">개인정보처리방침</a>
                </div>
            </div>

            {/* Right Panel - Highlight Image & Content */}
            <div className="hidden lg:flex w-[55%] bg-primary relative overflow-hidden flex-col items-center justify-center border-l border-white/10">

                {/* Decorative dynamic background elements */}
                <div className="absolute inset-0 w-full h-full opacity-30">
                    <div className="absolute -top-[10%] -left-[10%] w-[500px] h-[500px] bg-secondary rounded-full filter blur-[100px] mix-blend-screen opacity-60"></div>
                    <div className="absolute -bottom-[20%] -right-[10%] w-[600px] h-[600px] bg-accent-1 rounded-full filter blur-[120px] mix-blend-multiply opacity-80"></div>
                </div>

                {/* Diagonal patterned overlay */}
                <div className="absolute inset-0 opacity-[0.03]" style={{ backgroundImage: 'radial-gradient(var(--color-secondary) 1px, transparent 1px)', backgroundSize: '32px 32px' }}></div>

                <div className="relative z-10 w-full max-w-2xl px-12 py-16 flex flex-col h-full">

                    <div className="mt-8 mb-16">
                        <h2 className="text-4xl xl:text-5xl font-bold leading-tight text-white tracking-tight mb-6">
                            환자의 생명을 잇는 <br />원격 관제 시스템
                        </h2>
                        <p className="text-lg xl:text-xl text-secondary/90 max-w-md font-medium">
                            도서·산간 지역에 자율주행 모빌리티를 파견하고, 실시간 환자 생체 데이터를 모니터링합니다.
                        </p>
                    </div>

                    <div className="w-full flex-1 flex flex-col justify-end">
                        {/* Dashboard Mockup Component placeholder */}
                        <div className="w-full bg-white/10 backdrop-blur-xl border border-white/20 rounded-t-3xl shadow-2xl overflow-hidden p-6 sm:p-8 flex flex-col pt-10" style={{ boxShadow: '0 -20px 40px -10px rgba(0,0,0,0.3)' }}>

                            {/* Header mockup */}
                            <div className="flex justify-between items-center mb-8 border-b border-white/10 pb-4">
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center">
                                        <Activity className="w-5 h-5 text-white" />
                                    </div>
                                    <div>
                                        <div className="w-24 h-4 bg-white/30 rounded mb-1"></div>
                                        <div className="w-16 h-3 bg-white/10 rounded"></div>
                                    </div>
                                </div>
                                <div className="w-32 h-8 bg-white/10 rounded-full"></div>
                            </div>

                            {/* Data Cards Mockup */}
                            <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
                                <div className="bg-white/5 rounded-2xl p-4 border border-white/5">
                                    <div className="w-2/3 h-3 bg-white/20 rounded mb-4"></div>
                                    <div className="w-full h-8 bg-white/40 rounded mb-2"></div>
                                    <div className="w-1/2 h-2 bg-green-400/50 rounded"></div>
                                </div>
                                <div className="bg-white/5 rounded-2xl p-4 border border-white/5">
                                    <div className="w-2/3 h-3 bg-white/20 rounded mb-4"></div>
                                    <div className="w-full h-8 bg-white/40 rounded mb-2"></div>
                                    <div className="w-1/2 h-2 bg-yellow-400/50 rounded"></div>
                                </div>
                                <div className="bg-white/5 rounded-2xl p-4 border border-white/5 hidden lg:block">
                                    <div className="w-2/3 h-3 bg-white/20 rounded mb-4"></div>
                                    <div className="w-full h-8 bg-white/40 rounded mb-2"></div>
                                    <div className="w-1/2 h-2 bg-primary/50 rounded"></div>
                                </div>
                            </div>

                            {/* Graph / Chart Mockup */}
                            <div className="flex-1 bg-white/5 rounded-2xl border border-white/5 p-4 flex flex-col justify-end min-h-[160px]">
                                <div className="w-full flex h-full items-end gap-2 px-2">
                                    {[30, 40, 70, 50, 90, 60, 80, 45, 100].map((h, i) => (
                                        <div key={i} className="flex-1 bg-secondary/30 rounded-t border-t border-secondary/50" style={{ height: `${h}%` }}></div>
                                    ))}
                                </div>
                            </div>

                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Login;
