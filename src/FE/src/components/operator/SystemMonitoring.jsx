import { useState } from 'react';
import { ExternalLink, ShieldCheck } from 'lucide-react';
import apiClient from '../../utils/api';

const GRAFANA_DASHBOARD_PATH = '/grafana/d/operator-overview/operator-overview?orgId=1&kiosk=tv';

const SystemMonitoring = () => {
    const [isOpening, setIsOpening] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const handleOpenGrafana = async () => {
        const grafanaWindow = window.open('', '_blank');
        if (!grafanaWindow) {
            setErrorMessage('브라우저 팝업이 차단되었습니다. 팝업 허용 후 다시 시도하세요.');
            return;
        }

        setIsOpening(true);
        setErrorMessage('');

        try {
            await apiClient.post('/admin/monitoring/session');
            grafanaWindow.location.href = GRAFANA_DASHBOARD_PATH;
        } catch (error) {
            if (!grafanaWindow.closed) {
                grafanaWindow.close();
            }

            console.error('Grafana bootstrap failed:', error);
            setErrorMessage('모니터링 대시보드에 접근할 수 없습니다. 운영 권한 또는 네트워크 상태를 확인하세요.');
        } finally {
            setIsOpening(false);
        }
    };

    return (
        <section className="h-full overflow-y-auto bg-slate-100 px-8 py-10">
            <div className="mx-auto flex max-w-5xl flex-col gap-6">
                <div className="rounded-3xl bg-dark px-8 py-7 text-white shadow-xl">
                    <div className="flex items-start justify-between gap-6">
                        <div className="max-w-2xl">
                            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-secondary/80">Monitoring</p>
                            <h2 className="mt-3 text-3xl font-bold tracking-tight">시스템 모니터링</h2>
                            <p className="mt-3 text-sm leading-6 text-slate-300">
                                운영 환경의 Spring API, Postgres, Redis, 컨테이너 리소스 지표를 Grafana 대시보드에서 확인합니다.
                                Prometheus는 내부 수집 전용이며 외부 UI는 제공하지 않습니다.
                            </p>
                        </div>

                        <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                            <div className="flex items-center gap-3 text-sm font-semibold text-slate-100">
                                <ShieldCheck className="h-5 w-5 text-secondary" />
                                Waddoc ADMIN 전용
                            </div>
                            <p className="mt-2 max-w-xs text-sm leading-6 text-slate-300">
                                버튼을 누르면 monitoring 쿠키를 발급한 뒤 Grafana를 새 창으로 엽니다.
                            </p>
                        </div>
                    </div>
                </div>

                <div className="grid gap-6 lg:grid-cols-[1.35fr_0.95fr]">
                    <div className="rounded-3xl bg-white p-7 shadow-sm ring-1 ring-slate-200">
                        <h3 className="text-lg font-bold text-slate-900">포함 범위</h3>
                        <div className="mt-5 grid gap-3 sm:grid-cols-2">
                            {[
                                'Spring API 요청 수, 오류율, p95 응답시간',
                                'JVM heap, thread, GC 상태',
                                '컨테이너 CPU, memory, restart',
                                'Postgres 연결 상태와 Redis up 상태'
                            ].map((item) => (
                                <div key={item} className="rounded-2xl bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700">
                                    {item}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="rounded-3xl bg-white p-7 shadow-sm ring-1 ring-slate-200">
                        <h3 className="text-lg font-bold text-slate-900">Grafana 실행</h3>
                        <p className="mt-3 text-sm leading-6 text-slate-600">
                            접근이 실패하면 관리자 인증 상태와 운영망 연결, `/grafana/` reverse proxy 설정을 먼저 확인하세요.
                        </p>

                        {errorMessage ? (
                            <div className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
                                {errorMessage}
                            </div>
                        ) : (
                            <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
                                대시보드는 새 창에서 열립니다. 브라우저 팝업 차단이 켜져 있으면 먼저 해제해야 합니다.
                            </div>
                        )}

                        <button
                            type="button"
                            onClick={handleOpenGrafana}
                            disabled={isOpening}
                            className="mt-6 inline-flex items-center gap-2 rounded-2xl bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            <ExternalLink className="h-4 w-4" />
                            {isOpening ? 'Grafana 연결 중...' : 'Grafana 열기'}
                        </button>
                    </div>
                </div>
            </div>
        </section>
    );
};

export default SystemMonitoring;
