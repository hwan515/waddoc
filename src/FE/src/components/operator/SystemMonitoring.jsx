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
        <section className="flex h-full min-h-0 flex-col bg-slate-100 p-6">
            <div className="shrink-0 rounded-3xl bg-dark px-6 py-5 text-white shadow-xl">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-3">
                            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-secondary/80">Monitoring</p>
                            <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-200">
                                <ShieldCheck className="h-4 w-4 text-secondary" />
                                Waddoc ADMIN 전용
                            </div>
                        </div>
                        <h2 className="mt-3 text-3xl font-bold tracking-tight">시스템 모니터링</h2>
                        <p className="mt-2 text-sm leading-6 text-slate-300">
                            상단 탭은 유지하고, 아래 영역 전체를 Grafana 대시보드로 사용합니다.
                        </p>
                    </div>

                    <div className="flex flex-col items-stretch gap-3 xl:items-end">
                        {errorMessage ? (
                            <div className="rounded-2xl border border-rose-200/60 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
                                {errorMessage}
                            </div>
                        ) : (
                            <div className="rounded-2xl border border-emerald-200/60 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
                                {isBootstrapping
                                    ? 'Grafana 세션을 준비하고 있습니다.'
                                    : embeddedSrc
                                        ? '운영 대시보드를 전체 높이로 표시 중입니다.'
                                        : '대시보드를 불러오는 중입니다.'}
                            </div>
                        )}

                        <div className="flex flex-wrap items-center gap-3 xl:justify-end">
                            <button
                                type="button"
                                onClick={handleOpenGrafana}
                                disabled={isBootstrapping}
                                className="inline-flex items-center justify-center gap-2 rounded-2xl bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                <ExternalLink className="h-4 w-4" />
                                {isBootstrapping ? 'Grafana 연결 중...' : '새 창으로 열기'}
                            </button>

                            <button
                                type="button"
                                onClick={bootstrapMonitoringSession}
                                disabled={isBootstrapping}
                                className="inline-flex items-center justify-center gap-2 rounded-2xl border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-slate-100 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                다시 불러오기
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <div className="mt-4 min-h-0 flex-1 overflow-hidden rounded-3xl bg-white p-3 shadow-sm ring-1 ring-slate-200">
                <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-[28px] border border-slate-200 bg-slate-950">
                    <div className="flex shrink-0 items-center justify-between gap-4 border-b border-slate-200 bg-white/95 px-5 py-4">
                        <div>
                            <h3 className="text-lg font-bold text-slate-900">운영 대시보드</h3>
                            <p className="mt-1 text-sm text-slate-500">
                                시스템 모니터링 탭 내부에서 Grafana를 전체 화면처럼 확인합니다.
                            </p>
                        </div>
                        <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
                            {embeddedSrc ? '내장 보기' : '연결 준비 중'}
                        </div>
                    </div>

                    <div className="min-h-0 flex-1 bg-slate-950">
                        {embeddedSrc ? (
                            <iframe
                                src={embeddedSrc}
                                title="Grafana operator overview"
                                allowFullScreen
                                className="h-full w-full border-none bg-white"
                            />
                        ) : (
                            <div className="flex h-full items-center justify-center text-sm font-medium text-slate-300">
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
