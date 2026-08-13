# Feedback Loop

> 목표: 변경 유형에 맞는 가장 좁은 Sensor부터 실행해 빠르게 수정하고, 필요한 경우에만 더 넓은 검증으로 확장한다.
> 명령과 테스트 작성 원칙의 정본은 `.claude/rules/testing.md`이며, 이 문서는 선택 순서와 완료 조건을 정한다.

## 1. Guide

| Guide | 제공하는 판단 |
|---|---|
| `CLAUDE.md` | 작업 시작·완료 절차, 필요한 rule/skill/module 문서 진입점 |
| `.claude/rules/*.md` | 안전, 아키텍처, 계약, 보안, 테스트의 행동·승인 기준 |
| `<module>/CLAUDE.md` | 모듈 고유 제약과 실제 검증 명령 |
| `docs/modules/*.md`, `ARCHITECTURE.md`, `SERVICE_FLOWS.md` | 구조·호출 흐름·계약의 사실 근거 |
| `docs/TESTING.md` | 테스트 층과 Testcontainers/부팅 스모크의 설계 근거 |
| `ARCHITECTURE_CONSTRAINTS.md` | ArchUnit이 자동으로 검증하는 범위와 한계 |

## 2. Sensor

| Sensor | 목적 | 사용 시점 |
|---|---|---|
| `:<module>:compileJava` | 가장 빠른 타입·의존성 오류 검출 | Java 코드 변경 직후 |
| `:<module>:test` | 해당 모듈 unit/integration/E2E 테스트 | 구현·테스트 변경 직후 |
| `:common:common-arch-test:test` | Gradle 모듈 그래프와 package 계층 경계 | common·계층·의존성 변경 시 |
| `<service>Ci` | 서비스 build/test, 부팅 스모크, ArchUnit | 영향 서비스 단위 최종 검증 |
| `pytest scripts/ci` | affected-module 계산 로직 | `scripts/ci/` 변경 시 |
| `affected_modules.py --mode ...` | CI가 선택할 task/Docker 대상의 읽기 전용 확인 | CI 영향 분석 시 |
| GitHub Actions `ci-pr.yml` | PR diff 기준 affected 검증과 Dockerfile 빌드 | PR에서의 독립 재현 |

`:<service>:test` 같은 부모 집계 task는 대부분 비어 있다. 반드시 실제 하위 모듈 task 또는 `<service>Ci`를 사용한다. `commonCi`와 `protobufCi` task는 존재하지 않는다.

## 3. 변경 유형별 최소 검증 매트릭스

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
| `scripts/ci/`·affected CI | `pytest scripts/ci` | `python scripts/ci/affected_modules.py --mode build --include-arch-test` 및 `--mode docker` | PR의 `ci-pr.yml` affected CI |
| 문서·Harness 지침만 | `git diff --check`, 경로·명령 존재 확인 | 참조하는 가장 좁은 Gradle task 또는 pytest | 코드/CI 동작 변경이 없으면 추가 build 불필요 |

`<service>Ci`는 `chatCi`, `userCi`, `marketCi`, `notificationCi`, `oauth2AuthorizationServerCi`, `oauth2ClientCi`, `websocketGatewayCi`, `gatewayCi`, `springCloudConfigCi`, `marketDetectionCi`, `outboxPollerCi`, `eurekaServerCi` 중 영향 서비스에 맞는 task다.

## 4. 실행 순서와 실패 처리

```text
Guide 확인
  → 변경 파일/계약 범위 확인
  → 1차: compile 또는 관련 test
  → 실패: 원인 수정 후 같은 Sensor 재실행
  → 2차: ArchUnit·contract·integration Sensor
  → 실패: 원인 수정 후 1차부터 재실행
  → 최종: 영향 service CI
  → git diff 검토·사실 기반 보고
```

- 컴파일 실패는 타입·의존성·생성 코드 문제부터 수정한다.
- 테스트 실패는 assertion, 빈 와이어링, Testcontainers/Docker 환경 문제를 구분한다.
- ArchUnit 실패는 위반 source/target과 허용 의존 방향을 `ARCHITECTURE_CONSTRAINTS.md`·`architecture.md`에서 확인한다.
- 계약 영향이 비호환이면 service CI를 통과해도 구현·배포 전에 승인을 받는다.
- 실패한 Sensor를 삭제·비활성화하거나 assertion을 약화해 통과시키지 않는다.

## 5. Agent 작업 완료 조건

1. 요청된 동작과 영향 범위가 Guide·코드 근거와 일치한다.
2. 매트릭스의 1차 Sensor를 실행했고, 실패하면 수정 후 재실행했다.
3. 계층/의존성 변경에는 ArchUnit, 계약 변경에는 영향 조사를 포함했다.
4. 영향 서비스가 있으면 필요한 `<service>Ci`를 실행했거나, 미실행 사유를 명확히 기록했다.
5. `git status --short`와 `git diff`로 의도하지 않은 파일·secret·생성물을 확인했다.
6. 완료 보고에는 실행 명령, 결과, 미실행 항목과 사유, 영향 범위를 남겼다.

## 6. 로컬 검증과 CI의 역할

| 구분 | 로컬 | GitHub Actions |
|---|---|---|
| 목적 | 가장 빠른 원인 확인과 좁은 재검증 | PR diff 기준의 독립 재현과 merge gate |
| 범위 선택 | 변경자가 매트릭스로 필요한 Sensor를 선택 | `affected_modules.py`가 Gradle/Docker 대상을 계산 |
| 테스트 환경 | 개발 환경·재사용 Testcontainers, 필요하면 개별 service CI | 깨끗한 runner·Actions cache·PR Dockerfile build |
| 전체 검증 | `serviceCi`는 명시 요청일 때만 | 수동 full rebuild workflow가 담당 |
| 배포 | 실행하지 않는다 | CI와 CD는 분리되어 있으며 CD는 수동·승인 대상 |
