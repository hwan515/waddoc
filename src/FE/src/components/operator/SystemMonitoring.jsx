import { useEffect, useState } from 'react';
import { ExternalLink, ShieldCheck } from 'lucide-react';
import apiClient from '../../utils/api';

const GRAFANA_DASHBOARD_PATH = '/grafana/d/operator-overview/operator-overview?orgId=1&kiosk=tv';

const SystemMonitoring = () => {
    const [isBootstrapping, setIsBootstrapping] = useState(false);
    const [embeddedSrc, setEmbeddedSrc] = useState('');
    const [errorMessage, setErrorMessage] = useState('');

    const bootstrapMonitoringSession = async () => {
        setIsBootstrapping(true);
        setErrorMessage('');

        try {
            await apiClient.post('/admin/monitoring/session');
            setEmbeddedSrc(GRAFANA_DASHBOARD_PATH);
        } catch (error) {
            console.error('Grafana bootstrap failed:', error);
            setErrorMessage('모니터링 대시보드에 접근할 수 없습니다. 운영 권한 또는 네트워크 상태를 확인하세요.');
        } finally {
            setIsBootstrapping(false);
        }
    };

    useEffect(() => {
        bootstrapMonitoringSession();
    }, []);

    const handleOpenGrafana = async () => {
        let targetPath = embeddedSrc;

        if (!targetPath) {
            await bootstrapMonitoringSession();
            targetPath = GRAFANA_DASHBOARD_PATH;
        }

        const grafanaWindow = window.open(targetPath, '_blank', 'noopener,noreferrer');
        if (!grafanaWindow) {
            setErrorMessage('브라우저 팝업이 차단되었습니다. 팝업 허용 후 다시 시도하세요.');
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

                <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
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
                        <h3 className="text-lg font-bold text-slate-900">Grafana 연결</h3>
                        <p className="mt-3 text-sm leading-6 text-slate-600">
                            탭에 진입하면 monitoring 쿠키를 발급하고 대시보드를 바로 불러옵니다. 새 창이 필요하면 아래 버튼으로 분리해서 열 수 있습니다.
                        </p>

                        {errorMessage ? (
                            <div className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
                                {errorMessage}
                            </div>
                        ) : (
                            <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
                                {isBootstrapping
                                    ? 'Grafana 세션을 준비하고 있습니다.'
                                    : embeddedSrc
                                        ? '탭 하단에서 운영 대시보드를 바로 확인할 수 있습니다.'
                                        : '대시보드를 불러오는 중입니다.'}
                            </div>
                        )}

                        <div className="mt-6 flex flex-wrap items-center gap-3">
                            <button
                                type="button"
                                onClick={handleOpenGrafana}
                                disabled={isBootstrapping}
                                className="inline-flex items-center gap-2 rounded-2xl bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                <ExternalLink className="h-4 w-4" />
                                {isBootstrapping ? 'Grafana 연결 중...' : '새 창으로 열기'}
                            </button>

                            <button
                                type="button"
                                onClick={bootstrapMonitoringSession}
                                disabled={isBootstrapping}
                                className="inline-flex items-center gap-2 rounded-2xl border border-slate-300 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                다시 불러오기
                            </button>
                        </div>
                    </div>
                </div>

                <div className="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
                    <div className="flex items-center justify-between gap-4 border-b border-slate-200 px-3 pb-3">
                        <div>
                            <h3 className="text-lg font-bold text-slate-900">운영 대시보드</h3>
                            <p className="mt-1 text-sm text-slate-500">
                                시스템 모니터링 탭 안에서 Grafana를 바로 확인합니다.
                            </p>
                        </div>
                        <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
                            {embeddedSrc ? '내장 보기' : '연결 준비 중'}
                        </div>
                    </div>

                    <div className="mt-4 overflow-hidden rounded-3xl border border-slate-200 bg-slate-950">
                        {embeddedSrc ? (
                            <iframe
                                src={embeddedSrc}
                                title="Grafana operator overview"
                                className="h-[72vh] w-full border-none bg-white"
                            />
                        ) : (
                            <div className="flex h-[72vh] items-center justify-center text-sm font-medium text-slate-300">
                                Grafana 대시보드를 준비 중입니다.
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </section>
    );
};

export default SystemMonitoring;
