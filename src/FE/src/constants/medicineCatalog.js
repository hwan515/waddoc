export const MEDICINE_CATALOG = [
    {
        code: 'M001',
        name: '타이레놀정 500mg',
        category: '해열진통제',
        dosage: '1회 1정 / 1일 3회 / 3일분',
    },
    {
        code: 'M002',
        name: '이부프로펜정 200mg',
        category: '소염진통제',
        dosage: '1회 1정 / 1일 3회 / 식후 복용 / 3일분',
    },
    {
        code: 'M003',
        name: '아목시실린캡슐 250mg',
        category: '항생제',
        dosage: '1회 1캡슐 / 1일 3회 / 5일분',
    },
    {
        code: 'M004',
        name: '알마겔현탁액 15ml',
        category: '제산제',
        dosage: '1회 1포 / 1일 3회 / 식전 복용',
    },
    {
        code: 'M005',
        name: '뮤코펙트정 30mg',
        category: '진해거담제',
        dosage: '1회 1정 / 1일 3회 / 3일분',
    },
    {
        code: 'M006',
        name: '코푸시럽 20ml',
        category: '진해거담제',
        dosage: '1회 1포 / 1일 3회 / 3일분',
    },
    {
        code: 'M007',
        name: '지르텍정 10mg',
        category: '항히스타민제',
        dosage: '1일 1회 1정 / 취침 전 / 5일분',
    },
    {
        code: 'M008',
        name: '클라리틴정 10mg',
        category: '항히스타민제',
        dosage: '1일 1회 1정 / 5일분',
    },
    {
        code: 'M009',
        name: '슈다페드정 60mg',
        category: '비충혈제거제',
        dosage: '1회 1정 / 1일 2회 / 3일분',
    },
    {
        code: 'M010',
        name: '몬테루카스트정 10mg',
        category: '알레르기 치료제',
        dosage: '1일 1회 1정 / 취침 전 / 7일분',
    },
    {
        code: 'M011',
        name: '프레드니솔론정 5mg',
        category: '스테로이드제',
        dosage: '1회 1정 / 1일 2회 / 식후 복용 / 3일분',
    },
    {
        code: 'M012',
        name: '타미플루캡슐 75mg',
        category: '항바이러스제',
        dosage: '1회 1캡슐 / 1일 2회 / 5일분',
    },
    {
        code: 'M013',
        name: '지스로맥스정 250mg',
        category: '항생제',
        dosage: '1일차 2정 / 이후 1일 1정 / 총 5일',
    },
    {
        code: 'M014',
        name: '레보플록사신정 500mg',
        category: '항생제',
        dosage: '1일 1회 1정 / 5일분',
    },
    {
        code: 'M015',
        name: '돔페리돈정 10mg',
        category: '위장운동조절제',
        dosage: '1회 1정 / 1일 3회 / 식전 복용 / 3일분',
    },
    {
        code: 'M016',
        name: '로페라마이드캡슐 2mg',
        category: '지사제',
        dosage: '설사 후 1캡슐 / 1일 최대 4캡슐',
    },
    {
        code: 'M017',
        name: '경구수분보충용산',
        category: '수분보충제',
        dosage: '1포를 물에 녹여 필요 시 복용',
    },
    {
        code: 'M018',
        name: '볼타렌겔 1%',
        category: '외용 소염진통제',
        dosage: '환부에 1일 3회 도포',
    },
    {
        code: 'M019',
        name: '박트로반연고',
        category: '외용 항생제',
        dosage: '환부에 1일 2회 얇게 도포 / 5일분',
    },
    {
        code: 'M020',
        name: '하이드로코르티손크림 1%',
        category: '외용 스테로이드',
        dosage: '환부에 1일 2회 얇게 도포 / 5일분',
    },
    {
        code: 'M021',
        name: '메트포르민정 500mg',
        category: '당뇨병 치료제',
        dosage: '1회 1정 / 1일 2회 / 식후 복용',
    },
    {
        code: 'M022',
        name: '암로디핀정 5mg',
        category: '혈압강하제',
        dosage: '1일 1회 1정',
    },
    {
        code: 'M023',
        name: '로수바스타틴정 10mg',
        category: '지질강하제',
        dosage: '1일 1회 1정 / 취침 전',
    },
    {
        code: 'M024',
        name: '판토록정 40mg',
        category: '위산분비억제제',
        dosage: '1일 1회 1정 / 아침 식전 / 7일분',
    },
];

export const MEDICINE_CATALOG_BY_CODE = Object.fromEntries(
    MEDICINE_CATALOG.map((medicine) => [medicine.code, medicine])
);
