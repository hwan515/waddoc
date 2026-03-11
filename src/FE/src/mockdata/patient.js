export const mockPatientProfile = {
    id: 'p001',
    name: '김태형',
    age: 34,
    gender: 'M',
    phone: '010-1234-5678',
    address: '서울시 강남구 테헤란로 212',
    bloodType: 'A+',
    allergies: ['페니실린'],
    chronicDiseases: ['고혈압'],
};

export const mockMedicalRecords = [
    {
        id: 'mr001',
        date: '2023-08-01',
        time: '10:00',
        doctorName: '이소진',
        department: '내과',
        diagnosis: '상기도 감염 (감기)',
        hasPrescription: true,
        hasNote: true,
        status: '완료',
    },
    {
        id: 'mr002',
        date: '2023-08-15',
        time: '14:30',
        doctorName: '박지민',
        department: '이비인후과',
        diagnosis: '알레르기성 비염',
        hasPrescription: true,
        hasNote: false,
        status: '완료',
    },
    {
        id: 'mr003',
        date: '2023-09-05',
        time: '09:00',
        doctorName: '이소진',
        department: '내과',
        diagnosis: '-',
        hasPrescription: false,
        hasNote: false,
        status: '예약', // Upcoming
    }
];

export const mockCalendarEvents = [
    {
        id: 'ev001',
        title: '내과 정기 검진',
        date: '2023-08-01',
        doctor: '이소진',
        type: 'past'
    },
    {
        id: 'ev002',
        title: '비염 진료',
        date: '2023-08-15',
        doctor: '박지민',
        type: 'past'
    },
    {
        id: 'ev003',
        title: '내과 진료 예약',
        date: '2023-09-05',
        doctor: '이소진',
        type: 'upcoming'
    }
];
