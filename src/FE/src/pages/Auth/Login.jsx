import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';

const Login = () => {
    const navigate = useNavigate();
    const authenticatedRole = useAuthStore((state) => state.user?.role);

    const [formData, setFormData] = useState({
        id: '',
        password: '',
    });

    const [showPassword, setShowPassword] = useState(false);

    useEffect(() => {
        if (authenticatedRole === 'GUARDIAN') {
            navigate('/patient/portal', { replace: true });
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
            if (user?.role !== 'GUARDIAN') {
                useAuthStore.getState().logout();
                try {
                    await apiClient.post(
                        '/auth/logout',
                        {},
                        {
                            headers: {
                                Authorization: `Bearer ${accessToken}`,
                            },
                        }
                    );
                } catch {
                    // Ignore logout cleanup failures before redirecting the user.
                }
                alert('보호자 포털은 보호자 계정으로만 로그인할 수 있습니다.');
                return;
            }

            useAuthStore.getState().setAuth(accessToken, user);
            navigate('/patient/portal', { replace: true });
        } catch {
            alert('로그인에 실패했습니다. 아이디와 비밀번호를 다시 확인해주세요.');
        }
    };

    return (
        <div className="h-screen flex font-sans bg-white relative overflow-hidden">

            {/* 왼쪽 패널 - 로그인 폼 */}
            <div className="w-full lg:w-[45%] h-full flex flex-col px-8 sm:px-16 xl:px-24 py-8 relative z-10 bg-white overflow-y-auto custom-scrollbar">

                {/* 로고 */}
                <Link to="/" className="flex items-center gap-3 w-fit">
                    <img src="/waddoc-badge-primary.svg" alt="Waddoc logo" className="h-12 w-12" />
                    <span className="font-bold text-2xl text-dark tracking-tight">
                        Waddoc<span className="text-primary"> 왔닥</span>
                    </span>
                </Link>

                {/* 폼 컨테이너 */}
                <div className="w-full max-w-sm mx-auto flex-1 flex flex-col justify-center py-6">
                    <div className="mb-8 text-center lg:text-left">
                        <h1 className="text-3xl font-bold mb-3 text-slate-900 tracking-tight">보호자/환자 로그인</h1>
                        <p className="text-sm text-slate-500 font-medium">
                            승인된 보호자 계정으로 환자 포털에 접속하세요.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6" autoComplete="on">

                        {/* 아이디 입력 */}
                        <Input
                            label="아이디"
                            id="guardian-login-username"
                            name="id"
                            type="text"
                            required
                            value={formData.id}
                            onChange={handleChange}
                            placeholder="아이디를 입력해주세요"
                            autoComplete="username"
                            autoCapitalize="none"
                            spellCheck={false}
                        />

                        {/* 비밀번호 입력 */}
                        <Input
                            label="비밀번호"
                            id="guardian-login-password"
                            name="password"
                            type={showPassword ? "text" : "password"}
                            required
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="비밀번호를 입력해주세요"
                            autoComplete="current-password"
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
                    <span>Copyright © 2026 Waddoc.</span>
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
                            가족의 진료 기록을 <br />안전하게 확인하세요
                        </h2>
                        <p className="text-lg xl:text-xl text-secondary/90 max-w-md font-medium">
                            관리자 승인 후 진료 내역과 소견서, 처방전을 <br /> 간편하게 조회할 수 있습니다.
                        </p>
                    </div>

                    <div className="w-full flex-1 flex flex-col justify-end">
                        {/* Dashboard Mockup Component placeholder */}
                        <div className="w-full bg-white/10 backdrop-blur-xl border border-white/20 rounded-t-3xl shadow-2xl overflow-hidden p-6 sm:p-8 flex flex-col pt-10" style={{ boxShadow: '0 -20px 40px -10px rgba(0,0,0,0.3)' }}>

                            {/* Header mockup */}
                            <div className="flex justify-between items-center mb-8 border-b border-white/10 pb-4">
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center p-1.5">
                                        <img src="/waddoc-badge-primary.svg" alt="Waddoc logo" className="h-full w-full" />
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
