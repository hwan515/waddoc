# [MVP] 요구사항 명세서 (SRS)

# 1. 프로젝트 개요

## 1.1 한 줄 정의

- 도서·산간 의료취약지 환자가 **앱 없이 전화(IVR/TTS)** 로 예약하고, 예약 시간에 맞춰 **자율주행 차량(로봇)** 이 집 앞까지 이동하여 **본인확인·동의·기초 생체정보 측정·WebRTC 화상진료·재진 예약·처방 상태 추적**까지 연결하는 방문형 비대면 진료 서비스 MVP

---

## 1.2 프로젝트 목표

| 구분 | 내용 |
| --- | --- |
| MVP 목표 | 2026-03-30까지 **예약 → 출동 미션 생성 → MORAI 기반 자율주행 → 관제 복구 → 탑승 → 본인확인/동의 → vital 측정 → WebRTC 진료 → 재진 예약 → 처방 전송/상태 추적 → 보호자 조회**가 **한 번의 E2E 데모로 재현 가능**하도록 구현 |
| 주요 결과물 | 1) 전화 기반 예약/취소/확인 시스템(IVR/TTS) 2) 일정/배차/알림 백엔드 3) ROS2+MORAI 기반 실외 자율주행 시나리오 4) 관제 콘솔(지도/뷰/ETA/E-stop/teleop) 5) vital 수집 파이프라인(실제 구현이 어려워 Mock으로 대체) 6) 의사 UI 및 보호자 포털 7) 감사 로그 및 KPI 리포트 |
| 기대 효과 | 1) 디지털 소외 환자의 접근성 확보 2) 의료진·운영자 관점의 실운영 가능성 검증 3) 도서·산간형 방문 진료 서비스의 정책/사업화 검토를 위한 데모 자산 확보 4) 연속 케어(재진/처방 상태 추적) 구조 검증 |

---

# 2. 범위 정의

## 2.1 In Scope

| 구분 | 설명 |
| --- | --- |
| 서비스 기능 | 전화 예약(IVR/TTS), 예약 확인/취소, 초진/재진 규칙 적용, 10~20분 슬롯 운영, 예약 확정/전날 재안내 SMS, 의사 UI에서 재진 예약 등록, 처방 전송 상태 추적, 보호자 상태 조회 |
| 자율주행 기능 | MORAI 시뮬레이터 기반 실외 주행, Google Map 참조 유사 마을 맵 구성, GPS 기반 경로 추종, LiDAR/카메라 기반 장애물 인지 및 안전 정지, 집 앞 도착 판정, 원격 정지 및 teleop takeover |
| 관제 기능 | 차량 위치/경로/목표지점/ETA 표시, 속도/배터리/네트워크/센서 헬스 모니터링, 전방 카메라 스트림 조회, 에러 코드 기반 복구 절차, E-stop, teleop, 재시도/취소/수동개입 |
| 사용자 인터페이스 | 환자용 최소 탑승 UI(큰 글씨 안내/진료 시작), 의사 UI(vital·화상진료·재진 예약·처방 상태), 운영자 관제 UI, 보호자 웹 포털(예약/ETA/도착/진료/요약/처방 상태) |
| 통신 및 데이터 처리 | REST API, WebSocket 실시간 이벤트, WebRTC 화상 연결, vital 업로드/저장, 예약/미션/진료/처방 상태 저장, 감사 로그 및 장애 이벤트 수집 |

---

## 2.2 Out of Scope

| 구분 | 설명 |
| --- | --- |
| 제외 기능 | 실제 공공도로 상용 운행 인허가, 완전 무인 공공도로 실증, 의료 AI 진단/판독, 보험 청구/수가 정산, 실제 병원 EMR/HIS 양방향 연동, 약 최종 배송, 환자 전용 모바일 앱, 다차량 최적 배차 |
| 추후 확장 기능 | 보호자 동시 접속 3자 화상진료, 당일 아침 추가 문자, 자동 음성 증상 분류 고도화, 체중/문진 확장, 약국 전산 직접 연동, 다지역 운영, 실제 차량 탑재 검증, 정교한 경로 재계획/군집 운영 |

---

## 2.3 가정 및 제약

| 구분 | 내용 |
| --- | --- |
| 운영 지역 | MORAI 상에 구성된 **단일 의료취약지 마을 맵** 내 지정 geofence 구역. 환자 주소와 좌표는 **사전 등록**되어 있어야 함 |
| 운영 시간 | MVP 기준 평일 09:00~18:00, 마지막 출동 권장 17:00 이전. 전날 재안내 문자는 전일 오후 발송 |
| 환경 조건 | 신호등 없는 도서·산간형 저밀도 도로를 가정. 좁은 도로, 갓길, 보행자, 주차 차량, 일시적 장애물 발생 가능. 악천후/야간/급경사 등은 MVP 기본 시나리오에서 제외 |
| 네트워크 조건 | 차량과 서버 간 4G/5G 또는 동등한 네트워크를 전제하되, **일시적 품질 저하/패킷 손실/지연**이 발생할 수 있음. 화상진료는 TURN 및 저대역폭 모드 전환을 고려 |
| 법적 제약 | 실제 운영이 아닌 MVP/실증 전제이며, 관련 법령·가이드라인·기관 협약 범위 내 수행. 응급환자/고위험 환자에 대한 일반 외래형 비대면 진료 대체는 범위 외 |
| 협력 기관 | 병원/의료진, 보건소, 약국 또는 약 수령 기관, SMS 제공사, 지도/좌표 시스템, MORAI 시뮬레이터 운영 환경, 네트워크 운영 주체 |

---

# 3. 이해관계자 및 사용자

## 3.1 이해관계자

| 역할 | 설명 | 성공 기준 |
| --- | --- | --- |
| 환자 | 스마트폰/앱 사용이 어려운 도서·산간 지역 환자. 전화만으로 예약하고 차량 탑승 후 진료를 받아야 함 | 전화 예약 성공, 집 앞 도착, 본인확인/동의 완료, vital 측정, 화상진료 연결, 종료 안내 수신 |
| 보호자 | 환자와 떨어져 거주하며 예약·이동·진료·처방 진행 상태를 확인하고 싶어 하는 가족 | 웹 포털에서 ETA/진료 상태/측정값 요약/처방 상태 조회 가능 |
| 의료진 | 환자 정보, vital, 연결 상태를 확인하고 비대면 진료 후 재진 예약/처방 전송을 수행 | 환자 확인 완료, vital 사전 확인, 안정적 WebRTC 연결, 진료 종료 후 후속 일정/처방 상태 등록 가능 |
| 운영자 | 관제에서 차량과 미션을 감시하고 장애 발생 시 E-stop/teleop/복구를 수행 | 실시간 위치/상태/전방 뷰 확인, 장애 시 1차 복구 수행, 모든 개입 로그 기록 |
| 기관 | 병원/보건소/지자체/사업 주관기관. 서비스 타당성, 운영 가능성, 안전성, 기록성을 검토 | E2E 데모 재현 성공, KPI 충족, 감사 로그 보전, 향후 실증 확장 가능성 확보 |

---

## 3.2 사용자 페르소나

| 구분 | 설명 |
| --- | --- |
| 환자 | 70대 이상 고령자 또는 이동이 불편한 만성질환 환자. 스마트폰 사용이 서툴고, 예약은 전화로만 가능해야 함. 탑승 후 UI는 큰 글씨와 최소 버튼만 허용 |
| 보호자 | 도시 거주 자녀/가족. 현장에 없지만 환자 상태와 서비스 진행 상황을 확인하고 싶어 함. 모바일 웹/PC 웹 사용 가능 |
| 의료진 | 짧은 진료 시간 내에 환자 확인, vital 확인, 화상 연결, 재진 예약, 처방 상태 입력까지 마쳐야 하는 외래 의료진 |
| 운영자 | 1명의 운영자가 1대 차량과 1건 미션을 우선 관리하는 형태. 지도/영상/상태/에러코드 중심으로 빠르게 상황 판단해야 함 |

---

# 4. 시스템 개요

## 4.1 시스템 구성

| 구성 요소 | 설명 |
| --- | --- |
| Robot System | ROS2 기반 주행 스택. MORAI 연동, GPS/IMU/LiDAR/카메라 입력 수집, 경로 추종, 장애물 정지, teleop 수용, E-stop, 도착 판정, cabin HMI 및 vital 게이트웨이 포함 |
| Cloud Server | 예약/슬롯 스케줄러, 미션 오케스트레이터, 환자/보호자/의료진/차량 관리, SMS 발송, 상태 저장, 감사 로그, API 서버, 실시간 이벤트 브로커 |
| WebRTC System | 화상진료 세션 생성, signaling, STUN/TURN, 연결 상태 이벤트, 재시도 로직, 저대역폭 프로파일 전환 이벤트 관리 |
| Operator Console | 지도/경로/ETA/차량상태/센서헬스/카메라뷰/미션 단계/에러 코드 표시, E-stop, teleop, 복구/취소 절차 수행 |
| User Interface | IVR/TTS 예약 UI, 의사용 웹 UI, 보호자용 포털, 환자 탑승용 대화면 최소 UI |
| IoT Device | SpO2, 혈압계, 체온계 등 기초 활력징후 측정 기기. BLE/USB/Serial 기반 연동, 측정 시작/완료/실패 이벤트 송신 |

---

## 4.2 외부 시스템

| 시스템 | 역할 |
| --- | --- |
| 지도 API | 주소 정규화, 좌표 관리, MORAI 맵 구성 참조 데이터 제공 |
| 메시징 서비스 | 예약 확정/전날 재안내/재진 예약 안내 등 문자 발송 |
| 인증 서비스 | 의료진/운영자/보호자 웹 로그인, OTP/MFA 또는 동등한 인증 |
| 의료기기 | 활력징후 측정 데이터 생성. SDK 또는 직렬/BLE 인터페이스로 연결 |
| MORAI 시뮬레이터 | 실외 자율주행 시나리오 재현, 맵/차량/센서/교통 객체 시뮬레이션 |
| 약국/보건소 연계 채널 | MVP에서는 실제 전산 연동 대신 수동 업데이트 가능. 처방전 전송 상태(RX_SENT) 및 진행 상태 추적 대상으로 사용 |

---

# 5. 운영 정책

## 5.1 서비스 대상

| 항목 | 내용 |
| --- | --- |
| 대상 환자 | 도서·산간 의료취약지 내 사전 등록 환자, 비응급 외래형 상담이 가능한 환자, 재진 또는 경증·중등도 일반 진료 대상자 |
| 제외 환자 | 응급환자, 중증 외상, 의식저하, 심한 호흡곤란, 흉통 등 즉시 대면 응급 대응이 필요한 환자, 탑승 불가 환자, 본인확인/동의 불가 환자 |
| 서비스 범위 | 전화 예약, 차량 출동, 집 앞 도착, 탑승, 본인확인/동의, 기초 vital 측정, WebRTC 화상진료, 재진 예약, 처방 전송 상태 추적, 보호자 조회 |

---

## 5.2 운영 정책

| 항목 | 내용 |
| --- | --- |
| 출동 반경 | MVP 기본 **서비스 geofence 내 편도 5km 또는 20분 이내 경로**를 권장 기준으로 설정. 실제 기준은 맵/배터리/운영정책 파라미터화 |
| 날씨 제한 | MVP 기본 시나리오는 맑음/보통 가시거리 조건. 폭우/폭설/강풍/심한 안개/야간 저시정 상황은 제외 |
| 운영 시간 | 예약 가능 시간 09:00~17:00, 실제 진료 운영 09:00~18:00, 전날 재안내 발송 시간 16:00~18:00 |
| 응급 상황 처리 | 예약 IVR 또는 탑승/측정 중 red flag 증상 확인 시 일반 흐름을 중단하고 응급기관 또는 대면 진료로 전환. 미션은 CANCELLED_EMERGENCY로 종료하고 보호자/기관 알림 |

---

## 5.3 본인 확인 정책

| 단계 | 조건 | 인증 방법 | 실패 시 처리 |
| --- | --- | --- | --- |
| 사전 등록 | 환자/보호자/주소/연락처가 시스템에 사전 등록되어 있어야 함 | 기관 담당자가 오프라인 또는 사전 절차로 신분 확인 후 등록, 전화번호 바인딩, 주소 좌표화 | 예약 생성 불가. 기관 재등록 요청 |
| 재진 방문 | 기존 진료 이력이 있고 이번 증상이 기존 진료 흐름과 동일 | IVR에서 등록 전화번호 + 생년월일 확인, 차량 탑승 후 신분증 육안 확인 또는 운영자 확인 | VERIFY_HOLD 상태 전환, 운영자 수동 확인 후 재개 또는 취소 |
| 초진 예외 | 재진 환자라도 증상이 기존과 다르거나 신규 증상 | 초진 흐름으로 강제 전환, 신분증 확인 + 추가 동의 + 의사 확인 | 대면 진료 권고 또는 재예약 처리 |
| 신분증 미소지 | 사전 등록 환자이나 신분증을 지참하지 못한 경우 | 보호자 전화 확인 + 등록 정보 질의응답 + 운영자 영상 확인. 저위험 재진에 한해 예외 허용 가능 | 원칙적으로 재예약 또는 대면 전환. 예외 진행 시 로그에 사유 명시 |
| 고위험 상황 | 흉통, 호흡곤란, 의식 저하, 비정상 vital 등 안전 우려 | 최소 신원 파악 후 의료진/운영자가 즉시 응급 전환 여부 결정 | 일반 진료 플로우 중단, 응급 대응 프로토콜 수행 |
| 의심 상황 | 타인 대리 탑승, 강요 정황, 정보 불일치, 음주/폭력 등 | 운영자 수동 검토 + 의료진 판단 + 필요 시 보호자 추가 확인 | 진료 시작 차단, 미션 종료, 기관 보고 |

---

## 5.4 개인정보 정책

| 항목 | 정책 |
| --- | --- |
| 영상 저장 | 기본 정책은 **화상진료 전체 저장 안 함**. 단, 안전 이벤트(E-stop/사고 우려) 발생 시 짧은 incident clip 또는 메타데이터만 별도 보관 가능 |
| 음성 저장 | IVR 예약/동의 관련 최소 음성 기록 또는 이벤트 로그는 저장 가능. 화상진료 음성 전체 저장은 기본 제외 |
| 로그 보관 | 예약, 미션, 본인확인, 동의, E-stop, teleop, vital, 진료 시작/종료, 처방 상태 변경, 접근 로그를 감사 로그로 보관 |
| 개인정보 처리 | 최소수집 원칙 적용. 식별정보와 진료/운영 로그를 논리적으로 분리 저장하고, 화면 표시는 역할 기반 최소 공개 |
| 데이터 삭제 정책 | MVP 데모 데이터는 프로젝트/기관 정책에 따라 최소 필요 기간 보관 후 파기. 백업 포함 삭제 절차와 삭제 로그를 남겨야 함 |

---

# 6. 기능 요구사항

| ID | 영역 | 기능명 | 설명 | 우선순위 | 검증 |
| --- | --- | --- | --- | --- | --- |
| FR-RES-001 | 예약 | IVR 메인 메뉴 | 전화 진입 시 “진료 예약”, “예약 확인 및 취소”, “다시 듣기(0번)” 메뉴를 제공해야 함
  •  | P0 | IVR 시나리오 테스트 |
| FR-RES-002 | 예약 | 진료과 선택 | 내과/외과/치과/안과/가정의학과를 DTMF 입력으로 선택 가능해야 함 | P0 | 단위/통합 테스트 |
| FR-RES-003 | 예약 | 증상 기반 단순 분류 | 병명/증상 기반 단순 메뉴 분류를 제공하고 내부적으로 진료과로 매핑해야 함 | P1 | 메뉴 분기 테스트 |
| FR-RES-004 | 예약 | 초진/재진 분기 | 초진은 가장 빠른 슬롯 우선, 재진은 기존 의사 우선 배정, 단 증상 변경 시 초진 흐름으로 전환해야 함 | P0 | 규칙 엔진 테스트 |
| FR-RES-005 | 예약 | 슬롯 운영 | 예약 슬롯은 10~20분 단위로 운영되며 동일 지역/동일 시간 중복 배정을 방지해야 함 | P0 | DB 제약/통합 테스트 |
| FR-RES-006 | 예약 | 예약 확인/취소 | 환자가 IVR로 예약 정보를 확인하고 취소할 수 있어야 함 | P1 | E2E 테스트 |
| FR-NTF-001 | 알림 | 예약 즉시 문자 | 예약 확정 직후 예약일시/의사/진료과/병원/유의사항/문의번호를 문자 발송해야 함 | P0 | 메시지 로그 검증 |
| FR-NTF-002 | 알림 | 전날 재안내 문자 | 예약 전날 오후 예약일/시간/변경 연락/신분증 지참 안내 문자를 자동 발송해야 함 | P0 | 스케줄러 테스트 |
| FR-NTF-003 | 알림 | 재진 예약 문자 | 의사 UI에서 재진 예약 생성 시 즉시 문자를 발송해야 함 | P0 | UI/API 통합 테스트 |
| FR-MSN-001 | 오케스트레이션 | 예약→미션 자동 생성 | 확정 예약을 기준으로 환자 좌표, 의사 일정, 차량 가용성을 반영해 출동 미션을 생성해야 함 | P0 | 배치/이벤트 테스트 |
| FR-MSN-002 | 오케스트레이션 | 차량 배차 | 차량/운영 가능한 시간대를 고려하여 미션을 차량에 할당해야 함 | P0 | 스케줄링 테스트 |
| FR-MSN-003 | 오케스트레이션 | 미션 단계 관리 | 출동/이동중/도착/탑승/측정/진료/종료 단계를 서버 상태로 추적해야 함 | P0 | 상태 머신 테스트 |
| FR-AUTO-001 | 자율주행 | MORAI 실외 주행 | MORAI 상 유사 마을 맵에서 GPS 기반으로 환자 집 앞까지 주행해야 함 | P0 | 시뮬레이션 리플레이 |
| FR-AUTO-002 | 자율주행 | 장애물 안전 정지 | LiDAR/카메라를 통해 보행자/주차 차량/도로 차단을 감지하면 정지해야 함 | P0 | 장애물 주입 테스트 |
| FR-AUTO-003 | 자율주행 | 재시도/대기 | 장애물 또는 경로 문제 시 재시도 후 실패하면 안전 정지 상태로 전환하고 운영자 개입을 요청해야 함 | P0 | fault injection |
| FR-AUTO-004 | 자율주행 | 집 앞 도착 판정 | 목표 geofence, 속도 0, 방향/정지 조건을 만족하면 ARRIVED로 전환해야 함 | P0 | 위치 로그 검증 |
| FR-OPS-001 | 관제 | 지도/경로/ETA 표시 | 관제 화면에서 차량 위치/경로/목표지점/ETA를 확인할 수 있어야 함 | P0 | UI 점검 |
| FR-OPS-002 | 관제 | 차량 상태 표시 | 속도/배터리/네트워크/센서헬스/미션 단계가 실시간 표시되어야 함 | P0 | 실시간 스트림 테스트 |
| FR-OPS-003 | 관제 | 전방 카메라 스트림 | 운영자는 차량 전방 카메라 영상을 실시간으로 볼 수 있어야 함 | P0 | 영상 스트리밍 테스트 |
| FR-OPS-004 | 관제 | E-stop | 운영자는 언제든 원격 정지를 발령할 수 있어야 하며, 해제 전까지 정지 상태가 유지되어야 함 | P0 | 응답시간 테스트 |
| FR-OPS-005 | 관제 | Teleop takeover | 운영자는 필요 시 teleop로 수동 원격조작 후 자율주행으로 복귀할 수 있어야 함 | P0 | 시나리오 테스트 |
| FR-OPS-006 | 관제 | 장애 복구 | 에러 코드와 복구 액션(재시도/취소/수동개입/복귀)을 기록하고 수행할 수 있어야 함 | P0 | 복구 시나리오 테스트 |
| FR-ID-001 | 본인확인 | 탑승 본인확인 | 탑승 후 환자 신원 확인 결과를 시스템에 기록해야 함 | P0 | 운영자 UI 검증 |
| FR-CON-001 | 동의 | 비대면진료/개인정보 동의 | 진료 시작 전 동의 여부를 음성 또는 UI 기반으로 수집하고 타임스탬프와 함께 기록해야 함 | P0 | 감사 로그 테스트 |
| FR-VIT-001 | 측정 | vital 측정 제어 | SpO2, 혈압, 체온 측정을 시작/완료/실패 상태로 제어해야 함 | P0 | 디바이스 연동 테스트 |
| FR-VIT-002 | 측정 | vital 저장 및 반영 | 측정값을 서버에 저장하고 의사 UI에 반영해야 함 | P0 | E2E 테스트 |
| FR-VIT-003 | 측정 | 실패 시 대체 입력 | 센서 실패 시 재시도 후 운영자/의료진이 수동 더미 입력 또는 예외 플래그 입력을 할 수 있어야 함 | P1 | 실패 복구 테스트 |
| FR-RTC-001 | 진료 | WebRTC 연결 | 환자–의사 2자 WebRTC 세션을 생성하고 연결해야 함 | P0 | 연결 통합 테스트 |
| FR-RTC-002 | 진료 | 재시도/품질 저하 대응 | 연결 실패 시 재시도해야 하며, 저대역폭 모드 전환 이벤트를 UI에 표시해야 함 | P0 | 네트워크 저하 테스트 |
| FR-RTC-003 | 진료 | 보호자 동시 접속 | 보호자의 동시 접속은 선택 기능으로 설계하되 MVP에서는 비활성화 가능해야 함 | P2 | 옵션 검증 |
| FR-MED-001 | 의료 | 진료 종료 처리 | 의사는 진료 종료를 기록하고 다음 단계(재진 예약/처방 전송)로 진행할 수 있어야 함 | P0 | UI/API 테스트 |
| FR-MED-002 | 의료 | 재진 예약 등록 | 의사 UI에서 다음 예약을 등록할 수 있어야 하며 이미 점유된 지역 슬롯은 비활성화되어야 함 | P0 | 스케줄링 테스트 |
| FR-RX-001 | 처방 | 처방 전송 기록 | 처방전은 근처 약국 또는 보건소로 전송되며 최소한 RX_SENT 상태를 기록해야 함 | P0 | 상태 전이 테스트 |
| FR-RX-002 | 처방 | 조제 상태 추적 | PREPARING, READY, COMPLETED 등 처방 진행 상태를 수동 또는 연계 방식으로 업데이트할 수 있어야 함 | P1 | 포털 상태 검증 |
| FR-GRD-001 | 보호자 | 보호자 포털 조회 | 보호자는 예약 상태, 차량 ETA, 도착/진료중/완료 여부, vital 요약, 진료 내역 요약, 처방 상태를 조회할 수 있어야 함 | P0 | 포털 E2E 테스트 |
| FR-AUD-001 | 공통 | 감사 로그 | 예약, 출동, 본인확인, 동의, E-stop, teleop, vital, WebRTC, 처방 상태 변경, 접근 이벤트를 감사 로그에 남겨야 함 | P0 | 로그 무결성 점검 |
| FR-ALR-001 | 공통 | 장애 알림 | 미션 지연, 네트워크 저하, 센서 오류, 화상 연결 실패 시 운영자에게 경고를 발생시켜야 함 | P1 | 알림 테스트 |

---

# 7. 비기능 요구사항

| ID | 분류 | 요구사항 | 설명 | 우선순위 |
| --- | --- | --- | --- | --- |
| NFR-SAF-001 | 안전 | E-stop 우선순위 보장 | 원격 정지 명령 경로는 일반 제어보다 우선되어야 하며 latch 방식으로 유지되어야 함 | P0 |
| NFR-SAF-002 | 안전 | 장애물 발견 시 fail-safe | 전방 장애물/센서 이상 시 차량은 감속 또는 안전 정지 상태로 전환해야 함 | P0 |
| NFR-RT-001 | 실시간성 | 관제 상태 지연 | 차량 상태/미션 상태는 관제에 2초 이내 반영되어야 함 | P0 |
| NFR-RT-002 | 실시간성 | vital 반영 지연 | 측정 완료 후 10초 이내 의사 UI에 vital 값이 반영되어야 함 | P0 |
| NFR-RTC-001 | 미디어 | 화상 복구성 | WebRTC 실패 시 2회 이상 자동/수동 재시도를 지원하고 120초 이내 재연결을 목표로 해야 함 | P0 |
| NFR-SEC-001 | 보안 | 역할 기반 접근제어 | 환자/보호자/의료진/운영자/관리자 권한을 분리하고 최소권한 원칙을 적용해야 함 | P0 |
| NFR-SEC-002 | 보안 | 강인한 인증 | 운영자/의료진/관리자는 MFA 또는 동등 수준 인증을 사용해야 함 | P0 |
| NFR-DAT-001 | 데이터 | 전송 암호화 | 모든 외부 통신은 TLS 기반 암호화를 적용해야 함 | P0 |
| NFR-DAT-002 | 데이터 | 저장 데이터 보호 | 개인정보/의료 데이터는 암호화 또는 동등 수준 보호가 적용되어야 함 | P0 |
| NFR-OBS-001 | 관측성 | 통합 로그/메트릭 | 로봇, 백엔드, WebRTC, 관제, 알림 시스템의 로그와 핵심 메트릭을 중앙 수집해야 함 | P0 |
| NFR-REC-001 | 복구성 | 부분 재시작 허용 | 센서/화상/알림 등 일부 서브시스템 장애 시 전체 시스템 재기동 없이 복구 가능해야 함 | P0 |
| NFR-REC-002 | 복구성 | 이벤트 멱등성 | 미션 이벤트 및 상태 전이 API는 중복 호출 시 일관된 결과를 유지해야 함 | P0 |
| NFR-USA-001 | 사용성 | 환자 UI 단순성 | 환자 탑승 UI는 큰 글씨, 한 화면 한 행동, 최소 입력으로 구성되어야 함 | P0 |
| NFR-USA-002 | 사용성 | IVR 반복 청취 | 음성 안내는 0번으로 반복 청취가 가능해야 하며 고령 사용자 기준으로 충분히 느린 속도를 지원해야 함 | P0 |
| NFR-CMP-001 | 호환성 | 브라우저 호환 | 의사/보호자/운영자 웹 UI는 최신 Chrome 계열 브라우저 기준으로 동작해야 함 | P1 |
| NFR-SIM-001 | 재현성 | 데모 재현 가능성 | 동일 MORAI 맵/시나리오/설정값으로 반복 시연이 가능해야 함 | P0 |
| NFR-CAP-001 | 용량 | MVP 동시성 | 최소 1대 차량, 1명의 운영자, 1명의 의사, 1명의 환자, 5명의 보호자 조회를 지원해야 함 | P1 |
| NFR-AUD-001 | 감사 | 로그 무결성 | 모든 P0 이벤트에 대해 actor, timestamp, source, result를 포함한 감사 로그가 생성되어야 함 | P0 |

---

# 8. KPI 및 수락 기준

## 8.1 주행 KPI

| 항목 | 목표 | 측정 방법 |
| --- | --- | --- |
| 미션 성공률 | 사전 정의된 데모 시나리오 20회 중 **90% 이상** 성공 | `CLOSED_SUCCESS` 종료 비율을 서버 로그와 ROS 주행 로그로 계산 |
| 도착 오차 | 목표 정차 구역 중심 기준 **3m 이하** | MORAI 좌표와 목표 geofence 중심 간 오차 계산 |
| 원격 개입 시간 | 장애 알림 발생 후 **15초 이내** teleop 또는 조치 시작 | 관제 alert timestamp와 operator action timestamp 비교 |
| 정지 응답 시간 | E-stop 발령 후 **1초 이내** 정지 명령 래치 | operator command log와 vehicle control log 비교 |

---

## 8.2 진료 KPI

| 항목 | 목표 | 측정 방법 |
| --- | --- | --- |
| 도착 → 진료 시작 시간 | 차량 도착 후 **7분 이내** 진료 세션 시작 | ARRIVED 시각부터 CONSULTING 시작 시각까지 측정 |
| 본인 확인 성공률 | 사전 등록 환자 기준 **95% 이상** | VERIFYING 단계 성공률 산정 |
| vital 업로드 시간 | 측정 완료 후 **10초 이내** 서버 저장 및 의사 화면 반영 | device event, backend ingest, doctor UI event 로그 비교 |
| 화상 연결 성공률 | 2회 이내 재시도로 **95% 이상** 세션 연결 성공 | WebRTC 세션 성공/실패 로그 집계 |

---

## 8.3 운영 KPI

| 항목 | 목표 | 측정 방법 |
| --- | --- | --- |
| 상태 추적 누락률 | 필수 상태 이벤트 누락 **1% 이하** | 예약/미션/진료 상태 전이 로그 필수 이벤트 대조 |
| 로그 완전성 | P0 이벤트에 대해 **100%** 감사 로그 생성 | 요구사항 대비 로그 필드 충족률 점검 |
| 장애 복구율 | 주입된 복구 가능 장애의 **80% 이상**이 미션 취소 없이 복구 | 네트워크 저하/센서 실패/차단 시나리오 복구 성공 비율 집계 |

---

# 9. 인터페이스 명세

## 9.1 ROS2 인터페이스

### TF Frame

- `map -> odom -> base_link -> {gps_link, lidar_front_link, camera_front_link, cabin_link}`
- `map`: MORAI 전역 좌표계
- `odom`: 연속 주행 로컬 좌표계
- `base_link`: 차량 중심 기준 프레임
- `gps_link`: GPS 센서 장착 위치
- `lidar_front_link`: 전방 LiDAR 기준 프레임
- `camera_front_link`: 전방 카메라 기준 프레임
- `cabin_link`: 환자 탑승/측정 장치 기준 프레임

---

### Topic

| Topic | Type | 주기 | 설명 |
| --- | --- | --- | --- |
| `/gps/fix` | `sensor_msgs/msg/NavSatFix` | 5 Hz | 차량 전역 위치 |
| `/vehicle/odom` | `nav_msgs/msg/Odometry` | 20 Hz | 차량 속도/자세/로컬 이동 |
| `/lidar/points` | `sensor_msgs/msg/PointCloud2` | 10 Hz | 장애물 인지용 포인트클라우드 |
| `/camera/front/image/compressed` | `sensor_msgs/msg/CompressedImage` | 15 Hz | 관제용 전방 카메라 스트림 |
| `/mission/goal` | `geometry_msgs/msg/PoseStamped` | 이벤트 | 환자 집 앞 목표 위치 |
| `/mission/state` | `ruralcare_msgs/msg/MissionState` | 2 Hz | 미션 상태 및 단계 |
| `/health/diagnostics` | `diagnostic_msgs/msg/DiagnosticArray` | 1 Hz | 배터리/센서/네트워크 진단 |
| `/safety/estop` | `std_msgs/msg/Bool` | 이벤트 | 비상정지 상태 |
| `/teleop/cmd_vel` | `geometry_msgs/msg/Twist` | 20 Hz | teleop 제어 입력 |
| `/vitals/record` | `ruralcare_msgs/msg/VitalRecord` | 이벤트 | 측정 완료된 vital 패킷 |
| `/consultation/status` | `ruralcare_msgs/msg/ConsultationStatus` | 이벤트 | 화상진료 세션 상태 |
| `/alerts/code` | `std_msgs/msg/String` | 이벤트 | 장애/경고 코드 송신 |

---

### Service / Action

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `/mission/assign` | `ruralcare_srvs/srv/AssignMission` | 미션을 차량에 할당 |
| `/mission/cancel` | `ruralcare_srvs/srv/CancelMission` | 미션 취소 및 사유 기록 |
| `/nav/navigate_to_pose` | `nav2_msgs/action/NavigateToPose` | 목표 위치로 자율주행 |
| `/safety/trigger_estop` | `std_srvs/srv/SetBool` | E-stop 발령/해제 |
| `/teleop/enable` | `std_srvs/srv/SetBool` | teleop takeover 진입/해제 |
| `/arrival/confirm` | `std_srvs/srv/Trigger` | ARRIVED 상태 확정 |
| `/vitals/start_measurement` | `ruralcare_srvs/srv/StartVitals` | vital 측정 시작 |
| `/consultation/open_session` | `ruralcare_srvs/srv/OpenConsultation` | WebRTC 세션 생성 요청 |
| `/mission/recover` | `ruralcare_srvs/srv/RecoverMission` | 장애 후 재시도/복구 실행 |

---

## 9.2 Backend API

| Method | Path | 설명 | Request | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/ivr/bookings` | IVR 예약 생성 | `caller_number, patient_key, dept_code/symptom_code, preferred_window` | `booking_id, slot, provider_id, status` |
| GET | `/api/v1/bookings/{booking_id}` | 예약 조회 | path param | `booking detail, sms status, mission status` |
| POST | `/api/v1/bookings/{booking_id}/cancel` | 예약 취소 | `reason, actor` | `status, cancelled_at` |
| POST | `/api/v1/missions/generate` | 예약 기반 미션 생성 | `booking_id` | `mission_id, vehicle_id, planned_departure` |
| POST | `/api/v1/missions/{mission_id}/dispatch` | 미션 출동 시작 | `operator_id or scheduler trigger` | `mission status, dispatch_at` |
| POST | `/api/v1/missions/{mission_id}/events` | 미션 이벤트 수집 | `state, step, pose, eta, error_code` | `accepted=true` |
| POST | `/api/v1/verifications` | 본인확인 기록 | `mission_id, patient_id, method, result, evidence_ref` | `verification_id, status` |
| POST | `/api/v1/consents` | 동의 기록 | `mission_id, consent_type, channel, result` | `consent_id, status` |
| POST | `/api/v1/vitals` | vital 업로드 | `mission_id, spo2, bp_sys, bp_dia, temp, source` | `record_id, accepted_at` |
| POST | `/api/v1/consultations` | 진료 세션 생성 | `mission_id, provider_id, participants` | `session_id, signaling_info` |
| POST | `/api/v1/consultations/{session_id}/complete` | 진료 종료 | `summary, followup_needed` | `status, next_actions` |
| POST | `/api/v1/followups` | 재진 예약 등록 | `patient_id, provider_id, region_id, slot_id` | `booking_id, notification_status` |
| POST | `/api/v1/prescriptions/{rx_id}/send` | 처방 전송 | `destination_type, destination_id` | `rx_status=RX_SENT, sent_at` |
| PATCH | `/api/v1/prescriptions/{rx_id}/status` | 처방 상태 변경 | `status, actor, note` | `current_status, updated_at` |
| GET | `/api/v1/guardian/cases/{case_id}` | 보호자 조회용 케이스 상태 | path param | `booking, eta, mission, vitals_summary, consultation_summary, rx_status` |
| POST | `/api/v1/operator/missions/{mission_id}/estop` | 운영자 E-stop | `operator_id, reason` | `estop=true, event_id` |
| POST | `/api/v1/operator/missions/{mission_id}/teleop` | teleop takeover/release | `operator_id, action=START/STOP` | `teleop_state` |

---

## 9.3 실시간 채널

| Channel | Payload | 설명 |
| --- | --- | --- |
| `ws/operator/missions/{mission_id}` | `state, step, pose, eta_sec, error_code` | 관제용 미션 상태 스트림 |
| `ws/operator/vehicles/{vehicle_id}/health` | `speed, battery, network, sensor_health, estop` | 차량 헬스/진단 스트림 |
| `ws/guardian/cases/{case_id}` | `booking_status, eta, arrived, consulting, closed, rx_status` | 보호자 상태 조회 실시간 반영 |
| `ws/doctor/consultations/{session_id}/events` | `vital_update, connection_quality, retry_state` | 의사 화면 이벤트 |
| `webrtc/consultations/{session_id}` | `offer, answer, ice, quality_event` | 화상진료 미디어 세션 |

---

# 10. 데이터 모델

| Entity | 설명 |
| --- | --- |
| Patient | 환자 기본 정보. `patient_id, name, dob, phone, address, lat, lon, risk_flag, default_provider_id` 포함 |
| Guardian | 보호자 정보. `guardian_id, relation, phone, portal_auth_ref, notification_opt_in` 포함 |
| Provider | 의료진 정보. `provider_id, department, clinic, schedule, active` 포함 |
| Mission | 출동 단위 엔터티. `mission_id, booking_id, vehicle_id, patient_id, target_geo, planned_departure, eta, status, error_code` 포함 |
| MissionStep | 세부 단계 이력. `step_id, mission_id, step_type, started_at, ended_at, result, actor` 포함 |
| Verification | 본인확인 기록. `verification_id, mission_id, method, result, evidence_ref, verified_by, verified_at` 포함 |
| Consent | 동의 기록. `consent_id, mission_id, consent_type, channel, result, captured_at, actor` 포함 |
| VitalRecord | 활력징후 기록. `record_id, mission_id, spo2, bp_sys, bp_dia, temp, measured_at, source, validity_flag` 포함 |
| ConsultationSession | 화상진료 세션. `session_id, mission_id, provider_id, room_id, status, reconnect_count, started_at, ended_at, summary` 포함 |
| Alert | 장애/경고 이벤트. `alert_id, mission_id, severity, code, message, raised_at, resolved_at, owner` 포함 |
| AuditLog | 감사 로그. `log_id, actor, action, target_type, target_id, before, after, source, timestamp` 포함 |
| Prescription | 처방 상태 추적. `rx_id, mission_id, destination_type, destination_id, status, sent_at, updated_at` 포함 |
| Notification | 문자/알림 발송 이력. `notification_id, target, template_code, delivery_status, sent_at` 포함 |

---

# 11. 상태 머신

## 11.1 미션 상태

| 상태 | 설명 |
| --- | --- |
| CREATED | 예약 확정 후 미션 레코드가 생성된 상태. 아직 차량 승인/출동 전 |
| APPROVED | 환자/차량/슬롯/주소 조건 검증이 끝나 출동 가능 상태로 승인된 상태 |
| DISPATCHED | 차량이 해당 예약을 위해 배차되었고 출동 준비 또는 출발 시점에 진입한 상태 |
| ENROUTE | 차량이 환자 집 앞 목표 위치로 이동 중인 상태. 자율주행/teleop 포함 |
| ARRIVED | 목표 geofence에 진입하고 안전 정지한 상태. 탑승 가능 |
| VERIFYING | 탑승 후 본인확인, 동의, vital 측정이 진행되는 상태 |
| CONSULTING | WebRTC 화상진료가 진행 중이거나 재연결 허용 시간 내 복구 중인 상태 |
| CLOSED | 진료/재진 예약/처방 처리까지 종료되었거나, 취소/실패/응급전환으로 종료된 상태 |

---

## 11.2 에러 코드

| 코드 | 분류 | 설명 | 자동 처리 | 운영자 조치 |
| --- | --- | --- | --- | --- |
| NAV-001 | 주행 | GPS 신호 불안정/소실 | 속도 제한 또는 안전 정지 후 재시도 | teleop 전환 또는 미션 취소 |
| NAV-002 | 주행 | 경로 차단/장애물 지속 | 1회 재시도 후 대기 | 전방 뷰 확인 후 teleop 우회 |
| SEN-001 | 센서 | LiDAR heartbeat 손실 | 자율주행 중지 | 센서 상태 확인, 재기동, 수동개입 |
| SEN-002 | 센서 | 전방 카메라 손실 | 관제 영상 경고 및 안전 모드 | teleop 제한 또는 미션 보류 |
| NET-001 | 네트워크 | 차량 업링크 품질 저하 | 텔레메트리 유지, 비필수 스트림 축소 | 지속 여부 판단, 지연 안내 |
| RTC-001 | 화상 | WebRTC 초기 연결 실패 | TURN 재시도, 저대역폭 모드 전환 | 재접속 유도, 의사/운영자 확인 |
| RTC-002 | 화상 | 세션 중 품질 급락 | 해상도/비트레이트 하향 | 복구 실패 시 재연결 |
| VIT-001 | 측정 | 센서 응답 없음/타임아웃 | 재측정 1회 | 수동 입력 또는 측정 생략 처리 |
| ID-001 | 본인확인 | 등록정보 불일치 | VERIFY_HOLD 전환 | 보호자 확인 또는 취소 |
| CON-001 | 동의 | 동의 누락/미완료 | 재안내 및 재수집 | 수동 확인 또는 진료 중단 |
| BAT-001 | 차량 | 배터리 부족 | 신규 출동 차단/복귀 우선 | 미션 재배정 또는 취소 |
| OPS-001 | 운영 | E-stop 발동 | 정지 래치 유지 | 현황 확인 후 해제/복구 |
| RX-001 | 처방 | 전송 실패 | 재전송 큐 등록 | 수동 연락 및 상태 갱신 |

---

# 12. 보안 요구사항

| 항목 | 내용 |
| --- | --- |
| 인증 방식 | 운영자/의료진/관리자는 MFA 또는 동등 수준 인증 사용. 보호자는 OTP 링크 또는 로그인 기반 조회 허용 |
| 권한 관리 | RBAC 적용. 환자 직접 권한은 최소화하고, 보호자/의료진/운영자/관리자별 접근 범위 분리 |
| 데이터 암호화 | 외부 통신 TLS 적용, 저장 데이터는 암호화 또는 동등 수준 보호 적용 |
| 로그 보안 | 감사 로그는 append-only 성격으로 관리하고, 시간 동기화(NTP 등)와 접근 제어를 적용 |
| 데이터 보관 기간 | 감사/운영 로그는 기본 1년 보관 권장, incident clip은 90일 이내 별도 정책, 진료 데이터는 기관 정책 우선 적용 |

---

# 13. 테스트 계획

## 13.1 테스트 환경

| 구분 | 내용 |
| --- | --- |
| 시뮬레이션 환경 | MORAI 시뮬레이터, ROS2 기반 로봇 스택, Google Map 참조 유사 마을 맵, FastAPI/DB/WebSocket/WebRTC 서버, 의사/보호자/운영자 브라우저 |
| 네트워크 환경 | 정상망, 지연/패킷 손실/대역폭 저하 주입 환경, WebRTC TURN 강제 경유 환경 |
| 필드 테스트 환경 | 공공도로 제외. 필요 시 폐쇄공간 또는 실내 통합 환경에서 탑승/측정/UI/관제/복구 절차 검증 |

---

## 13.2 테스트 케이스

| TC ID | 관련 요구사항 | 테스트 절차 | 성공 기준 |
| --- | --- | --- | --- |
| TC-001 | FR-RES-001~005 | 환자가 IVR로 진료과 선택 후 예약 생성 | 예약 생성, 슬롯 배정, 예약 즉시 문자 발송 성공 |
| TC-002 | FR-RES-004, FR-MED-002 | 재진 환자가 동일 의사 우선 배정되는지 확인 | 기존 의사 우선 배정, 증상 변경 시 초진 흐름 전환 |
| TC-003 | FR-RES-006, FR-NTF-001~002 | 예약 확인/취소 및 전날 재안내 스케줄링 확인 | 취소 반영, 문자 발송 이력 생성 |
| TC-004 | FR-MSN-001~003 | 예약 확정 후 미션이 자동 생성되고 출동 준비 상태가 되는지 확인 | mission_id 생성, 배차 완료, 상태 전이 정상 |
| TC-005 | FR-AUTO-001~004 | MORAI에서 환자 집 앞까지 주행 후 도착 판정 | 목표 geofence 진입, 안전 정지, ARRIVED 전환 |
| TC-006 | FR-OPS-004~006 | 주행 중 장애물 발생 후 E-stop/teleop 복구 수행 | E-stop 응답, teleop 우회, ENROUTE 재개 |
| TC-007 | FR-ID-001, FR-CON-001 | 탑승 후 본인확인 및 동의 수집 | verification/consent 로그 생성 |
| TC-008 | FR-VIT-001~003 | vital 측정 성공 및 실패 시 재시도/수동 입력 검증 | 의사 UI 반영 또는 예외 플래그 반영 |
| TC-009 | FR-RTC-001~002 | 화상 연결 후 네트워크 저하 주입 | 재시도 또는 저대역폭 전환 후 진료 지속 |
| TC-010 | FR-MED-001~002 | 진료 종료 후 재진 예약 생성 | 중복 슬롯 비활성화, 예약 문자 발송 |
| TC-011 | FR-RX-001~002 | 처방 전송 및 상태 업데이트 | RX_SENT 기록, PREPARING/READY 상태 변경 확인 |
| TC-012 | FR-GRD-001 | 보호자 포털에서 전체 흐름 조회 | ETA, 도착, 진료, 요약, 처방 상태 표시 |
| TC-013 | FR-AUD-001, NFR-AUD-001 | 핵심 이벤트 감사 로그 검사 | 모든 P0 이벤트에 actor/timestamp/source 존재 |
| TC-014 | NFR-SIM-001 | 동일 시나리오 반복 재현성 점검 | 3회 이상 반복 시 동일 플로우 재현 가능 |

---

# 14. 산출물

| 산출물 | 설명 |
| --- | --- |
| 소스코드 | ROS2 패키지, 백엔드 API, 관제 UI, 의사 UI, 보호자 포털, IVR/TTS 플로우, WebRTC signaling 포함 |
| 실행 가이드 | 개발/실행 환경 구성, 시뮬레이터 기동, 서비스 기동 순서, 장애 복구 절차, 데모 실행 절차 문서 |
| 데모 시나리오 | 예약 전화 → 출동 → 주행 → 장애/복구 → 도착 → 측정 → 화상진료 → 재진 예약 → 처방 상태 추적 → 보호자 확인 흐름 |
| KPI 보고서 | 주행/진료/운영 KPI 측정 결과와 로그 기반 분석 |
| 테스트 결과 | 테스트 케이스별 Pass/Fail, 이슈 목록, 수정 내역, 재검증 결과 |

---

# 15. 리스크 및 이슈

| 이슈 | 영향 | 대응 | 담당 | 상태 |
| --- | --- | --- | --- | --- |
| MORAI 맵 현실성 부족 | 주행 설득력 저하 | Google Map 참조 기반 정차구역/도로폭/장애물 배치 보정 | 자율주행 리드 | Open |
| 네트워크 급락 시 화상 불안정 | 진료 시연 실패 가능 | TURN 준비, 저대역폭 모드, 재시도 UX, 사전 네트워크 리허설 | 백엔드/WebRTC 리드 | Mitigating |
| teleop 지연/조작성 문제 | 운영 복구 장면 실패 가능 | 제어 주기 고정, 네트워크 조건 제한, 단순 우회 조작 시나리오 설계 | 운영 리드 | Open |
| 본인확인/동의 UX 복잡성 | 탑승 후 흐름 지연 | 사전 등록 강화, 환자 대화 최소화, 운영자 보조 절차 정의 | 서비스/운영 | Mitigating |
| vital 장비 연동 불안정 | 의사 UI 데이터 미반영 | 디바이스 에뮬레이터와 수동 더미 입력 fallback 준비 | IoT/백엔드 | Open |
| 범위 과다 | 3/30 이전 미완성 위험 | P0 우선 개발, 보호자 3자 화상 등 P2 후순위 고정 | PM/PO | Open |
| 약국/보건소 실제 연동 불가 | 처방 파이프라인 단절 위험 | MVP는 RX_SENT + 수동 상태 업데이트로 정의 | 서비스 기획/백엔드 | Accepted |
| 법적/기관 협의 지연 | 실제 확장 검토 지연 | 본 문서는 MVP 기술 검증용으로 한정, 제도 검토는 별도 트랙 운영 | PO/기관 협의 | Open |

---

## 부록: 3/30 기준 데모 수락 시나리오 요약

**수락 가능한 최소 데모(P0 완주 기준)**

1. 환자가 IVR로 예약한다.
2. 예약 확정 문자와 전날 재안내 문자가 발송된다.
3. 예약으로부터 미션이 자동 생성되고 차량이 MORAI에서 출동한다.
4. 관제에서 위치/경로/ETA/전방 뷰/차량 상태를 본다.
5. 중간에 장애를 1회 주입하고, 운영자가 E-stop 또는 teleop로 복구한다.
6. 차량이 집 앞에 도착한다.
7. 환자 탑승 후 본인확인과 동의를 완료한다.
8. SpO2/혈압/체온 중 최소 2개 이상이 정상적으로 업로드되고 의사 UI가 바뀐다.
9. WebRTC 진료가 연결되고, 네트워크 저하 시 재시도 또는 저대역폭 이벤트가 표시된다.
10. 의사가 재진 예약을 생성한다.
11. 처방전은 RX_SENT 상태로 기록되고, 보호자 포털에서 최종 상태를 확인한다.
12. 전체 흐름의 감사 로그와 KPI 로그가 남는다.