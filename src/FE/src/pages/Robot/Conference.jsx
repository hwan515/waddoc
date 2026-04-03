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
import {
    LIVEKIT_HIGH_QUALITY_ROOM_OPTIONS,
    LIVEKIT_HIGH_QUALITY_VIDEO_CONSTRAINTS,
} from '../../utils/livekitVideoConfig';

const TERMINAL_SESSION_STATUSES = new Set(['COMPLETED', 'FAILED', 'ABANDONED']);

const ConferenceUI = () => {
    const { localParticipant } = useLocalParticipant();
    const localVideoTrack = useTracks([Track.Source.Camera]).find((trackRef) => trackRef.participant.identity === localParticipant.identity);
    const remoteVideoTracks = useTracks([Track.Source.Camera]).filter((trackRef) => trackRef.participant.identity !== localParticipant.identity);
    const remoteTrack = remoteVideoTracks.length > 0 ? remoteVideoTracks[0] : null;

    return (
        <div className="w-screen h-screen bg-slate-900 relative overflow-hidden">
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

            <div className="absolute bottom-0 right-0 w-120 h-90 bg-slate-800 border-l border-t border-slate-700 shadow-2xl overflow-hidden z-10">
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
    const [livekitToken, setLivekitToken] = useState('');
    const [livekitUrl, setLivekitUrl] = useState('');
    const [isWaiting, setIsWaiting] = useState(true);
    const [errorMsg, setErrorMsg] = useState('');

    useEffect(() => {
        let isPolling = true;

        const terminalToken = localStorage.getItem('robot_mission_terminal_token');
        const missionId = localStorage.getItem('current_mission_id');

        const pollForToken = async () => {
            if (!isPolling) return;

            try {
                if (!terminalToken || !missionId) {
                    throw new Error("미션 또는 단말 토큰이 없습니다.");
                }

                const response = await apiClient.post(`/missions/${missionId}/participants/patient/token`, {}, {
                    headers: { Authorization: `Bearer ${terminalToken}` }
                });

                const patientToken = response?.data?.patientToken;
                const nextLivekitUrl = response?.data?.room?.livekitUrl;
                if (patientToken && nextLivekitUrl) {
                    setLivekitToken(patientToken);
                    setLivekitUrl(nextLivekitUrl);
                    setErrorMsg('');
                    setIsWaiting(false);
                    return;
                }
            } catch (error) {
                console.warn("세션 접속 대기 중...", error?.response?.data || error.message);
                setErrorMsg('');
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

    useEffect(() => {
        let isPolling = true;
        let timerId = null;

        const terminalToken = localStorage.getItem('robot_mission_terminal_token');
        const missionId = localStorage.getItem('current_mission_id');

        const pollConsultationStatus = async () => {
            if (!isPolling || !terminalToken || !missionId) {
                return;
            }

            try {
                const response = await apiClient.get(`/missions/${missionId}/consultation-status`, {
                    headers: { Authorization: `Bearer ${terminalToken}` }
                });
                const sessionStatus = response?.data?.status;

                if (TERMINAL_SESSION_STATUSES.has(sessionStatus)) {
                    navigate('/robot/finish', { replace: true });
                    return;
                }
            } catch (error) {
                const status = error?.response?.status;
                if (status && status !== 404) {
                    console.warn('진료 상태 조회에 실패했습니다.', error?.response?.data || error.message);
                }
            }

            if (isPolling) {
                timerId = setTimeout(pollConsultationStatus, 2000);
            }
        };

        pollConsultationStatus();

        return () => {
            isPolling = false;
            if (timerId) {
                clearTimeout(timerId);
            }
        };
    }, [navigate]);

    const handleDisconnected = () => {
        navigate('/robot/finish', { replace: true });
    };

    if (isWaiting) {
        return (
            <div className="w-screen h-screen bg-slate-900 flex flex-col items-center justify-center p-8 text-center text-white font-sans">
                <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mb-6"></div>
                <h2 className="text-3xl font-bold mb-3 tracking-widest text-secondary">
                    의사 선생님을 기다리고 있습니다
                </h2>
                <p className="text-xl text-slate-400 font-medium">연결 시 잠시 화면이 깜빡일 수 있습니다...</p>
                {errorMsg && <p className="mt-4 text-sm text-red-300">{errorMsg}</p>}
            </div>
        );
    }

    return (
        <LiveKitRoom
            connect={Boolean(livekitToken && livekitUrl)}
            video={LIVEKIT_HIGH_QUALITY_VIDEO_CONSTRAINTS}
            audio={true}
            token={livekitToken}
            serverUrl={livekitUrl}
            options={LIVEKIT_HIGH_QUALITY_ROOM_OPTIONS}
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
