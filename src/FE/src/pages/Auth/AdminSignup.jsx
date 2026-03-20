import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { User, Mail, Lock, CheckCircle2 } from 'lucide-react';
import useAuthStore from '../../store/authStore';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';

const AdminSignup = () => {
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);

    const [formData, setFormData] = useState({
        name: '',
        email: '',
        password: '',
        passwordConfirm: '',
        role: 'operator',
    });

    const [isSuccess, setIsSuccess] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (formData.password !== formData.passwordConfirm) {
            alert('비밀번호가 일치하지 않습니다.');
            return;
        }

        // 임시 가입 성공 처리
        setIsSuccess(true);

        // 자동 로그인
        setTimeout(() => {
            login(formData);
            navigate('/operator/control');
        }, 1500);
    };

    if (isSuccess) {
        return (
            <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center bg-slate-50 px-4">
                <div className="bg-white p-10 rounded-3xl shadow-xl w-full max-w-sm text-center animate-fade-in-up">
                    <div className="flex justify-center mb-6">
                        <div className="bg-blue-100 p-4 rounded-full">
                            <CheckCircle2 className="w-12 h-12 text-[#0353A4]" />
                        </div>
                    </div>
                    <h2 className="text-2xl font-bold text-slate-800 mb-2">가입 완료!</h2>
                    <p className="text-slate-500 text-sm mb-6">
                        VitalConnect 관제센터에 오신 것을 환영합니다.<br />
                        잠시 후 대시보드로 이동합니다.
                    </p>
                    <div className="w-full bg-slate-100 rounded-full h-1.5 mb-4 overflow-hidden">
                        <div className="bg-[#0353A4] h-1.5 rounded-full animate-progress"></div>
                    </div>
                </div>
                <style dangerouslySetInnerHTML={{
                    __html: `
          @keyframes slideUp {
            from { opacity: 0; transform: translateY(20px); }
            to { opacity: 1; transform: translateY(0); }
          }
          @keyframes progress {
            from { width: 0%; }
            to { width: 100%; }
          }
          .animate-fade-in-up { animation: slideUp 0.5s ease-out forwards; }
          .animate-progress { animation: progress 1.5s ease-in-out forwards; }
        `}} />
            </div>
        );
    }

    return (
        <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8 relative overflow-hidden">
            {/* 배경 */}
            <div className="absolute top-0 right-0 -mr-20 -mt-20 w-72 h-72 bg-blue-100 rounded-full blur-3xl opacity-50" />

            <div className="max-w-md w-full space-y-6 bg-white p-10 rounded-3xl shadow-xl border border-slate-100 relative z-10">
                <div className="text-center">
                    <h2 className="text-3xl font-extrabold text-[#0353A4] mb-2">관리자 등록</h2>
                    <p className="text-sm text-slate-500">
                        통합 관제 시스템 운영을 위한 관리자 계정을 생성합니다
                    </p>
                </div>

                <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
                    <div className="space-y-4">
                        {/* 이름 */}
                        <Input
                            label="이름"
                            name="name"
                            type="text"
                            required
                            value={formData.name}
                            onChange={handleChange}
                            placeholder="관리자 이름"
                            iconLeft={<User className="h-4 w-4" />}
                        />

                        {/* 이메일 */}
                        <Input
                            label="이메일 (사번)"
                            name="email"
                            type="email"
                            required
                            value={formData.email}
                            onChange={handleChange}
                            placeholder="admin@vitalconnect.co.kr"
                            iconLeft={<Mail className="h-4 w-4" />}
                        />

                        {/* 비밀번호 */}
                        <div className="grid grid-cols-2 gap-4">
                            <Input
                                label="비밀번호"
                                name="password"
                                type="password"
                                required
                                value={formData.password}
                                onChange={handleChange}
                                placeholder="••••••••"
                                iconLeft={<Lock className="h-4 w-4" />}
                            />
                            <Input
                                label="비밀번호 확인"
                                name="passwordConfirm"
                                type="password"
                                required
                                value={formData.passwordConfirm}
                                onChange={handleChange}
                                placeholder="••••••••"
                                iconLeft={<Lock className="h-4 w-4" />}
                                errorMessage={formData.passwordConfirm && formData.password !== formData.passwordConfirm ? '비밀번호가 일치하지 않습니다.' : ''}
                            />
                        </div>
                    </div>

                    <div className="mt-6">
                        <Button type="submit" fullWidth>
                            사내망 계정 등록
                        </Button>
                    </div>
                </form>

                <div className="mt-4 text-center text-sm">
                    <span className="text-slate-500">이미 등록된 관리자이신가요? </span>
                    <Link to="/operator/login" className="font-semibold text-[#0353A4] hover:underline">
                        로그인
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default AdminSignup;
