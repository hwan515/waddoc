import { Video, Mic, MicOff, VideoOff, MonitorUp } from 'lucide-react';

const PreJoinRoom = ({
    patientName,
    micEnabled,
    setMicEnabled,
    videoEnabled,
    setVideoEnabled,
    onJoin,
    localVideoRef
}) => {
    return (
        <div className="min-h-screen bg-[#111315] text-white flex flex-col font-sans">
            {/* 상단 헤더 영역 */}
            <div className="flex items-center justify-between p-6">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center">
                        <Video className="w-5 h-5 text-white" />
                    </div>
                    <span className="font-bold text-lg text-slate-200">참여할 준비하기</span>
                </div>
                <button className="flex items-center gap-2 text-slate-300 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-4 py-2 rounded-lg text-sm font-medium">
                    <MonitorUp className="w-4 h-4" />
                    장치에 연결
                </button>
            </div>

            {/* 메인 콘텐츠 영역 */}
            <div className="flex-1 flex flex-col items-center justify-center -mt-10">
                <h2 className="text-2xl font-bold mb-8">{patientName} 환자의 화상 진료실</h2>

                {/* 비디오 프리뷰 영역 */}
                <div className="w-[800px] h-[450px] bg-[#1C1F22] rounded-2xl flex relative overflow-hidden ring-1 ring-white/10 shadow-2xl">
                    {videoEnabled ? (
                        <div className="w-full h-full bg-black flex items-center justify-center">
                            {/* 실제 로컬 카메라 프리뷰 */}
                            <video
                                ref={localVideoRef}
                                autoPlay
                                playsInline
                                muted
                                style={{ transform: 'scaleX(-1)' }}
                                className="w-full h-full object-cover"
                            />
                        </div>
                    ) : (
                        <div className="w-full h-full flex flex-col items-center justify-center">
                            <div className="w-32 h-32 rounded-full bg-[#2A2D31] flex items-center justify-center text-4xl font-bold text-slate-400">
                                나
                            </div>
                            <span className="mt-4 text-slate-400 font-medium tracking-wide">카메라가 꺼져 있습니다</span>
                        </div>
                    )}

                    {!micEnabled && (
                        <div className="absolute top-4 right-4 bg-red-500/80 backdrop-blur px-3 py-1.5 rounded-full flex items-center gap-2 text-sm font-bold shadow-lg">
                            <MicOff className="w-4 h-4" /> 음소거됨
                        </div>
                    )}
                </div>

                {/* 하단 컨트롤 및 참가 버튼 */}
                <div className="w-[800px] flex items-center justify-between mt-8">
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => setMicEnabled(!micEnabled)}
                            className={`flex items-center gap-2 px-5 py-3 rounded-full font-medium transition-all ${micEnabled ? 'bg-white/10 hover:bg-white/20 text-white' : 'bg-red-500 text-white hover:bg-red-600'
                                }`}
                        >
                            {micEnabled ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
                            {micEnabled ? '음소거' : '음소거 해제'}
                            <span className="ml-1 opacity-50 text-xs">▼</span>
                        </button>

                        <button
                            onClick={() => setVideoEnabled(!videoEnabled)}
                            className={`flex items-center gap-2 px-5 py-3 rounded-full font-medium transition-all ${videoEnabled ? 'bg-white/10 hover:bg-white/20 text-white' : 'bg-red-500 text-white hover:bg-red-600'
                                }`}
                        >
                            {videoEnabled ? <Video className="w-5 h-5" /> : <VideoOff className="w-5 h-5" />}
                            {videoEnabled ? '비디오 중지' : '비디오 시작'}
                            <span className="ml-1 opacity-50 text-xs">▼</span>
                        </button>
                    </div>

                    <button
                        onClick={onJoin}
                        className="bg-primary hover:bg-blue-600 text-white px-8 py-3 rounded-full font-bold shadow-lg shadow-primary/30 transition-all hover:-translate-y-0.5"
                    >
                        미팅 시작
                    </button>
                </div>
            </div>
        </div>
    );
};

export default PreJoinRoom;
