# Feedback Loop

> 테스트 작성·실행 원칙은 [`.claude/rules/testing.md`](../../.claude/rules/testing.md), 테스트 계층의 설명은 [`docs/TESTING.md`](../TESTING.md)가 정본이다. 이 문서는 변경 유형별 최소 검증과 확대 순서만 정한다.

## 1. 검증 수단

| Sensor | 목적 | 사용 시점 |
|---|---|---|
| `:<module>:compileJava` | 가장 빠른 타입·의존성 오류 검출 | Java 코드 변경 직후 |
| `:<module>:test` | 해당 모듈 단위·통합·E2E 테스트 | 구현·테스트 변경 직후 |
| `:common:common-arch-test:test` | Gradle 모듈 그래프와 package 계층 경계 | common·계층·의존성 변경 시 |
| `<service>Ci` | 서비스 build/test, 부팅 스모크, ArchUnit | 영향 서비스 단위 최종 검증 |
| `affected_modules.py --mode ...` | CI가 선택할 task/Docker 대상의 읽기 전용 확인 | CI 영향 분석 시 |
| GitHub Actions `ci-pr.yml` | PR diff 기준 affected 검증과 Dockerfile 빌드 | PR에서의 독립 재현 |

`:<service>:test` 같은 부모 집계 task는 대부분 비어 있다. 실제 하위 모듈 task 또는 루트 `<service>Ci`를 사용한다. 현재 task 목록은 `./gradlew tasks --group verification`과 루트 `build.gradle`에서 확인하며 `commonCi`·`protobufCi`는 없다.

## 2. 변경 유형별 최소 검증

| 변경 유형 | 1차 검증 | 2차 검증 | 최종 검증 |
|---|---|---|---|
| Domain 로직·VO·도메인 이벤트 | `:<service>:<domain>:compileJava` → `test` | 계층/모듈 의존을 바꿨으면 `:common:common-arch-test:test` | 영향 `<service>Ci` |
| Application service·port·transaction | `:<service>:<application>:compileJava` → `test` | `:common:common-arch-test:test` | 영향 `<service>Ci` |
| Adapter-in/out·Controller·Repository | 변경 adapter의 `compileJava` → `test` | 계약 영향이면 producer/consumer 테스트, 계층 변경이면 ArchUnit | 영향 `<service>Ci` |
| common 모듈 | `:common:<submodule>:compileJava` → `test` | `:common:common-arch-test:test` | 영향을 받는 서비스의 `<service>Ci`만 선택 |
| Gradle 모듈·build-logic·ArchUnit | 변경 테스트/Gradle task | `:common:common-arch-test:test` | 대표 영향 `<service>Ci`; 전체 `serviceCi`는 명시 요청 시만 |
| gRPC/proto | `:protobuf:build` | producer·모든 consumer의 compile/test, `review-contract-impact` | 영향 서비스별 `<service>Ci` |
| Kafka event·Outbox/DLQ | producer/consumer의 compile/test, 계약 검색 | 관련 contract·integration test, `review-contract-impact` | producer·consumer 서비스의 `<service>Ci` |
| REST·STOMP·Redis key·JWT·DB schema | 변경 모듈 test와 계약 영향 조사 | producer/consumer/E2E 또는 migration 검증 | 영향 서비스 `<service>Ci`; 비호환 계약은 구현 전 승인 |
| 런타임 config·infra 설정 | 변경 service bootstrap `compileJava`과 설정 참조 확인 | 해당 `BootSmokeTest` 또는 service CI | 영향 `<service>Ci`; 배포 workflow는 실행하지 않고 정적 검토 |
| `scripts/ci/`·affected CI | 실제 base/head 기준 `--mode build --include-arch-test` 및 `--mode docker` 산출 비교 | workflow의 affected 계산과 조건 정적 검토 | PR의 `ci-pr.yml` affected CI |
| 문서·Harness 지침만 | `git diff --check`, 링크·경로·명령 존재 확인 | 문서가 설명하는 구현의 정본과 대조 | 코드/CI 동작 변경이 없으면 build 불필요 |

## 3. 실행 순서

```text
관련 지침·근거 확인
  → 변경 파일/계약 범위 확인
  → 1차: compile 또는 관련 test
  → 실패: 원인 수정 후 같은 Sensor 재실행
  → 2차: ArchUnit·contract·integration Sensor
  → 실패: 원인 수정 후 1차부터 재실행
  → 최종: 영향 service CI
  → git diff 검토·사실 기반 보고
```

- 실패하면 원인을 수정한 뒤 같은 단계부터 다시 실행한다. 테스트 삭제·비활성화·assertion 약화로 통과시키지 않는다.
- ArchUnit 실패는 [`ARCHITECTURE_CONSTRAINTS.md`](ARCHITECTURE_CONSTRAINTS.md), 계약 변경은 외부 계약 rule과 영향 조사 결과를 확인한다.
- 완료 전 `git status --short`·`git diff`로 무관한 변경, secret, 생성물을 확인한다.
- 완료 보고에는 실행 명령과 결과, 미실행 항목의 사유, 영향 범위를 남긴다.
