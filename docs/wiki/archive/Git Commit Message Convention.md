# Git Commit Message Convention

> Status: Archived
>
> Owner: Team
>
> Last updated: 2026-05-27
>
> Current canonical document: [../Conventions.md](../Conventions.md)
>
> Note: 이 문서는 팀 개발 규칙 통합 전 원본 기록입니다.

## 1. Commit Model

* Conventional Commits 기반으로 작성한다.
* 커밋 메시지에는 **Jira 이슈 키 포함을 권장**한다.
* 최소 요구사항: **PR 제목에는 반드시 Jira 이슈 키 포함** (필수)

> Trunk-based 운영에서는 squash merge로 최종 커밋이 정리되므로, PR 제목이 사실상 “최종 커밋 메시지”가 된다.

---

## 2. Commit Format

### Format (Recommended)

```text
<type>(<scope>): <subject> (<JIRA-KEY>)
```

### Examples

* `feat(be): add login endpoint (S14P21A603-2)`
* `fix(fe): handle empty payload (S14P21A603-19)`
* `ref(be): split auth service layer (S14P21A603-21)`
* `chore(ai): bump deps for inference (S14P21A603-88)`

---

## 3. Type 목록 (Allowed)

* `feat`  : 기능 추가
* `fix`   : 버그 수정
* `ref`   : 리팩터링
* `docs`  : 문서 수정
* `test`  : 테스트 코드
* `chore` : 빌드/설정 변경
* `perf`  : 성능 개선

---

## 4. Scope 목록 (Allowed)

* `fe` : Frontend
* `be` : Backend API
* `ws` : ROS2 Workspace
* `ai` : AI 서비스/모델/파이프라인

> scope는 반드시 위 목록 중 하나만 사용한다.<br>
> 변경이 여러 scope에 걸치면 대표 scope 1개만 쓰고, PR 본문 `Impact`에 추가 scope를 명시한다.

---

## 5. Subject Rules

* subject는 50자 이내 권장
* 동사 원형 사용 (add, fix, improve)
* 마침표 사용 금지
* 한글 금지 (로그 추적 및 자동화 목적)

---

## 6. Commit Rules (Minimum)

* 커밋에 Jira 키 포함은 “권장”
* PR 제목에 Jira 키 포함은 “필수”
* 최종적으로 Jira에서 추적 가능해야 하므로 아래 중 하나는 반드시 만족

  * 커밋 메시지에 Jira 키 포함
  * PR 제목에 Jira 키 포함 (필수)

---
