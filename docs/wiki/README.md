# Waddoc Wiki

> Status: Canonical
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Purpose: 팀 내부에서 최신 요구사항, 설계, 운영 절차, 트러블슈팅 문서를 빠르게 찾기 위한 docs/wiki 메인 목차입니다.

## 먼저 볼 문서

| 문서 | 목적 |
| --- | --- |
| [프로젝트 소개](./Projectinfo.md) | 서비스 목표, 배경, 핵심 기능, 기대 효과 |
| [MVP 요구사항](./MVP_Requirements_v2.md) | 최신 MVP 범위, 사용자 시나리오, 기능/비기능 요구사항 |
| [시스템 아키텍처](./Architecture.md) | 서비스 경계, 데이터 흐름, 인증, 실시간 통신, 보안 구조 |

## 설계 기준 문서

| 문서 | 목적 |
| --- | --- |
| [API 명세](./API_Specification.md) | 인증, 예약, 미션, 진료 세션, 보호자/관리자/로봇 API |
| [ERD](./ERD.md) | 도메인 엔티티, 상태 enum, 핵심 관계, DB 제약 조건 |
| [트래픽/스케일링 설계](./Traffic_Scalability_Design.md) | Outbox/Kafka, Redis Pub/Sub, SSE, LiveKit, 병목과 보완 우선순위 |

## 운영 문서

| 문서 | 목적 |
| --- | --- |
| [인프라 설정](./Infrastructure_Setup.md) | 로컬/운영 실행, Jenkins, monitoring, MQTT, 포트 매핑 |
| [트러블슈팅](./Troubleshooting.md) | 운영 중 확인한 장애, 원인, 조치, 검증 방법 |

## 팀 규칙과 FAQ

| 문서 | 목적 |
| --- | --- |
| [팀 개발 규칙](./Conventions.md) | 브랜치, 커밋, 이슈, PR 작성 규칙 |
| [FAQ](./FAQ.md) | 프로젝트 발표/리뷰에서 자주 나오는 질문과 답변 |

## Archive

`archive/`에는 현재 기준 문서로 쓰지 않는 과거 플랜, 분리 전 문서, 세부 초안이 보존됩니다. 새 작업을 시작할 때는 archive 문서를 기준으로 삼지 말고 위 Canonical 문서를 먼저 확인합니다.

| Archived 문서 | 현재 기준 |
| --- | --- |
| [ROS2-Unity MVP 요구사항](./archive/MVP_Requirements_ros.md) | [시스템 아키텍처](./Architecture.md), [인프라 설정](./Infrastructure_Setup.md) |
| [동의 기능 P1 확장](./archive/P1_Consent_Extension.md) | [MVP 요구사항](./MVP_Requirements_v2.md), [API 명세](./API_Specification.md), [ERD](./ERD.md) |
| [세부 기능 리스트](./archive/Detailed.md) | [프로젝트 소개](./Projectinfo.md), [MVP 요구사항](./MVP_Requirements_v2.md) |
| [Unity Camera MediaMTX 계획](./archive/Unity_Cam_EC2_MediaMTX_Plan.md) | [인프라 설정](./Infrastructure_Setup.md), [트러블슈팅](./Troubleshooting.md) |
| [Branch Convention](./archive/Git%20Branch%20Convention.md) | [팀 개발 규칙](./Conventions.md) |
| [Commit Convention](./archive/Git%20Commit%20Message%20Convention.md) | [팀 개발 규칙](./Conventions.md) |
| [Issue Convention](./archive/Git%20Issue%20Convention.md) | [팀 개발 규칙](./Conventions.md) |
| [Pull Request Convention](./archive/Git%20Pull%20Request%20Convention.md) | [팀 개발 규칙](./Conventions.md) |
| [예상 질문 리스트](./archive/Anticipated_Questions.md) | [FAQ](./FAQ.md) |
