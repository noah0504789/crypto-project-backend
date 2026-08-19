# Instructions Map

> 목적: 지시 파일의 정본과 탐색 순서를 명확히 하되, 규칙 본문을 이 문서에 복제하지 않는다.

## 1. 책임 맵

| 파일/디렉터리 | 책임 | 언제 읽는가 | 포함하면 안 되는 내용 |
|---|---|---|---|
| `CLAUDE.md` | 프로젝트 전체 Entry Point/Router, 공통 안전·작업·완료 절차, 상황별 정본 안내 | 모든 작업 시작 시 | 모듈 구현 세부, 규칙 본문, 테스트 명령 전체 목록, agent의 상세 절차 |
| `.claude/rules/` | 작업 유형별 행동 기준과 금지/승인 조건 | 해당 유형의 변경 전과 구현 중 | 구조·흐름의 장문 설명, 반복 절차, 특정 모듈에만 해당하는 규칙 |
| `<module>/CLAUDE.md` | 해당 모듈의 책임, 고유 제약, 주요 파일, 모듈 검증 진입점 | 대상 모듈에서 작업하거나 영향받을 때 | 루트 안전 규칙의 복사, 다른 모듈의 규칙, 상세 설계 배경의 복사 |
| `.claude/skills/` | 반복 가능한 조사·검증·초안 작성 절차 | 작업이 skill의 적용 조건에 맞을 때 | 규칙의 정본, 모듈별 상세 지식, 절차와 무관한 정책 설명 |
| `.claude/agents/` | 별도 컨텍스트가 유익한 읽기 전용 조사·감사·빌드 실행 역할 | 탐색량이 크거나 독립적인 조사/검증이 필요할 때 | 코드 수정, 규칙 본문 복사, main agent의 승인 판단 |
| `docs/` | 구조·흐름·근거·규약·운영 맥락의 상세 지식 | 작업 유형·대상 모듈이 정해진 뒤 | 즉시 적용할 짧은 행동 규칙, 반복 절차, 미확정 사항의 중복 관리 |

지시 체계는 한 문장으로 다음과 같다.

> `CLAUDE.md`가 길을 안내하고, rules가 행동을 제한하며, 모듈 CLAUDE가 지역 제약을 더하고, skills/agents가 절차와 분리된 실행을 제공하고, docs가 그 판단의 근거를 제공한다.

## 2. 작업 시작과 탐색 순서

1. `CLAUDE.md`와 항상 적용되는 `git-safety.md`를 읽고 Git 상태를 확인한다.
2. 작업 유형에 맞는 rule을 읽는다. 계약·보안·멀티모듈·CI/CD 등 Plan Mode 대상이면 수정 전에 분석과 계획을 제시한다.
3. 대상 모듈이 있으면 `<module>/CLAUDE.md`와 대응하는 `docs/modules/<NAME>.md`를 읽는다.
4. 구조·흐름·테스트·CI/CD·코드 스타일의 근거가 필요할 때 `docs/README.md`에서 해당 상세 문서로 이동한다.
5. 반복 절차는 skill을 사용하고, 중간 탐색량을 분리할 이점이 있을 때만 읽기 전용 agent를 호출한다.
6. 코드 변경 뒤에는 `verify-change`와 대상 모듈의 검증 명령으로 좁은 범위부터 확인하고, `CLAUDE.md` 완료 보고 형식으로 결과를 남긴다.

## 3. rules의 정본 범위

| rule | 정본 책임 | 대표 적용 시점 |
|---|---|---|
| `git-safety.md` | 파일 변경, Git, 민감 정보, 검증 무결성, Plan Mode 우선 | 항상 |
| `architecture.md` | 계층·의존·포트/어댑터·트랜잭션·Outbox/DLQ·Redis·예외 | 구조·도메인·인프라 경계 변경 |
| `external-contracts.md` | REST/gRPC/Kafka/Redis/STOMP/JWT/DB 계약 변경 절차 | 외부 소비자가 있는 값·형식 변경 전 |
| `security.md` | OAuth2/JWT·인가·Cookie·Secret 안전 기준 | 인증·인가·Secret 변경 전 |
| `testing.md` | 테스트 작성과 검증 원칙 | 테스트 추가·수정 또는 검증 실행 전 |
| `commit-pr.md` | 커밋 제목과 PR 본문 형식 | 커밋·PR 초안 작성 전 |

규칙을 다른 rule, skill, agent, 모듈 CLAUDE에 복제하지 않는다. 다른 파일은 필요한 rule을 링크하고 그 파일의 적용 절차나 모듈 특이점만 추가한다.

## 4. skills와 agents의 역할 경계

| 종류 | 이름 | 책임 | 사용하지 않을 때 |
|---|---|---|---|
| skill | `analyze-module` | 단일 모듈의 계층·의존·설정·계약을 코드 근거로 조사 | 위치가 이미 확정된 단순 수정 |
| skill | `review-contract-impact` | 외부 계약의 producer/consumer/config/test 영향과 호환성 조사 | 내부 구현만 바뀌는 경우 |
| skill | `verify-change` | 변경 후 상태·좁은 검증·계약 영향·diff를 확인 | 읽기 전용 조사만 수행한 경우 |
| skill | `cross-repo-impact` | 여러 모듈/저장소/축의 병렬 조사와 순차 실행을 종합 | 단일 모듈·단일 파일 작업 |
| skill | `pr-draft` | push/PR 생성 없이 PR 제목·본문 초안 작성 | PR 초안이 필요하지 않은 로컬 작업 |
| skill | `project-skeleton` | 새 서비스 모듈 스켈레톤의 Gradle·설정·부팅 스모크·문서/하네스 갱신 절차 | 라이브러리 모듈 추가, 기존 서비스 내부 변경 |
| agent | `module-explorer` | 모듈별 코드 위치·호출 흐름 조사 | 단일 파일의 자명한 위치 확인 |
| agent | `contract-scanner` | backend·frontend·infra 소비처를 포함한 계약 영향 조사 | 계약과 무관한 변경 |
| agent | `build-runner` | Gradle 검증·CI 영향도 계산과 압축된 결과 보고 | 코드 수정이나 배포 |
| agent | `arch-reviewer` | ArchUnit 밖의 아키텍처 규약을 diff 기준으로 감사 | 단순 문서·무관한 설정 변경 |

- skill은 절차를 제공하고, agent는 독립 컨텍스트에서 그 절차 또는 규칙을 실행한다.
- 코드 수정과 승인 판단은 main agent의 책임이다.
- `build-runner`는 실행 역할이고 `verify-change`는 완료 절차다. 변경 유형별 검증 선택은 `FEEDBACK_LOOP.md`를 따른다.
