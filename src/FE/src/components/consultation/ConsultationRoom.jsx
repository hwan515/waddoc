import { useState, useEffect } from 'react';
import {
    Mic, MicOff, Video, VideoOff, Settings, LogOut,
    MonitorUp, Activity, Droplet, Heart, Thermometer
} from 'lucide-react';
import { LineChart, Line, ResponsiveContainer, YAxis } from 'recharts';

const ConsultationRoom = ({
    details,      // 환자/의사 등 공통 정보
    vitals,       // 환자 바이탈 정보
    ecgData,      // ECG 라이브 데이터
    micEnabled,
    setMicEnabled,
    videoEnabled,
    setVideoEnabled,
    onEndCall,
    role = 'DOCTOR' // 'DOCTOR' 또는 'PATIENT'
}) => {
    const [durationSec, setDurationSec] = useState(0);

    // 진료 시간 타이머
    useEffect(() => {
        const timerInterval = setInterval(() => {
            setDurationSec(prev => prev + 1);
        }, 1000);
        return () => clearInterval(timerInterval);
    }, []);

    const formatTime = (sec) => {
        const m = Math.floor(sec / 60).toString().padStart(2, '0');
        const s = (sec % 60).toString().padStart(2, '0');
        return `${m}:${s}`;
    };

    // 상대방 화면과 내 화면을 역할에 따라 구분
    const isDoctor = role === 'DOCTOR';
    const mainVideoLabel = isDoctor ? `${details.patientName} (환자)` : `${details.doctorName} (의사)`;
    const smallVideoLabel = isDoctor ? `나 (의사)` : `나 (환자)`;
    const mainVideoSrc = isDoctor
        ? "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=1200"
        : "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?auto=format&fit=crop&q=80&w=1200"; // 의사 이미지 대체

    return (
        <div className="min-h-screen bg-slate-50 flex flex-col font-sans h-screen overflow-hidden">
            {/* 상단 컨트롤 바 */}
            <div className="h-14 bg-white border-b border-slate-200 flex items-center justify-between px-6 shrink-0 shadow-sm z-10">
                <div className="flex items-center gap-4">
                    <h1 className="font-extrabold text-slate-800 text-lg tracking-tight uppercase">WHAT DOCTOR</h1>
                    <div className="w-px h-4 bg-slate-300"></div>
                    <div className="flex items-center gap-3 text-sm font-medium text-slate-600">
                        <span>{details.roomNumber}</span>
                        <div className="w-1 h-1 bg-slate-300 rounded-full"></div>
                        <span className="text-primary w-12 text-center font-bold tracking-wider">{formatTime(durationSec)}</span>
                        <div className="w-1 h-1 bg-slate-300 rounded-full"></div>
                        <span>담당의사: {details.doctorName}</span>
                        <div className="w-1 h-1 bg-slate-300 rounded-full"></div>
                        <span className="font-bold text-slate-800">환자: {details.patientName}</span>
                    </div>
                </div>
                <div className="flex items-center gap-3 text-sm font-medium">
                    <div className="flex items-center gap-1.5 text-green-600 bg-green-50 px-2.5 py-1 rounded-full">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        연결 상태: Good
                    </div>
                    <span className="text-slate-400 text-xs tracking-wide">지연시간: 40 ms</span>
                </div>
            </div>

            {/* 메인 콘텐츠 영역 */}
            <div className="flex-1 flex p-4 gap-4 overflow-hidden h-full">
                {/* 왼쪽: 비디오 스트림 영역 */}
                <div className="flex-1 flex flex-col gap-4 min-w-[60%]">
                    {/* 메인 비디오 (상대방) */}
                    <div className="flex-1 bg-slate-900 rounded-2xl relative overflow-hidden shadow-lg border border-slate-200">
                        <div className="absolute inset-0 flex items-center justify-center text-slate-500 bg-[#1A1C20]">
                            <img
                                src={mainVideoSrc}
                                alt="Remote"
                                className="w-full h-full object-cover opacity-90"
                            />
                        </div>
                        {/* 레이블 및 상태 표시 */}
                        <div className="absolute top-4 left-4 bg-black/40 backdrop-blur-sm px-3 py-1.5 rounded-lg text-white text-sm font-medium flex items-center gap-2">
                            {mainVideoLabel}
                        </div>
                        <div className="absolute bottom-4 left-4 bg-black/40 backdrop-blur-sm px-3 py-1.5 rounded-lg text-white text-sm font-medium flex items-center gap-2">
                            <Mic className="w-4 h-4" /> 상대방 마이크 켬
                        </div>
                    </div>

                    {/* 작은 비디오 (나) & 하단 조작 바 */}
                    <div className="h-48 shrink-0 flex gap-4">
                        {/* 로컬 비디오 */}
                        <div className="w-64 bg-slate-900 rounded-2xl relative overflow-hidden shadow-lg border border-slate-200">
                            <div className="absolute inset-0 flex items-center justify-center text-slate-500 bg-[#2A2D31]">
                                {videoEnabled ? (
                                    <div className="w-16 h-16 rounded-full bg-slate-600 flex items-center justify-center text-xl font-bold text-white">
                                        나
                                    </div>
                                ) : (
                                    <div className="flex flex-col items-center">
                                        <div className="w-16 h-16 rounded-full bg-slate-700 flex items-center justify-center text-xl font-bold text-slate-400">
                                            나
                                        </div>
                                    </div>
                                )}
                            </div>
                            <div className="absolute bottom-3 left-3 bg-black/40 backdrop-blur-sm px-2.5 py-1 rounded text-white text-xs font-medium flex items-center gap-1.5">
                                {micEnabled ? <Mic className="w-3.5 h-3.5" /> : <MicOff className="w-3.5 h-3.5" />}
                                {smallVideoLabel}
                            </div>
                        </div>

                        {/* 화상 회의 컨트롤 바 */}
                        <div className="flex-1 bg-white border border-slate-200 rounded-2xl shadow-sm flex items-center justify-center gap-4">
                            <button
                                onClick={() => setMicEnabled(!micEnabled)}
                                className={`w-14 h-14 rounded-full flex items-center justify-center shadow-sm transition-all ${micEnabled ? 'bg-slate-100 hover:bg-slate-200 text-slate-700' : 'bg-red-100 hover:bg-red-200 text-red-600'
                                    }`}
                            >
                                {micEnabled ? <Mic className="w-6 h-6" /> : <MicOff className="w-6 h-6" />}
                            </button>

                            <button
                                onClick={() => setVideoEnabled(!videoEnabled)}
                                className={`w-14 h-14 rounded-full flex items-center justify-center shadow-sm transition-all ${videoEnabled ? 'bg-slate-100 hover:bg-slate-200 text-slate-700' : 'bg-red-100 hover:bg-red-200 text-red-600'
                                    }`}
                            >
                                {videoEnabled ? <Video className="w-6 h-6" /> : <VideoOff className="w-6 h-6" />}
                            </button>

                            <div className="w-px h-8 bg-slate-200 mx-2"></div>

                            <button className="w-14 h-14 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center shadow-sm transition-colors">
                                <MonitorUp className="w-6 h-6" />
                            </button>
                            <button className="w-14 h-14 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center shadow-sm transition-colors">
                                <Settings className="w-6 h-6" />
                            </button>

                            <div className="w-px h-8 bg-slate-200 mx-2"></div>

                            <button
                                onClick={onEndCall}
                                className="px-6 h-14 rounded-full bg-red-500 hover:bg-red-600 text-white font-bold flex items-center justify-center gap-2 shadow-lg shadow-red-500/20 transition-all hover:scale-105"
                            >
                                <LogOut className="w-5 h-5" />
                                진료 종료
                            </button>
                        </div>
                    </div>
                </div>

                {/* 오른쪽: 환자 바이탈 모니터링 대시보드 */}
                <div className="w-[360px] shrink-0 flex flex-col gap-4 h-full">
                    {/* 바이탈 카드들 */}
                    <div className="bg-white border border-slate-200 rounded-2xl shadow-sm p-5 flex flex-col gap-4">
                        <div className="flex items-center justify-between mb-2">
                            <h3 className="font-extrabold text-slate-800 text-[15px] flex items-center gap-2">
                                <Activity className="w-4 h-4 text-primary" />
                                실시간 환자 바이탈
                            </h3>
                            <span className="flex items-center gap-1.5 px-2 py-0.5 bg-green-50 text-green-600 rounded text-[10px] font-bold">
                                <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></div> LIVE
                            </span>
                        </div>

                        {/* 산소포화도 SpO2 */}
                        <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100/50 hover:bg-slate-100 transition-colors">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-500 flex items-center justify-center">
                                    <Droplet className="w-5 h-5" />
                                </div>
                                <span className="font-bold text-slate-700 text-sm">SpO₂ 산소포화도</span>
                            </div>
                            <div className="flex items-end gap-1">
                                <span className="font-extrabold text-2xl text-slate-800">{vitals.spO2}</span>
                                <span className="text-slate-400 text-xs pb-1 font-medium">%</span>
                            </div>
                        </div>

                        {/* 심박수 HR */}
                        <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100/50 hover:bg-slate-100 transition-colors">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-red-100 text-red-500 flex items-center justify-center">
                                    <Heart className="w-5 h-5 fill-current animate-pulse" style={{ animationDuration: '1s' }} />
                                </div>
                                <span className="font-bold text-slate-700 text-sm">HR 심박수</span>
                            </div>
                            <div className="flex items-end gap-1">
                                <span className="font-extrabold text-2xl text-red-500">{vitals.heartRate}</span>
                                <span className="text-slate-400 text-xs pb-1 font-medium">bpm</span>
                            </div>
                        </div>

                        {/* 혈압 BP */}
                        <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100/50 hover:bg-slate-100 transition-colors">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-indigo-100 text-indigo-500 flex items-center justify-center">
                                    <Activity className="w-5 h-5" />
                                </div>
                                <span className="font-bold text-slate-700 text-sm">BP 혈압</span>
                            </div>
                            <div className="flex items-end gap-1">
                                <span className="font-extrabold text-2xl text-slate-800 tracking-tight">
                                    {vitals.bloodPressureSys}<span className="text-lg text-slate-400 font-medium">/</span>{vitals.bloodPressureDia}
                                </span>
                            </div>
                        </div>

                        {/* 체온 Temp */}
                        <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100/50 hover:bg-slate-100 transition-colors">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-orange-100 text-orange-500 flex items-center justify-center">
                                    <Thermometer className="w-5 h-5" />
                                </div>
                                <span className="font-bold text-slate-700 text-sm">Temp 체온</span>
                            </div>
                            <div className="flex items-end gap-1">
                                <span className="font-extrabold text-2xl text-slate-800">{vitals.temperature}</span>
                                <span className="text-slate-400 text-xs pb-1 font-medium">°C</span>
                            </div>
                        </div>
                    </div>

                    {/* ECG 실시간 연속 심전도 차트 */}
                    <div className="flex-1 bg-white border border-slate-200 rounded-2xl shadow-sm p-4 flex flex-col min-h-0">
                        <div className="flex items-center justify-between mb-4 shrink-0">
                            <h3 className="font-extrabold text-slate-800 text-sm">실시간 심전도 (ECG)</h3>
                        </div>
                        <div className="flex-1 bg-[#1A1C20] rounded-xl border border-slate-800 overflow-hidden relative">
                            {/* 차트 배경 그리드 오버레이 */}
                            <div className="absolute inset-0 opacity-10" style={{
                                backgroundImage: `linear-gradient(rgba(255, 255, 255, 0.2) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.2) 1px, transparent 1px)`,
                                backgroundSize: '20px 20px'
                            }}></div>

                            {/* Recharts를 이용한 애니메이션 없는 스트리밍 효과 적용 */}
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={ecgData} margin={{ top: 10, right: 0, left: 0, bottom: 0 }}>
                                    <YAxis domain={[-50, 150]} hide />
                                    <Line
                                        type="monotone"
                                        dataKey="value"
                                        stroke="#4ade80"
                                        strokeWidth={2}
                                        dot={false}
                                        isAnimationActive={false} // 순수 스트리밍 형태로 렌더링하기 위해 제거
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ConsultationRoom;
