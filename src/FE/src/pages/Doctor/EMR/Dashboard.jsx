import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, X } from 'lucide-react';
import useAuthStore from '../../../store/authStore';
import { useSSE } from '../../../hooks/useSSE';
import apiClient from '../../../utils/api';
import { sanitizeSelectionReason } from '../../../utils/intakeSelectionReason';
import { logoutSession } from '../../../utils/logout';
import { parsePrescriptionNote } from '../../../utils/prescriptionNote';
import { isRobotDirectWebRtcEnabled } from '../../../utils/runtimeConfig';

const getSymptomCandidates = (deptName = '') => {
    if (deptName.includes('정형')) {
        return ['어깨 통증', '무릎 관절염', '발목 염좌', '허리 디스크 증상', '손목 시큰거림'];
    }
    if (deptName.includes('내과')) {
        return ['속쓰림, 소화불량', '기침, 가래', '발열 및 오한', '두통, 어지러움', '복통, 설사'];
    }
    if (deptName.includes('이비인후')) {
        return ['귀 통증', '코막힘, 콧물', '인후통', '편도선 붓기', '어지럼증'];
    }
    if (deptName.includes('안과')) {
        return ['눈 충혈', '시력 침침함', '안구 건조증', '눈물 흘림', '눈 주위 통증'];
    }

    return ['단순 문진', '가벼운 통증', '컨디션 저하', '정기 진료 대기', '약 처방 문의'];
};

const getStableSymptomFallback = (seed, deptName = '') => {
    const candidates = getSymptomCandidates(deptName);
    const normalizedSeed = String(seed || deptName || 'default');
    const hash = [...normalizedSeed].reduce((acc, char) => acc + char.charCodeAt(0), 0);

    return candidates[hash % candidates.length];
};

const resolveReservationSymptom = (source, existingSymptom = '') => {
    const actualSymptom = sanitizeSelectionReason(
        source?.intakeSummary?.selectionReason ?? source?.selectionReason,
        ''
    );

    if (actualSymptom) {
        return actualSymptom;
    }
    if (existingSymptom) {
        return existingSymptom;
    }

    return getStableSymptomFallback(
        source?.caseId ?? source?.bookingId ?? source?.patientId,
        source?.departmentName
    );
};

// 예약일 비교를 위해 YYYY-MM-DD 키를 만든다.
const formatDateKey = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const CASE_SYNC_INTERVAL_MS = 10000;
const STARTABLE_CASE_STATUSES = new Set(['CREATED', 'PREPARING']);
const DEFAULT_STARTABLE_MISSION_PHASES = new Set(['ARRIVED', 'VERIFYING']);
const DIRECT_STARTABLE_MISSION_PHASES = new Set(['DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'VERIFYING']);
const TERMINAL_CASE_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);
const REJOINABLE_SESSION_STATUSES = new Set(['CREATED', 'READY', 'IN_PROGRESS']);
const REMOTE_BOOKING_CHANNELS = new Set(['WEB_SIMULATOR', 'PHONE']);

const CASE_STATUS_LABELS = {
    CREATED: '예약',
    PREPARING: '준비 중',
    IN_PROGRESS: '진료 중',
    COMPLETED: '완료',
    FAILED: '실패',
    CANCELLED: '취소',
};

const mapCaseStatusToLabel = (status) => CASE_STATUS_LABELS[status] || '상태 미상';

const mapGenderLabel = (gender) => {
    const normalizedGender = String(gender || '').toUpperCase();

    if (normalizedGender === 'MALE') {
        return '남';
    }
    if (normalizedGender === 'FEMALE') {
        return '여';
    }

    return '미상';
};

const mapReservationType = (bookingChannel) => {
    const normalizedChannel = String(bookingChannel || '').toUpperCase();

    if (!normalizedChannel || REMOTE_BOOKING_CHANNELS.has(normalizedChannel)) {
        return '비대면';
    }
    if (normalizedChannel === 'OUTPATIENT') {
        return '외래';
    }

    return '외래';
};

const mapCaseToReservation = (caseData, existingReservation = {}) => ({
    ...existingReservation,
    id: caseData.caseId,
    bookingId: caseData.bookingId || existingReservation.bookingId,
    ptNo: caseData.patientId,
    name: caseData.patientName,
    gender: mapGenderLabel(caseData.patientGender),
    symptom: resolveReservationSymptom(caseData, existingReservation.symptom),
    date: caseData.appointmentDate,
    time: caseData.startTime?.substring(0, 5) || '00:00',
    status: mapCaseStatusToLabel(caseData.status),
    caseStatus: caseData.status || null,
    type: mapReservationType(caseData.bookingChannel || existingReservation.bookingChannel),
    bookingChannel: caseData.bookingChannel || existingReservation.bookingChannel || null,
    missionPhase: caseData.missionPhase || null,
    sessionId: caseData.sessionId || null,
    sessionStatus: caseData.sessionStatus || null,
    isNotificationOnly: false,
});

const mapNotificationToReservation = (notif) => ({
    id: notif.caseId || notif.bookingId,
    bookingId: notif.bookingId,
    ptNo: notif.patientId,
    name: notif.patientName,
    gender: mapGenderLabel(notif.patientGender),
    symptom: resolveReservationSymptom(notif),
    date: notif.appointmentDate,
    time: notif.startTime?.substring(0, 5) || '00:00',
    status: mapCaseStatusToLabel(notif.caseStatus || 'CREATED'),
    caseStatus: notif.caseStatus || 'CREATED',
    type: mapReservationType(notif.bookingChannel),
    bookingChannel: notif.bookingChannel || null,
    missionPhase: notif.missionPhase || null,
    sessionId: null,
    sessionStatus: null,
    isNotificationOnly: true,
});

const buildPrescriptionSummary = (prescriptionNote, isPrescriptionIssued) => {
    const parsedPrescription = parsePrescriptionNote(prescriptionNote);

    if (parsedPrescription.isStructured && parsedPrescription.items.length > 0) {
        return parsedPrescription.items.map((item) => item.name).join(', ');
    }

    if (parsedPrescription.rawText) {
        return parsedPrescription.rawText;
    }

    return isPrescriptionIssued ? '처방전 등록' : '처방 없음';
};

const mapConsultationHistoryToRow = (history) => ({
    id: history.caseId,
    date: history.consultationDate || '',
    doctor: history.doctorName || '담당의 미상',
    symptom: history.symptom || '문진 내용 없음',
    dx: history.summaryNote || '소견서 없음',
    rx: buildPrescriptionSummary(
        history.prescriptionNote,
        history.isPrescriptionIssued ?? history.prescriptionIssued
    ),
});

const getReservationStatusClassName = (reservation) => {
    switch (reservation.caseStatus) {
        case 'CREATED':
            return 'text-blue-700 font-semibold';
        case 'PREPARING':
            return 'text-amber-600 font-semibold';
        case 'IN_PROGRESS':
            return 'text-red-600 font-bold';
        case 'FAILED':
            return 'text-red-700 font-bold';
        case 'CANCELLED':
            return 'text-slate-500';
        default:
            return 'text-slate-600';
    }
};

// 비대면 예약 상태인 항목만 진료 시작 버튼 대상으로 본다.
const patientDetailLabelCellClass =
    'bg-[#E2EFDA] border border-slate-300 px-3 py-2 text-sm font-bold text-[#385723]';
const patientDetailValueCellClass =
    'border border-slate-300 px-3 py-2 text-sm';
const patientDetailStrongValueCellClass =
    `${patientDetailValueCellClass} font-bold text-blue-800`;
const patientDetailNameCellClass =
    `${patientDetailValueCellClass} font-bold text-base lg:text-lg leading-none`;
const patientDetailAlertLabelCellClass =
    'bg-[#FFE699] border border-slate-300 px-3 py-2 text-sm font-bold text-[#C55A11] align-top';
const historyHeaderCellClass =
    'border-r border-[#3B62A4] px-2.5 py-1.5 text-sm font-bold last:border-r-0';
const historyBodyCellClass =
    'border-r border-slate-200 px-2.5 py-2 text-sm last:border-r-0';

const isConsultationStartTarget = (reservation) => {
    return reservation.type === '비대면' && STARTABLE_CASE_STATUSES.has(reservation.caseStatus);
};

const hasRejoinableConsultationSession = (reservation) => (
    reservation.type === '비대면'
    && Boolean(reservation.sessionId)
    && REJOINABLE_SESSION_STATUSES.has(reservation.sessionStatus)
);

const shouldShowConsultationAction = (reservation) => (
    hasRejoinableConsultationSession(reservation) || isConsultationStartTarget(reservation)
);

const resolveStartableMissionPhases = (directWebRtcEnabled) => (
    directWebRtcEnabled ? DIRECT_STARTABLE_MISSION_PHASES : DEFAULT_STARTABLE_MISSION_PHASES
);

// 비대면 예약이면서 예약일이 오늘이고 환자 도착 이후 단계(본인 확인 포함)일 때만 진료 시작을 허용한다.
const canStartConsultation = (reservation, now, directWebRtcEnabled) => {
    if (hasRejoinableConsultationSession(reservation)) {
        return true;
    }
    if (!isConsultationStartTarget(reservation)) {
        return false;
    }
    if (!reservation.date) {
        return false;
    }
    return reservation.date === formatDateKey(now)
        && resolveStartableMissionPhases(directWebRtcEnabled).has(reservation.missionPhase);
};

// 버튼이 비활성화된 이유를 바로 이해할 수 있도록 안내 문구를 분기한다.
const getConsultationStartButtonTitle = (reservation, now, directWebRtcEnabled) => {
    if (hasRejoinableConsultationSession(reservation)) {
        return '진행 중인 진료실로 다시 들어갑니다.';
    }
    if (!reservation.date || reservation.date !== formatDateKey(now)) {
        return '진료 시작은 예약 당일에만 가능합니다.';
    }
    if (!resolveStartableMissionPhases(directWebRtcEnabled).has(reservation.missionPhase)) {
        return directWebRtcEnabled
            ? '차량 출발 후 활성화됩니다.'
            : '환자 도착 후 활성화됩니다.';
    }
    return '진료를 시작합니다.';
};

const LegacyEMRDashboard = () => {
    const navigate = useNavigate();
    const doctorDisplayName = useAuthStore((state) => state.user?.name || state.user?.username || '원장');
    const directWebRtcEnabled = isRobotDirectWebRtcEnabled();

    // SSE 알림 연동
    const { isConnected, notifications, removeNotification, getNotificationKey } = useSSE();

    const [currentTime, setCurrentTime] = useState(new Date());

    // 1. 예약 필터
    const [filterType, setFilterType] = useState('전체');

    // 2. State (API 연동 데이터)
    const [reservations, setReservations] = useState([]);
    const [patientDB, setPatientDB] = useState({});
    const [historyDB, setHistoryDB] = useState({});
    const [expandedHistoryRows, setExpandedHistoryRows] = useState({});

    // 3. 현재 선택된 예약
    const [selectedReservationId, setSelectedReservationId] = useState(null);

    const syncAssignedCases = useCallback(async () => {
        try {
            const response = await apiClient.get('/cases');
            const cases = response.data.cases || [];

            setReservations(prev => {
                const previousById = new Map(prev.map((reservation) => [reservation.id, reservation]));
                const syncedReservations = cases.map((caseData) => (
                    mapCaseToReservation(caseData, previousById.get(caseData.caseId))
                ));
                const syncedReservationIds = new Set(syncedReservations.map((reservation) => reservation.id));
                const pendingNotificationReservations = prev.filter((reservation) => (
                    reservation.isNotificationOnly && !syncedReservationIds.has(reservation.id)
                ));

                return [...syncedReservations, ...pendingNotificationReservations];
            });
        } catch {
            // Keep the current reservation snapshot when refresh fails.
        }
    }, []);

    // API를 통한 백엔드 케이스(예약) 초기 로드
    useEffect(() => {
        syncAssignedCases();
    }, [syncAssignedCases]);

    // SSE 연결이 늦게 붙은 경우 누락된 신규 예약을 한 번 더 동기화한다.
    useEffect(() => {
        if (!isConnected) {
            return;
        }
        syncAssignedCases();
    }, [isConnected, syncAssignedCases]);

    // MQTT가 갱신한 미션 상태를 의사 EMR에서도 따라가도록 주기적으로 재조회한다.
    useEffect(() => {
        const timer = setInterval(() => {
            syncAssignedCases();
        }, CASE_SYNC_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [syncAssignedCases]);

    // 시계 업데이트
    useEffect(() => {
        const timer = setInterval(() => setCurrentTime(new Date()), 60000);
        return () => clearInterval(timer);
    }, []);

    // 환자 선택 (디테일 조회)
    const handlePatientSelect = async (ptNo, caseId) => {
        setSelectedReservationId(caseId);
        try {
            const response = await apiClient.get(`/cases/${caseId}`);
            const detail = response.data;
            const pInfo = detail.patient || {};

            let age = '미상';
            if (pInfo.birthDate) {
                const birthYear = new Date(pInfo.birthDate).getFullYear();
                const currentYear = new Date().getFullYear();
                age = currentYear - birthYear;
            }

            setPatientDB(prev => ({
                ...prev,
                [ptNo]: {
                    ptNo: pInfo.patientId,
                    name: pInfo.name,
                    address: pInfo.address || '주소 미상',
                    birthDate: pInfo.birthDate || '상세정보 미상',
                    phone: pInfo.phone || '연락처 없음',
                    age: age,
                    gender: mapGenderLabel(pInfo.gender),
                    note: sanitizeSelectionReason(
                        detail.intakeSummary?.selectionReason,
                        '자세한 특이사항 없음'
                    )
                }
            }));

            setReservations(prev => prev.map((reservation) => (
                reservation.id === caseId
                    ? { ...reservation, symptom: resolveReservationSymptom(detail, reservation.symptom) }
                    : reservation
            )));

            setHistoryDB(prev => ({
                ...prev,
                [ptNo]: (detail.consultationHistories || []).map(mapConsultationHistoryToRow)
            }));

            return;
        } catch {
            // Leave the existing patient detail visible if the first detail fetch fails.
        }

        // 만약 환자 상세 정보가 아직 API에서 불러와지지 않았거나(미상), SSE로 등록된 임시 상태라면
        if (!patientDB[ptNo] || patientDB[ptNo].age === '미상') {
            try {
                const response = await apiClient.get(`/cases/${caseId}`);
                const detail = response.data;
                const pInfo = detail.patient || {};

                let age = '미상';
                if (pInfo.birthDate) {
                    const birthYear = new Date(pInfo.birthDate).getFullYear();
                    const currentYear = new Date().getFullYear();
                    age = currentYear - birthYear;
                }
                setPatientDB(prev => ({
                    ...prev,
                    [ptNo]: {
                        ptNo: pInfo.patientId,
                        name: pInfo.name,
                        address: pInfo.address || '주소 미상',
                        birthDate: pInfo.birthDate || '상세정보 미상',
                        phone: pInfo.phone || '연락처 없음',
                        age: age,
                        gender: mapGenderLabel(pInfo.gender),
                        note: sanitizeSelectionReason(
                            detail.intakeSummary?.selectionReason,
                            '자세한 특이사항 없음'
                        )
                    }
                }));

                setReservations(prev => prev.map((reservation) => (
                    reservation.id === caseId
                        ? { ...reservation, symptom: resolveReservationSymptom(detail, reservation.symptom) }
                        : reservation
                )));

                setHistoryDB(prev => ({
                    ...prev,
                    [ptNo]: [] // 현재 과거 진료내역 API가 별도로 없으므로 빈 배열로 초기화
                }));

            } catch {
                // Preserve the fallback state when the retry also fails.
            }
        }
    };

    const handleDismissNotification = (notif) => {
        removeNotification(getNotificationKey(notif));
    };

    const handleAcceptNotification = async (notif) => {
        const notificationReservation = mapNotificationToReservation(notif);

        setReservations(prev => {
            if (prev.some(r => r.id === notificationReservation.id)) return prev;
            return [...prev, notificationReservation];
        });

        setPatientDB(prev => {
            if (prev[notif.patientId] && prev[notif.patientId].age !== '미상') return prev;

            let age = '미상';
            if (notif.patientBirthDate) {
                const birthYear = new Date(notif.patientBirthDate).getFullYear();
                const currentYear = new Date().getFullYear();
                age = currentYear - birthYear;
            }

            return {
                ...prev,
                [notif.patientId]: {
                    ptNo: notif.patientId,
                    name: notif.patientName,
                    address: notif.location || '주소 없음',
                    birthDate: notif.patientBirthDate || '확인필요',
                    phone: notif.patientPhone || '조회필요',
                    age: age,
                    gender: mapGenderLabel(notif.patientGender),
                    note: '신규 SSE 접수 (세부내용 조회시 업데이트 됨)'
                }
            };
        });

        // 기본 히스토리 빈 배열 명시
        setHistoryDB(prev => {
            if (prev[notif.patientId]) return prev;
            return { ...prev, [notif.patientId]: [] };
        });

        try {
            if (notif.caseId) {
                await handlePatientSelect(notif.patientId, notif.caseId);
            } else {
                setSelectedReservationId(notificationReservation.id);
            }
            await syncAssignedCases();
        } catch {
            // Ignore notification sync failures and still dismiss the toast.
        } finally {
            handleDismissNotification(notif);
        }
    };

    // 현재 선택된 환자 데이터 & 필터링 뷰
    const sortReservations = (a, b) => {
        // 1. 완료 상태를 맨 아래로
        const isACompleted = TERMINAL_CASE_STATUSES.has(a.caseStatus) ? 1 : 0;
        const isBCompleted = TERMINAL_CASE_STATUSES.has(b.caseStatus) ? 1 : 0;
        if (isACompleted !== isBCompleted) {
            return isACompleted - isBCompleted;
        }
        
        // 2. 날짜 오름차순 (빠른 날짜 우선)
        if (a.date !== b.date) {
            return (a.date || '').localeCompare(b.date || '');
        }
        
        // 3. 시간 오름차순 (빠른 시간 우선)
        return (a.time || '').localeCompare(b.time || '');
    };

    const filteredReservations = [...reservations]
        .filter(res => filterType === '전체' || res.type === filterType)
        .sort(sortReservations);
    const effectiveSelectedReservationId = filteredReservations.some((reservation) => reservation.id === selectedReservationId)
        ? selectedReservationId
        : (filteredReservations.find((reservation) => !TERMINAL_CASE_STATUSES.has(reservation.caseStatus))?.id
            || filteredReservations[0]?.id
            || null);
    const effectiveSelectedReservation = filteredReservations.find((reservation) => reservation.id === effectiveSelectedReservationId) || null;
    const effectiveSelectedPatientId = effectiveSelectedReservation?.ptNo || null;
    const selectedPatientInfo = patientDB[effectiveSelectedPatientId] || null;
    const selectedHistory = historyDB[effectiveSelectedPatientId] || [];

    useEffect(() => {
        setExpandedHistoryRows({});
    }, [effectiveSelectedPatientId]);

    const handleLogout = async () => {
        await logoutSession();
        navigate('/emr/login');
    };

    const handleStartConsultation = (resId) => {
        // 비대면 화상진료 화면으로 이동 (resId = caseId)
        navigate(`/doctor/consultation/${resId}`);
    };

    const toggleHistoryRowExpansion = (historyId) => {
        setExpandedHistoryRows((prev) => ({
            ...prev,
            [historyId]: !prev[historyId]
        }));
    };

    return (
        <div className="flex flex-col h-screen bg-[#F0F0F0] font-sans text-sm select-none">
            {/* 1. 클래식 상단 네비게이션 바 */}
            <div className="bg-[#E0E0E0] border-b-2 border-slate-400 flex items-center justify-between px-2 py-1 shrink-0">
                <div className="flex space-x-1">
                    <button className="px-4 py-1.5 bg-[#F0F0F0] border border-slate-400 shadow-[inset_1px_1px_0_#FFF,1px_1px_0_#888] active:shadow-[inset_1px_1px_0_#888,1px_1px_0_#FFF] flex flex-col items-center">
                        <span className="font-bold text-slate-800">예약관리</span>
                    </button>
                    <button className="px-4 py-1.5 bg-[#E0E0E0] hover:bg-[#F0F0F0] border border-transparent flex flex-col items-center">
                        <span className="text-slate-700">진료관리</span>
                    </button>
                    <button className="px-4 py-1.5 bg-[#E0E0E0] hover:bg-[#F0F0F0] border border-transparent flex flex-col items-center">
                        <span className="text-slate-700">환자관리</span>
                    </button>
                    <button className="px-4 py-1.5 bg-[#E0E0E0] hover:bg-[#F0F0F0] border border-transparent flex flex-col items-center">
                        <span className="text-slate-700">수납관리</span>
                    </button>
                    <button className="px-4 py-1.5 bg-[#E0E0E0] hover:bg-[#F0F0F0] border border-transparent flex flex-col items-center">
                        <span className="text-slate-700">증명서발급</span>
                    </button>
                    <button className="px-4 py-1.5 bg-[#E0E0E0] hover:bg-[#F0F0F0] border border-transparent flex flex-col items-center">
                        <span className="text-slate-700">환경설정</span>
                    </button>
                </div>
                <div className="flex items-center space-x-4 pr-2">
                    <div className="text-slate-600 bg-white px-2 py-0.5 border border-slate-300 shadow-inner text-xs">
                        {currentTime.toLocaleDateString()} {currentTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                    <button onClick={handleLogout} className="px-4 py-1 bg-[#F0F0F0] border border-slate-400 shadow-[inset_1px_1px_0_#FFF,1px_1px_0_#888] active:shadow-[inset_1px_1px_0_#888,1px_1px_0_#FFF]">
                        <span className="text-red-700 font-bold text-xs">종료</span>
                    </button>
                </div>
            </div>

            {/* 2. 메인 3단 레이아웃 콘텐츠 구역 */}
            <div className="flex-1 flex overflow-hidden p-1 gap-1">

                {/* 좌측: 예약 관리 (대기자 리스트) */}
                <div className="w-[45%] shrink-0 flex flex-col border border-slate-400 bg-white">
                    {/* 패널 타이틀바 */}
                        <div className="bg-linear-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300 flex justify-between items-center">
                        <span className="font-bold text-slate-800 text-base lg:text-lg">📋 예약 및 대기자 관리</span>
                        <div className="flex space-x-2 text-xs">
                            <label className="flex items-center space-x-1 cursor-pointer">
                                <input type="radio" name="filter" checked={filterType === '전체'} onChange={() => setFilterType('전체')} /><span>전체</span>
                            </label>
                            <label className="flex items-center space-x-1 cursor-pointer">
                                <input type="radio" name="filter" checked={filterType === '외래'} onChange={() => setFilterType('외래')} /><span>외래</span>
                            </label>
                            <label className="flex items-center space-x-1 cursor-pointer">
                                <input type="radio" name="filter" checked={filterType === '비대면'} onChange={() => setFilterType('비대면')} /><span>비대면</span>
                            </label>
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto bg-white">
                        <div className="sticky top-0 z-10 bg-[#4472C4] text-white flex border-b border-slate-400 text-sm text-center font-bold">
                            <div className="w-12 shrink-0 border-r border-[#3B62A4] py-1.5">번호</div>
                            <div className="w-20 shrink-0 border-r border-[#3B62A4] py-1.5">환자명</div>
                            <div className="w-12 shrink-0 border-r border-[#3B62A4] py-1.5">성별</div>
                            <div className="flex-1 min-w-0 border-r border-[#3B62A4] py-1.5 text-left px-2.5">병명/증상</div>
                            <div className="w-20 shrink-0 border-r border-[#3B62A4] py-1.5">날짜</div>
                            <div className="w-16 shrink-0 border-r border-[#3B62A4] py-1.5">시간</div>
                            <div className="w-20 shrink-0 border-r border-[#3B62A4] py-1.5">구분</div>
                            <div className="w-24 shrink-0 py-1.5">상태</div>
                        </div>

                        {filteredReservations.length === 0 ? (
                            <div className="h-full flex items-center justify-center text-slate-400 text-sm">
                                데이터가 없습니다.
                            </div>
                        ) : (
                            filteredReservations.map((res, idx) => {
                                const consultationActionVisible = shouldShowConsultationAction(res);
                                const consultationStartEnabled = canStartConsultation(res, currentTime, directWebRtcEnabled);
                                const consultationStartButtonTitle = getConsultationStartButtonTitle(
                                    res,
                                    currentTime,
                                    directWebRtcEnabled
                                );

                                return (
                                    <div
                                        key={res.id}
                                        onClick={() => handlePatientSelect(res.ptNo, res.id)}
                                        className={`flex text-sm border-b border-slate-200 cursor-pointer ${effectiveSelectedReservationId === res.id ? 'bg-[#D9E1F2] font-semibold' : 'hover:bg-slate-50'
                                            }`}
                                    >
                                        <div className="w-12 shrink-0 py-2 text-center border-r border-slate-200">{idx + 1}</div>
                                        <div className="w-20 shrink-0 py-2 text-center border-r border-slate-200 truncate">{res.name}</div>
                                        <div className="w-12 shrink-0 py-2 text-center border-r border-slate-200 truncate">{res.gender}</div>
                                        <div className="flex-1 min-w-0 py-2 px-2.5 text-left border-r border-slate-200 truncate">{res.symptom}</div>
                                        <div className="w-20 shrink-0 py-2 text-center border-r border-slate-200 truncate">{res.date?.substring(5)}</div>
                                        <div className="w-16 shrink-0 py-2 text-center border-r border-slate-200 truncate">{res.time}</div>
                                        <div className="w-20 shrink-0 py-2 text-center border-r border-slate-200 text-[#0051C4] font-bold truncate">
                                            {res.type}
                                        </div>
                                        <div className="w-24 shrink-0 py-1.5 text-center flex justify-center items-center">
                                            {consultationActionVisible ? (
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        if (!consultationStartEnabled) {
                                                            return;
                                                        }
                                                        handleStartConsultation(res.id);
                                                    }}
                                                    disabled={!consultationStartEnabled}
                                                    title={consultationStartButtonTitle}
                                                    className={`px-1.5 py-0.5 text-xs whitespace-nowrap border shadow-sm ${consultationStartEnabled
                                                        ? 'bg-blue-600 text-white border-blue-800 hover:bg-blue-700'
                                                        : 'bg-slate-200 text-slate-500 border-slate-400 cursor-not-allowed'
                                                        }`}
                                                >
                                                    {hasRejoinableConsultationSession(res) ? '진료실 복귀 ↩' : '진료 시작 🎬'}
                                                </button>
                                            ) : (
                                                <span className={getReservationStatusClassName(res)}>{res.status}</span>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>

                </div>

                {/* 우측 패널들 (상/하 분할) */}
                <div className="flex-1 min-w-0 flex flex-col gap-1">

                    {/* 우측 상단: 환자 정보 창 */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-[#EFEFEF]">
                            <div className="bg-linear-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300">
                            <span className="font-bold text-slate-800 text-base lg:text-lg">👤 환자 상세 정보</span>
                        </div>
                        <div className="p-2 flex-1 flex flex-col pt-0">
                            {selectedPatientInfo ? (
                                <div className="bg-white border border-slate-300 p-3 lg:p-4 h-full overflow-hidden flex flex-col">
                                    <div className="overflow-hidden">
                                        <table className="w-full table-fixed text-left border-collapse">
                                            <tbody>
                                                <tr>
                                                    <th className={`w-20 lg:w-24 ${patientDetailLabelCellClass}`}>환자번호</th>
                                                    <td className={`w-36 lg:w-40 ${patientDetailStrongValueCellClass} whitespace-nowrap`}>{selectedPatientInfo.ptNo}</td>
                                                    <th className={`w-20 lg:w-24 ${patientDetailLabelCellClass}`}>성명</th>
                                                    <td className={`w-20 lg:w-24 ${patientDetailNameCellClass}`}>{selectedPatientInfo.name}</td>
                                                    <th className={`w-20 lg:w-24 ${patientDetailLabelCellClass}`}>성별/나이</th>
                                                    <td className={`${patientDetailValueCellClass} break-words`}>{selectedPatientInfo.gender} / {selectedPatientInfo.age === '미상' ? '미상' : `만 ${selectedPatientInfo.age}세`}</td>
                                                </tr>
                                                <tr>
                                                    <th className={patientDetailLabelCellClass}>생년월일</th>
                                                    <td className={`${patientDetailValueCellClass} tracking-tight whitespace-nowrap`}>{selectedPatientInfo.birthDate}</td>
                                                    <th className={patientDetailLabelCellClass}>연락처</th>
                                                    <td colSpan="3" className={`${patientDetailValueCellClass} break-all`}>{selectedPatientInfo.phone}</td>
                                                </tr>
                                                <tr>
                                                    <th className={patientDetailLabelCellClass}>자택주소</th>
                                                    <td colSpan="5" className={`${patientDetailValueCellClass} break-words`}>{selectedPatientInfo.address}</td>
                                                </tr>
                                                <tr>
                                                    <th className={patientDetailAlertLabelCellClass}>
                                                        <span className="block">특이사항</span>
                                                        <span className="mt-1 block text-xs leading-tight">(알러지 등)</span>
                                                    </th>
                                                    <td colSpan="5" className={`${patientDetailValueCellClass} h-16 align-top break-words font-bold text-red-600`}>{selectedPatientInfo.note}</td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            ) : (
                                <div className="flex-1 flex items-center justify-center text-sm lg:text-base text-slate-400 bg-white border border-slate-300">
                                    선택된 환자가 없습니다.
                                </div>
                            )}
                        </div>
                    </div>

                    {/* 우측 하단: 진료 내역 (History) */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-white">
                            <div className="bg-linear-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300">
                            <span className="font-bold text-slate-800 text-base lg:text-lg">📁 진료 및 처방 이력</span>
                        </div>

                        {/* 과거 내역 데이터 테이블 */}
                        <div className="flex-1 flex flex-col overflow-hidden bg-white">
                            <div className="flex flex-col min-h-0 flex-1">
                                <div className="grid w-full grid-cols-[3.25rem_7.5rem_4.5rem_minmax(0,1.35fr)_minmax(0,1.05fr)] bg-[#4472C4] text-white border-b border-slate-400 text-center">
                                    <div className={historyHeaderCellClass}>순번</div>
                                    <div className={historyHeaderCellClass}>진료일자</div>
                                    <div className={historyHeaderCellClass}>담당의</div>
                                    <div className={`${historyHeaderCellClass} min-w-0 text-left`}>진단명(상병)</div>
                                    <div className="min-w-0 px-2.5 py-1.5 text-left text-sm font-bold">처방 내역</div>
                                </div>

                                <div className="flex-1 overflow-y-auto bg-white">
                                    {selectedHistory.length === 0 ? (
                                        <div className="h-full flex items-center justify-center text-sm lg:text-base text-slate-400">
                                            등록된 과거 진료 내역이 없습니다.
                                        </div>
                                    ) : (
                                        selectedHistory.map((hist, idx) => {
                                            const historyRowId = hist.id || `${hist.date}-${hist.doctor}-${idx}`;
                                            const isHistoryExpanded = Boolean(expandedHistoryRows[historyRowId]);

                                            return (
                                                <div
                                                    key={historyRowId}
                                                    className={`grid w-full grid-cols-[3.25rem_7.5rem_4.5rem_minmax(0,1.35fr)_minmax(0,1.05fr)] border-b border-slate-200 transition-colors ${isHistoryExpanded ? 'bg-slate-50' : 'hover:bg-slate-50'} cursor-pointer`}
                                                    onClick={() => toggleHistoryRowExpansion(historyRowId)}
                                                    onKeyDown={(event) => {
                                                        if (event.key === 'Enter' || event.key === ' ') {
                                                            event.preventDefault();
                                                            toggleHistoryRowExpansion(historyRowId);
                                                        }
                                                    }}
                                                    role="button"
                                                    tabIndex={0}
                                                    aria-expanded={isHistoryExpanded}
                                                    title={isHistoryExpanded ? '클릭하면 접습니다.' : '클릭하면 전체 내용을 펼칩니다.'}
                                                >
                                                    <div className={`${historyBodyCellClass} text-center text-slate-500 whitespace-nowrap`}>{idx + 1}</div>
                                                    <div className={`${historyBodyCellClass} text-center whitespace-nowrap`}>{hist.date}</div>
                                                    <div className={`${historyBodyCellClass} text-center truncate`}>{hist.doctor}</div>
                                                    <div className={`${historyBodyCellClass} min-w-0 text-left font-semibold text-blue-700`}>
                                                        <div className={isHistoryExpanded ? 'break-words whitespace-normal leading-snug' : 'truncate'}>{hist.dx}</div>
                                                    </div>
                                                    <div className="min-w-0 px-2.5 py-2 text-left text-sm">
                                                        <div className={isHistoryExpanded ? 'break-words whitespace-normal leading-snug' : 'truncate'}>{hist.rx}</div>
                                                    </div>
                                                </div>
                                            );
                                        })
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

            </div>

            {/* SSE 알림 토스트 (우측 하단) */}
            <div className="fixed bottom-12 right-4 z-50 flex flex-col gap-3 pointer-events-none">
                {notifications.map((notif, index) => (
                    <div
                        key={getNotificationKey(notif) || index}
                        className="bg-white border-l-4 border-primary shadow-2xl rounded-lg w-80 overflow-hidden pointer-events-auto"
                    >
                        <div className="p-4">
                            <div className="flex justify-between items-start mb-2">
                                <div className="flex items-center gap-2">
                                    <div className="bg-blue-100 p-1.5 rounded-full">
                                        <Bell className="w-4 h-4 text-primary animate-pulse" />
                                    </div>
                                    <h3 className="font-bold text-slate-800">신규 예약 접수</h3>
                                </div>
                                <button
                                    onClick={() => handleDismissNotification(notif)}
                                    className="text-slate-400 hover:text-slate-600 transition-colors"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            </div>
                            <div className="text-sm text-slate-700 font-medium mb-1">
                                {notif.patientName}님 / {notif.appointmentDate} {notif.startTime?.substring(0, 5)}
                            </div>
                            <div className="text-xs text-slate-500 truncate mb-3">
                                {notif.location}
                            </div>
                            <div className="flex items-center justify-between mt-2">
                                <div className="text-xs font-semibold text-primary bg-blue-50 py-1 px-2 rounded inline-block">
                                    {notif.departmentName} · {notif.doctorName}
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        onClick={() => handleAcceptNotification(notif)}
                                        className="px-3 py-1 bg-green-600 text-white text-xs font-bold rounded shadow-sm hover:bg-green-700 transition"
                                    >
                                        확인
                                    </button>
                                    <button
                                        onClick={() => handleDismissNotification(notif)}
                                        className="px-3 py-1 bg-slate-200 text-slate-700 text-xs font-bold rounded shadow-sm hover:bg-slate-300 transition"
                                    >
                                        닫기
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* 상태 표시줄 (Bottom Bar) */}
            <div className="bg-[#E0E0E0] border-t border-slate-400 px-2 py-0.5 flex justify-between text-[11px] text-slate-600 shrink-0">
                <div className="flex space-x-4">
                    <span>의사랑 Ver 5.2.14 [최신버전]</span>
                    <span>사용자: {doctorDisplayName}</span>
                </div>
                <span>Caps Lock: OFF | NUM Lock: ON</span>
            </div>
        </div>
    );
};

export default LegacyEMRDashboard;
