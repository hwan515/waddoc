import { useNavigate } from 'react-router-dom';
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
    
    // 로컬 스토리지에서 환자용 토큰 및 URL 가져오기 (없으면 테스트 토큰 임시 할당)
    const livekitToken = localStorage.getItem('webrtc_patient_token') || 'test-token';
    const livekitUrl = localStorage.getItem('webrtc_livekit_url') || 'wss://test.livekit.cloud';

    const handleDisconnected = () => {
        // 통화 종료 시 완료 화면으로 이동
        navigate('/robot/finish');
    };

    return (
        <LiveKitRoom
            connect={livekitToken !== 'test-token'} // 가짜 토큰이면 실제 웹소켓 접속 시도 안함
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
