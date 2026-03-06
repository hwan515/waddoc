# Git Issue Convention

## 1. Issue Model

* 모든 작업은 Issue(Jira)로 시작한다.
* 브랜치는 반드시 해당 Jira 이슈에서 파생된다.
* 이슈 하나가 “완결 가능한 작업 단위”가 되도록 쪼갠다.
  (너무 큰 이슈는 Epic/Story로 나누고 Task로 분해)

---

## 2. Issue Title Convention

### Format (Recommended)

```text
[<scope>] <short title>
```

### Examples

* `[be] 로그인 API 구현`
* `[fe] 사용자 로그인 UI 구현`
* `[ai] 모델 로더 설정 정리`
* `[ws] 이벤트 처리 워커 재시작 정책 추가`

> Jira 키는 Jira가 자동 부여하므로 제목에 키를 넣지 않는다.

---

## 3. Issue Description Template (Required)

### Goal

* 이 이슈에서 달성해야 하는 목표 1 ~ 3줄

### Scope

* 영향 범위 명시: `fe / be / ws / ai`

### Requirements (Acceptance Criteria)

* 완료 조건을 체크박스로 명확히 작성

  * [ ] API 스펙 확정 및 문서화
  * [ ] 정상/예외 케이스 처리
  * [ ] 테스트 추가 (unit/integration/e2e)
  * [ ] 로깅/메트릭(필요 시)
  * [ ] 배포 영향(ENV/DB) 명시

### Out of Scope

* 이번 이슈에서 하지 않을 것 명시 (스코프 방어)

### Notes

* 레퍼런스 링크/스크린샷/결정 사항 기록

---

## 4. Labels / Components (Recommended)

### Labels

* `type:feat`, `type:fix`, `type:ref`, `type:docs`, `type:test`, `type:chore`, `type:perf`
* `scope:fe`, `scope:be`, `scope:ws`, `scope:ai`
* `priority:P0/P1/P2` (선택)
* `risk:high/medium/low` (선택)

---

## 5. Issue → Branch → PR Mapping (Required)

* Issue 생성 (Jira 키 발급)
* 브랜치 생성:

  * `<JIRA-KEY>/<type>/<scope>-<short-desc>`
* PR 생성:

  * 제목에 Jira 키 포함: `[JIRA-KEY] <type>(<scope>): <summary>`
* PR 본문에 Jira 링크 포함

---

## 6. Done Definition (Recommended)

* 요구사항(AC) 체크 완료
* 테스트 수행 및 결과 기록
* 리뷰 승인 완료
* `dev`에 merge 완료
* 릴리즈 항목이면 `dev → master` 반영 완료

---