import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import PreJoinRoom from '../../components/consultation/PreJoinRoom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import { generateECGData, mockConsultationDetails, mockVitals } from '../../mockdata/consultations';
import { useWebRTC } from '../../hooks/useWebRTC';
import apiClient from '../../utils/api';

const VideoConference = () => {
    // eslint-disable-next-line no-unused-vars
    const { id } = useParams();
    const navigate = useNavigate();

    const [isJoined, setIsJoined] = useState(false);
    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);
    const [ecgData, setEcgData] = useState(generateECGData(50));
    
    // API Data
    const [consultationDetails, setConsultationDetails] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    // Fetch case details
    useEffect(() => {
        const fetchCaseDetails = async () => {
            if (!id || id === 'test-room') {
                // 테스트용 방일 경우 mock 활용
                setConsultationDetails(mockConsultationDetails);
                setIsLoading(false);
                return;
            }
            try {
                const res = await apiClient.get(`/cases/${id}`);
                const caseData = res.data;
                const pt = caseData.patient || {};
                const intake = caseData.intakeSummary || {};

                // 나이 계산
                let age = '-';
                if (pt.birthDate6) {
                    const birthYearStr = pt.birthDate6.substring(0, 2);
                    let birthYear = parseInt(birthYearStr);
                    birthYear += birthYear > 30 ? 1900 : 2000;
                    age = new Date().getFullYear() - birthYear;
                }

                setConsultationDetails({
                    caseId: caseData.caseId,
                    patientName: pt.name || '알 수 없음',
                    patientId: pt.patientId,
                    age: age,
                    gender: pt.gender || 'M',
                    symptoms: intake.selectionReason || '문진 내용이 없습니다.',
                    recentVisits: pt.lastConsultationDate || '최근 진료 기록 없음',
                    department: intake.departmentName || '내과',
                    bloodType: pt.bloodType ? pt.bloodType.replace('_PLUS', '+').replace('_MINUS', '-') : '확인 불가',
                    allergies: '데이터 없음',
                    medicalHistory: '데이터 없음'
                });
            } catch (error) {
                console.error("Failed to fetch case details:", error);
                setConsultationDetails(mockConsultationDetails); // Fallback
            } finally {
                setIsLoading(false);
            }
        };

        fetchCaseDetails();
    }, [id]);

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
        navigate('/emr/dashboard');
    };

    const handleJoin = () => {
        setIsJoined(true);
        joinRoom(); // 화상 통신 시작 및 방 접속
    };

    if (isLoading || !consultationDetails) {
        return <div className="h-screen flex items-center justify-center bg-slate-900 text-white">진료 정보를 불러오는 중입니다...</div>;
    }

    // 의사는 화면 확인용 준비 라운지(Pre-join)를 거치도록 함
    if (!isJoined) {
        return (
            <PreJoinRoom
                patientName={consultationDetails.patientName}
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
            details={consultationDetails}
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
