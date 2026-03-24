import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Lock, Phone, User, Users } from 'lucide-react';
import apiClient from '../../utils/api';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';

const initialFormData = {
    username: '',
    name: '',
    patientPhone: '',
    relation: '',
    password: '',
    passwordConfirm: '',
};

const Signup = () => {
    const [formData, setFormData] = useState(initialFormData);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState('');
    const [signupResult, setSignupResult] = useState(null);

    const handleChange = (e) => {
        const { name, value } = e.target;

        setFormData((current) => ({
            ...current,
            [name]: name === 'patientPhone' ? value.replace(/\D/g, '').slice(0, 11) : value,
        }));
    };

    const validateForm = () => {
        if (!formData.username.trim()) {
            return '아이디를 입력해주세요.';
        }

        if (!formData.name.trim()) {
            return '이름을 입력해주세요.';
        }

        if (formData.patientPhone.length < 10) {
            return '환자 전화번호를 정확히 입력해주세요.';
        }

        if (!formData.relation.trim()) {
            return '환자와의 관계를 입력해주세요.';
        }

        if (!formData.password) {
            return '비밀번호를 입력해주세요.';
        }

        if (formData.password !== formData.passwordConfirm) {
            return '비밀번호가 일치하지 않습니다.';
        }

        return '';
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const validationError = validateForm();
        if (validationError) {
            setSubmitError(validationError);
            return;
        }

        setIsSubmitting(true);
        setSubmitError('');

        try {
            const response = await apiClient.post('/auth/guardians/signup', {
                username: formData.username.trim(),
                password: formData.password,
                name: formData.name.trim(),
                patientPhone: formData.patientPhone,
                relation: formData.relation.trim(),
            });

            setSignupResult(response.data);
        } catch (error) {
            const apiMessage = error.response?.data?.message;
            setSubmitError(apiMessage || '회원가입 요청에 실패했습니다. 입력값을 다시 확인해주세요.');
        } finally {
            setIsSubmitting(false);
        }
    };

    if (signupResult) {
        const patientName = signupResult.patient?.name;
        const birthDate6 = signupResult.patient?.birthDate6;

        return (
            <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center bg-slate-50 px-4">
                <div className="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-10 text-center shadow-xl">
                    <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
                        <CheckCircle2 className="h-9 w-9 text-green-600" />
                    </div>
                    <h2 className="mb-2 text-2xl font-bold text-slate-900">가입 요청이 접수되었습니다</h2>
                    <p className="mb-6 text-sm leading-6 text-slate-500">
                        {signupResult.message || '관리자 승인 후 로그인할 수 있습니다.'}
                    </p>

                    {(patientName || birthDate6) && (
                        <div className="mb-6 rounded-2xl bg-slate-50 p-4 text-left">
                            <p className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                                연결 요청 정보
                            </p>
                            {patientName && (
                                <div className="mb-2 flex items-center justify-between text-sm">
                                    <span className="text-slate-500">환자명</span>
                                    <span className="font-semibold text-slate-800">{patientName}</span>
                                </div>
                            )}
                            {birthDate6 && (
                                <div className="flex items-center justify-between text-sm">
                                    <span className="text-slate-500">생년월일 6자리</span>
                                    <span className="font-semibold text-slate-800">{birthDate6}</span>
                                </div>
                            )}
                        </div>
                    )}

                    <div className="space-y-3">
                        <Link
                            to="/"
                            className="inline-flex w-full items-center justify-center rounded-xl bg-primary px-4 py-4 text-base font-bold text-white shadow-lg shadow-primary/30 transition-all hover:-translate-y-0.5 hover:bg-accent-1"
                        >
                            로그인 화면으로 이동
                        </Link>
                        <p className="text-xs text-slate-400">
                            승인 전까지는 로그인할 수 없습니다.
                        </p>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
            <div className="mx-auto w-full max-w-xl">
                <section className="rounded-[2rem] border border-slate-100 bg-white p-8 shadow-xl sm:p-10">
                    <div className="mb-8">
                        <h2 className="mb-2 text-3xl font-extrabold text-slate-900">보호자 회원가입</h2>
                        <p className="text-sm leading-6 text-slate-500">
                            관리자 승인 이후부터 환자 포털에서 진료일, 소견서, 처방전을 조회할 수 있습니다.
                        </p>
                    </div>

                    <form className="space-y-5" onSubmit={handleSubmit}>
                        <Input
                            label="아이디"
                            name="username"
                            value={formData.username}
                            onChange={handleChange}
                            placeholder="guardian_lee"
                            required
                            iconLeft={<User className="h-4 w-4" />}
                        />

                        <Input
                            label="이름"
                            name="name"
                            value={formData.name}
                            onChange={handleChange}
                            placeholder="이보호"
                            required
                            iconLeft={<User className="h-4 w-4" />}
                        />

                        <div className="grid gap-4 sm:grid-cols-2">
                            <Input
                                label="환자 전화번호"
                                name="patientPhone"
                                type="tel"
                                value={formData.patientPhone}
                                onChange={handleChange}
                                placeholder="01012345678"
                                required
                                inputMode="numeric"
                                maxLength={11}
                                iconLeft={<Phone className="h-4 w-4" />}
                            />

                            <Input
                                label="환자와의 관계"
                                name="relation"
                                value={formData.relation}
                                onChange={handleChange}
                                placeholder="자녀"
                                required
                                iconLeft={<Users className="h-4 w-4" />}
                            />
                        </div>

                        <div className="grid gap-4 sm:grid-cols-2">
                            <Input
                                label="비밀번호"
                                name="password"
                                type="password"
                                value={formData.password}
                                onChange={handleChange}
                                placeholder="비밀번호 입력"
                                required
                                iconLeft={<Lock className="h-4 w-4" />}
                            />

                            <Input
                                label="비밀번호 확인"
                                name="passwordConfirm"
                                type="password"
                                value={formData.passwordConfirm}
                                onChange={handleChange}
                                placeholder="비밀번호 재입력"
                                required
                                iconLeft={<Lock className="h-4 w-4" />}
                                errorMessage={
                                    formData.passwordConfirm && formData.password !== formData.passwordConfirm
                                        ? '비밀번호가 일치하지 않습니다.'
                                        : ''
                                }
                            />
                        </div>

                        {submitError && (
                            <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                                {submitError}
                            </div>
                        )}

                        <Button type="submit" fullWidth size="large" disabled={isSubmitting}>
                            {isSubmitting ? '가입 요청 처리 중...' : '가입 요청 보내기'}
                        </Button>
                    </form>

                    <div className="mt-6 text-center text-sm">
                        <span className="text-slate-500">이미 계정이 있으신가요? </span>
                        <Link to="/" className="font-semibold text-primary hover:text-accent-1">
                            로그인
                        </Link>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default Signup;
