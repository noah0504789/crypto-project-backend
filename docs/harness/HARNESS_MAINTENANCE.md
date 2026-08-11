# Harness Maintenance

## 유지보수 기준

| 대상 | 확인 기준 | 확인 시점 |
|---|---|---|
| 문서·지침 | 변경한 링크와 참조 경로가 존재하고 index가 필요한 경우 갱신됨 | 문서·Harness 변경 전후 |
| 신규 실행 서비스 | module `CLAUDE.md`, 모듈 문서, root CI task, `serviceCi` 집계가 함께 갱신됨 | 서비스 추가·이름 변경 시 |
| Gradle 명령 | 문서에 적은 task가 실제 task와 일치함 | Gradle 구조·CI 변경 시 |
| Architecture | 모듈·package 경계가 ArchUnit 규칙을 통과함 | 계층·의존성 변경 시 |
| CI selector | affected task 계산이 기대한 결과를 냄 | `scripts/ci/` 변경 시 |
| 구조 이상 | 임시·backup 파일은 목적과 Git 추적 여부를 확인한 뒤 사람이 판단함 | 발견 시 |

## 실행 구분

| 시점 | 실행 항목 |
|---|---|
| 문서·하네스 변경 전/후 로컬 | 관련 링크·참조 경로 확인, `git diff --check` |
| Gradle 구조·계층 변경 후 로컬 | ArchUnit + 영향 `<service>Ci` |
| CI script 변경 후 로컬 | `pytest scripts/ci` + affected 계산 |
| 정기 housekeeping | 구조 후보를 출력만 하고 사람이 확인·삭제 판단 |

## 수동 리뷰가 필요한 항목

- 계약 호환성, transaction/Outbox 의미, Redis hash tag 변경은 자동 검사만으로 판정하지 않는다.
- 경로가 존재해도 문서 내용이 코드와 맞는지는 변경 코드와 함께 사람이 검토한다.
- 발견한 temp/backup 파일은 자동 삭제하지 않는다. 소유자·생성 목적·Git 추적 여부를 확인한다.

## 신규 모듈 체크리스트

1. `settings.gradle` include와 모듈 디렉터리
2. `<module>/CLAUDE.md` 및 `docs/README.md` 대응 링크
3. 모듈 상세 문서와 필요한 `<service>Ci` task(스크립트의 service → task mapping 포함)
4. `common-arch-test` 적용 범위와 레거시 예외 필요성
5. 관련 `BootSmokeTest`, 계약/CI 영향
