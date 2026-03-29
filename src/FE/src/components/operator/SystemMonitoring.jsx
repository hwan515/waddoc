import { useEffect, useState } from 'react';
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

    return (
        <section className="flex h-full min-h-0 flex-col bg-slate-950">
            {embeddedSrc ? (
                <iframe
                    src={embeddedSrc}
                    title="Grafana operator overview"
                    allowFullScreen
                    className="h-full w-full flex-1 border-none bg-white"
                />
            ) : (
                <div className="flex h-full flex-1 items-center justify-center px-6">
                    <div className="flex max-w-md flex-col items-center gap-4 text-center">
                        <p className="text-sm font-medium text-slate-300">
                            {isBootstrapping
                                ? 'Grafana 대시보드를 준비 중입니다.'
                                : errorMessage || 'Grafana 세션을 초기화하지 못했습니다.'}
                        </p>

                        {errorMessage && (
                            <button
                                type="button"
                                onClick={bootstrapMonitoringSession}
                                disabled={isBootstrapping}
                                className="inline-flex items-center justify-center rounded-2xl bg-white px-5 py-3 text-sm font-semibold text-slate-900 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                다시 시도
                            </button>
                        )}
                    </div>
                </div>
            )}
        </section>
    );
};

export default SystemMonitoring;
