import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import ecg from '../../../assets/ecg.png';

const Ecg = () => {
    const navigate = useNavigate();
    const [temperatureValue, setTemperatureValue] = useState(null);

    const handleNext = () => {
        // 비대면진료 페이지로 이동
        navigate('/robot/conference');
    };

    return (
        <div className="h-screen w-full flex flex-col items-center pt-8 p-6 bg-[#061A40] font-sans relative overflow-hidden text-white">
            {/* Background Decorations */}
            <div className="absolute top-0 right-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-30"></div>
            <div className="absolute bottom-0 left-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <main className="relative z-10 w-full max-w-6xl h-full flex flex-col items-center justify-between">

                {/* 상단 Progress Bar */}
                <div className="w-full flex items-center justify-between relative bg-white/10 p-4 rounded-3xl backdrop-blur-md border border-white/20 mb-4">

                    {/* Step 1 */}
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            1. 체온
                        </div>
                    </div>
                    {/* Step 2 */}
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            2. 혈압
                        </div>
                    </div>
                    {/* Step 3 */}
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            3. 산소포화도
                        </div>
                    </div>
                    {/* Step 4 */}
                    <div className="flex flex-col items-center flex-1 relative">
                        <div className="mt-2 bg-[#B9D6F2] text-[#061A40] px-3 py-1 rounded-full font-bold text-sm shadow-md whitespace-nowrap">
                            4. 심전도
                        </div>
                        <div className="mt-1 text-[#B9D6F2] text-xs font-semibold tracking-wider">측정 중</div>
                    </div>
                </div>

                {/* 타이틀 */}
                <h1 className="text-4xl font-bold tracking-tight">
                    <span className="text-[#B9D6F2]">심전도 측정기</span> 사용 방법
                </h1>

                {/* 사용 방법 가이드 영역 */}
                <div className="w-full max-w-6xl flex-1 mt-8 mb-8 flex flex-col justify-center items-center">
                    <div className="flex flex-row items-stretch justify-center gap-6 w-full max-w-3xl">

                    {/* 1단계 */}
                    <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                        {/* 수정된 부분: aspect-video를 제거하고 h-60 (또는 원하는 높이)를 추가합니다. */}
                        <div className="w-full h-72 mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                            <img src={ecg} alt="심전도 측정" className="w-full h-full object-contain p-2" />
                        </div>
                        <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                            화면에 보이는 것과 같이 손을 올려주세요
                        </p>
                    </div>
                    </div>
                </div>

                {/* 다음 단계 버튼 */}
                <button
                    onClick={handleNext}
                    className="mt-6 mb-2 w-full max-w-xl py-4 bg-[#0353A4] hover:bg-[#006DAA] text-white text-xl md:text-2xl font-bold rounded-2xl border border-[#006DAA] shadow-xl shadow-[#0353A4]/30 transform hover:-translate-y-1 transition-all"
                >
                    측정이 완료되면 비대면진료실로 이동합니다
                </button>
            </main>
        </div>
    );
};

export default Ecg;
