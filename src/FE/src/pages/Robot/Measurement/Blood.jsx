import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import blood1 from '../../../assets/blood_1.png';
import blood2 from '../../../assets/blood_2.png';
import blood3 from '../../../assets/blood_3.png';
import {
    createBloodMeasurement,
    extractVitalsApiErrorMessage,
    upsertMissionVitals,
} from '../../../utils/vitalsApi';

const Blood = () => {
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
            const nextMeasurement = createBloodMeasurement();
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
                        navigate('/robot/measure/spo2');
                    }
                }, remainingDelay);
            } catch (error) {
                if (isCancelled) {
                    return;
                }

                console.error('Blood vital save failed:', error);
                setSaveStatus('error');
                setErrorMsg(extractVitalsApiErrorMessage(
                    error,
                    '혈압 저장에 실패했습니다. 잠시 후 다시 시도해주세요.'
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
        ? '혈압과 심박수를 측정하고 있습니다. \n잠시만 기다려주세요.'
        : saveStatus === 'saving'
            ? '혈압과 심박수가 측정되었습니다.\n저장 중입니다.'
            : saveStatus === 'saved'
                ? '혈압과 심박수가 저장되었습니다.\n5초 뒤 산소포화도 측정 단계로 이동합니다.'
                : '혈압 저장에 실패했습니다. 다시 시도해주세요.';

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
                    <div className="flex flex-col items-center flex-1 relative">
                        <div className="mt-2 bg-[#B9D6F2] text-[#061A40] px-3 py-1 rounded-full font-bold text-sm shadow-md whitespace-nowrap">
                            2. 혈압
                        </div>
                        <div className="mt-1 text-[#B9D6F2] text-xs font-semibold tracking-wider">측정 중</div>
                    </div>
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            3. 산소포화도
                        </div>
                    </div>
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            4. 심전도
                        </div>
                    </div>
                </div>

                <h1 className="text-4xl font-bold tracking-tight">
                    <span className="text-[#B9D6F2]">혈압계</span> 사용 방법
                </h1>

                <div className="w-full max-w-md rounded-3xl border border-white/15 bg-white/10 px-6 py-5 text-center backdrop-blur-md shadow-lg">
                    {measurement ? (
                        <>
                            <div className="text-4xl font-black text-white">
                                {measurement.bloodPressureSys}
                                <span className="mx-2 text-slate-400">/</span>
                                {measurement.bloodPressureDia}
                                <span className="ml-2 text-lg font-semibold text-slate-300">mmHg</span>
                            </div>
                            <div className="mt-2 text-lg font-semibold text-[#B9D6F2]">
                                심박수 {measurement.heartRate} bpm
                            </div>
                        </>
                    ) : (
                        <div className="flex justify-center">
                            <div className="h-14 w-14 rounded-full border-4 border-[#B9D6F2]/30 border-t-[#B9D6F2] animate-spin"></div>
                        </div>
                    )}
                    <p className="mt-4 whitespace-pre-line text-lg font-semibold leading-relaxed text-slate-200 md:text-xl">{statusMessage}</p>
                    {errorMsg && (
                        <p className="mt-4 rounded-2xl border border-red-300/40 bg-red-400/10 px-4 py-3 text-sm text-red-100">
                            {errorMsg}
                        </p>
                    )}
                    {saveStatus === 'error' && (
                        <button
                            onClick={() => setRetryKey((prev) => prev + 1)}
                            className="mt-4 rounded-2xl border border-white/20 bg-[#0353A4] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#006DAA]"
                        >
                            혈압 저장 다시 시도
                        </button>
                    )}
                </div>

                <div className="w-full max-w-6xl mt-8 mb-8 flex flex-col justify-center">
                    <div className="flex flex-row items-stretch justify-center gap-6 w-full">
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood1} alt="혈압계 전원 작동" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                커프 부분에 팔을 넣고<br /><span className="text-[#B9D6F2]">검정색 시작 버튼을 누릅니다</span>
                            </p>
                        </div>

                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood2} alt="혈압 측정" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                측정이 완료될 때까지<br /><span className="text-[#B9D6F2]">움직이거나 말하지 않습니다</span>
                            </p>
                        </div>

                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood3} alt="혈압 결과 확인" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                액정화면에 측정된<br /><span className="text-[#B9D6F2]">혈압을 확인</span>합니다
                            </p>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default Blood;
