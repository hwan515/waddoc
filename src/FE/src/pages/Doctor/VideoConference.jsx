import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import PreJoinRoom from '../../components/consultation/PreJoinRoom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import { generateECGData, mockConsultationDetails, mockVitals } from '../../mockdata/consultations';

const VideoConference = () => {
    // eslint-disable-next-line no-unused-vars
    const { id } = useParams();
    const navigate = useNavigate();

    const [isJoined, setIsJoined] = useState(false);
    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);
    const [ecgData, setEcgData] = useState(generateECGData(50));

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
        // 진료 종료 시 대시보드로 이동
        navigate('/doctor/dashboard');
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
                onJoin={() => setIsJoined(true)}
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
        />
    );
};

export default VideoConference;
