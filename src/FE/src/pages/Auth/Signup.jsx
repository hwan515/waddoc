import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { User, Mail, Lock, CheckCircle2, Hospital } from 'lucide-react';
import useAuthStore from '../../store/authStore';

const Signup = () => {
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);

    const [formData, setFormData] = useState({
        name: '',
        email: '',
        password: '',
        passwordConfirm: '',
        role: 'patient',
    });

    const [isSuccess, setIsSuccess] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleRoleSelect = (role) => {
        setFormData({ ...formData, role });
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
            navigate('/');
        }, 1500);
    };

    const roles = [
        { id: 'patient', label: '환자/보호자' },
        { id: 'doctor', label: '의료진' }
    ];

    if (isSuccess) {
        return (
            <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center bg-slate-50 px-4">
                <div className="bg-white p-10 rounded-3xl shadow-xl w-full max-w-sm text-center animate-fade-in-up">
                    <div className="flex justify-center mb-6">
                        <div className="bg-green-100 p-4 rounded-full">
                            <CheckCircle2 className="w-12 h-12 text-green-500" />
                        </div>
                    </div>
                    <h2 className="text-2xl font-bold text-slate-800 mb-2">가입 완료!</h2>
                    <p className="text-slate-500 text-sm mb-6">
                        VitalConnect에 오신 것을 환영합니다.<br />
                        잠시 후 메인 화면으로 이동합니다.
                    </p>
                    <div className="w-full bg-slate-100 rounded-full h-1.5 mb-4 overflow-hidden">
                        <div className="bg-primary h-1.5 rounded-full animate-progress"></div>
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
            {/* Background Decor */}
            <div className="absolute top-0 right-0 -mr-20 -mt-20 w-72 h-72 bg-accent-1/5 rounded-full blur-3xl" />

            <div className="max-w-md w-full space-y-6 bg-white p-10 rounded-3xl shadow-xl border border-slate-100 relative z-10">
                <div className="text-center">
                    <h2 className="text-3xl font-extrabold text-slate-900 mb-2">회원가입</h2>
                    <p className="text-sm text-slate-500">
                        서비스 이용을 위해 계정을 생성합니다
                    </p>
                </div>

                <form className="mt-8 space-y-5" onSubmit={handleSubmit}>

                    {/* Role Selector */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-2">가입 유형 선택</label>
                        <div className="grid grid-cols-2 gap-3">
                            {roles.map((r) => (
                                <button
                                    key={r.id}
                                    type="button"
                                    onClick={() => handleRoleSelect(r.id)}
                                    className={`py-2 flex items-center justify-center text-xs font-semibold rounded-xl border transition-all whitespace-nowrap ${formData.role === r.id
                                        ? 'border-primary bg-primary text-white shadow-md shadow-primary/30'
                                        : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50'
                                        }`}
                                >
                                    {r.label}
                                </button>
                            ))}
                        </div>
                        {formData.role === 'doctor' && (
                            <p className="mt-2 text-xs text-amber-600 flex items-center gap-1">
                                <Hospital className="w-3 h-3" /> 의료진은 별도 기관 인증이 필요합니다.
                            </p>
                        )}
                    </div>

                    <div className="space-y-4">
                        {/* Name */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">이름</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <User className="h-4 w-4 text-slate-400" />
                                </div>
                                <input
                                    name="name"
                                    type="text"
                                    required
                                    value={formData.name}
                                    onChange={handleChange}
                                    className="appearance-none relative block w-full px-3 py-3 pl-10 border border-slate-300 placeholder-slate-400 text-slate-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary sm:text-sm"
                                    placeholder="홍길동"
                                />
                            </div>
                        </div>

                        {/* Email */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">이메일 주소</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Mail className="h-4 w-4 text-slate-400" />
                                </div>
                                <input
                                    name="email"
                                    type="email"
                                    required
                                    value={formData.email}
                                    onChange={handleChange}
                                    className="appearance-none relative block w-full px-3 py-3 pl-10 border border-slate-300 placeholder-slate-400 text-slate-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary sm:text-sm"
                                    placeholder="name@example.com"
                                />
                            </div>
                        </div>

                        {/* Password */}
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">비밀번호</label>
                                <div className="relative">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <Lock className="h-4 w-4 text-slate-400" />
                                    </div>
                                    <input
                                        name="password"
                                        type="password"
                                        required
                                        value={formData.password}
                                        onChange={handleChange}
                                        className="appearance-none relative block w-full px-3 py-3 pl-10 border border-slate-300 placeholder-slate-400 text-slate-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary sm:text-sm"
                                        placeholder="••••••••"
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">비밀번호 확인</label>
                                <div className="relative">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <Lock className="h-4 w-4 text-slate-400" />
                                    </div>
                                    <input
                                        name="passwordConfirm"
                                        type="password"
                                        required
                                        value={formData.passwordConfirm}
                                        onChange={handleChange}
                                        className={`appearance-none relative block w-full px-3 py-3 pl-10 border placeholder-slate-400 text-slate-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary sm:text-sm ${formData.passwordConfirm && formData.password !== formData.passwordConfirm
                                            ? 'border-red-500 focus:ring-red-500'
                                            : 'border-slate-300'
                                            }`}
                                        placeholder="••••••••"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>

                    <div>
                        <button
                            type="submit"
                            className="w-full flex justify-center py-3.5 px-4 border border-transparent text-sm font-semibold rounded-xl text-white bg-primary hover:bg-accent-1 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary shadow-md hover:shadow-lg transition-all"
                        >
                            회원가입 완료
                        </button>
                    </div>
                </form>

                <div className="mt-4 text-center text-sm">
                    <span className="text-slate-500">이미 계정이 있으신가요? </span>
                    <Link to="/login" className="font-semibold text-primary hover:text-accent-1">
                        로그인
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default Signup;
