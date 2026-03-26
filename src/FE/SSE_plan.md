# Doctor SSE Notification Contract v0

태그: 설계

**목적:**

- 의사 로그인 상태에서 담당 신규 예약이 생성되면 EMR 화면에 실시간 알림을 표시한다.
- 이번 범위는 단방향 알림만 포함한다.
1. **연결 정보**
• Method: GET
• Endpoint: /api/v1/doctors/me/notifications/stream
• Content-Type: text/event-stream
• 사용 대상: DOCTOR 권한 사용자만
• 연결 시점: 의사 대시보드 진입 후 즉시 연결
• 연결 종료 시점: 로그아웃, 탭 이탈, 대시보드 unmount 시 종료
2. **인증 방식**
• Header: Authorization: Bearer {accessToken}
• 주의: 브라우저 기본 EventSource는 Authorization 헤더를 못 붙이므로 사용하지 않음
• 권장 클라이언트: fetch-event-source 계열 SSE 클라이언트 사용
3. **식별 기준**
• 백엔드 내부 라우팅 기준: doctorProfile.publicId
• 프론트는 별도 doctorId를 URL에 넣지 않음
• 서버가 로그인 사용자 기준으로 담당 의사를 판별해 해당 스트림으로 연결함
4. **이벤트 종류**
• connected
• notification
• ping
5. **이벤트 payload notification 이벤트의 data는 JSON 문자열이며 현재는 아래 1종만 사용**

```json
{
"type": "NEW_BOOKING",
"bookingId": "bk_xxxxxxxx",
"caseId": "case_xxxxxxxx",
"doctorId": "doc_xxxxxxxx",
"doctorName": "김도현",
"departmentName": "내과",
"patientName": "박순자",
"appointmentDate": "2026-03-24",
"startTime": "14:30:00",
"location": "경북 김천시 증산면 장전1길 69",
"createdAt": "2026-03-19T15:42:11+09:00"
}
```

필드 설명:
• type: 알림 타입, 현재는 NEW_BOOKING
• bookingId: 예약 식별자
• caseId: 생성된 케이스 식별자
• doctorId: 담당 의사 식별자
• doctorName: 담당 의사명
• departmentName: 진료과명
• patientName: 환자명
• appointmentDate: 예약 날짜
• startTime: 예약 시작 시간
• location: 환자 주소
• createdAt: 알림 생성 시각

1. **SSE 원문 예시**

```json

event: connected
data: {"connectedAt":"2026-03-19T15:40:00+09:00"}

event: ping
data: {"ts":"2026-03-19T15:40:30+09:00"}

event: notification
data: {"type":"NEW_BOOKING","bookingId":"bk_xxxxxxxx","caseId":"case_xxxxxxxx","doctorId":"doc_xxxxxxxx","doctorName":"김도현","departmentName":"내과","patientName":"박순자","appointmentDate":"2026-03-24","startTime":"14:30:00","location":"경북 김천시 증산면 장전1길 69","createdAt":"2026-03-19T15:42:11+09:00"}
```

**7. 프론트 처리 규칙**

- connected 수신 시 연결 성공 상태만 표시
- ping은 UI 반영 없이 무시
- notification 수신 시 data.type 기준으로 분기
- type === NEW_BOOKING이면 토스트 또는 모달 노출
- 알림 클릭 시 예약 상세 또는 케이스 상세 화면으로 이동
- 필요 시 알림 수신 직후 목록 재조회 API 호출 가능

**8. 재연결 정책**

• 네트워크 단절 또는 서버 종료 시 자동 재연결
• 권장 재시도 간격: 3초 -> 5초 -> 10초
• 401/403 응답이면 재연결 중단 후 로그인 상태 확인
• 토큰 갱신이 필요한 경우 기존 프론트 auth 흐름 후 재연결

**9. 에러 처리 기준**

- 401: 로그인 만료 또는 토큰 문제
- 403: 의사 권한 없음
- 5xx: 서버 일시 오류, 재연결 대상
- emitter 종료로 연결이 끊기면 프론트는 자동 재연결

**10. UI 표시 권장 문구**

• 제목: 신규 예약이 접수되었습니다
• 본문: {patientName}님 / {appointmentDate} {startTime} / {location}
• 보조: {departmentName} · {doctorName}

**11. 프론트 작업 범위**

- SSE 연결 훅 또는 서비스 작성
- NEW_BOOKING 이벤트 파싱
- 토스트/모달 UI 연결
- 재연결 및 cleanup 처리
- mock payload 기반 선개발 가능

**12. 참고 경로**

- 예약 생성 백엔드: BookingService.java
- 기존 AFTER_COMMIT 패턴: BookingNotificationListener.java
- 의사 대시보드: Dashboard.jsx
- 로그인/토큰 저장: Login.jsx, authStore.js, api.js

**요약**

<aside>
💡

GET /api/v1/doctors/me/notifications/stream에 Bearer 토큰으로 SSE 연결하고, notification 이벤트의 type=NEW_BOOKING payload를 받아 토스트/모달을 띄우면 됩니다.

</aside>