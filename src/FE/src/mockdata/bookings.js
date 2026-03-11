// src/mockdata/bookings.js

// Represents INTAKE_SESSION / BOOKING waiting for a doctor's approval (left column)
export const mockNewRequests = [
    {
        id: 1,
        name: '김태형',
        phone: '010-1234-5678',
        age: 34,
        requestDate: '2023.08.10 (목)',
        timeStr: '14:30',
        type: '초진',
        dayIdx: 4
    },
    {
        id: 2,
        name: '이수진',
        phone: '010-8765-4321',
        age: 28,
        requestDate: '2023.08.11 (금)',
        timeStr: '10:00',
        type: '재진',
        dayIdx: 5
    },
];

// Represents CONFIRMED bookings / cases scheduled for today (right column)
export const mockTodaySchedule = [
    {
        id: 1,
        time: '09:00',
        name: '박지민',
        type: '초진',
        status: '대기',
        phone: '010-1111-2222',
        age: 45,
        memo: '혈압약 처방 연장'
    },
    {
        id: 2,
        time: '10:30',
        name: '김철수',
        type: '재진',
        status: '완료',
        phone: '010-3333-4444',
        age: 60,
        memo: '당뇨 정기 검진'
    },
    {
        id: 3,
        time: '13:00',
        name: '김서율',
        type: '초진',
        status: '예정',
        phone: '010-5555-6666',
        age: 32,
        memo: '최근 잦은 두통 호소'
    },
    {
        id: 4,
        time: '15:30',
        name: '정하늘',
        type: '재진',
        status: '예정',
        phone: '010-7777-8888',
        age: 25,
        memo: '이전 처방 부작용 확인'
    },
];
