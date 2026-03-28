import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Activity, ShieldCheck } from 'lucide-react';
import useAuthStore from '../../../store/authStore';
import apiClient from '../../../utils/api';

const EMRLogin = () => {
    const navigate = useNavigate();
    const authenticatedRole = useAuthStore((state) => state.user?.role);

    const [formData, setFormData] = useState({
        id: '',
        password: '',
    });

    useEffect(() => {
        if (authenticatedRole === 'DOCTOR') {
            navigate('/emr/dashboard', { replace: true });
        }
    }, [authenticatedRole, navigate]);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!formData.id || !formData.password) {
            alert('사번과 비밀번호를 입력해주세요.');
            return;
        }

        try {
            const response = await apiClient.post('/auth/login', {
                username: formData.id,
                password: formData.password
            });

            const { accessToken, user } = response.data;
            if (user?.role !== 'DOCTOR') {
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
                } catch (logoutError) {
                    console.error('Doctor-only logout cleanup failed:', logoutError);
                }
                alert('EMR 로그인은 의사 계정으로만 사용할 수 있습니다.');
                return;
            }

            useAuthStore.getState().setAuth(accessToken, user);
            navigate('/emr/dashboard', { replace: true });
        } catch (error) {
            console.error('Login Failed:', error);
            alert('로그인에 실패했습니다. 사번과 비밀번호를 다시 확인해주세요.');
        }
    };

    return (
        <div className="h-screen bg-slate-50 flex items-center justify-center p-4">
            <div className="max-w-md w-full">

                {/* 헤더 */}
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary mb-4 shadow-lg shadow-primary/20">
                        <Activity className="w-8 h-8 text-white" />
                    </div>
                    <h1 className="text-2xl font-bold text-slate-800 tracking-tight">EMR</h1>
                </div>

                {/* 로그인 카드 */}
                <div className="bg-white rounded-2xl shadow-xl shadow-slate-200/50 p-8 border border-slate-100">
                    <form onSubmit={handleSubmit} className="space-y-6">

                        <div>
                            <label className="block text-sm font-semibold text-slate-700 mb-2">의료진 사번 (ID)</label>
                            <input
                                name="id"
                                type="text"
                                required
                                value={formData.id}
                                onChange={handleChange}
                                className="w-full px-4 py-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-primary focus:border-primary transition-all bg-slate-50 focus:bg-white"
                                placeholder="사번을 입력하세요 (예: D10023)"
                            />
                        </div>

                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <label className="block text-sm font-semibold text-slate-700">비밀번호</label>
                                <a href="#" className="text-sm font-semibold text-primary hover:underline">비밀번호 찾기</a>
                            </div>
                            <input
                                name="password"
                                type="password"
                                required
                                value={formData.password}
                                onChange={handleChange}
                                className="w-full px-4 py-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-primary focus:border-primary transition-all bg-slate-50 focus:bg-white"
                                placeholder="비밀번호를 입력하세요"
                            />
                        </div>

                        <div className="flex items-center gap-2 text-[13px] text-slate-600 bg-blue-50/50 p-3 rounded-lg border border-blue-100">
                            <ShieldCheck className="w-4 h-4 text-primary" />
                            <span>의료법에 의거, 비인가자의 접근은 처벌받을 수 있습니다.</span>
                        </div>

                        <button
                            type="submit"
                            className="w-full py-3.5 bg-primary hover:bg-accent-1 text-white font-bold rounded-xl transition-colors shadow-md shadow-primary/20"
                        >
                            로그인
                        </button>
                    </form>
                </div>

                <div className="text-center mt-8 space-y-3 text-sm text-slate-400 font-medium whitespace-pre-line">
                    <Link to="/" className="inline-flex items-center justify-center text-sm font-semibold text-primary hover:text-accent-1">
                        메인페이지로 돌아가기
                    </Link>
                    <div>ⓒ 2026 MediCloud Healthcare Solutions. All Rights Reserved.</div>
                </div>
            </div>
        </div>
    );
};

export default EMRLogin;
