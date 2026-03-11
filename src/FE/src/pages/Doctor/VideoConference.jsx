import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import PreJoinRoom from '../../components/consultation/PreJoinRoom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import { generateECGData, mockConsultationDetails, mockVitals } from '../../mockdata/consultations';
import { useWebRTC } from '../../hooks/useWebRTC';

const VideoConference = () => {
    // eslint-disable-next-line no-unused-vars
    const { id } = useParams();
    const navigate = useNavigate();

    const [isJoined, setIsJoined] = useState(false);
    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);
    const [ecgData, setEcgData] = useState(generateECGData(50));

    // WebRTC Hook 로드 (의사는 방을 여는 Initiator 역할)
    const {
        localVideoRef,
        remoteVideoRef,
        localStream,
        initCamera,
        joinRoom,
        toggleMedia,
        cleanupMedia
    } = useWebRTC(id || 'test-room', true);

    // 카메라/마이크 On/Off 상태 동기화
    useEffect(() => {
        toggleMedia('audio', micEnabled);
    }, [micEnabled, toggleMedia]);

    useEffect(() => {
        toggleMedia('video', videoEnabled);
    }, [videoEnabled, toggleMedia]);

    // 마운트 시 카메라 권한 요청 및 비디오/오디오 스트림 미리 켜두기 (방 접속 전)
    useEffect(() => {
        initCamera(videoEnabled, micEnabled);

        // 언마운트 시 미디어 스트림 정리
        return () => {
            cleanupMedia();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);


    // 심전도 차트 실시간 업데이트
    useEffect(() => {
        if (!isJoined) return;

        const ecgInterval = setInterval(() => {
            setEcgData(prev => {
                const newData = [...prev.slice(1)];
                const lastTime = prev[prev.length - 1].time;
                newData.push({
                    time: lastTime + 1,
                    value: lastTime % 10 === 0 ? 90 : lastTime % 10 === 1 ? -30 : lastTime % 10 === 2 ? 70 : Math.random() * 10 - 5
                });
                return newData;
            });
        }, 100);

        return () => clearInterval(ecgInterval);
    }, [isJoined]);

    const handleEndCall = () => {
        cleanupMedia(); // 미디어 스트림 정리
        navigate('/doctor/dashboard');
    };

    const handleJoin = () => {
        setIsJoined(true);
        joinRoom(); // 화상 통신 시작 및 방 접속
    };

    // 의사는 화면 확인용 준비 라운지(Pre-join)를 거치도록 함
    if (!isJoined) {
        return (
            <PreJoinRoom
                patientName={mockConsultationDetails.patientName}
                micEnabled={micEnabled}
                setMicEnabled={setMicEnabled}
                videoEnabled={videoEnabled}
                setVideoEnabled={setVideoEnabled}
                onJoin={handleJoin}
                localVideoRef={localVideoRef}
            />
        );
    }

    // 메인 화상 진료실 (의사 권한으로 접속)
    return (
        <ConsultationRoom
            details={mockConsultationDetails}
            vitals={mockVitals}
            ecgData={ecgData}
            micEnabled={micEnabled}
            setMicEnabled={setMicEnabled}
            videoEnabled={videoEnabled}
            setVideoEnabled={setVideoEnabled}
            onEndCall={handleEndCall}
            role="DOCTOR"
            localVideoRef={localVideoRef}
            remoteVideoRef={remoteVideoRef}
            localStream={localStream}
        />
    );
};

export default VideoConference;
