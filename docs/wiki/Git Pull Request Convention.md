# Git Pull Request Convention

## 1. PR Model

* PR은 반드시 **Jira 이슈 단위**로 생성한다.
* 기본 Target 브랜치는 `dev`이다.
* 릴리즈는 `dev → master` PR로 진행한다.
* PR 제목/본문만으로 “무엇을, 왜, 어떻게, 어떻게 검증했는지”가 파악 가능해야 한다.

---

## 2. PR Title Convention

### Rule

* PR 제목에는 반드시 **Jira 이슈 키**를 포함한다.
* 브랜치에서 이미 Jira 키가 있어도, PR 제목에도 반드시 포함한다. (추적 안정화 목적)

### Format (Recommended)

```text
[JIRA-KEY] <type>(<scope>): <summary>
```

### Examples

* `[S14P21A603-2] feat(be): add auth login API`
* `[S14P21A603-19] fix(fe): prevent crash on empty state`
* `[S14P21A603-88] chore(ai): update model loader config`

> `summary`는 커밋 subject처럼 동사 원형 + 50자 이내를 권장한다.

---

## 3. PR Template (Required)

### Summary

* 변경 내용 요약 (무엇을 바꿨는지)

### Motivation

* 왜 필요한 변경인지 (이슈/버그/요구사항 배경)

### Implementation

* 핵심 설계/로직/구현 포인트
* 트레이드오프가 있으면 명시

### Test

* 테스트 방법 및 결과 (필수)

  * Unit / Integration / E2E / Manual 중 수행한 항목 체크
  * 실행 명령 또는 스크린샷/로그 첨부 권장

### Impact

* 영향 범위(scope) 명시 (필수)

  * 예: `fe`, `be`
* 배포/환경변수/마이그레이션 필요 여부 명시

### Jira

* 관련 Jira 링크 (필수)

---

## 4. PR Rules

### PR 생성 조건

* 빌드 성공
* 로컬 테스트 완료
* 충돌 해결 완료

### Merge Policy

* 최소 1인 이상 승인 필요
* 큰 변경(300 라인 이상) 또는 설계 변경은 **설계 설명** 필수
* 성능/지연(latency) 영향 변경 시 벤치마크/근거 첨부

### Squash / Merge 방식 (권장)

* 기본은 **Squash merge 권장**
* Squash merge 사용 시, 최종 커밋 메시지가 PR 제목/설명에서 만들어지므로 **PR 제목 규칙 준수는 필수**이다.

---


