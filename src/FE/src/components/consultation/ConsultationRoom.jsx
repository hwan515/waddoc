import { useState, useEffect } from 'react';
import {
    Mic, MicOff, Video, VideoOff,
    Eye, EyeOff
} from 'lucide-react';
import { useTracks, useLocalParticipant, VideoTrack } from '@livekit/components-react';
import { Track } from 'livekit-client';
import {
    MEDICINE_CATALOG,
    MEDICINE_CATALOG_BY_CODE,
} from '../../constants/medicineCatalog';
import {
    createConsultationSummaryPayload,
    hasSummaryNote,
} from '../../utils/consultationSummary';
import EcgWaveform from './EcgWaveform';

const ConsultationRoom = ({
    details,
    vitals,
    micEnabled,
    setMicEnabled,
    videoEnabled,
    setVideoEnabled,
    onEndCall,
    isSavingSummary = false,
    summarySaveStatus = { type: 'idle', message: '' },
    role = 'DOCTOR'
}) => {
    // 1. 상태 변수 설정
    const [currentTime, setCurrentTime] = useState(new Date());
    const [durationSec, setDurationSec] = useState(0);

    // 토글 상태
    const [showVitals, setShowVitals] = useState(true);
    const [showLocalVideo, setShowLocalVideo] = useState(true);

    // 처방전 관련 상태
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedMeds, setSelectedMeds] = useState([]);
    const [prescribedMeds, setPrescribedMeds] = useState([]);
    const [consultationNote, setConsultationNote] = useState('');
    const [needsFollowUp, setNeedsFollowUp] = useState(false);
    const [validationMessage, setValidationMessage] = useState('');

    // 진료 시간 타이머 & 상단 시계
    useEffect(() => {
        const timer1 = setInterval(() => setCurrentTime(new Date()), 60000);
        const timer2 = setInterval(() => setDurationSec(prev => prev + 1), 1000);
        return () => {
            clearInterval(timer1);
            clearInterval(timer2);
        };
    }, []);

    const formatTime = (sec) => {
        const m = Math.floor(sec / 60).toString().padStart(2, '0');
        const s = (sec % 60).toString().padStart(2, '0');
        return `${m}:${s}`;
    };

    // LiveKit Hooks: 로컬 참가자와 원격 참가자의 비디오 트랙을 가져옴
    const { localParticipant } = useLocalParticipant();
    const localVideoTrack = useTracks([Track.Source.Camera]).find((t) => t.participant.identity === localParticipant.identity);
    const remoteVideoTracks = useTracks([Track.Source.Camera]).filter((t) => t.participant.identity !== localParticipant.identity);
    const remoteTrack = remoteVideoTracks.length > 0 ? remoteVideoTracks[0] : null;

    const handleSearchChange = (e) => setSearchQuery(e.target.value);
    
    const handleMedToggle = (medCode) => {
        setSelectedMeds(prev => 
            prev.includes(medCode) 
                ? prev.filter(code => code !== medCode)
                : [...prev, medCode]
        );
    };

    const buildSummaryPayload = (prescriptionCodes = prescribedMeds) => createConsultationSummaryPayload({
        summaryNote: consultationNote,
        prescriptionCodes,
        needsFollowUp,
    });

    const handleAddPrescription = () => {
        if (selectedMeds.length === 0) {
            return;
        }

        const nextPrescribedMeds = [...new Set([...prescribedMeds, ...selectedMeds])];
        setValidationMessage('');
        setPrescribedMeds(nextPrescribedMeds);
        setSelectedMeds([]);
    };

    const handleRemovePrescription = (medCode) => {
        const nextPrescribedMeds = prescribedMeds.filter((code) => code !== medCode);
        setValidationMessage('');
        setPrescribedMeds(nextPrescribedMeds);
        setSelectedMeds((prev) => prev.filter((code) => code !== medCode));
    };

    const handleClearPrescription = () => {
        if (prescribedMeds.length === 0) {
            return;
        }

        setValidationMessage('');
        setPrescribedMeds([]);
        setSelectedMeds([]);
    };

    const handleResetConsultationNote = () => {
        setConsultationNote('');
        setNeedsFollowUp(false);
        setValidationMessage('');
    };

    const normalizedQuery = searchQuery.trim().toLowerCase();
    const filteredMeds = MEDICINE_CATALOG.filter((medicine) => {
        if (!normalizedQuery) {
            return true;
        }

        return [
            medicine.code,
            medicine.name,
            medicine.category,
            medicine.dosage,
        ].some((value) => value.toLowerCase().includes(normalizedQuery));
    });

    const hasValue = (value) => value !== null && value !== undefined && value !== '';
    const measuredAtText = vitals?.measuredAt
        ? new Date(vitals.measuredAt).toLocaleString('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        })
        : '미측정';
    const waveform = Array.isArray(vitals?.ecgWaveform) ? vitals.ecgWaveform : [];
    const prescribedMedicineItems = prescribedMeds
        .map((code) => MEDICINE_CATALOG_BY_CODE[code])
        .filter(Boolean);
    const feedbackType = validationMessage ? 'error' : summarySaveStatus?.type;
    const feedbackMessage = validationMessage || summarySaveStatus?.message || '';

    const handleEndCallClick = async () => {
        if (role === 'DOCTOR') {
            if (!hasSummaryNote(consultationNote)) {
                setValidationMessage('진료 기록을 입력한 후 진료를 종료하세요.');
                return;
            }

            setValidationMessage('');
            await onEndCall(buildSummaryPayload());
        } else {
            onEndCall();
        }
    };

    return (
        <div className="flex flex-col h-screen bg-[#F0F0F0] font-sans text-sm select-none">
            {/* 1. 클래식 상단 네비게이션 바 (대시보드와 동일한 테마) */}
            <div className="bg-[#E0E0E0] border-b-2 border-slate-400 flex items-center justify-between px-2 py-1 shrink-0">
                <div className="flex space-x-1">
                    <button className="px-4 py-1.5 bg-[#F0F0F0] border border-slate-400 shadow-[inset_1px_1px_0_#FFF,1px_1px_0_#888] active:shadow-[inset_1px_1px_0_#888,1px_1px_0_#FFF] flex flex-col items-center">
                        <span className="font-bold text-slate-800 text-xs">진료실</span>
                    </button>
                    <div className="h-6 w-px bg-slate-400 mx-2 self-center"></div>
                    <div className="flex items-center text-xs space-x-4 pl-2 font-medium text-slate-700">
                        <span>환자명: <span className="font-bold text-blue-800">{details.patientName}</span></span>
                        <span>|</span>
                        <span>담당의: <span className="font-bold">{details.doctorName}</span></span>
                        <span>|</span>
                        <span>진료시간: <span className="font-bold text-red-600">{formatTime(durationSec)}</span></span>
                    </div>
                </div>
                <div className="flex items-center space-x-4 pr-2">
                    <div className="flex space-x-2">
                        <button 
                            onClick={() => setMicEnabled(!micEnabled)}
                            className={`px-3 py-1 text-xs border border-slate-400 shadow-sm flex items-center space-x-1 ${micEnabled ? 'bg-white text-slate-800' : 'bg-red-100 text-red-700'}`}
                        >
                            {micEnabled ? <Mic className="w-3.5 h-3.5" /> : <MicOff className="w-3.5 h-3.5" />}
                            <span>{micEnabled ? '마이크 ON' : '마이크 OFF'}</span>
                        </button>
                        <button 
                            onClick={() => setVideoEnabled(!videoEnabled)}
                            className={`px-3 py-1 text-xs border border-slate-400 shadow-sm flex items-center space-x-1 ${videoEnabled ? 'bg-white text-slate-800' : 'bg-red-100 text-red-700'}`}
                        >
                            {videoEnabled ? <Video className="w-3.5 h-3.5" /> : <VideoOff className="w-3.5 h-3.5" />}
                            <span>{videoEnabled ? '카메라 ON' : '카메라 OFF'}</span>
                        </button>
                    </div>
                    <div className="text-slate-600 bg-white px-2 py-0.5 border border-slate-300 shadow-inner text-xs">
                        {currentTime.toLocaleDateString()} {currentTime.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                    </div>
                    <button onClick={handleEndCallClick} className="px-4 py-1 bg-[#F0F0F0] border border-slate-400 shadow-[inset_1px_1px_0_#FFF,1px_1px_0_#888] active:shadow-[inset_1px_1px_0_#888,1px_1px_0_#FFF]">
                        <span className="text-red-700 font-bold text-xs">진료완료</span>
                    </button>
                </div>
            </div>

            {/* 2. 메인 2단 분할 레이아웃 */}
            <div className="flex-1 flex overflow-hidden p-1 gap-1">
                
                {/* 2-1. 좌측: 비디오 및 스트리밍 영역 (상대방 화면 및 오버레이) */}
                <div className="flex-[6] relative border border-slate-400 bg-black overflow-hidden flex flex-col">
                    {/* 상단 툴바 (토글 기능) */}
                    <div className="absolute top-0 left-0 right-0 z-20 flex justify-between p-2 pointer-events-none">
                        <div className="pointer-events-auto bg-black/60 px-2 py-1 text-white text-xs font-bold border border-slate-600 flex items-center space-x-2">
                            <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
                            <span>{details.patientName} 님 연결 중</span>
                        </div>
                        <div className="flex space-x-2 pointer-events-auto">
                            <button 
                                onClick={() => setShowVitals(!showVitals)}
                                className="bg-black/60 hover:bg-black/80 px-2 py-1 text-white text-xs border border-slate-600 flex items-center space-x-1"
                            >
                                {showVitals ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                                <span>생체정보 {showVitals ? '숨기기' : '보기'}</span>
                            </button>
                            <button 
                                onClick={() => setShowLocalVideo(!showLocalVideo)}
                                className="bg-black/60 hover:bg-black/80 px-2 py-1 text-white text-xs border border-slate-600 flex items-center space-x-1"
                            >
                                {showLocalVideo ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                                <span>내 화면 {showLocalVideo ? '숨기기' : '보기'}</span>
                            </button>
                        </div>
                    </div>

                    {/* 메인 비디오 (상대방) */}
                    <div className="flex-1 w-full h-full relative z-0">
                        {remoteTrack ? (
                            <VideoTrack 
                                trackRef={remoteTrack} 
                                className="w-full h-full object-cover" 
                            />
                        ) : (
                            <div className="flex flex-col items-center justify-center h-full bg-slate-800 text-slate-400">
                                <div className="w-12 h-12 border-4 border-slate-500 border-t-transparent rounded-full animate-spin mb-4"></div>
                                <span className="font-bold">환자 접속 대기 중입니다...</span>
                            </div>
                        )}
                    </div>

                    {/* 우측 상단 오버레이: 생체 정보 (Vitals) */}
                    {showVitals && (
                        <div className="absolute top-10 right-2 w-[26rem] max-h-[calc(100%-4rem)] overflow-y-auto bg-white/90 backdrop-blur-md border-2 border-slate-400 shadow-xl z-20 flex flex-col text-xs">
                            <div className="bg-[#4472C4] text-white px-2 py-1 font-bold text-center border-b border-slate-400">
                                📈 환자 생체정보
                            </div>
                            <div className="p-2 space-y-2">
                                <div className="rounded-md bg-slate-100 px-2 py-1 text-[11px] font-medium text-slate-700">
                                    측정 시각: <span className="font-bold">{measuredAtText}</span>
                                </div>
                                <div className="flex justify-between items-center border-b border-slate-200 pb-1">
                                    <span className="font-bold text-slate-700">체온 (Temp)</span>
                                    {hasValue(vitals?.temperature) ? (
                                        <span className="font-extrabold text-blue-700">{vitals.temperature} <span className="text-[10px] text-slate-500 font-normal">°C</span></span>
                                    ) : (
                                        <span className="font-bold text-slate-400">미측정</span>
                                    )}
                                </div>
                                <div className="flex justify-between items-center border-b border-slate-200 pb-1">
                                    <span className="font-bold text-slate-700">혈압 (BP)</span>
                                    {hasValue(vitals?.bloodPressureSys) && hasValue(vitals?.bloodPressureDia) ? (
                                        <span className="font-extrabold text-slate-800">{vitals.bloodPressureSys}/{vitals.bloodPressureDia}</span>
                                    ) : (
                                        <span className="font-bold text-slate-400">미측정</span>
                                    )}
                                </div>
                                <div className="flex justify-between items-center border-b border-slate-200 pb-1">
                                    <span className="font-bold text-slate-700">심박수 (HR)</span>
                                    {hasValue(vitals?.heartRate) ? (
                                        <span className="font-extrabold text-red-600 flex items-center gap-1">
                                            {vitals.heartRate} <span className="text-[10px] text-slate-500 font-normal">bpm</span>
                                        </span>
                                    ) : (
                                        <span className="font-bold text-slate-400">미측정</span>
                                    )}
                                </div>
                                <div className="flex justify-between items-center">
                                    <span className="font-bold text-slate-700">산소포화도 (SpO2)</span>
                                    {hasValue(vitals?.spO2) ? (
                                        <span className="font-extrabold text-green-700">{vitals.spO2} <span className="text-[10px] text-slate-500 font-normal">%</span></span>
                                    ) : (
                                        <span className="font-bold text-slate-400">미측정</span>
                                    )}
                                </div>
                                <div className="border-t border-slate-200 pt-2">
                                    <div className="mb-2 text-[11px] font-bold text-slate-700">측정 시점 ECG</div>
                                    <EcgWaveform
                                        waveform={waveform}
                                        samplingHz={vitals?.ecgSamplingHz ?? 25}
                                        durationSeconds={vitals?.ecgDurationSeconds ?? 8}
                                        compact
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* 좌측 하단 오버레이: 내 화면 (Local PIP) */}
                    {showLocalVideo && (
                        <div className="absolute bottom-2 left-2 w-52 h-36 bg-slate-900 border-2 border-slate-400 shadow-xl z-20 overflow-hidden">
                            <div className="absolute top-1 left-1 bg-black/50 px-1 text-white text-[10px] z-30">내 화면 (의사)</div>
                            {videoEnabled && localVideoTrack ? (
                                <VideoTrack
                                    trackRef={localVideoTrack}
                                    className="absolute inset-0 w-full h-full object-cover custom-video-mirror"
                                />
                            ) : (
                                <div className="absolute inset-0 flex items-center justify-center bg-slate-800 text-slate-500">
                                    <VideoOff className="w-8 h-8" />
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* 2-2. 우측: 차팅 및 처방 영역 */}
                <div className="flex-[4] flex flex-col gap-1">
                    
                    {/* 우측 상단: 처방전 약 선택 */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-white overflow-hidden">
                        <div className="bg-gradient-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300 flex justify-between items-center shrink-0">
                            <span className="font-bold text-slate-800 text-sm">💊 약품 처방</span>
                            <div className="flex items-center space-x-1">
                                <span className="text-[11px] font-bold text-slate-600">검색:</span>
                                <input 
                                    type="text" 
                                    value={searchQuery}
                                    onChange={handleSearchChange}
                                    placeholder="약품명 / 코드"
                                    className="border border-slate-400 bg-white text-slate-800 placeholder:text-slate-400 h-5 px-1 w-32 text-xs focus:outline-none focus:bg-[#FFFFCC]"
                                />
                            </div>
                        </div>
                        
                        <div className="bg-[#4472C4] text-white flex border-b border-slate-400 text-xs text-center font-bold shrink-0">
                            <div className="w-8 border-r border-[#3B62A4] py-1">선택</div>
                            <div className="w-16 border-r border-[#3B62A4] py-1">코드</div>
                            <div className="w-32 border-r border-[#3B62A4] py-1 text-left px-2">약품명</div>
                            <div className="flex-1 py-1 text-left px-2">용법 / 용량</div>
                        </div>

                        <div className="flex-1 overflow-y-auto bg-white">
                            {filteredMeds.map((med) => {
                                const isChecked = selectedMeds.includes(med.code);
                                const isPrescribed = prescribedMeds.includes(med.code);
                                return (
                                    <div 
                                        key={med.code} 
                                        onClick={() => handleMedToggle(med.code)}
                                        className={`flex text-[11px] border-b border-slate-200 cursor-pointer ${
                                            isChecked
                                                ? 'bg-[#D9E1F2] font-semibold text-blue-900'
                                                : isPrescribed
                                                    ? 'bg-emerald-50 text-emerald-900'
                                                    : 'hover:bg-slate-50'
                                        }`}
                                    >
                                        <div className="w-8 py-1 flex items-center justify-center border-r border-slate-200">
                                            <input 
                                                type="checkbox" 
                                                checked={isChecked} 
                                                onChange={() => {}} 
                                                className="cursor-pointer"
                                            />
                                        </div>
                                        <div className="w-16 py-1 text-center border-r border-slate-200 text-slate-500">{med.code}</div>
                                        <div className="w-32 py-1 px-2 text-left border-r border-slate-200 truncate text-slate-800" title={med.name}>
                                            <div className="flex items-center gap-1">
                                                <span className="truncate">{med.name}</span>
                                                {isPrescribed ? (
                                                    <span className="shrink-0 rounded bg-emerald-100 px-1 py-[1px] text-[10px] font-bold text-emerald-700">
                                                        처방
                                                    </span>
                                                ) : null}
                                            </div>
                                        </div>
                                        <div className="flex-1 py-1 px-2 text-left truncate text-slate-600">{med.dosage}</div>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="border-t border-slate-200 bg-slate-50 px-2 py-2 text-[11px] text-slate-700">
                            <div className="flex items-center justify-between gap-2">
                                <div className="font-bold text-slate-800">처방 내역</div>
                                <button
                                    onClick={handleClearPrescription}
                                    disabled={prescribedMedicineItems.length === 0 || isSavingSummary}
                                    className="px-2 py-0.5 text-[10px] font-bold text-slate-600 border border-slate-300 bg-white disabled:cursor-not-allowed disabled:text-slate-300"
                                >
                                    전체 비우기
                                </button>
                            </div>
                            {prescribedMedicineItems.length > 0 ? (
                                <div className="mt-1 flex flex-wrap gap-1">
                                    {prescribedMedicineItems.map((medicine) => (
                                        <div
                                            key={medicine.code}
                                            className="flex items-center gap-1 rounded border border-emerald-300 bg-emerald-100 px-2 py-1 text-[11px] font-semibold text-emerald-800"
                                            title={`${medicine.category} / ${medicine.dosage}`}
                                        >
                                            <span>{medicine.name}</span>
                                            <button
                                                onClick={() => handleRemovePrescription(medicine.code)}
                                                className="rounded border border-emerald-400 bg-white px-1 text-[10px] font-bold text-emerald-700 hover:bg-emerald-50"
                                                aria-label={`${medicine.name} 삭제`}
                                                disabled={isSavingSummary}
                                            >
                                                삭제
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <div className="mt-1 text-slate-500">아직 추가된 처방 내역이 없습니다.</div>
                            )}
                        </div>
                        
                        <div className="bg-[#F0F0F0] border-t border-slate-300 p-1 flex justify-between items-center shrink-0">
                            <span className="text-xs font-bold text-slate-700 pl-1">
                                선택 {selectedMeds.length}개 | 처방 {prescribedMeds.length}개
                            </span>
                            <button
                                onClick={handleAddPrescription}
                                disabled={selectedMeds.length === 0 || isSavingSummary}
                                className="px-3 py-0.5 bg-blue-100 border border-blue-400 text-xs text-blue-800 font-bold active:bg-blue-200 disabled:cursor-not-allowed disabled:border-slate-300 disabled:bg-slate-100 disabled:text-slate-400"
                            >
                                처방 내역 추가
                            </button>
                        </div>
                        {feedbackMessage ? (
                            <div
                                className={`border-t px-2 py-1 text-[11px] font-medium ${
                                    feedbackType === 'error'
                                        ? 'border-red-200 bg-red-50 text-red-700'
                                        : feedbackType === 'success'
                                            ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                                            : 'border-blue-200 bg-blue-50 text-blue-700'
                                }`}
                            >
                                {feedbackMessage}
                            </div>
                        ) : null}
                    </div>

                    {/* 우측 하단: 진료 내역 입력란 */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-white">
                        <div className="bg-gradient-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300 shrink-0 flex items-center justify-between gap-2">
                            <div>
                                <span className="font-bold text-slate-800 text-sm">📝 진료 기록 (경과 기록지)</span>
                                <div className="text-[11px] font-medium text-slate-500">작성 내용은 진료 종료 시 저장됩니다.</div>
                            </div>
                            <div className="flex items-center gap-2">
                                <label className="flex items-center gap-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs font-semibold text-slate-700">
                                    <input
                                        type="checkbox"
                                        checked={needsFollowUp}
                                        onChange={(e) => setNeedsFollowUp(e.target.checked)}
                                        className="h-3.5 w-3.5 accent-amber-600"
                                    />
                                    재진 필요
                                </label>
                                <button
                                    onClick={handleResetConsultationNote}
                                    className="px-2 py-0.5 bg-white border border-slate-400 text-xs text-slate-700 active:bg-slate-100"
                                >
                                    초기화
                                </button>
                            </div>
                        </div>
                        <div className="flex-1 p-2 bg-[#EAE6D0]">
                            <textarea 
                                value={consultationNote}
                                onChange={(e) => {
                                    setConsultationNote(e.target.value);
                                    if (validationMessage) {
                                        setValidationMessage('');
                                    }
                                }}
                                className="h-full w-full resize-none rounded-md border border-amber-300 bg-[#FFFDF5] p-3 font-mono text-sm leading-6 text-slate-900 shadow-inner placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
                                placeholder="환자 증상, 진단 소견, 처방 이유를 자세히 기록하세요."
                            ></textarea>
                        </div>
                    </div>
                </div>

            </div>
            
            {/* 상태 표시줄 (Bottom Bar) */}
            <div className="bg-[#E0E0E0] border-t border-slate-400 px-2 py-0.5 flex justify-between text-[11px] text-slate-600 shrink-0">
                <div className="flex space-x-4">
                    <span>비대면 진료 모듈 [Active]</span>
                    <span className="text-green-700 font-bold">네트워크 연결 정상 (WebRTC)</span>
                </div>
                <span>Server: LOCAL | Ping: 12ms</span>
            </div>
        </div>
    );
};

export default ConsultationRoom;
