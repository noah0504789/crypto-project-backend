# Harness Status

## 구성과 책임

| 영역 | 현재 책임 | 정본/진입점 | 확인 방법 |
|---|---|---|---|
| 지시 문서 | 작업 진입, 행동 규칙, 모듈 고유 제약, 반복 절차와 분리 실행 | `CLAUDE.md`, `.claude/rules/`, module `CLAUDE.md`, `.claude/skills/`, `.claude/agents/` | 문서 링크 검사 |
| 아키텍처·품질 제약 | 코드 스타일, 타입, Gradle 모듈·package 의존 방향 강제 | `architecture.md`, `ARCHITECTURE_CONSTRAINTS.md`, `common-arch-test` | `qualityCheck`, pre-commit hook |
| 피드백 루프 | 변경 유형별 최소 검증 선택과 완료 조건 | `testing.md`, `FEEDBACK_LOOP.md`, `verify-change` | 모듈 test, `<service>Ci`, affected build/docker 계산 |
| 지식 저장소 | 구조·흐름·결정·미해결 항목의 기록 위치 제공 | `docs/README.md`, `docs/decisions/`, `TODO.md` | 변경한 경로와 참조 확인 |
| Harness 유지보수 | 지침·문서·서비스 CI 구성을 변경 범위와 함께 검토 | `HARNESS_MAINTENANCE.md` | `git diff --check`, 관련 경로·task 확인 |

## 작업 시작 흐름

1. 루트와 대상 module의 `CLAUDE.md`를 읽고 `docs/README.md`에서 상세 문서를 찾는다.
2. 작업 유형에 맞는 rule·skill·agent와 `docs/harness/INSTRUCTIONS_MAP.md`를 따른다.
3. 변경 전후 `docs/harness/FEEDBACK_LOOP.md`의 최소 검증을 좁은 범위부터 실행한다.
4. 구조 변경은 ArchUnit을 실행하고, Harness·문서 변경은 `git diff --check`와 관련 경로·명령을 확인한다.
5. 계약·보안·삭제·대규모 리팩터링은 자동화 결과와 별개로 수동 검토·승인을 거친다.

## 유지보수 원칙

- 즉시 행동 규칙은 `CLAUDE.md` 또는 `.claude/rules/`, 반복 절차는 skill/agent, 근거·배경은 `docs/`에 둔다.
- 기존 규칙을 중복하지 않고 링크로 연결하며, 규칙 추가 시 `INSTRUCTIONS_MAP.md`와 관련 index를 함께 갱신한다.
- 자동 검사는 탐지와 명확한 실패만 담당한다. 파일 삭제·아키텍처 예외 추가·계약 판단은 사람 review를 유지한다.
- 새 모듈·CI·아키텍처 경계가 늘면 maintenance checklist와 관련 문서 index를 먼저 갱신한다.
- 아직 구현하지 않은 개선은 이 문서에 서술하지 않고 `TODO.md`에 기록한다.
