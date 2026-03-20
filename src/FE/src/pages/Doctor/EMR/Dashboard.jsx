import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, X } from 'lucide-react';
import useAuthStore from '../../../store/authStore';
import { useSSE } from '../../../hooks/useSSE';

const LegacyEMRDashboard = () => {
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);

    // SSE 알림 연동
    const { isConnected, notifications, removeNotification } = useSSE();

    // Initial Mock Data Context
    const [currentTime, setCurrentTime] = useState(new Date());

    // 1. 예약 관리 (왼쪽 리스트) - 오프라인 대기 및 비대면 예약 통합
    const [reservations, setReservations] = useState([
        { id: 'RV001', ptNo: 'P1001', name: '김철수', gender: '남', symptom: '감기, 기침', date: '2026-03-13', time: '09:00', status: '진료대기', type: '외래' },
        { id: 'RV002', ptNo: 'P1002', name: '이영희', gender: '여', symptom: '소화불량', date: '2026-03-13', time: '09:30', status: '예약', type: '외래' },
        { id: 'RV003', ptNo: 'P1003', name: '박지성', gender: '남', symptom: '발목 통증', date: '2026-03-13', time: '10:00', status: '수납대기', type: '외래' },
        { id: 'RV004', ptNo: 'P1004', name: '최수아', gender: '여', symptom: '정기 검진', date: '2026-03-13', time: '10:30', status: '완료', type: '외래' },
    ]);



    // 현재 선택된 환자 (우측 패널에 정보 표시)
    const [selectedPatientId, setSelectedPatientId] = useState('P1001');

    // 2. 환자 관리 DB (Mock) - 우측 상단용
    const patientDB = {
        'P1001': { ptNo: 'P1001', name: '김철수', address: '서울시 강남구 테헤란로 123', jumin: '800101-1234567', phone: '010-1234-5678', age: 46, gender: '남', note: '본태성 고혈압 약 복용중' },
        'P1002': { ptNo: 'P1002', name: '이영희', address: '서울시 서초구 서초대로 45', jumin: '940505-2345678', phone: '010-9876-5432', age: 32, gender: '여', note: '페니실린 알러지 주의' },
        'P1003': { ptNo: 'P1003', name: '박지성', address: '경기도 성남시 분당구 판교역로 88', jumin: '810225-1111222', phone: '010-5555-6666', age: 45, gender: '남', note: '특이사항 없음' },
        'P1004': { ptNo: 'P1004', name: '최수아', address: '서울시 송파구 올림픽로 300', jumin: '000101-4444555', phone: '010-7777-8888', age: 26, gender: '여', note: '비염 이력' },
        'P2001': { ptNo: 'P2001', name: '홍길동', address: '비대면 환자 (주소 미상)', jumin: '900301-1999888', phone: '010-1111-2222', age: 36, gender: '남', note: '타병원 위내시경 결과 가지고 있음' },
    };

    // 3. 진료 정보 DB (Mock) - 우측 하단용
    const historyDB = {
        'P1001': [
            { id: 'H1', date: '2026-02-13', doctor: '김의사', symptom: '감기 몸살', dx: 'J00 급성 비인두염', rx: '타이레놀 3일치' },
            { id: 'H2', date: '2026-01-10', doctor: '김의사', symptom: '두통', dx: 'G44 기타 두통 증후군', rx: '이부프로펜 2일치' }
        ],
        'P1002': [
            { id: 'H3', date: '2025-12-25', doctor: '최원장', symptom: '복통', dx: 'K30 기능성 소화불량', rx: '소화제 처방' }
        ],
        'P2001': [
            { id: 'H4', date: '2025-08-10', doctor: '이원장', symptom: '어지러움', dx: 'H811 양성 발작성 현기증', rx: '안정 권유' }
        ]
    };

    // 현재 선택된 환자 데이터
    const selectedPatientInfo = patientDB[selectedPatientId] || null;
    const selectedHistory = historyDB[selectedPatientId] || [];

    // 시계 업데이트
    useEffect(() => {
        const timer = setInterval(() => setCurrentTime(new Date()), 60000);
        return () => clearInterval(timer);
    }, []);

    const handlePatientSelect = (ptNo) => {
        setSelectedPatientId(ptNo);
    };



    const handleAcceptNotification = (notif) => {
        setReservations(prev => [...prev, {
            id: notif.bookingId,
            ptNo: notif.patientId,
            name: notif.patientName,
            gender: notif.patientGender,
            symptom: "진료시 확인 요청",
            date: notif.appointmentDate,
            time: notif.startTime?.substring(0, 5) || '00:00', // 14:30:00 -> 14:30
            status: '예약',
            type: '비대면'
        }].sort((a, b) => a.time.localeCompare(b.time)));

        removeNotification(notif.createdAt);
    };



    const handleLogout = () => {
        logout();
        navigate('/emr/login');
    };

    const handleStartConsultation = (resId) => {
        // 비대면 화상진료 화면으로 이동
        navigate(`/doctor/consultation/${resId}`);
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
                <div className="flex-[4] flex flex-col border border-slate-400 bg-white">
                    {/* 패널 타이틀바 */}
                    <div className="bg-gradient-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300 flex justify-between items-center">
                        <span className="font-bold text-slate-800 text-sm">📋 예약 및 대기자 관리</span>
                        <div className="flex space-x-2 text-xs">
                            <label className="flex items-center space-x-1"><input type="radio" name="filter" defaultChecked /><span>전체</span></label>
                            <label className="flex items-center space-x-1"><input type="radio" name="filter" /><span>외래</span></label>
                            <label className="flex items-center space-x-1"><input type="radio" name="filter" /><span>비대면</span></label>
                        </div>
                    </div>

                    {/* 데이터 테이블 Header 영역 */}
                    <div className="bg-[#4472C4] text-white flex border-b border-slate-400 text-xs text-center font-bold">
                        <div className="w-10 border-r border-[#3B62A4] py-1">번호</div>
                        <div className="w-20 border-r border-[#3B62A4] py-1">환자명</div>
                        <div className="w-12 border-r border-[#3B62A4] py-1">성별</div>
                        <div className="flex-1 border-r border-[#3B62A4] py-1 text-left px-2">병명/증상</div>
                        <div className="w-20 border-r border-[#3B62A4] py-1">날짜</div>
                        <div className="w-16 border-r border-[#3B62A4] py-1">시간</div>
                        <div className="w-24 border-r border-[#3B62A4] py-1">구분</div>
                        <div className="w-28 py-1">상태 (액션)</div>
                    </div>

                    {/* 데이터 테이블 Body 영역 */}
                    <div className="flex-1 overflow-y-auto bg-white">
                        {reservations.map((res, idx) => (
                            <div
                                key={res.id}
                                onClick={() => handlePatientSelect(res.ptNo)}
                                className={`flex text-xs border-b border-slate-200 cursor-pointer ${selectedPatientId === res.ptNo ? 'bg-[#D9E1F2] font-semibold' : 'hover:bg-slate-50'
                                    }`}
                            >
                                <div className="w-10 py-1.5 text-center border-r border-slate-200">{idx + 1}</div>
                                <div className="w-20 py-1.5 text-center border-r border-slate-200">{res.name}</div>
                                <div className="w-12 py-1.5 text-center border-r border-slate-200">{res.gender}</div>
                                <div className="flex-1 py-1.5 px-2 text-left border-r border-slate-200 truncate">{res.symptom}</div>
                                <div className="w-20 py-1.5 text-center border-r border-slate-200">{res.date.substring(5)}</div>
                                <div className="w-16 py-1.5 text-center border-r border-slate-200">{res.time}</div>
                                <div className="w-24 py-1.5 text-center border-r border-slate-200 text-[#0051C4] font-bold">
                                    {res.type}
                                </div>
                                <div className="w-28 py-1 text-center flex justify-center items-center">
                                    {res.type === '비대면' && res.status === '예약' ? (
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleStartConsultation(res.id); }}
                                            className="px-2 py-0.5 bg-blue-600 text-white text-xs border border-blue-800 shadow-sm hover:bg-blue-700"
                                        >
                                            진료 시작 🎬
                                        </button>
                                    ) : (
                                        <span className={`${res.status === '진료대기' ? 'text-red-600 font-bold' :
                                                res.status === '수납대기' ? 'text-orange-600' : 'text-slate-600'
                                            }`}>{res.status}</span>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>


                </div>

                {/* 우측 패널들 (상/하 분할) */}
                <div className="flex-[6] flex flex-col gap-1">

                    {/* 우측 상단: 환자 정보 창 */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-[#EFEFEF]">
                        <div className="bg-gradient-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300">
                            <span className="font-bold text-slate-800 text-sm">👤 환자 상세 정보</span>
                        </div>
                        <div className="p-2 flex-1 flex flex-col pt-0">
                            {selectedPatientInfo ? (
                                <div className="bg-white border border-slate-300 p-3 h-full overflow-hidden flex flex-col">
                                    <table className="w-full text-xs text-left border-collapse">
                                        <tbody>
                                            <tr>
                                                <th className="w-24 bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">환자번호</th>
                                                <td className="w-32 border border-slate-300 px-2 py-1.5 font-bold text-blue-800">{selectedPatientInfo.ptNo}</td>
                                                <th className="w-24 bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">성명</th>
                                                <td className="w-32 border border-slate-300 px-2 py-1.5 font-bold text-lg leading-none">{selectedPatientInfo.name}</td>
                                                <th className="w-20 bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">성별/나이</th>
                                                <td className="border border-slate-300 px-2 py-1.5">{selectedPatientInfo.gender} / 만 {selectedPatientInfo.age}세</td>
                                            </tr>
                                            <tr>
                                                <th className="bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">주민등록번호</th>
                                                <td className="border border-slate-300 px-2 py-1.5 tracking-widest">{selectedPatientInfo.jumin}</td>
                                                <th className="bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">연락처</th>
                                                <td colSpan="3" className="border border-slate-300 px-2 py-1.5">{selectedPatientInfo.phone}</td>
                                            </tr>
                                            <tr>
                                                <th className="bg-[#E2EFDA] border border-slate-300 px-2 py-1.5 font-bold text-[#385723]">자택주소</th>
                                                <td colSpan="5" className="border border-slate-300 px-2 py-1.5">{selectedPatientInfo.address}</td>
                                            </tr>
                                            <tr>
                                                <th className="bg-[#FFE699] border border-slate-300 px-2 py-1.5 font-bold text-[#C55A11] align-top">특이사항 (알러지)</th>
                                                <td colSpan="5" className="border border-slate-300 px-2 py-1.5 text-red-600 font-bold h-12 align-top">{selectedPatientInfo.note}</td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            ) : (
                                <div className="flex-1 flex items-center justify-center text-slate-400 bg-white border border-slate-300">
                                    선택된 환자가 없습니다.
                                </div>
                            )}
                        </div>
                    </div>

                    {/* 우측 하단: 진료 내역 (History) */}
                    <div className="flex-1 flex flex-col border border-slate-400 bg-white">
                        <div className="bg-gradient-to-b from-[#FFF] to-[#E5E5E5] px-2 py-1 border-b border-slate-300">
                            <span className="font-bold text-slate-800 text-sm">📁 진료 및 처방 이력</span>
                        </div>

                        {/* 과거 내역 데이터 테이블 */}
                        <div className="bg-[#4472C4] text-white flex border-b border-slate-400 text-xs text-center font-bold">
                            <div className="w-10 border-r border-[#3B62A4] py-1">순번</div>
                            <div className="w-24 border-r border-[#3B62A4] py-1">진료일자</div>
                            <div className="w-16 border-r border-[#3B62A4] py-1">담당의</div>
                            <div className="w-32 border-r border-[#3B62A4] py-1 text-left px-2">내원 사유</div>
                            <div className="w-40 border-r border-[#3B62A4] py-1 text-left px-2">진단명(상병)</div>
                            <div className="flex-1 py-1 text-left px-2">처방 내역</div>
                        </div>

                        <div className="flex-1 overflow-y-auto bg-white">
                            {selectedHistory.length === 0 ? (
                                <div className="h-full flex items-center justify-center text-slate-400">
                                    등록된 과거 진료 내역이 없습니다.
                                </div>
                            ) : (
                                selectedHistory.map((hist, idx) => (
                                    <div key={hist.id} className="flex text-xs border-b border-slate-200 hover:bg-slate-50 cursor-default">
                                        <div className="w-10 py-1.5 text-center border-r border-slate-200 text-slate-500">{idx + 1}</div>
                                        <div className="w-24 py-1.5 text-center border-r border-slate-200">{hist.date}</div>
                                        <div className="w-16 py-1.5 text-center border-r border-slate-200">{hist.doctor}</div>
                                        <div className="w-32 py-1.5 px-2 text-left border-r border-slate-200 truncate">{hist.symptom}</div>
                                        <div className="w-40 py-1.5 px-2 text-left text-blue-700 font-semibold border-r border-slate-200 truncate">{hist.dx}</div>
                                        <div className="flex-1 py-1.5 px-2 text-left truncate">{hist.rx}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>

            </div>

            {/* SSE 알림 토스트 (우측 하단) */}
            <div className="fixed bottom-12 right-4 z-50 flex flex-col gap-3 pointer-events-none">
                {notifications.map((notif, index) => (
                    <div
                        key={notif.createdAt || index}
                        className="bg-white border-l-4 border-[#0353A4] shadow-2xl rounded-lg w-80 overflow-hidden pointer-events-auto"
                    >
                        <div className="p-4">
                            <div className="flex justify-between items-start mb-2">
                                <div className="flex items-center gap-2">
                                    <div className="bg-blue-100 p-1.5 rounded-full">
                                        <Bell className="w-4 h-4 text-[#0353A4] animate-pulse" />
                                    </div>
                                    <h3 className="font-bold text-slate-800">신규 예약 접수</h3>
                                </div>
                                <button
                                    onClick={() => removeNotification(notif.createdAt)}
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
                                <div className="text-xs font-semibold text-[#0353A4] bg-blue-50 py-1 px-2 rounded inline-block">
                                    {notif.departmentName} · {notif.doctorName}
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        onClick={() => handleAcceptNotification(notif)}
                                        className="px-3 py-1 bg-green-600 text-white text-xs font-bold rounded shadow-sm hover:bg-green-700 transition"
                                    >
                                        수락
                                    </button>
                                    <button
                                        onClick={() => removeNotification(notif.createdAt)}
                                        className="px-3 py-1 bg-slate-200 text-slate-700 text-xs font-bold rounded shadow-sm hover:bg-slate-300 transition"
                                    >
                                        거절
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
                    <span>사용자: 원장</span>
                </div>
                <span>Caps Lock: OFF | NUM Lock: ON</span>
            </div>
        </div>
    );
};

export default LegacyEMRDashboard;
