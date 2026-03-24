import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, Eye, EyeOff } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { getHomePathForRole } from '../../utils/authRouting';

const Login = () => {
    const navigate = useNavigate();
    const authenticatedRole = useAuthStore((state) => state.user?.role);

    const [formData, setFormData] = useState({
        id: '',
        password: '',
    });

    const [showPassword, setShowPassword] = useState(false);

    useEffect(() => {
        const destination = getHomePathForRole(authenticatedRole);
        if (destination) {
            navigate(destination, { replace: true });
        }
    }, [authenticatedRole, navigate]);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

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
            const destination = getHomePathForRole(user?.role);

            if (!destination) {
                alert('지원하지 않는 계정 권한입니다. 관리자에게 문의해주세요.');
                return;
            }

            useAuthStore.getState().setAuth(accessToken, user);
            navigate(destination, { replace: true });
        } catch (error) {
            console.error('Login Failed:', error);
            alert('로그인에 실패했습니다. 아이디와 비밀번호를 다시 확인해주세요.');
        }
    };

    return (
        <div className="h-screen flex font-sans bg-white relative overflow-hidden">

            {/* 왼쪽 패널 - 로그인 폼 */}
            <div className="w-full lg:w-[45%] h-full flex flex-col px-8 sm:px-16 xl:px-24 py-8 relative z-10 bg-white overflow-y-auto custom-scrollbar">

                {/* 로고 */}
                <div className="flex items-center gap-3">
                    <div className="bg-primary/10 p-2 rounded-xl">
                        <Activity className="w-8 h-8 text-primary" strokeWidth={2.5} />
                    </div>
                    <span className="font-bold text-2xl text-dark tracking-tight">
                        Waddoc<span className="text-primary"> 왔닥</span>
                    </span>
                </div>

                {/* 폼 컨테이너 */}
                <div className="w-full max-w-sm mx-auto flex-1 flex flex-col justify-center py-6">
                    <div className="mb-8 text-center lg:text-left">
                        <h1 className="text-3xl font-bold mb-3 text-slate-900 tracking-tight">보호자 포털 로그인</h1>
                        <p className="text-sm text-slate-500 font-medium">
                            승인된 보호자 계정으로 환자 포털에 접속하세요.
                        </p>
                        <Link
                            to="/"
                            className="mt-3 inline-flex text-sm font-semibold text-primary transition-colors hover:text-accent-1"
                        >
                            다른 역할 진입 경로 보기
                        </Link>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">

                        {/* 아이디 입력 */}
                        <Input
                            label="아이디"
                            name="id"
                            type="text"
                            required
                            value={formData.id}
                            onChange={handleChange}
                            placeholder="아이디를 입력해주세요"
                        />

                        {/* 비밀번호 입력 */}
                        <Input
                            label="비밀번호"
                            name="password"
                            type={showPassword ? "text" : "password"}
                            required
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="비밀번호를 입력해주세요"
                            iconRight={
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    className="focus:outline-none hover:text-primary transition-colors"
                                >
                                    {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                                </button>
                            }
                        />

                        {/* 로그인 유지 & 비밀번호 찾기 */}
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

                        {/* 로그인 버튼 */}
                        <Button type="submit" fullWidth className="mt-4">
                            포털 로그인
                        </Button>
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

            {/* 오른쪽 패널 - 이미지 및 컨텐츠 */}
            <div className="hidden lg:flex w-[55%] bg-primary relative overflow-hidden flex-col items-center justify-center border-l border-white/10">

                {/* 배경 */}
                <div className="absolute inset-0 w-full h-full opacity-30">
                    <div className="absolute -top-[10%] -left-[10%] w-[500px] h-[500px] bg-secondary rounded-full filter blur-[100px] mix-blend-screen opacity-60"></div>
                    <div className="absolute -bottom-[20%] -right-[10%] w-[600px] h-[600px] bg-accent-1 rounded-full filter blur-[120px] mix-blend-multiply opacity-80"></div>
                </div>

                {/* 대각선 패턴 오버레이 */}
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
