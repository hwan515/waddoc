import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/api';
import { 
    LiveKitRoom, 
    RoomAudioRenderer, 
    useTracks, 
    useLocalParticipant, 
    VideoTrack 
} from '@livekit/components-react';
import { Track } from 'livekit-client';
import '@livekit/components-styles';

// 내부 컨퍼런스 UI 컴포넌트
const ConferenceUI = () => {
    // LiveKit Hooks: 로컬 참가자와 원격 참가자의 비디오 트랙을 가져옴
    const { localParticipant } = useLocalParticipant();
    const localVideoTrack = useTracks([Track.Source.Camera]).find((t) => t.participant.identity === localParticipant.identity);
    const remoteVideoTracks = useTracks([Track.Source.Camera]).filter((t) => t.participant.identity !== localParticipant.identity);
    const remoteTrack = remoteVideoTracks.length > 0 ? remoteVideoTracks[0] : null;

    return (
        <div className="w-screen h-screen bg-slate-900 relative overflow-hidden">
            {/* 메인 비디오 (의사 화면 - 전체 화면) */}
            <div className="w-full h-full absolute inset-0 z-0 bg-slate-900">
                {remoteTrack ? (
                    <VideoTrack trackRef={remoteTrack} className="w-full h-full object-cover" />
                ) : (
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                        <span className="text-white/30 text-3xl font-bold tracking-widest">
                            의사 화면 대기 중...
                        </span>
                    </div>
                )}
            </div>

            {/* 내 비디오 (PIP, 우측 하단) */}
            <div className="absolute bottom-0 right-0 w-[480px] h-[360px] bg-slate-800 border-l border-t border-slate-700 shadow-2xl overflow-hidden z-10">
                {localVideoTrack ? (
                    <VideoTrack trackRef={localVideoTrack} className="w-full h-full object-cover custom-video-mirror" />
                ) : (
                    <div className="absolute inset-0 flex items-center justify-center bg-[#FFF2CC] pointer-events-none">
                        <div className="w-8 h-8 border-4 border-slate-400 border-t-transparent rounded-full animate-spin mb-2 mx-auto"></div>
                        <span className="text-black/50 text-2xl font-bold tracking-widest block text-center mt-2">내 화면 연결 중...</span>
                    </div>
                )}
            </div>
            
            <style dangerouslySetInnerHTML={{
                __html: `
                .custom-video-mirror {
                    transform: scaleX(-1);
                }
            `}} />
        </div>
    );
};

const Conference = () => {
    const navigate = useNavigate();
    
    // 컴포넌트 마운트 시 로컬 상태 초기화
    const [livekitToken, setLivekitToken] = useState('test-token');
    const [livekitUrl, setLivekitUrl] = useState('wss://test.livekit.cloud');
    const [isWaiting, setIsWaiting] = useState(true);

    useEffect(() => {
        let isPolling = true;

        const terminalToken = localStorage.getItem('webrtc_terminal_token');
        const sessionId = localStorage.getItem('robot_session_id') || "ses_L6pQr1";
        console.log("📡 [대기방] 접속 대기 중인 세션 ID:", sessionId);
        
        // 의사 세션이 생성될 때까지 폴링하여 환자 토큰을 요청
        const pollForToken = async () => {
            if (!isPolling) return;

            try {
                if (!terminalToken) throw new Error("단말 토큰이 없습니다.");

                const response = await apiClient.post(`/sessions/${sessionId}/participants/patient/token`, {}, {
                    headers: { 'Authorization': `Bearer ${terminalToken}` }
                });

                if (response.data && response.data.patientToken) {
                    setLivekitToken(response.data.patientToken);
                    setLivekitUrl(response.data.room?.livekitUrl || 'wss://test.livekit.cloud');
                    setIsWaiting(false);
                    return; // 성공 시 폴링 중단
                }
            } catch (err) {
                // 아직 준비 안된(403, 404 등) 경우 1초 뒤 재시도
                console.warn("세션 접속 대기 중...", err?.response?.data || err.message);
                if (isPolling) {
                    setTimeout(pollForToken, 3000);
                }
            }
        };

        pollForToken();

        return () => {
            isPolling = false;
        };
    }, []);

    const handleDisconnected = () => {
        // 통화 종료 시 완료 화면으로 이동
        navigate('/robot/finish');
    };

    if (isWaiting) {
        return (
            <div className="w-screen h-screen bg-slate-900 flex flex-col items-center justify-center p-8 text-center text-white font-sans">
                <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mb-6"></div>
                <h2 className="text-3xl font-bold mb-3 tracking-widest text-[#B9D6F2]">
                    의사 선생님을 기다리고 있습니다
                </h2>
                <p className="text-xl text-slate-400 font-medium">연결 시 잠시 화면이 깜빡일 수 있습니다...</p>
            </div>
        );
    }

    return (
        <LiveKitRoom
            connect={livekitToken !== 'test-token'} // 실제로는 !!livekitToken 으로 체크
            video={true}
            audio={true}
            token={livekitToken}
            serverUrl={livekitUrl}
            data-lk-theme="default"
            className="w-full h-full p-0 m-0 border-0 bg-transparent"
            onDisconnected={handleDisconnected}
        >
            <ConferenceUI />
            <RoomAudioRenderer />
        </LiveKitRoom>
    );
};

export default Conference;
