# 팀 개발 규칙

> Status: Canonical
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Purpose: 브랜치, 커밋, 이슈, Pull Request 규칙을 한 문서에서 확인하기 위한 기준 문서입니다.

## Branch Strategy

![branch](../assets/branch.jpg)

### Branch Model

- `master`: Production 배포 브랜치. 항상 배포 가능한 상태를 유지한다.
- `dev`: Development 통합 브랜치. 기능 개발 머지 대상이다.
- 작업 브랜치: 기능, 버그, 리팩터링 등 이슈 단위 작업 브랜치.
- 기본 흐름: 작업 브랜치 -> `dev` -> `master`.

### Work Branch Naming

```text
<JIRA-ISSUE-KEY>/<type>/<scope>-<short-desc>
```

- Jira issue key를 포함한다. 예: `S14P21A603-2`.
- 브랜치 1개는 이슈 1개에 대응한다.
- `short-desc`는 kebab-case를 사용한다. 예: `be-auth`, `fe-user-login`, `ai-model-loader`.

Allowed type:

| Type | 의미 |
| --- | --- |
| `feat` | 기능 개발 |
| `fix` | 버그 수정 |
| `ref` | 리팩터링 |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 |
| `chore` | 빌드, 설정, 의존성 변경 |
| `perf` | 성능 개선 |

Allowed scope:

| Scope | 의미 |
| --- | --- |
| `fe` | Frontend |
| `be` | Backend API |
| `ws` | ROS2 Workspace |
| `ai` | AI 서비스, 모델, 파이프라인 |

### Branch Workflow

1. `dev`에서 작업 브랜치를 생성한다.
2. 기능 개발 완료 후 PR을 생성한다.
3. PR에는 관련 이슈 링크와 테스트 결과를 포함한다.
4. Code Review 통과 후 `dev`로 merge한다.
5. 릴리즈 시점에 `dev` -> `master` PR을 생성하고 배포한다.

## Commit Message Convention

Conventional Commits 기반으로 작성한다. Trunk-based 운영에서는 squash merge로 최종 커밋이 정리되므로 PR 제목 규칙을 특히 엄격하게 지킨다.

### Format

```text
<type>(<scope>): <subject> (<JIRA-KEY>)
```

Examples:

- `feat(be): add login endpoint (S14P21A603-2)`
- `fix(fe): handle empty payload (S14P21A603-19)`
- `ref(be): split auth service layer (S14P21A603-21)`
- `chore(ai): bump deps for inference (S14P21A603-88)`

Subject rules:

- 50자 이내를 권장한다.
- 동사 원형을 사용한다. 예: `add`, `fix`, `improve`.
- 마침표를 쓰지 않는다.
- 로그 추적과 자동화를 위해 영어를 사용한다.

Minimum rules:

- 커밋 메시지의 Jira 키 포함은 권장이다.
- PR 제목의 Jira 키 포함은 필수다.
- 최종적으로 커밋 또는 PR 제목 중 하나에서는 이슈 추적이 가능해야 한다.

## Issue Convention

### Issue Model

- 모든 작업은 이슈로 시작한다.
- 브랜치는 해당 이슈에서 파생한다.
- 이슈는 완결 가능한 작업 단위로 쪼갠다.
- 큰 작업은 Epic/Story로 나누고 Task로 분해한다.

### Issue Title

```text
[<scope>] <short title>
```

Examples:

- `[be] 로그인 API 구현`
- `[fe] 사용자 로그인 UI 구현`
- `[ai] 모델 로더 설정 정리`
- `[ws] 이벤트 처리 워커 재시작 정책 추가`

### Issue Description Template

```markdown
### Goal
- 이 이슈에서 달성해야 하는 목표

### Scope
- 영향 범위: fe / be / ws / ai

### Requirements
- [ ] API 스펙 확정 및 문서화
- [ ] 정상/예외 케이스 처리
- [ ] 테스트 추가
- [ ] 로깅/메트릭 필요 여부 확인
- [ ] 배포 영향 명시

### Out of Scope
- 이번 이슈에서 하지 않을 것

### Notes
- 레퍼런스 링크, 스크린샷, 결정 사항
```

Recommended labels:

- `type:feat`, `type:fix`, `type:ref`, `type:docs`, `type:test`, `type:chore`, `type:perf`
- `scope:fe`, `scope:be`, `scope:ws`, `scope:ai`
- `priority:P0/P1/P2`
- `risk:high/medium/low`

## Pull Request Convention

### PR Model

- PR은 이슈 단위로 생성한다.
- 기본 target 브랜치는 `dev`이다.
- 릴리즈는 `dev` -> `master` PR로 진행한다.
- PR 제목과 본문만으로 무엇을, 왜, 어떻게, 어떻게 검증했는지 파악 가능해야 한다.

### PR Title

```text
[JIRA-KEY] <type>(<scope>): <summary>
```

Examples:

- `[S14P21A603-2] feat(be): add auth login API`
- `[S14P21A603-19] fix(fe): prevent crash on empty state`
- `[S14P21A603-88] chore(ai): update model loader config`

### PR Body Template

```markdown
### Summary
- 변경 내용 요약

### Motivation
- 필요한 변경인 이유

### Implementation
- 핵심 설계와 구현 포인트
- 트레이드오프

### Test
- 테스트 방법과 결과

### Impact
- 영향 범위
- 배포, 환경변수, 마이그레이션 필요 여부

### Jira
- 관련 Jira 링크
```

### Merge Policy

- PR 생성 전 빌드 성공, 로컬 테스트, 충돌 해결을 완료한다.
- 최소 1인 이상 승인 후 merge한다.
- 300 line 이상 변경 또는 설계 변경은 설계 설명을 포함한다.
- 성능/지연 영향이 있는 변경은 벤치마크나 근거를 첨부한다.
- 기본 merge 방식은 squash merge를 권장한다.
