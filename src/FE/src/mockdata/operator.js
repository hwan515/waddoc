// src/mockdata/operator.js

export const mockVehicles = [
    {
        id: 'doc1',
        name: '현장 모빌리티 1호기',
        location: { lat: 37.1234, lng: 127.1234 },
        status: '운행 중', // 운행 중, 대기 중, 진료 중, 점검 중, 장애
        battery: 85,
        speed: 45, // km/h
        lastUpdated: '2026-03-09T14:30:00Z'
    },
    {
        id: 'doc2',
        name: '현장 모빌리티 2호기',
        location: { lat: 37.5665, lng: 126.9780 },
        status: '진료 중',
        battery: 42,
        speed: 0,
        lastUpdated: '2026-03-09T14:28:00Z'
    },
    {
        id: 'doc3',
        name: '예비 모빌리티 3호기',
        location: { lat: 35.1796, lng: 129.0756 },
        status: '대기 중',
        battery: 100,
        speed: 0,
        lastUpdated: '2026-03-09T14:15:00Z'
    },
    {
        id: 'doc4',
        name: '긴급 모빌리티 4호기',
        location: { lat: 37.4562, lng: 126.7052 },
        status: '점검 중',
        battery: 15,
        speed: 0,
        lastUpdated: '2026-03-09T13:50:00Z'
    }
];

// 전체 병원/예약 통계 일정 (캘린더 연동 용, 의사 대시보드 구조 재사용 가능토록 구성)
export const mockTotalCalendarEvents = [
    { id: 't1', name: '김태형', type: '초진', timeStr: '14:30', dayIdx: 1, doctor: '김의사' },
    { id: 't2', name: '이수진', type: '재진', timeStr: '10:00', dayIdx: 2, doctor: '박의사' },
    { id: 't3', name: '박지민', type: '초진', timeStr: '09:00', dayIdx: 3, doctor: '이의사' },
    { id: 't4', name: '김철수', type: '재진', timeStr: '15:30', dayIdx: 4, doctor: '최의사' },
    { id: 't5', name: '김서율', type: '초진', timeStr: '11:00', dayIdx: 5, doctor: '김의사' }
];

// 금일 대기 인원 현황 (카드형 표출 용)
export const mockTodayQueue = [
    {
        id: 'q1',
        name: '정하늘',
        address: '서울시 강남구 테헤란로 212',
        phone: '010-7777-8888',
        doctorName: '이의사',
        status: '진료중' // 대기중, 진료중, 진료완료
    },
    {
        id: 'q2',
        name: '최영수',
        address: '경기도 성남시 분당구 판교역로 152',
        phone: '010-9999-0000',
        doctorName: '김의사',
        status: '대기중'
    },
    {
        id: 'q3',
        name: '박민지',
        address: '서울시 서초구 서초대로 74길 11',
        phone: '010-2222-3333',
        doctorName: '박의사',
        status: '진료완료'
    }
];

// 연/월/일별 통계 정보
export const mockStatistics = {
    totalPatients: 142,
    avgWaitTime: 18, // 분
    avgConsultTime: 12, // 분
    vehicleMissions: {
        doc1: 12,
        doc2: 8,
        doc3: 2,
        doc4: 0
    }
};
