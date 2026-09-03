# Architecture Constraints

> 기준: 루트 품질 task와 `common:common-arch-test`의 실제 테스트. 행동 규칙은 [`.claude/rules/architecture.md`](../../.claude/rules/architecture.md)와 [`docs/CODE_STYLE.md`](../CODE_STYLE.md)가 정본이며, 이 문서는 자동 제약의 범위와 한계만 설명한다.

## 1. 자동 품질 게이트

| 대상 | 도구·task | 적용 범위 |
|---|---|---|
| 공백·unused import | Spotless `spotlessCheck` | `main` 이후 변경된 Java와 Gradle/properties 파일 |
| Gradle 의존 선언 순서 | `checkDependencyOrder` | 저장소의 `build.gradle` project 의존성 |
| 타입 오류 | Java `compileJava` | 모든 Java 모듈 |
| 모듈·package·일부 코드 스타일 | `:common:common-arch-test:test` | Gradle 모듈 그래프, 계층 package, common import, 접근제어자·빈 생성자 수 |
| commit 전 검사 | `.githooks/pre-commit` → `spotlessCheck` | staged Java·Gradle·품질 설정 변경 시 포맷과 공백 오류를 차단 |

`./gradlew qualityCheck`는 Spotless, Gradle 의존 선언 순서, 모든 Java 컴파일, 아키텍처 테스트를 함께 실행한다. pre-commit hook은 빠른 포맷 gate만 담당하고, 구조·의존성 변경 후에는 `qualityCheck`를 실행한다. 새 clone에서는 한 번 `./gradlew installGitHooks`를 실행해 versioned pre-commit hook을 활성화한다.

Spotless는 기존 전체 코드를 한 번에 재포맷하지 않는다. `main` 이후 변경한 파일부터 동일한 포맷 기준을 강제한다.

## 2. 실행 방법

| 목적 | 명령 | 포함 범위 |
|---|---|---|
| 전체 품질 제약 | `./gradlew qualityCheck` | Spotless, 의존 선언 순서, Java 컴파일, 아키텍처 테스트 |
| 자동 아키텍처 제약 | `./gradlew :common:common-arch-test:test` | Gradle 모듈 그래프, 컴파일된 package·코드 스타일 규칙 |
| 서비스 변경 후 구조 포함 검증 | `./gradlew <service>Ci` | 영향 서비스 build/test + `common-arch-test` |
| 예: chat 변경 | `./gradlew chatCi` | chat의 하위 모듈 test/build + 아키텍처 제약 |

`common-arch-test:test`는 공통 모듈, `-domain`/`-application`/`-adapter-in`/`-adapter-out` 모듈, 실제 `-bootstrap` 모듈의 `classes` task를 먼저 실행한다. 따라서 컴파일된 클래스가 없어서 package 규칙이 검사 없이 통과하지 않는다.

## 3. 현재 자동 제약

### 3.1 Gradle 모듈 그래프 (`ModuleArchitectureTest`)

| 규칙 | 우선순위 | 자동 검증 |
|---|---|---|
| `settings.gradle`의 include 프로젝트에 대응 디렉터리 존재 | P0 | include와 디렉터리 일치 검사 |
| domain → application/adapter/bootstrap 의존 금지 | P0 | `project(...)` 의존 검사 |
| domain은 common/protobuf/자기 서비스 contract만 의존 | P0 | `project(...)` 허용 목록 검사 |
| application → adapter/bootstrap 의존 금지 | P0 | `project(...)` 의존 검사 |
| application은 common/protobuf/contract/자기 domain만 의존 | P0 | `project(...)` 허용 목록 검사 |
| adapter → 다른 adapter/bootstrap 의존 금지 | P0 | `project(...)` 의존 검사 |
| adapter의 허용 의존 범위 | P0 | common/protobuf/contract/client/자기 application·domain만 허용 |
| bootstrap의 외부 서비스 구현 의존 금지 | P0 | common/protobuf/contract/자기 서비스만 허용 |
| 서비스 간 구현 모듈 직접 의존 금지 | P0 | contract/client를 제외한 타 서비스 의존 차단 |
| common → 서비스 모듈 의존 금지, common source import 금지 | P0 | Gradle 의존과 `src/main/java` import 검사 |
| 프로젝트 의존 순환 금지 | P0 | Gradle 그래프 순환 탐지 |

### 3.2 컴파일된 클래스 package 규칙 (`PackageArchitectureTest`)

| 규칙 | 우선순위 | 자동 검증 |
|---|---|---|
| common 모듈의 서비스 패키지 import 금지 | P0 | 모든 `common-*` 컴파일 클래스 검사 |
| domain → adapter/infra package 의존 금지 | P0 | 실제 `-domain` 모듈을 동적으로 탐색 |
| domain/application/adapter → 다른 서비스 domain package 의존 금지 | P0 | `SERVICE_BOUNDARIES`에 등록된 서비스 경계별 package 의존 검사 |
| application → adapter-in/adapter-out package 의존 금지 | P0 | 실제 `-application` 모듈을 동적으로 탐색 |
| bootstrap에는 `Main`만 존재 | P1 | 실제 `-bootstrap` 모듈을 동적으로 탐색하며 예외 없음 |

### 3.3 코드 스타일 규칙 (`CodeStyleArchitectureTest`)

| 규칙 | 자동 검증 |
|---|---|
| production 멤버의 접근제어자 명시 | record·enum·interface·익명/생성 클래스를 제외한 field·method 검사 |
| Spring bean의 생성자는 하나만 존재 | stereotype annotation이 붙은 class의 constructor 수 검사 |

### 3.4 검사 동작 보장과 한계

1. `common-arch-test:test`는 검사 대상의 `classes` task를 먼저 실행한다.
2. domain/application/bootstrap 대상은 실제 `build.gradle` 모듈 구조에서 동적으로 찾는다.
3. 요청한 클래스 출력 디렉터리가 없으면 `Architecture test skipped compiled classes: ...` 오류로 실패한다.
4. 다른 서비스 domain package 차단은 `PackageArchitectureTest.SERVICE_BOUNDARIES`의 명시 목록을 사용하므로 새 domain 서비스 추가 시 등록 여부를 확인한다.
5. `CodeStyleArchitectureTest`는 현재 생성된 `build/classes/java/main`을 검사한다. 모든 Java 모듈을 먼저 컴파일하는 `qualityCheck`에서 전체 범위가 보장된다.

## 4. 자동 검사 밖의 규칙

다음 규칙은 맥락·호환성·제어 흐름 판단이 필요하므로 정적 검사만으로 강제하지 않는다.

| 규칙 | 판단 위치 |
|---|---|
| transaction 경계와 named transaction manager | `architecture.md`, `arch-reviewer` |
| Domain Event → Outbox, 직접 `ApplicationEventPublisher`/Kafka 발행 금지 | `architecture.md`, `arch-reviewer` |
| Outbox/DLQ 상태 전이와 실패 처리 | 도메인 테스트, `arch-reviewer` |
| RedisKey enum·hash tag 변경 | `external-contracts.md`, `review-contract-impact` |
| 외부 계약의 consumer·설정·다른 저장소 영향 | `external-contracts.md`, `contract-scanner` |
| 테스트 계층과 명명 규칙 | `TESTING.md`, `testing.md` |

## 5. 실패 메시지와 수정 방향

- Gradle 의존 위반은 `:<from> must ... depend on :<target>` 형식으로 위반 모듈과 금지 대상을 보여 준다.
- package 의존 위반은 ArchUnit이 source/target class와 규칙을 출력한다.
- 컴파일 출력 누락은 누락된 `build/classes/java/main` 경로와 `classes` 선행 의존을 명시한다.
- 기존 구조와 의도적으로 다른 레거시 예외는 숨기지 않고 test 상수와 이 문서에 함께 기록한다.
