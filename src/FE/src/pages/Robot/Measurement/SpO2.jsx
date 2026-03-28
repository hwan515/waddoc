import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import spo2_1 from '../../../assets/spo2_1.png';
import spo2_2 from '../../../assets/spo2_2.png';
import spo2_3 from '../../../assets/spo2_3.png';
import {
    createSpO2Measurement,
    extractVitalsApiErrorMessage,
    upsertMissionVitals,
} from '../../../utils/vitalsApi';
import {
    MEASUREMENT_NEXT_STEP_DELAY_MS,
    MEASUREMENT_REVEAL_DELAY_MS,
} from './measurementTiming';

const SpO2MeasurementCard = ({ onRetry }) => {
    const navigate = useNavigate();
    const [measurement, setMeasurement] = useState(null);
    const [saveStatus, setSaveStatus] = useState('measuring');
    const [errorMsg, setErrorMsg] = useState('');

    useEffect(() => {
        let isCancelled = false;
        let revealTimeoutId = null;
        let navigateTimeoutId = null;

        revealTimeoutId = window.setTimeout(async () => {
            const nextMeasurement = createSpO2Measurement();
            const revealedAt = Date.now();

            if (isCancelled) {
                return;
            }

            setMeasurement(nextMeasurement);
            setSaveStatus('saving');

            try {
                await upsertMissionVitals(nextMeasurement);
                if (isCancelled) {
                    return;
                }

                setSaveStatus('saved');
                const remainingDelay = Math.max(0, MEASUREMENT_NEXT_STEP_DELAY_MS - (Date.now() - revealedAt));
                navigateTimeoutId = window.setTimeout(() => {
                    if (!isCancelled) {
                        navigate('/robot/measure/ecg');
                    }
                }, remainingDelay);
            } catch (error) {
                if (isCancelled) {
                    return;
                }

                console.error('SpO2 vital save failed:', error);
                setSaveStatus('error');
                setErrorMsg(extractVitalsApiErrorMessage(
                    error,
                    '산소포화도 저장에 실패했습니다. 잠시 후 다시 시도해주세요.'
                ));
            }
        }, MEASUREMENT_REVEAL_DELAY_MS);

        return () => {
            isCancelled = true;
            window.clearTimeout(revealTimeoutId);
            window.clearTimeout(navigateTimeoutId);
        };
    }, [navigate]);

    const statusMessage = saveStatus === 'measuring'
        ? '산소포화도를 측정하고 있습니다.\n잠시만 기다려주세요.'
        : saveStatus === 'saving'
            ? '산소포화도가 측정되었습니다.\n저장 중입니다.'
            : saveStatus === 'saved'
                ? '산소포화도가 저장되었습니다.\n3초 뒤 심전도 측정 단계로 이동합니다.'
                : '';

    return (
        <div className="w-full max-w-md rounded-3xl border border-white/15 bg-white/10 px-6 py-5 text-center backdrop-blur-md shadow-lg">
            {measurement ? (
                <div className="text-5xl font-black text-white">
                    {measurement.spO2}
                    <span className="ml-2 text-xl font-semibold text-slate-300">%</span>
                </div>
            ) : (
                <div className="flex justify-center">
                    <div className="h-14 w-14 rounded-full border-4 border-secondary/30 border-t-secondary animate-spin"></div>
                </div>
            )}
            {statusMessage && (
                <p className="mt-4 whitespace-pre-line text-lg font-semibold leading-relaxed text-slate-200 md:text-xl">{statusMessage}</p>
            )}
            {errorMsg && (
                <p className="mt-4 rounded-2xl border border-red-300/40 bg-red-400/10 px-4 py-3 text-sm text-red-100">
                    {errorMsg}
                </p>
            )}
            {saveStatus === 'error' && (
                <button
                    onClick={onRetry}
                    className="mt-4 rounded-2xl border border-white/20 bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:bg-accent-1"
                >
                    산소포화도 저장 다시 시도
                </button>
            )}
        </div>
    );
};

const SpO2 = () => {
    const [retryKey, setRetryKey] = useState(0);

    return (
        <div className="min-h-screen w-full flex flex-col items-center px-6 pt-8 pb-10 bg-dark font-sans relative overflow-y-auto text-white">
            <div className="absolute top-0 right-0 w-96 h-96 bg-primary rounded-full mix-blend-screen filter blur-[150px] opacity-30"></div>
            <div className="absolute bottom-0 left-0 w-96 h-96 bg-secondary rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <main className="relative z-10 w-full max-w-6xl flex flex-col items-center">
                <div className="w-full flex items-center justify-between relative bg-white/10 px-4 py-3 rounded-3xl backdrop-blur-md border border-white/20 mb-8">
                    <div className="flex items-center justify-center flex-1 opacity-50 relative">
                        <div className="bg-slate-700/50 text-white px-4 py-1.5 rounded-full font-semibold text-base md:text-lg whitespace-nowrap border border-slate-500">
                            1. 체온
                        </div>
                    </div>
                    <div className="flex items-center justify-center flex-1 opacity-50 relative">
                        <div className="bg-slate-700/50 text-white px-4 py-1.5 rounded-full font-semibold text-base md:text-lg whitespace-nowrap border border-slate-500">
                            2. 혈압
                        </div>
                    </div>
                    <div className="flex items-center justify-center flex-1 relative">
                        <div className="bg-secondary text-dark px-4 py-1.5 rounded-full font-bold text-base md:text-lg shadow-md whitespace-nowrap">
                            3. 산소포화도
                        </div>
                    </div>
                    <div className="flex items-center justify-center flex-1 opacity-50 relative">
                        <div className="bg-slate-700/50 text-white px-4 py-1.5 rounded-full font-semibold text-base md:text-lg whitespace-nowrap border border-slate-500">
                            4. 심전도
                        </div>
                    </div>
                </div>

                <SpO2MeasurementCard key={retryKey} onRetry={() => setRetryKey((prev) => prev + 1)} />

                <h1 className="mt-10 mb-3 text-4xl font-bold tracking-tight">
                    <span className="text-secondary">산소포화도 측정기</span> 사용 방법
                </h1>

                <div className="w-full max-w-6xl mb-8 flex flex-col justify-center">
                    <div className="flex flex-row items-stretch justify-center gap-6 w-full">
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={spo2_1} alt="산소포화도 측정 준비" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                전원 버튼을 누르거나 제품을 벌려<br /><span className="text-secondary">손가락 하나를 넣습니다</span>
                            </p>
                        </div>

                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={spo2_2} alt="산소포화도 측정" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                측정이 완료될 때까지<br /><span className="text-secondary">바른 자세를 유지합니다</span>
                            </p>
                        </div>

                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={spo2_3} alt="산소포화도 결과 확인" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                액정화면에 측정된<br /><span className="text-secondary">산소포화도를 확인</span>합니다
                            </p>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default SpO2;
