# Branch Strategy

![branch](../assets/branch.jpg)

## 1. Branch Model

- master : Production 배포 브랜치 (항상 배포 가능한 상태 유지)
-  dev : Development 통합 브랜치 (기능 개발 머지 대상)
- `<JIRA-KEY>/<type>/<scope>-<short-desc>` : 작업 브랜치(기능/버그/리팩터링 등)
- 기본 흐름: 작업 브랜치 → dev → master

## 2. Work Branch Naming Convention

Rule

``` text
<JIRA_ISSUE_KEY>/<type>/<scope>-<short-desc>
```
- JIRA ISSUE KEY 반드시 포함 (예: S14P21A603-2)
- 브랜치 1개 = Jira 이슈 1개 원칙

Type 목록 (Allowed)
- `feat` : 기능 개발
- `fix` : 버그 수정
- `ref` : 리팩터링
- `docs` : 문서 수정
- `test` : 테스트 코드
- `chore` : 빌드/설정/의존성 변경
- `perf` : 성능 개선

Scope 목록 (Monorepo 고정)
- `fe` : Frontend
- `be` : Backend API
- `ws` : ROS2 Workspace
- `ai` : AI 서비스/모델/파이프라인

short-desc 규칙
- kebab-case 사용 (예: be-auth, fe-user-login, ai-model-loader)
- 너무 길게 쓰지 않음 (의미 전달 최소 단위)

## 3. Workflow
1. `dev`에서 작업 브랜치 생성
2. 기능 개발 완료 후 PR 생성 (Jira 이슈 링크 + 테스트 결과 필수)
3. Code Review 통과 후 `dev`로 Merge
4. 릴리즈 시점에 `dev` → `master` PR 생성 및 배포

예:
- `feat/ros2-cartographer`
- `ref/ai-module`

---