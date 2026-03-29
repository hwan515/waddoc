import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import apiClient from '../../utils/api';
import { getRobotTerminalConfig } from '../../utils/runtimeConfig';

const CURRENT_MISSION_POLL_INTERVAL_MS = 3000;
const GREETING_REDIRECT_DELAY_MS = 1800;

const clearRobotSessionState = () => {
    localStorage.removeItem('robot_mission_id');
    localStorage.removeItem('robot_session_id');
    localStorage.removeItem('webrtc_terminal_token');
    localStorage.removeItem('robot_device_terminal_token');
    localStorage.removeItem('robot_mission_terminal_token');
    localStorage.removeItem('current_mission_id');
    localStorage.removeItem('current_patient_name');
};

const extractApiErrorMessage = (error, fallbackMessage) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || fallbackMessage
);

const Home = () => {
    const navigate = useNavigate();
    const [screenState, setScreenState] = useState('bootstrapping');
    const [currentMission, setCurrentMission] = useState(null);
    const [errorMsg, setErrorMsg] = useState('');
    const [isStarting, setIsStarting] = useState(false);
    const greetingTimerRef = useRef(null);

    useEffect(() => {
        let isCancelled = false;
        let pollTimerId = null;
        const bootstrapDeviceTerminal = async (terminalId, terminalKey) => {
            const response = await apiClient.post('/terminal/bootstrap-token', {
                terminalId,
                terminalKey,
            });

            const deviceTerminalToken = response?.data?.deviceTerminalToken;
            if (!deviceTerminalToken) {
                throw new Error('차량 단말 토큰이 비어 있습니다.');
            }

            localStorage.setItem('robot_device_terminal_token', deviceTerminalToken);
            return deviceTerminalToken;
        };

        const fetchCurrentMission = async (token) => {
            const response = await apiClient.get('/terminal/current-mission', {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            });
            return response?.data;
        };

        const syncCurrentMission = async (terminalId, terminalKey) => {
            let deviceTerminalToken = localStorage.getItem('robot_device_terminal_token');
            if (!deviceTerminalToken) {
                deviceTerminalToken = await bootstrapDeviceTerminal(terminalId, terminalKey);
            }

            try {
                return await fetchCurrentMission(deviceTerminalToken);
            } catch (error) {
                if (error?.response?.status !== 401) {
                    throw error;
                }

                localStorage.removeItem('robot_device_terminal_token');
                const refreshedToken = await bootstrapDeviceTerminal(terminalId, terminalKey);
                return fetchCurrentMission(refreshedToken);
            }
        };

        const startPolling = async () => {
            const { terminalId, terminalKey } = getRobotTerminalConfig();
            if (!terminalId.trim() || !terminalKey.trim()) {
                setScreenState('error');
                setErrorMsg('차량 단말 설정이 없습니다. runtime-config.js 또는 로컬 환경 변수를 확인해주세요.');
                return;
            }

            clearRobotSessionState();
            setErrorMsg('');
            setCurrentMission(null);
            setScreenState('bootstrapping');

            const pollOnce = async () => {
                const response = await syncCurrentMission(terminalId, terminalKey);
                if (isCancelled) {
                    return;
                }

                if (response?.hasMission) {
                    setCurrentMission(response);
                    setScreenState(response.phase === 'ARRIVED' ? 'ready' : 'waiting');
                    return;
                }

                setCurrentMission(null);
                setScreenState('waiting');
            };

            try {
                await pollOnce();
                pollTimerId = window.setInterval(() => {
                    if (isCancelled) {
                        return;
                    }
                    pollOnce().catch((error) => {
                        setScreenState('error');
                        setErrorMsg(extractApiErrorMessage(error, '차량 상태 조회에 실패했습니다.'));
                    });
                }, CURRENT_MISSION_POLL_INTERVAL_MS);
            } catch (error) {
                setScreenState('error');
                setErrorMsg(extractApiErrorMessage(error, '차량 단말 인증에 실패했습니다.'));
            }
        };

        startPolling();

        return () => {
            isCancelled = true;
            if (pollTimerId) {
                window.clearInterval(pollTimerId);
            }
            if (greetingTimerRef.current) {
                window.clearTimeout(greetingTimerRef.current);
            }
        };
    }, []);

    const handleStart = async () => {
        const deviceTerminalToken = localStorage.getItem('robot_device_terminal_token');
        if (!deviceTerminalToken || !currentMission?.missionId || isStarting) {
            return;
        }

        setIsStarting(true);
        setErrorMsg('');

        try {
            const response = await apiClient.post('/terminal/current-mission/claim', null, {
                headers: {
                    Authorization: `Bearer ${deviceTerminalToken}`,
                },
            });

            const missionTerminalToken = response?.data?.terminalToken;
            const missionId = response?.data?.missionId;
            const patientName = response?.data?.patientName || currentMission.patientName;
            if (!missionTerminalToken || !missionId) {
                throw new Error('미션 단말 토큰 또는 미션 ID가 비어 있습니다.');
            }

            localStorage.setItem('current_mission_id', missionId);
            localStorage.setItem('robot_mission_terminal_token', missionTerminalToken);
            localStorage.setItem('current_patient_name', patientName || '');
            setCurrentMission((prev) => ({
                ...prev,
                missionId,
                patientName,
                phase: 'ARRIVED',
            }));
            setScreenState('intro');

            greetingTimerRef.current = window.setTimeout(() => {
                navigate('/robot/auth', { replace: true });
            }, GREETING_REDIRECT_DELAY_MS);
        } catch (error) {
            setErrorMsg(extractApiErrorMessage(error, '현재 차량 진료를 시작할 수 없습니다.'));
            setIsStarting(false);
        }
    };

    const patientName = currentMission?.patientName || localStorage.getItem('current_patient_name') || '환자';
    const isReadyToStart = screenState === 'ready';

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-dark font-sans relative overflow-hidden">
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-primary rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-secondary rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-4xl text-center space-y-6 animate-fade-in-up">
                {screenState === 'intro' ? (
                    <>
                        <h1 className="text-4xl md:text-5xl font-bold text-white tracking-tight leading-tight">
                            <span className="text-secondary">{patientName}</span>님 안녕하세요.
                        </h1>
                        <p className="text-xl md:text-2xl text-slate-200 leading-relaxed">
                            본인 인증을 시작하겠습니다.
                        </p>
                    </>
                ) : (
                    <>
                        <h1 className="text-4xl md:text-5xl font-bold text-white tracking-tight leading-tight">
                            {isReadyToStart ? '차량이 도착했습니다.' : '차량이 자율 주행중입니다.'}
                        </h1>
                        <p className="text-lg md:text-2xl text-slate-300 leading-relaxed">
                            {isReadyToStart
                                ? `${patientName}님 진료를 시작할 준비가 완료되었습니다.`
                                : '차량 도착 후 진료 시작 버튼이 자동으로 활성화됩니다.'}
                        </p>
                        {currentMission?.hasMission && (
                            <p className="text-base md:text-lg text-slate-400">
                                {currentMission.appointmentDate} {currentMission.appointmentTime}
                            </p>
                        )}
                    </>
                )}
            </div>

            {screenState === 'ready' && (
                <button
                    onClick={handleStart}
                    disabled={isStarting}
                    className="relative z-10 mt-14 w-full max-w-3xl py-12 md:py-16 bg-primary hover:bg-accent-1 disabled:opacity-60 disabled:cursor-not-allowed text-white text-3xl md:text-5xl font-semibold border border-accent-1 rounded-2xl shadow-xl shadow-primary/30 transform hover:-translate-y-1 transition-all flex items-center justify-center animate-fade-in"
                >
                    {isStarting ? '진료 준비 중...' : '진료 시작'}
                </button>
            )}

            {errorMsg && (
                <div className="relative z-10 mt-10 w-full max-w-2xl rounded-2xl border border-red-400/40 bg-red-400/10 px-6 py-4 text-center text-base text-red-200">
                    {errorMsg}
                </div>
            )}

            <style dangerouslySetInnerHTML={{
                __html: `
                @keyframes fadeInUp {
                    from { opacity: 0; transform: translateY(20px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in-up { animation: fadeInUp 0.8s ease-out forwards; }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                .animate-fade-in { animation: fadeIn 1s ease-out forwards; animation-delay: 0.2s; opacity: 0; }
            `}} />
        </div>
    );
};

export default Home;
