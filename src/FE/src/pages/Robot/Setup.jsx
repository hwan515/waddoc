import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import apiClient from '../../utils/api';
import { getRobotTerminalConfig } from '../../utils/runtimeConfig';

const Setup = () => {
    const navigate = useNavigate();
    const [phoneLast4, setPhoneLast4] = useState('');
    const [birthDate6, setBirthDate6] = useState('');
    const [candidates, setCandidates] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const { terminalId, terminalKey } = useMemo(() => getRobotTerminalConfig(), []);
    const hasCandidates = candidates.length > 0;

    const hasTerminalConfig = useMemo(
        () => Boolean(terminalId.trim() && terminalKey.trim()),
        [terminalId, terminalKey]
    );

    const extractApiErrorMessage = (error, fallbackMessage) => {
        const detail = error?.response?.data?.detail;
        const message = error?.response?.data?.message;
        return detail || message || fallbackMessage;
    };

    const clearLegacyRobotState = () => {
        localStorage.removeItem('robot_mission_id');
        localStorage.removeItem('robot_session_id');
        localStorage.removeItem('webrtc_terminal_token');
        localStorage.removeItem('robot_device_terminal_token');
        localStorage.removeItem('robot_mission_terminal_token');
        localStorage.removeItem('current_mission_id');
    };

    const bootstrapDeviceTerminal = async () => {
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

    const lookupCandidates = async (deviceTerminalToken) => {
        const response = await apiClient.post('/terminal/check-in/candidates', {
            phoneLast4,
            birthDate6,
        }, {
            headers: {
                Authorization: `Bearer ${deviceTerminalToken}`,
            },
        });

        return response?.data?.candidates || [];
    };

    const claimMission = async (missionId, deviceTerminalToken) => {
        const response = await apiClient.post(`/terminal/missions/${missionId}/claim`, {
            phoneLast4,
            birthDate6,
        }, {
            headers: {
                Authorization: `Bearer ${deviceTerminalToken}`,
            },
        });

        const missionTerminalToken = response?.data?.terminalToken;
        if (!missionTerminalToken) {
            throw new Error('미션 단말 토큰이 비어 있습니다.');
        }

        localStorage.setItem('current_mission_id', missionId);
        localStorage.setItem('robot_mission_terminal_token', missionTerminalToken);
        navigate('/robot/auth');
    };

    const handleLookup = async () => {
        if (!hasTerminalConfig) {
            setErrorMsg('차량 단말 설정이 없습니다. runtime-config.js 또는 로컬 환경 변수를 확인해주세요.');
            return;
        }
        if (!/^\d{4}$/.test(phoneLast4)) {
            setErrorMsg('전화번호 뒤 4자리를 입력해주세요.');
            return;
        }
        if (!/^\d{6}$/.test(birthDate6)) {
            setErrorMsg('생년월일 6자리를 입력해주세요.');
            return;
        }

        setIsSubmitting(true);
        setErrorMsg('');
        setCandidates([]);
        clearLegacyRobotState();

        try {
            const deviceTerminalToken = await bootstrapDeviceTerminal();
            const nextCandidates = await lookupCandidates(deviceTerminalToken);

            if (nextCandidates.length === 0) {
                setErrorMsg('오늘 차량 진료 대상이 조회되지 않았습니다. 입력 정보를 다시 확인해주세요.');
                return;
            }

            if (nextCandidates.length === 1) {
                await claimMission(nextCandidates[0].missionId, deviceTerminalToken);
                return;
            }

            setCandidates(nextCandidates);
        } catch (error) {
            console.error('Robot check-in lookup failed:', error);
            setErrorMsg(extractApiErrorMessage(error, '차량 단말 인증 또는 대상 조회에 실패했습니다.'));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCandidateSelect = async (missionId) => {
        setIsSubmitting(true);
        setErrorMsg('');
        try {
            const deviceTerminalToken = localStorage.getItem('robot_device_terminal_token') || await bootstrapDeviceTerminal();
            await claimMission(missionId, deviceTerminalToken);
        } catch (error) {
            console.error('Mission claim failed:', error);
            setErrorMsg(extractApiErrorMessage(error, '선택한 예약으로 차량 진료를 시작할 수 없습니다.'));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleResetLookup = () => {
        setCandidates([]);
        setErrorMsg('');
    };

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-[#061A40] font-sans relative overflow-hidden">
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-3xl bg-white/10 backdrop-blur-md border border-white/20 p-8 flex flex-col items-center rounded-2xl shadow-2xl text-white">
                <h1 className="text-3xl font-bold mb-4 text-center text-[#B9D6F2]">진료 대상 확인</h1>
                <p className="text-slate-300 mb-8 text-center whitespace-pre-line leading-relaxed text-sm">
                    {hasCandidates
                        ? '조회된 차량 진료 대상 중에서\n진료를 시작할 환자를 선택해주세요.'
                        : '차량 진료 대상을 확인하기 위해\n전화번호 뒤 4자리와 생년월일 6자리를 입력해주세요.'}
                </p>

                {!hasCandidates && (
                    <>
                        <div className="space-y-6 w-full max-w-md">
                            <div>
                                <label className="block text-sm font-medium text-slate-300 mb-2">
                                    전화번호 뒤 4자리
                                </label>
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={4}
                                    value={phoneLast4}
                                    onChange={(event) => setPhoneLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
                                    placeholder="예: 3720"
                                    className="w-full px-4 py-3 bg-[#061A40] border border-[#3B62A4] rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-[#B9D6F2] text-xl tracking-widest text-center"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-300 mb-2">
                                    생년월일 6자리
                                </label>
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={6}
                                    value={birthDate6}
                                    onChange={(event) => setBirthDate6(event.target.value.replace(/\D/g, '').slice(0, 6))}
                                    placeholder="예: 580315"
                                    className="w-full px-4 py-3 bg-[#061A40] border border-[#3B62A4] rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-[#B9D6F2] text-xl tracking-widest text-center"
                                />
                            </div>
                        </div>

                        <div className="mt-10 flex gap-4 w-full max-w-md">
                            <button
                                onClick={handleLookup}
                                disabled={isSubmitting}
                                className="px-10 py-4 bg-[#0353A4] hover:bg-[#006DAA] disabled:opacity-60 disabled:cursor-not-allowed text-white text-xl font-semibold border border-[#B9D6F2]/30 rounded-xl shadow-lg transition-colors w-full flex items-center justify-center gap-2"
                            >
                                {isSubmitting ? '조회 중...' : '대상 조회하기'}
                            </button>
                        </div>
                    </>
                )}

                {errorMsg && (
                    <div className="mt-6 w-full max-w-md rounded-xl border border-red-400/40 bg-red-400/10 px-4 py-3 text-sm text-red-200 text-center">
                        {errorMsg}
                    </div>
                )}

                {hasCandidates && (
                    <div className="mt-2 w-full max-w-2xl">
                        <h2 className="text-xl font-semibold text-[#B9D6F2] mb-4 text-center">차량 진료 대상 선택</h2>
                        <div className="space-y-3">
                            {candidates.map((candidate) => (
                                <button
                                    key={candidate.missionId}
                                    onClick={() => handleCandidateSelect(candidate.missionId)}
                                    disabled={isSubmitting}
                                    className="w-full rounded-2xl border border-white/20 bg-[#061A40]/80 px-5 py-4 text-left transition hover:border-[#B9D6F2]/60 hover:bg-[#0B2447] disabled:opacity-60"
                                >
                                    <div className="flex items-center justify-between gap-4">
                                        <div>
                                            <p className="text-lg font-semibold text-white">{candidate.patientMaskedName}</p>
                                            <p className="text-sm text-slate-300 mt-1">
                                                {candidate.appointmentDate} {candidate.appointmentTime} / 담당 {candidate.doctorMaskedName}
                                            </p>
                                        </div>
                                        <span className="text-xs font-medium text-[#B9D6F2]">
                                            {candidate.missionPhase}
                                        </span>
                                    </div>
                                </button>
                            ))}
                        </div>
                        <button
                            onClick={handleResetLookup}
                            disabled={isSubmitting}
                            className="mt-6 w-full rounded-xl border border-white/20 bg-white/5 px-5 py-3 text-base font-semibold text-slate-200 transition hover:bg-white/10 disabled:opacity-60"
                        >
                            다시 조회하기
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default Setup;
