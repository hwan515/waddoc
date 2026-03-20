import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import blood1 from '../../../assets/blood_1.png';
import blood2 from '../../../assets/blood_2.png';
import blood3 from '../../../assets/blood_3.png';

const Blood = () => {
    const navigate = useNavigate();
    const [temperatureValue, setTemperatureValue] = useState(null);

    const handleNext = () => {
        // 산소포화도 측정 페이지로 이동
        navigate('/robot/measure/spo2');
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
                    <div className="flex flex-col items-center flex-1 relative">
                        <div className="mt-2 bg-[#B9D6F2] text-[#061A40] px-3 py-1 rounded-full font-bold text-sm shadow-md whitespace-nowrap">
                            2. 혈압
                        </div>
                        <div className="mt-1 text-[#B9D6F2] text-xs font-semibold tracking-wider">측정 중</div>
                    </div>
                    {/* Step 3 */}
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            3. 산소포화도
                        </div>
                    </div>
                    {/* Step 4 */}
                    <div className="flex flex-col items-center flex-1 opacity-50 relative">
                        <div className="mt-2 bg-slate-700/50 text-white px-3 py-1 rounded-full font-medium text-sm whitespace-nowrap border border-slate-500">
                            4. 심전도
                        </div>
                    </div>
                </div>

                {/* 타이틀 */}
                <h1 className="text-4xl font-bold tracking-tight">
                    <span className="text-[#B9D6F2]">혈압계</span> 사용 방법
                </h1>

                {/* 사용 방법 가이드 영역 */}
                <div className="w-full max-w-6xl flex-1 mt-8 mb-8 flex flex-col justify-center">
                    <div className="flex flex-row items-stretch justify-center gap-6 w-full">

                        {/* 1단계 (각 카드의 너비를 동일하게 하기 위해 flex-1 추가) */}
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood1} alt="혈압계 전원 작동" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                커프 부분에 팔을 넣고<br /><span className="text-[#B9D6F2]">검정색 시작 버튼을 누릅니다</span>
                            </p>
                        </div>

                        {/* 2단계 */}
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood2} alt="체온 측정" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                측정이 완료될 때까지<br /><span className="text-[#B9D6F2]">움직이거나 말하지 않습니다</span>
                            </p>
                        </div>

                        {/* 3단계 */}
                        <div className="flex-1 bg-white/5 border border-white/10 rounded-3xl p-6 flex flex-col items-center text-center shadow-lg backdrop-blur-sm">
                            <div className="w-full aspect-video mb-6 overflow-hidden rounded-2xl border border-white/20 bg-white">
                                <img src={blood3} alt="체온 결과 확인" className="w-full h-full object-contain p-2" />
                            </div>
                            <p className="text-lg md:text-xl font-semibold leading-snug break-keep">
                                액정화면에 측정된<br /><span className="text-[#B9D6F2]">혈압을 확인</span>합니다
                                <span className="block mt-2 text-[#B9D6F2] text-sm opacity-80">(자동 저장됩니다)</span>
                            </p>
                        </div>
                    </div>
                </div>

                {/* 다음 단계 버튼 */}
                <button
                    onClick={handleNext}
                    className="mt-6 mb-2 w-full max-w-xl py-4 bg-[#0353A4] hover:bg-[#006DAA] text-white text-xl md:text-2xl font-bold rounded-2xl border border-[#006DAA] shadow-xl shadow-[#0353A4]/30 transform hover:-translate-y-1 transition-all"
                >
                    측정이 완료되면 다음 단계로 넘어갑니다
                </button>
            </main>
        </div>
    );
};

export default Blood;
