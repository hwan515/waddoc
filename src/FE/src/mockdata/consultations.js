// src/mockdata/consultations.js

// ECG 더미 데이터를 생성하는 함수
export const generateECGData = (length = 50) => {
    return Array.from({ length }, (_, i) => ({
        time: i,
        // 단순 반복 패턴
        value: i % 10 === 0 ? 80 : i % 10 === 1 ? -20 : i % 10 === 2 ? 60 : 0
    }));
};

// 화상 진료실 내에서 사용되는 환자/의사 등 공통 더미 데이터
export const mockConsultationDetails = {
    patientName: '김태형',
    patientAge: 34,
    patientPhone: '010-1234-5678',
    doctorName: '홍길동',
    roomNumber: '진료 5번방',
};

// 실시간 환자 바이탈 초기 더미 데이터
export const mockVitals = {
    spO2: 97,
    heartRate: 72,
    bloodPressureSys: 120,
    bloodPressureDia: 80,
    temperature: 36.7
};
