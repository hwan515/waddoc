import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LiveKitRoom, RoomAudioRenderer } from '@livekit/components-react';
import '@livekit/components-styles';
import PreJoinRoom from '../../components/consultation/PreJoinRoom';
import ConsultationRoom from '../../components/consultation/ConsultationRoom';
import useConsultationSummarySave from '../../hooks/useConsultationSummarySave';
import apiClient from '../../utils/api';
import { sanitizeSelectionReason } from '../../utils/intakeSelectionReason';
import {
    LIVEKIT_HIGH_QUALITY_ROOM_OPTIONS,
    LIVEKIT_HIGH_QUALITY_VIDEO_CONSTRAINTS,
} from '../../utils/livekitVideoConfig';

const EMPTY_VITALS = {
    caseId: null,
    temperature: null,
    bloodPressureSys: null,
    bloodPressureDia: null,
    heartRate: null,
    spO2: null,
    ecgWaveform: null,
    ecgSamplingHz: null,
    ecgDurationSeconds: null,
    measuredAt: null,
    createdAt: null,
    updatedAt: null,
};

const DEMO_CONSULTATION_DETAILS = {
    caseId: 'test-room',
    patientName: '데모 환자',
    patientId: 'demo-patient',
    age: 34,
    gender: 'MALE',
    symptoms: '테스트용 진료입니다.',
    recentVisits: '최근 진료 기록 없음',
    department: '내과',
    bloodType: '확인 불가',
    allergies: '데이터 없음',
    medicalHistory: '데이터 없음',
    doctorName: '데모 의사',
};

const DEMO_VITALS = {
    ...EMPTY_VITALS,
    temperature: 36.7,
    bloodPressureSys: 128,
    bloodPressureDia: 82,
    heartRate: 72,
    spO2: 98,
    ecgWaveform: [0.12, 0.18, 0.11, -0.05, 0.45, 1.1, 0.38, -0.12, 0.08, 0.1, 0.14, 0.22, 0.12, -0.08, 0.5, 1.05, 0.33, -0.1, 0.07, 0.09],
    ecgSamplingHz: 25,
    ecgDurationSeconds: 8,
    measuredAt: new Date().toISOString(),
};

const DOCTOR_RECONNECT_WINDOW_MS = 30000;
const DOCTOR_RECONNECT_INTERVAL_MS = 3000;

const normalizeVitals = (vitals) => ({
    ...EMPTY_VITALS,
    ...(vitals || {}),
});

const createFallbackDetails = (caseId) => ({
    caseId: caseId || null,
    patientName: '알 수 없음',
    patientId: null,
    age: '-',
    gender: null,
    symptoms: '문진 내용이 없습니다.',
    recentVisits: '최근 진료 기록 없음',
    department: '내과',
    bloodType: '확인 불가',
    allergies: '데이터 없음',
    medicalHistory: '데이터 없음',
    doctorName: '담당의 미확인',
});

const extractApiErrorMessage = (error, fallbackMessage) => (
    error?.response?.data?.message
    || error?.response?.data?.error
    || error?.message
    || fallbackMessage
);

const VideoConference = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const isDemoMode = !id || id === 'test-room';

    const [isJoined, setIsJoined] = useState(false);
    const [livekitToken, setLivekitToken] = useState('');
    const [livekitUrl, setLivekitUrl] = useState('');
    const [sessionId, setSessionId] = useState(null);
    const [joinError, setJoinError] = useState('');
    const [isJoining, setIsJoining] = useState(false);
    const [isReconnecting, setIsReconnecting] = useState(false);
    const [roomRenderKey, setRoomRenderKey] = useState(0);
    const isDoctorEndingRef = useRef(false);
    const isUnmountedRef = useRef(false);
    const reconnectTimerRef = useRef(null);
    const reconnectDeadlineRef = useRef(0);
    const isReconnectingRef = useRef(false);

    const [micEnabled, setMicEnabled] = useState(true);
    const [videoEnabled, setVideoEnabled] = useState(true);

    // API 데이터 상태
    const [consultationDetails, setConsultationDetails] = useState(null);
    const [vitals, setVitals] = useState(EMPTY_VITALS);
    const [isLoading, setIsLoading] = useState(true);
    const {
        isSavingSummary,
        summarySaveStatus,
        saveSummary,
    } = useConsultationSummarySave({
        sessionId,
        isDemoMode,
    });

    const clearReconnectTimer = () => {
        if (reconnectTimerRef.current) {
            window.clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
    };

    const applyRoomConnection = ({ doctorToken, nextLivekitUrl, nextSessionId }) => {
        if (isUnmountedRef.current) {
            return;
        }
        setLivekitToken(doctorToken);
        setLivekitUrl(nextLivekitUrl);
        setSessionId(nextSessionId);
        setRoomRenderKey((previous) => previous + 1);
    };

    const fetchDoctorRoomConnection = async () => {
        const response = await apiClient.post(`/cases/${id}/sessions`);
        const doctorToken = response.data?.doctorToken;
        const nextLivekitUrl = response.data?.room?.livekitUrl;
        const nextSessionId = response.data?.sessionId;

        if (!doctorToken || !nextLivekitUrl || !nextSessionId) {
            throw new Error('진료실 연결 정보가 올바르지 않습니다.');
        }

        return { doctorToken, nextLivekitUrl, nextSessionId };
    };

    const stopReconnectFlow = () => {
        clearReconnectTimer();
        isReconnectingRef.current = false;
        if (!isUnmountedRef.current) {
            setIsReconnecting(false);
        }
    };

    const handleReconnectFailure = () => {
        stopReconnectFlow();
        if (isUnmountedRef.current) {
            return;
        }
        setLivekitToken('');
        setLivekitUrl('');
        setSessionId(null);
        setJoinError('진료실 연결이 끊겼습니다. 다시 입장해 주세요.');
        setIsJoined(false);
    };

    const attemptReconnect = async () => {
        try {
            const connection = await fetchDoctorRoomConnection();
            if (isUnmountedRef.current) {
                return;
            }
            clearReconnectTimer();
            applyRoomConnection(connection);
            setJoinError('');
            stopReconnectFlow();
        } catch {
            if (isUnmountedRef.current) {
                return;
            }

            if (Date.now() < reconnectDeadlineRef.current && !isDoctorEndingRef.current) {
                reconnectTimerRef.current = window.setTimeout(() => {
                    void attemptReconnect();
                }, DOCTOR_RECONNECT_INTERVAL_MS);
                return;
            }

            handleReconnectFailure();
        }
    };

    const startReconnectFlow = () => {
        if (isDemoMode || isDoctorEndingRef.current || isReconnectingRef.current) {
            return;
        }

        clearReconnectTimer();
        reconnectDeadlineRef.current = Date.now() + DOCTOR_RECONNECT_WINDOW_MS;
        isReconnectingRef.current = true;
        setIsReconnecting(true);
        void attemptReconnect();
    };

    useEffect(() => () => {
        isUnmountedRef.current = true;
        clearReconnectTimer();
    }, []);

    // 진료 내역 데이터 조회
    useEffect(() => {
        const fetchCaseDetails = async () => {
            if (isDemoMode) {
                // 테스트용 방일 경우 mock 활용
                setConsultationDetails(DEMO_CONSULTATION_DETAILS);
                setVitals(DEMO_VITALS);
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
                    symptoms: sanitizeSelectionReason(
                        intake.selectionReason,
                        '문진 내용이 없습니다.'
                    ),
                    recentVisits: pt.lastConsultationDate || '최근 진료 기록 없음',
                    department: intake.departmentName || '내과',
                    bloodType: pt.bloodType ? pt.bloodType.replace('_PLUS', '+').replace('_MINUS', '-') : '확인 불가',
                    allergies: '데이터 없음',
                    medicalHistory: '데이터 없음',
                    doctorName: caseData.doctor?.name || '담당의 미확인',
                });
                setVitals(normalizeVitals(caseData.vitals));
            } catch {
                setConsultationDetails(createFallbackDetails(id));
                setVitals(EMPTY_VITALS);
            } finally {
                setIsLoading(false);
            }
        };

        fetchCaseDetails();
    }, [id, isDemoMode]);

    const handleEndCall = async (summaryData) => {
        if (summaryData) {
            isDoctorEndingRef.current = true;
            const result = await saveSummary(summaryData, {
                successMessage: '진료 요약을 저장하고 진료를 종료했습니다.',
                savingMessage: '진료 종료 기록을 저장하는 중입니다.',
                errorMessage: '진료 종료 기록 저장에 실패했습니다.',
            });

            if (!result.ok) {
                isDoctorEndingRef.current = false;
                return;
            }
        }

        navigate('/emr/dashboard');
    };

    const handleJoin = async () => {
        if (isJoining) {
            return;
        }

        setJoinError('');
        setIsJoining(true);

        try {
            if (isDemoMode) {
                console.warn('임시(데모) 예약건이므로 방 생성 API를 건너뛰고 데모 모드로 전환합니다.');
                setLivekitToken('test-token');
                setLivekitUrl('wss://test.livekit.cloud');
                setSessionId(null);
                setRoomRenderKey((previous) => previous + 1);
                setIsJoined(true);
                return;
            }

            const connection = await fetchDoctorRoomConnection();
            applyRoomConnection(connection);

            // 현재 단계(LiveKit 적용)에서는 발급받은 토큰으로 방에 입장
            setIsJoined(true);

        } catch (error) {
            setLivekitToken('');
            setLivekitUrl('');
            setSessionId(null);
            setIsJoined(false);
            setJoinError(extractApiErrorMessage(error, '진료실 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.'));
        } finally {
            setIsJoining(false);
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
                errorMessage={joinError}
                isJoining={isJoining}
            />
        );
    }

    // 메인 화상 진료실 (의사 권한으로 접속)
    return (
        <LiveKitRoom
            key={roomRenderKey}
            connect={!!livekitToken && livekitToken !== 'test-token'} // 실제 토큰이 아니면 오프라인 모드 유지 (웹소켓 401 방지)
            video={videoEnabled ? LIVEKIT_HIGH_QUALITY_VIDEO_CONSTRAINTS : false}
            audio={micEnabled}
            token={livekitToken}
            serverUrl={livekitUrl}
            options={LIVEKIT_HIGH_QUALITY_ROOM_OPTIONS}
            data-lk-theme="default"
            className="relative w-full h-full flex flex-col p-0 m-0 border-0 bg-transparent"
            onDisconnected={() => {
                if (isDoctorEndingRef.current) {
                    return;
                }
                startReconnectFlow();
            }}
        >
            {isReconnecting && (
                <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-4 bg-slate-950/88 text-center text-white">
                    <div className="h-16 w-16 animate-spin rounded-full border-4 border-cyan-400 border-t-transparent" />
                    <div>
                        <p className="text-2xl font-semibold">진료실 연결을 복구하는 중입니다.</p>
                        <p className="mt-2 text-sm text-slate-300">네트워크가 잠시 불안정할 수 있습니다. 잠시만 기다려 주세요.</p>
                    </div>
                </div>
            )}
            <ConsultationRoom
                details={consultationDetails}
                vitals={vitals}
                micEnabled={micEnabled}
                setMicEnabled={setMicEnabled}
                videoEnabled={videoEnabled}
                setVideoEnabled={setVideoEnabled}
                onEndCall={handleEndCall}
                isSavingSummary={isSavingSummary}
                summarySaveStatus={summarySaveStatus}
                role="DOCTOR"
            />
            {/* LiveKit 오디오 랜더링 허용을 위한 트랙 랜더러 (기본 숨김) */}
            <RoomAudioRenderer />
        </LiveKitRoom>
    );
};

export default VideoConference;
