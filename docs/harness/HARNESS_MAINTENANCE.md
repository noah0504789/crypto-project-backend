# Harness Maintenance

> Harness 문서의 중복과 코드·설정 간 Drift를 점검하는 기준이다. 작업 절차 자체는 rules·skills에 둔다.

## 점검 기준

| 대상 | 사실 정본 | 확인 내용 |
|---|---|---|
| 문서 링크·분류 | 파일 시스템, `docs/README.md` | 링크가 존재하고 문서 책임이 겹치지 않는가 |
| 지시 목록·책임 | `CLAUDE.md`, `.claude/{rules,skills,agents}/` | 추가·삭제·이름 변경이 index와 `INSTRUCTIONS_MAP.md`에 반영됐는가 |
| Gradle 모듈·task | `settings.gradle`, 루트·모듈 `build.gradle` | 문서의 모듈, `<service>Ci`, 검증 명령이 실제로 존재하는가 |
| Architecture 제약 | `common/common-arch-test` | 문서의 자동 검사 범위·예외가 테스트와 일치하는가 |
| CI selector | `scripts/ci/`, `.github/workflows/` | affected build/docker 계산 설명이 실제 workflow와 일치하는가 |
| 신규 실행 서비스 | `project-skeleton` skill | 모듈 지침·상세 문서·CI·부팅 스모크·index가 함께 갱신됐는가 |

## 실행 구분

| 시점 | 실행 항목 |
|---|---|
| 문서·Harness 변경 전/후 로컬 | 관련 링크·참조 경로 확인, `git diff --check` |
| Gradle 구조·계층 변경 후 로컬 | ArchUnit + 영향 `<service>Ci` |
| CI script 변경 후 로컬 | 실제 base/head 기준 affected build/docker 계산 비교 |
| 정기 housekeeping | 구조 후보를 출력만 하고 사람이 확인·삭제 판단 |

## 수동 리뷰가 필요한 항목

- 계약 호환성, transaction/Outbox 의미, Redis hash tag 변경은 자동 검사만으로 판정하지 않는다.
- 경로가 존재해도 문서 내용이 코드와 맞는지는 변경 코드와 함께 사람이 검토한다.
- 발견한 temp/backup 파일은 자동 삭제하지 않는다. 소유자·생성 목적·Git 추적 여부를 확인한다.

새 실행 서비스는 [`project-skeleton`](../../.claude/skills/project-skeleton/SKILL.md) skill을 따른다. 라이브러리 모듈은 `settings.gradle`, 모듈 문서·지침, 소비 모듈의 계약/CI 영향, `common-arch-test` 적용 여부를 확인한다.
