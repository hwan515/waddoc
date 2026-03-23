import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import ecg from '../../../assets/ecg.png';
import EcgWaveform from '../../../components/consultation/EcgWaveform';
import {
    createEcgMeasurement,
    extractVitalsApiErrorMessage,
    upsertMissionVitals,
} from '../../../utils/vitalsApi';

const Ecg = () => {
    const navigate = useNavigate();
    const [measurement, setMeasurement] = useState(null);
    const [saveStatus, setSaveStatus] = useState('measuring');
    const [errorMsg, setErrorMsg] = useState('');
    const [retryKey, setRetryKey] = useState(0);

    useEffect(() => {
        let isCancelled = false;
        let revealTimeoutId = null;
        let navigateTimeoutId = null;

        setMeasurement(null);
        setSaveStatus('measuring');
        setErrorMsg('');

        revealTimeoutId = window.setTimeout(async () => {
            const nextMeasurement = createEcgMeasurement();
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
                const remainingDelay = Math.max(0, 5000 - (Date.now() - revealedAt));
                navigateTimeoutId = window.setTimeout(() => {
                    if (!isCancelled) {
                        navigate('/robot/conference');
                    }
                }, remainingDelay);
            } catch (error) {
                if (isCancelled) {
                    return;
                }

                console.error('ECG vital save failed:', error);
                setSaveStatus('error');
                setErrorMsg(extractVitalsApiErrorMessage(
                    error,
                    '심전도 저장에 실패했습니다. 잠시 후 다시 시도해주세요.'
                ));
            }
        }, 10000);

        return () => {
            isCancelled = true;
            window.clearTimeout(revealTimeoutId);
            window.clearTimeout(navigateTimeoutId);
        };
    }, [navigate, retryKey]);

    const statusMessage = saveStatus === 'measuring'
        ? '심전도를 측정하고 있습니다. 잠시만 기다려주세요.'
        : saveStatus === 'saving'
            ? '심전도 파형이 측정되었습니다.\n저장 중입니다.'
            : saveStatus === 'saved'
                ? '심전도 파형이 저장되었습니다.\n5초 뒤 비대면진료실로 이동합니다.'
                : '심전도 저장에 실패했습니다. 다시 시도해주세요.';

    return (
        <div className="min-h-screen w-full flex flex-col items-center px-6 pt-8 pb-10 bg-[#061A40] font-sans relative overflow-y-auto text-white">
            <div className="absolute top-0 right-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-30"></div>
            <div className="absolute bottom-0 left-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <main className="relative z-10 w-full max-w-6xl flex flex-col items-center">
                <div className="w-full flex items-center justify-between relative bg-white/10 p-4 rounded-3xl backdrop-blur-md border border-white/20 mb-4">
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            1. 체온
                        </div>
                    </div>
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            2. 혈압
                        </div>
                    </div>
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            3. 산소포화도
                        </div>
                    </div>
                    <div className="flex flex-col items-center flex-1 relative">
                        <div className="mt-2 bg-[#B9D6F2] text-[#061A40] px-3 py-1 rounded-full font-bold text-sm shadow-md whitespace-nowrap">
                            4. 심전도
                        </div>
                        <div className="mt-1 text-[#B9D6F2] text-xs font-semibold tracking-wider">측정 중</div>
                    </div>
                </div>

                <h1 className="text-4xl font-bold tracking-tight">
                    <span className="text-[#B9D6F2]">심전도 측정기</span> 사용 방법
                </h1>

                <div className="w-full max-w-6xl mt-8 mb-8 flex flex-col justify-center items-center">
                    <div className="flex flex-row items-stretch justify-center gap-6 w-full max-w-5xl">
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full h-72 mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={ecg} alt="심전도 측정" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                화면에 보이는 것과 같이 손을 올려주세요
                            </p>
                        </div>

                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 shadow-lg backdrop-blur-sm">
                            <div className="mb-4 text-center">
                                <p className="whitespace-pre-line text-lg font-semibold leading-relaxed text-slate-200 md:text-xl">{statusMessage}</p>
                            </div>
                            {measurement ? (
                                <EcgWaveform
                                    waveform={measurement.ecgWaveform}
                                    samplingHz={measurement.ecgSamplingHz}
                                    durationSeconds={measurement.ecgDurationSeconds}
                                />
                            ) : (
                                <div className="flex h-80 items-center justify-center rounded-3xl border border-white/10 bg-slate-950/60">
                                    <div className="h-14 w-14 rounded-full border-4 border-[#B9D6F2]/30 border-t-[#B9D6F2] animate-spin"></div>
                                </div>
                            )}
                            {errorMsg && (
                                <p className="mt-4 rounded-2xl border border-red-300/40 bg-red-400/10 px-4 py-3 text-sm text-red-100">
                                    {errorMsg}
                                </p>
                            )}
                            {saveStatus === 'error' && (
                                <button
                                    onClick={() => setRetryKey((prev) => prev + 1)}
                                    className="mt-4 w-full rounded-2xl border border-white/20 bg-[#0353A4] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#006DAA]"
                                >
                                    심전도 저장 다시 시도
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default Ecg;
