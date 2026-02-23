# Contributing Guide

본 프로젝트는 체계적인 브랜치 전략과 코드 품질 관리를 기반으로 협업합니다.
아래 규칙을 반드시 준수하십시오.

---

# 1. Branch Strategy

## 1.1 Branch Model

- master    : 배포 가능한 안정 버전
- develop(dev)   : 통합 개발 브랜치
- feature(feat)/* : 기능 개발
- refactor(ref)/*: 리팩토링

## 1.2 Workflow

1. develop에서 feature 브랜치 생성
2. 기능 개발 완료 후 PR 생성 (문서화를 위한 이미지 첨부 필수)
3. Code Review 통과 후 develop에 담장자 지정 후 Merge Requests
4. 릴리즈 시 develop → master merge

예:

feat/ros2-cartographer
refactor/ai-module

---

# 2. Commit Convention

Conventional Commits 기반으로 작성

## 형식

<type>(scope): <subject>

## Type 목록

- feat     : 기능 추가
- fix      : 버그 수정
- refactor : 리팩토링
- docs     : 문서 수정
- test     : 테스트 코드
- chore    : 빌드/설정 변경
- perf     : 성능 개선

## 예시

feat: add bezier trajectory generator
fix: resolve PID overshoot issue
refactor: split state machine module

## 규칙

- subject는 50자 이내
- 동사 원형 사용 (add, fix, improve)
- 마침표 사용 금지
- 한글 금지 (로그 추적 및 자동화 목적)

---

# 3. Pull Request Rule

## 3.1 PR 생성 조건

- 빌드 성공
- 로컬 테스트 완료
- 충돌 해결 완료

## 3.2 PR Template

### Summary
변경 내용 요약

### Motivation
왜 필요한 변경인가?

### Implementation
핵심 설계/로직 설명

### Test
테스트 방법 및 결과

### Impact
영향 범위

---

# 4. Code Review Policy

- 최소 1인 이상 승인 필요
- 300라인 이상 변경 시 설계 설명 필수
- 성능/지연(latency) 영향 변경 시 벤치마크 첨부

---

# 5. Documentation

- 아키텍처 변경 시 Wiki 업데이트 필수
- API 변경 시 issue 발행

---

# 6. Issue-based Branch Workflow

- 모든 작업은 Issue로 시작합니다.
- 브랜치는 Issue 페이지의 `Create branch` 버튼을 통해 생성합니다.
- 브랜치명은 자동 생성된 `<iid>-<slug>` 형식을 기본으로 사용합니다.

권장 Prefix:

- feature/<iid>-<slug>
- fix/<iid>-<slug>
- refactor/<iid>-<slug>
- 브랜치명에 Issue 번호 포함

예:
feature/123-add-navigation

---

이 문서는 프로젝트 품질 유지와 협업 효율을 위한 필수 규칙입니다.