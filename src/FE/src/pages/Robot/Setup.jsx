import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const Setup = () => {
    const navigate = useNavigate();
    const [missionId, setMissionId] = useState(localStorage.getItem('robot_mission_id') || '');
    const [sessionId, setSessionId] = useState(localStorage.getItem('robot_session_id') || '');

    const handleSaveAndNext = () => {
        if (!missionId.trim()) {
            alert('Mission ID는 필수입니다.');
            return;
        }
        localStorage.setItem('robot_mission_id', missionId.trim());
        localStorage.setItem('robot_session_id', sessionId.trim());
        navigate('/robot/auth');
    };

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-[#061A40] font-sans relative overflow-hidden">
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-2xl bg-white/10 backdrop-blur-md border border-white/20 p-8 flex flex-col items-center rounded-2xl shadow-2xl text-white">
                <h1 className="text-3xl font-bold mb-4 text-center text-[#B9D6F2]">🛠️ [TEST] 데이터 주입</h1>
                <p className="text-slate-300 mb-8 text-center whitespace-pre-line leading-relaxed text-sm">
                    실제 환경에서는 서버부터 자동으로 할당받거나 로봇단말에 하드웨어적으로 주입되어야 하지만,{'\n'}
                    현재 E2E(End-to-End) 테스트를 위해 수동으로 ID를 주입합니다.
                </p>

                <div className="space-y-6 w-full max-w-md">
                    <div>
                        <label className="block text-sm font-medium text-slate-300 mb-2">
                            Mission ID <span className="text-[#B9D6F2] font-semibold">(필수)</span>
                        </label>
                        <input
                            type="text"
                            value={missionId}
                            onChange={(e) => setMissionId(e.target.value)}
                            placeholder="예: ms_xxxx"
                            className="w-full px-4 py-3 bg-[#061A40] border border-[#3B62A4] rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-[#B9D6F2] flex-1 text-xl tracking-wider text-center"
                        />
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-slate-400 mb-2">
                            Session ID (화상진료 방 입장 시 폴링 오류 대안용)
                        </label>
                        <input
                            type="text"
                            value={sessionId}
                            onChange={(e) => setSessionId(e.target.value)}
                            placeholder="예: ses_xxxx"
                            className="w-full px-4 py-3 bg-[#061A40] border border-[#3B62A4] rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-[#B9D6F2] flex-1 text-xl tracking-wider text-center"
                        />
                    </div>
                </div>

                <div className="mt-10 flex gap-4 w-full max-w-md">
                    <button
                        onClick={handleSaveAndNext}
                        className="px-10 py-4 bg-[#0353A4] hover:bg-[#006DAA] text-white text-xl font-semibold border border-[#B9D6F2]/30 rounded-xl shadow-lg transition-colors w-full flex items-center justify-center gap-2"
                    >
                        확인 및 인증 진행하기 ➔
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Setup;
