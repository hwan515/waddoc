import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import { generateECGData, mockConsultationDetails, mockVitals } from '../../mockdata/consultations';

const Conference = () => {
    const navigate = useNavigate();

    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);
    const [ecgData, setEcgData] = useState(generateECGData(50));

    // 심전도 차트 실시간 업데이트 (환자 화면에서도 동일하게 보여줌)
    useEffect(() => {
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
    }, []);

    const handleEndCall = () => {
        // 환자가 진료를 종료하면 홈 화면으로 이동하거나, 
        // 키오스크/로봇 환경에 맞는 종료 안내 화면으로 이동
        navigate('/');
    };

    // 환자 로봇 화상 진료실 (PATIENT 권한으로 접속)
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
            role="PATIENT" // 역할 기반으로 화면 레이아웃 (나 vs 의사) 자동 전환됨
        />
    );
};

export default Conference;
