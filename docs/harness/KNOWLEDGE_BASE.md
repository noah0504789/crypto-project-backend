# Knowledge Base Map

> 기존 `docs/`와 `docs/modules/`의 구조는 명확하므로 대규모 이동하지 않는다. 새 지식의 유형을 먼저 판별하고 필요한 위치만 추가한다.

## 문서 분류

| 종류 | 현재 위치 | 책임 |
|---|---|---|
| Architecture | `ARCHITECTURE.md`, `docs/modules/*.md` | 현재 구조, 모듈 경계, 서비스·계약·저장소 관계 |
| Decision | `docs/decisions/` | 전역 기술 선택의 이유, 결과, 재검토 시 제약 |
| Convention | `CODE_STYLE.md`, `TESTING.md`, `.claude/rules/` | 코드/테스트 기준과 즉시 적용할 작업 규칙 |
| Domain | `SERVICE_FLOWS.md`, `SCENARIO_TEST.md`, 모듈 문서 흐름 절 | 사용자·이벤트 흐름과 서비스별 비즈니스 맥락 |
| Operation | `CI_CD.md`, 모듈 문서 설정·배포 절 | CI/CD와 현재 운영 구성. 장애 Runbook은 아직 없음 |
| Failure / Open question | `TODO.md` | 미확정 사실·결정의 단일 관리처. 실패 실험 전용 문서는 아직 없음 |
| Harness | `docs/harness/` | 지시 체계, 제약, 피드백 루프, 지식·Drift 관리 |

## 탐색과 책임 경계

| 상황 | 먼저 읽을 문서 | 그 다음 |
|---|---|---|
| 서비스 구조·흐름 변경 | `ARCHITECTURE.md`, 대상 `docs/modules/<NAME>.md` | `SERVICE_FLOWS.md`, 모듈 `CLAUDE.md` |
| 전역 기술 선택 변경/대안 제안 | `docs/decisions/README.md`, 해당 ADR | `ARCHITECTURE.md`, `external-contracts.md`, 영향 코드 |
| 코드·테스트 작성 | `CODE_STYLE.md`, `TESTING.md` | rules, 모듈 `CLAUDE.md` |
| 계약 변경 | `ARCHITECTURE.md` §6–7, 모듈 문서 | `SERVICE_FLOWS.md`, 계약 rule/skill |
| CI/CD·배포 흐름 | `CI_CD.md` | workflow·infra 실제 스크립트 |
| 미해결 사실·승인 필요 선택 | `TODO.md` | 관련 문서·코드 근거 |

`CLAUDE.md`와 module `CLAUDE.md`는 행동과 탐색을 안내할 뿐 상세 구조·이유·흐름을 복사하지 않는다.

## ADR 기록 기준

현재 ADR은 [`docs/decisions/README.md`](../decisions/README.md)에서 관리한다. 여러 서비스·계약·배포 순서에 영향을 주고 되돌리기 어려운 기술 선택은 ADR로 남긴다. 코드의 현재 구조만 설명하면 충분한 선택은 해당 구조 문서에 기록한다.

## 새 지식 기록 위치

| 새 지식 | 위치 |
|---|---|
| 전역·되돌리기 어려운 기술 선택 | `docs/decisions/ADR-<번호>-<slug>.md`와 index |
| 현재 구조·서비스 관계 | `ARCHITECTURE.md` 또는 모듈 문서 |
| 사용자/이벤트 흐름 | `SERVICE_FLOWS.md`, `SCENARIO_TEST.md`, 모듈 문서 |
| 반복 코드·테스트 기준 | `CODE_STYLE.md`, `TESTING.md`, 또는 rules |
| 배포·운영 절차 | `CI_CD.md`; 실제 Runbook 필요 시 `docs/operations/` |
| 중요하지만 채택하지 않은 접근 | 재현 근거가 있을 때 `docs/failures/` |
| 미확정 사실·승인 필요 선택 | `TODO.md` |

빈 `operations/`·`failures/` 디렉터리는 만들지 않는다. 실제 관리 대상이 생길 때만 docs index에 함께 등록한다.

## 문서 관리 원칙

- `CLAUDE.md`/rules에는 즉시 행동 기준, docs에는 설명·근거·이유를 둔다.
- 문서 경로와 실행 서비스의 지침·CI 구성은 변경 범위에 맞춰 확인한다.
- 미확정 사실과 승인 필요 선택은 문서에 복제하지 않고 `TODO.md`에서 관리한다.
