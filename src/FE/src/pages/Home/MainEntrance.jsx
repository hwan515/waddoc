import { Link } from 'react-router-dom';
import {
    Activity,
    ArrowRight,
    Bot,
    ShieldCheck,
    Stethoscope,
    Users,
} from 'lucide-react';
import useAuthStore from '../../store/authStore';
import { getHomePathForRole, getRoleDisplayName } from '../../utils/authRouting';

const primaryEntries = [
    {
        title: '보호자/환자 포털',
        badge: '정식 웹 진입',
        description: '승인된 보호자가 환자 포털에서 진료일, 소견서, 처방전을 조회합니다.',
        icon: Users,
        actions: [
            { label: '보호자 로그인', to: '/login', tone: 'primary' },
            { label: '회원가입', to: '/signup', tone: 'secondary' },
        ],
    },
    {
        title: '관리자/관제',
        badge: '정식 웹 진입',
        description: '관제 운영 담당자가 예약, 세션, 환자 연결 요청을 관리하는 콘솔입니다.',
        icon: ShieldCheck,
        actions: [
            { label: '관리자 로그인', to: '/operator/login', tone: 'primary' },
        ],
    },
];

const internalEntries = [
    {
        title: '의사 EMR',
        badge: '시연/내부 전용',
        description: '의사는 평소 사용하는 EMR 흐름 안에서 예약을 수락하고 원격진료로 진입합니다.',
        icon: Stethoscope,
        actions: [
            { label: 'EMR 열기', to: '/emr/login', tone: 'outline' },
        ],
    },
    {
        title: '로봇 단말',
        badge: '시연/내부 전용',
        description: '차량 태블릿에서 본인 확인, 활력징후 측정, 환자 세션 입장을 진행하는 전용 화면입니다.',
        icon: Bot,
        actions: [
            { label: '로봇 화면 열기', to: '/robot', tone: 'outline' },
        ],
    },
];

const actionStyles = {
    primary: 'bg-white text-dark hover:bg-secondary/90',
    secondary: 'bg-white/10 text-white hover:bg-white/15',
    outline: 'border border-slate-200 bg-white text-slate-800 hover:border-primary hover:text-primary',
};

const ActionLink = ({ to, label, tone = 'primary' }) => (
    <Link
        to={to}
        className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 py-3 text-sm font-bold transition-all ${actionStyles[tone]}`}
    >
        <span>{label}</span>
        <ArrowRight className="h-4 w-4" />
    </Link>
);

const EntryCard = ({ title, badge, description, icon, actions, highlighted = false }) => {
    const IconComponent = icon;

    return (
        <article
            className={`rounded-[1.75rem] border p-6 shadow-lg transition-transform hover:-translate-y-1 ${
                highlighted
                    ? 'border-primary/40 bg-gradient-to-br from-primary via-accent-1 to-accent-2 text-white shadow-primary/25'
                    : 'border-slate-200 bg-white text-slate-900 shadow-slate-200/70'
            }`}
        >
            <div className="mb-5 flex items-start justify-between gap-4">
                <div>
                    <span
                        className={`inline-flex rounded-full px-3 py-1 text-[11px] font-bold uppercase tracking-[0.2em] ${
                            highlighted ? 'bg-white/15 text-secondary' : 'bg-secondary/50 text-accent-2'
                        }`}
                    >
                        {badge}
                    </span>
                    <h2 className={`mt-4 text-2xl font-bold tracking-tight ${highlighted ? 'text-white' : 'text-slate-900'}`}>
                        {title}
                    </h2>
                </div>
                <div className={`rounded-2xl p-3 ${highlighted ? 'bg-white/12 ring-1 ring-white/10' : 'bg-primary/10'}`}>
                    <IconComponent className={`h-6 w-6 ${highlighted ? 'text-secondary' : 'text-primary'}`} />
                </div>
            </div>

            <p className={`mb-6 text-sm leading-6 ${highlighted ? 'text-white/85' : 'text-slate-600'}`}>
                {description}
            </p>

            <div className="flex flex-wrap gap-3">
                {actions.map((action) => (
                    <ActionLink key={`${title}-${action.to}`} {...action} />
                ))}
            </div>
        </article>
    );
};

const MainEntrance = () => {
    const user = useAuthStore((state) => state.user);
    const currentHomePath = getHomePathForRole(user?.role);

    return (
        <div className="min-h-screen overflow-hidden bg-slate-50">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(185,214,242,0.55),transparent_28%),radial-gradient(circle_at_bottom_right,rgba(3,83,164,0.12),transparent_24%)]" />

            <div className="relative mx-auto flex min-h-screen w-full max-w-7xl flex-col px-4 py-6 sm:px-6 lg:px-8">
                <header className="mb-6 flex items-center justify-between gap-4">
                    <Link to="/" className="inline-flex items-center gap-3">
                        <div className="rounded-2xl bg-primary/10 p-3">
                            <Activity className="h-7 w-7 text-primary" strokeWidth={2.5} />
                        </div>
                        <div>
                            <div className="text-xl font-bold tracking-tight text-dark">
                                Waddoc<span className="text-primary"> 왔닥</span>
                            </div>
                            <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">
                                Role-Based Entry
                            </p>
                        </div>
                    </Link>

                    {currentHomePath && (
                        <Link
                            to={currentHomePath}
                            className="inline-flex items-center gap-2 rounded-full border border-primary/15 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm shadow-slate-200/70 transition-all hover:border-primary hover:text-primary"
                        >
                            <span>{getRoleDisplayName(user.role)}로 계속</span>
                            <ArrowRight className="h-4 w-4" />
                        </Link>
                    )}
                </header>

                <main className="mt-2 grid gap-8">
                    <section>
                        <div className="mb-4 flex items-center justify-between">
                            <div>
                                <p className="text-sm font-bold uppercase tracking-[0.2em] text-primary">Primary Access</p>
                                <h2 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">정식 웹 진입</h2>
                            </div>
                            <p className="hidden text-sm text-slate-500 md:block">
                                보호자 포털과 관리자 콘솔은 메인페이지에서 바로 진입합니다.
                            </p>
                        </div>

                        <div className="grid gap-5 lg:grid-cols-2">
                            {primaryEntries.map((entry) => (
                                <EntryCard key={entry.title} {...entry} highlighted />
                            ))}
                        </div>
                    </section>

                    <section>
                        <div className="mb-4 flex items-center justify-between">
                            <div>
                                <p className="text-sm font-bold uppercase tracking-[0.2em] text-slate-500">Internal Access</p>
                                <h2 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">시연 및 내부 전용</h2>
                            </div>
                            <p className="hidden text-sm text-slate-500 md:block">
                                의사와 로봇 단말은 실제 운영 흐름에 맞는 별도 화면으로 분리했습니다.
                            </p>
                        </div>

                        <div className="grid gap-5 lg:grid-cols-2">
                            {internalEntries.map((entry) => (
                                <EntryCard key={entry.title} {...entry} />
                            ))}
                        </div>
                    </section>
                </main>
            </div>
        </div>
    );
};

export default MainEntrance;
