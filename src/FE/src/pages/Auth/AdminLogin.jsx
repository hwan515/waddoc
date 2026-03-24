import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, Eye, EyeOff } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import apiClient from '../../utils/api';
import { getHomePathForRole } from '../../utils/authRouting';

const AdminLogin = () => {
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
            alert('사번(아이디)과 비밀번호를 입력해주세요.');
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
        <div className="h-screen flex font-sans bg-slate-50 relative overflow-hidden">
            {/* 로그인폼 */}
            <div className="w-full h-full flex items-center justify-center px-4 relative z-10">
                {/* 배경 */}
                <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-200 rounded-full blur-3xl opacity-20 pointer-events-none" />
                <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-primary rounded-full blur-3xl opacity-10 pointer-events-none" />

                <div className="w-full max-w-md bg-white p-10 rounded-3xl shadow-xl shadow-slate-200/50 border border-slate-100 flex flex-col relative z-20">

                    {/* 로고 및 헤드라인 */}
                    <div className="mb-8 text-center flex flex-col items-center">
                        <div className="flex items-center gap-2 mb-6">
                            <div className="bg-primary/10 p-2 rounded-xl">
                                <Activity className="w-6 h-6 text-primary" strokeWidth={2.5} />
                            </div>
                            <span className="font-bold text-xl text-slate-800 tracking-tight">
                                Waddoc<span className="text-primary"> 왔닥</span>
                            </span>
                        </div>
                        <h1 className="text-2xl font-bold mb-2 text-slate-900 tracking-tight">운영자 콘솔 로그인</h1>
                        <p className="text-sm text-slate-500 font-medium">관리자 사번(아이디)으로 로그인하세요.</p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        {/* 아이디 입력 */}
                        <div>
                            <label className="block text-sm font-bold text-slate-800 mb-2">관리자 아이디</label>
                            <input
                                name="id"
                                type="text"
                                required
                                value={formData.id}
                                onChange={handleChange}
                                className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-all sm:text-sm font-medium bg-slate-50 focus:bg-white"
                                placeholder="관리자 아이디를 입력하세요"
                            />
                        </div>

                        {/* 비밀번호 입력 */}
                        <div>
                            <label className="block text-sm font-bold text-slate-800 mb-2">비밀번호</label>
                            <div className="relative">
                                <input
                                    name="password"
                                    type={showPassword ? "text" : "password"}
                                    required
                                    value={formData.password}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-all sm:text-sm font-medium pr-12 bg-slate-50 focus:bg-white"
                                    placeholder="••••••••"
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

                        {/* 제출 버튼 */}
                        <button
                            type="submit"
                            className="w-full py-4 mt-6 bg-primary hover:bg-dark text-white rounded-xl font-bold text-base shadow-lg shadow-primary/20 transform hover:-translate-y-0.5 transition-all"
                        >
                            콘솔 입장
                        </button>
                    </form>

                    <div className="mt-8 mb-2 text-center text-sm font-medium flex flex-col gap-2">
                        <span className="text-slate-500">
                            계정이 없으신가요?{' '}
                            <Link to="/operator/signup" className="font-bold text-primary hover:underline underline-offset-4">
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
