# Knowledge Base Map

> 전체 문서 목록은 [`docs/README.md`](../README.md)가 관리한다. 이 문서는 새 지식을 어디에 기록할지만 정한다.

## 기록 위치

| 지식의 성격 | 위치 |
|---|---|
| 현재 시스템 구조·서비스 관계·계약 | `ARCHITECTURE.md`, `docs/modules/*.md` |
| 사용자·이벤트 흐름 | `SERVICE_FLOWS.md`, `SCENARIO_TEST.md`, 모듈 문서 |
| 전역적이고 되돌리기 어려운 기술 선택 | `docs/decisions/ADR-<번호>-<slug>.md`와 index |
| 코드·테스트 작성 기준 | `CODE_STYLE.md`, `TESTING.md` |
| 즉시 적용할 행동·승인 기준 | `.claude/rules/` |
| CI/CD와 현재 운영 구성 | `CI_CD.md`, 모듈 문서의 설정·배포 절 |
| 미확정 사실·승인 필요 선택 | `TODO.md` |

## 새 문서가 필요한 경우

- 반복해서 수행하는 장애 대응 절차가 생기면 `docs/operations/`에 Runbook을 추가한다.
- 재현 가능하고 다시 참고할 가치가 있는 실패 실험이 생기면 `docs/failures/`에 기록한다.
- 여러 서비스·계약·배포 순서에 영향을 주는 기술 선택은 [`docs/decisions/README.md`](../decisions/README.md)의 ADR 기준을 따른다.
- 코드의 현재 구조를 설명하는 데 그치면 새 문서를 만들지 않고 기존 구조·모듈 문서를 갱신한다.

빈 디렉터리나 내용 없는 문서는 미리 만들지 않는다. 새 문서를 추가할 때는 `docs/README.md`에 진입점을 함께 등록한다.
