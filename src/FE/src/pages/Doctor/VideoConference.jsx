import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LiveKitRoom, RoomAudioRenderer } from '@livekit/components-react';
import '@livekit/components-styles';
import PreJoinRoom from '../../components/consultation/PreJoinRoom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import { generateECGData, mockConsultationDetails, mockVitals } from '../../mockdata/consultations';
import apiClient from '../../utils/api';

const VideoConference = () => {
    // eslint-disable-next-line no-unused-vars
    const { id } = useParams();
    const navigate = useNavigate();

    const [isJoined, setIsJoined] = useState(false);
    const [livekitToken, setLivekitToken] = useState('');
    const [livekitUrl, setLivekitUrl] = useState('');
    const [sessionId, setSessionId] = useState(null);

    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);
    const [ecgData, setEcgData] = useState(generateECGData(50));

    // API 데이터 상태
    const [consultationDetails, setConsultationDetails] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    // 진료 내역 데이터 조회
    useEffect(() => {
        const fetchCaseDetails = async () => {
            if (!id || id === 'test-room' || id.startsWith('RV_')) {
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
                setConsultationDetails(mockConsultationDetails); // 에러 시 더미 데이터 폴백 추가
            } finally {
                setIsLoading(false);
            }
        };

        fetchCaseDetails();
    }, [id]);

    // 강제 화면 송출을 위해 임시 Ref 유지 (PreJoin용)
    const localVideoRef = null;

    // 카메라/마이크 On/Off 상태 동기화 (LiveKitRoom에서 props로 제어됨)
    // PreJoinRoom에서 미디어 초기화를 담당하도록 변경 가능하지만, 현재는 LiveKitRoom 진입 전 상태로만 사용
    useEffect(() => {
        // initCamera(videoEnabled, micEnabled);
        return () => {
            // cleanupMedia();
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

    const handleEndCall = async (summaryData) => {
        if (sessionId && summaryData) {
            try {
                await apiClient.put(`/sessions/${sessionId}/summary`, summaryData);
                console.log("✅ 진료 종료 및 요약 기록 완료");
            } catch (error) {
                console.error("❌ 진료 종료 기록 실패:", error);
            }
        }
        navigate('/emr/dashboard');
    };

    const handleJoin = async () => {
        try {
            if (!id || id === 'test-room' || id.startsWith('RV_')) {
                console.warn('임시(데모) 예약건이므로 방 생성 API를 건너뛰고 데모 모드로 전환합니다.');
                setLivekitToken('test-token');
                setLivekitUrl('wss://test.livekit.cloud');
                setIsJoined(true);
                return;
            }

            // [API 연동] 의사의 진료 세션 생성 및 LiveKit 토큰 발급 요청
            // POST /api/v1/cases/{caseId}/sessions
            console.log(`🚀 [API 호출 준비] 전달받은 URL 파라미터(Case ID): ${id}`);
            console.log(`➜ 호출될 엔드포인트: /api/v1/cases/${id}/sessions`);

            const response = await apiClient.post(`/cases/${id}/sessions`);

            if (response.data && response.data.doctorToken) {
                setLivekitToken(response.data.doctorToken);
                setLivekitUrl(response.data.room?.livekitUrl || 'wss://test.livekit.cloud');
                setSessionId(response.data.sessionId);

                console.log("✅ 의사 세션(LiveKit) 생성 완료:", response.data);
            }

            // 현재 단계(LiveKit 적용)에서는 발급받은 토큰으로 방에 입장
            setIsJoined(true);

        } catch (error) {
            console.error("❌ 세션 생성 API 호출 실패:", error);
            console.warn("백엔드 세션 생성 API 호출에 실패했습니다.\n데모 진행을 위해 가짜 토큰으로 임시 입장합니다.");
            setLivekitToken('test-token');
            setLivekitUrl('wss://test.livekit.cloud');
            setIsJoined(true);
        }
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
                localVideoRef={null}
            />
        );
    }

    // 메인 화상 진료실 (의사 권한으로 접속)
    return (
        <LiveKitRoom
            connect={!!livekitToken && livekitToken !== 'test-token'} // 실제 토큰이 아니면 오프라인 모드 유지 (웹소켓 401 방지)
            video={videoEnabled}
            audio={micEnabled}
            token={livekitToken}
            serverUrl={livekitUrl}
            data-lk-theme="default"
            className="w-full h-full flex flex-col p-0 m-0 border-0 bg-transparent"
            onDisconnected={() => handleEndCall(null)}
        >
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
            />
            {/* LiveKit 오디오 랜더링 허용을 위한 트랙 랜더러 (기본 숨김) */}
            <RoomAudioRenderer />
        </LiveKitRoom>
    );
};

export default VideoConference;
