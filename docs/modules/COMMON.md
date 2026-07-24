# COMMON — 공통 모듈(common-*) 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 소스(`common/*/src`) 및 `settings.gradle`·각 `build.gradle`
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - 파사드 재수출 목록(`common/build.gradle`) 변경
>   - `common-core`의 계약 enum/프로퍼티(`RedisKey`·`KafkaTopic`·`KafkaHeaderKey`·`StompDestination`·`JwtClaimKey`·`HttpHeaderKey`·`RoleKey`·`AuthTokenKey` 등) 변경
>   - `common-outbox`/`common-event`의 도메인·이벤트 계약 변경
>   - `common-jpa`의 Read Replica 라우팅(`@ReadReplica`·`ReadReplicaAspect`) 변경
>   - `common-arch-test`의 ArchUnit 규칙(`ModuleArchitectureTest`·`PackageArchitectureTest`) 변경

## 1. 문서 목적과 기준 시점

`common/` 하위 공통 모듈의 구조·역할·계약·근거를 사람과 AI가 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다. 짧은 작업 규칙은 [`../../common/CLAUDE.md`](../../common/CLAUDE.md)에 있다. 상위 요약은 [`../ARCHITECTURE.md`](../ARCHITECTURE.md) §5.

## 2. 파사드 구조

- `common`(부모)은 **11개 모듈을 `api`로 재수출하는 파사드**다(`common/build.gradle`): `common-core`, `common-jpa`, `common-event`, `common-web`, `common-grpc`, `common-id`, `common-outbox`, `common-redis`, `common-redisson`, `common-util`, `common-mongo`.
- 서비스는 보통 `implementation project(':common')` 하나로 위 11개를 전이 확보한다.
- **파사드에서 제외**되어 필요한 모듈만 개별 의존하는 것: `common-test`(테스트), `common-arch-test`(CI 게이트), `common-actuator-core/webmvc/webflux`(모니터링). 예: 실행 모듈은 web/webflux에 맞춰 `common-actuator-webmvc` 또는 `-webflux`를 골라 의존한다.
- 모든 모듈은 `crypto-common-library` convention plugin으로 빌드된다.

## 3. 모듈별 요약

| 모듈 | 역할 | 핵심 산출물 | 주요 의존 |
|---|---|---|---|
| `common-core` | 계약 상수·공통 예외·프로퍼티·검증·시계 | enums(`RedisKey`·`KafkaTopic`·`KafkaHeaderKey`·`StompDestination`·`JwtClaimKey`·`JwtHeaderKey`·`HttpHeaderKey`·`AuthTokenKey`·`RoleKey`·`PriceAlertChangeRateThreshold`), 예외(`InfrastructureException`·`InvalidRequestException`·`ResourceNotFoundException` 등), 프로퍼티(`JwtProperties`·`ApiPathProperties`·`AppRedisProperties`·`FrontendProperties`), `ValidationResult`·`@NotBlankIfPresent`, `Clock`/`ClockService` | validation starter |
| `common-jpa` | JPA + Read Replica 라우팅 | `BaseEntity`, `@ReadReplica`, `ReadReplicaAspect`, `DataSourceContextHolder`, `DataSourceType`, `ReplicationRoutingDataSource` | data-jpa, aop, mysql(runtime) |
| `common-event` | Kafka 이벤트 계약·발행 유틸 | `KafkaEvent`, `EventUtils`, `EventsInitializer`, `HandleableEvent`, `ProducibleEvent`, `RecoverableEvent`, `TypedKey`/`TypedPayload` | common-core, stream-kafka |
| `common-web` | REST(MVC) 공통 | `GlobalExceptionHandler`(`@RestControllerAdvice`), `CursorPage`/`CursorPages`, `MessageConverterConfig` | common-core, web, validation |
| `common-grpc` | gRPC 예외 처리 | `BaseGrpcExceptionAdvice`, `GrpcExceptionTranslator`, `GrpcClientException`, `GrpcFailureCode` | common-core, grpc(bom/stub/server-starter) |
| `common-outbox` | Outbox/DLQ 도메인·서비스(헥사고날) | `Outbox`/`OutboxStatus`/`OutboxService`/`OutboxEventListListener`, `Dlq`/`DlqStatus`/`DlqService`, `Abstract*OutboxEvent(List)`, `*PublishPort` | common-jpa, common-event, common-util, jackson |
| `common-redis` | Redis 코덱·Fail-open·연산 | `RedisValueCodec`, `RedisHashCodec`, `CacheFailOpen`(+`Aspect`), `StringRedisHashOperations`, `RedisConnectionFactorySupport` | common-core, data-redis, aop |
| `common-redisson` | 분산락 | `DistributedLockExecutor`, `DistributedLockPolicy`, `RedissonConfig` | common-core, redisson starter |
| `common-id` | ID 생성 | `SnowflakeIdGenerator`/`SnowflakeIdProvider`, `@SnowflakeId`, `ObjectIdGenerator`(Mongo `ObjectId`), `IdGenProperties` | common-core, mongodb-bson, data-jpa |
| `common-mongo` | Mongo 매핑 설정 | `SnakeCaseFieldNamingStrategy`, `Date↔LocalDateTime` 컨버터 | common-core, data-mongodb |
| `common-util` | 유틸리티 | `EventIdUtils`(ULID 기반) | ulid-creator |
| `common-test` | 테스트 인프라 | Testcontainers(Kafka·Mongo·MySQL RW·Redis) + Initializer/Extension, `TestBootApplication` | boot-test, testcontainers* |
| `common-arch-test` | ArchUnit 아키텍처 게이트 | `ModuleArchitectureTest`, `PackageArchitectureTest` (전 서비스 `testRuntimeOnly` 참조) | archunit-junit5 |
| `common-actuator-core` | 배포 제어·readiness 코어 | `DeploymentReadiness`, `DeploymentReadinessHealthIndicator`, `DeploymentControlProperties` | actuator |
| `common-actuator-webmvc` | 배포 제어(MVC) | `DeploymentControlAuthFilter`, `DeploymentReadinessController` | actuator-core, web |
| `common-actuator-webflux` | 배포 제어(WebFlux, gateway용) | `DeploymentControlAuthWebFilter`, `DeploymentReadinessWebFluxController` | actuator-core, webflux |

## 4. 계약 허브: common-core

`common-core`는 **여러 서비스가 공유하는 계약 문자열·상수의 단일 출처**다. 아래는 외부 계약(→ `external-contracts.md`)으로 취급한다.

- `RedisKey`(pattern + expectedArgCount, hash tag `{chat}`/`{auth}`/`{session}`), `KafkaTopic`·`KafkaHeaderKey`(`transaction_id`·`dlq_id`·`__TypeId__` 등), `StompDestination`, `JwtClaimKey`/`JwtHeaderKey`, `HttpHeaderKey`(`X-User-Id` 등), `AuthTokenKey`(refresh 쿠키명 등), `RoleKey`(`ROLE_*`).
- 예외 계층: `InfrastructureException`/`InvalidRequestException`/`ResourceNotFoundException`을 기반으로 각 서비스·common 모듈이 파생(예: `common-outbox`의 `*PersistenceException`, `common-config`의 예외). REST 매핑은 `common-web/GlobalExceptionHandler`, gRPC 매핑은 `common-grpc`가 담당한다.
- 프로퍼티 레코드: `JwtProperties`(`keyName`·`jwksUri`·`signUri`·TTL 등, → `SPRING_CLOUD_CONFIG.md`/`OAUTH2_*`), `ApiPathProperties`, `AppRedisProperties`, `FrontendProperties`.

## 5. 핵심 패턴 모듈

### 5.1 common-outbox (Outbox/DLQ)
- 도메인 상태 변경은 도메인 메서드로만: `markPublished()`·`markFailed()`·`increaseRetryCnt()`·`isRetryExhausted(int)`·`markCompleted()` 등(`Outbox`/`Dlq`).
- 발행 흐름: 도메인 이벤트 → `EventUtils.raise(list)`(common-event) → `@EventListener OutboxEventListListener`(adapter-in) → `OutboxService.saveAll`(application) → `outbox-poller`가 폴링해 Kafka 발행. Spring `ApplicationEventPublisher`를 직접 쓰지 않는다.
- 헥사고날 구조를 common 안에서 유지: `domain`/`application/port/out`/`adapter/in`/`adapter/out`. DLQ도 대칭.

### 5.2 common-event
- `KafkaEvent`·`ProducibleEvent`·`HandleableEvent`·`RecoverableEvent`가 이벤트 계약 인터페이스. `EventUtils`가 수집·발행 진입점, `EventsInitializer`가 이벤트 목록 초기화. payload 타입 키는 `TypedKey`/`TypedPayload`.

### 5.3 common-jpa (Read Replica)
- `@ReadReplica`가 **명시적 read 라우팅 지시자**다. `@Transactional(readOnly=true)`만으로는 read 노드로 가지 않는다(`ReadReplicaAspect`가 `@ReadReplica`를 트리거로 `DataSourceContextHolder`를 통해 `ReplicationRoutingDataSource`를 전환). 이미 write 트랜잭션이 활성이면 write 우선.
- 상세·현황(user 미적용 등)은 [`USER.md §10`](USER.md)과 TODO 2.1.

### 5.4 common-redis / common-redisson
- Redis key는 `common-core/RedisKey` enum으로만 조립(임의 문자열 금지). 조회 실패는 `CacheFailOpen`(Aspect)로 fail-open 가능. 분산락은 `DistributedLockExecutor`(+`DistributedLockPolicy`), 설정 `RedissonConfig`.

### 5.5 common-arch-test (아키텍처 게이트)
- `ModuleArchitectureTest`: `settings.gradle`/`build.gradle`을 파싱해 **의존 방향**을 강제 — domain은 application/adapter/bootstrap 비의존, application은 adapter/bootstrap 비의존, adapter는 타 adapter/bootstrap 비의존, **common은 서비스 모듈에 비의존**, 서비스 간 구현 모듈 상호 비의존, **의존 그래프 순환 금지**. common 소스가 서비스 패키지를 import하지 못하게도 검사.
- `PackageArchitectureTest`: ArchUnit `layeredArchitecture`로 서비스별 domain/application/adapter 패키지 레이어 규칙 검사.
- 모든 서비스 CI(`serviceCi` 및 각 `*Ci`)에 `:common:common-arch-test:test`가 포함된다 → 계층 위반 시 CI 실패.

### 5.6 common-actuator-*
- `DeploymentReadiness`(readiness 상태)와 `DeploymentReadinessHealthIndicator`로 배포 준비 상태를 노출(각 서비스 `health.group.readiness`에 `deploymentReadiness` 포함). 배포 제어 엔드포인트는 `DeploymentControlProperties.token`(config의 `deployment.control.token`=`${DEPLOY_TOKEN}`)으로 인증(`DeploymentControlAuthFilter`/`...AuthWebFilter`). MVC 서비스는 `-webmvc`, gateway(WebFlux)는 `-webflux`를 쓴다.

## 6. 사용처(대표)

- 전 서비스: `common-core`(계약/예외), `common-web` 또는 `common-grpc`(예외 매핑), `common-actuator-*`(모니터링).
- Kafka/Outbox 사용 서비스(chat·market·notification·outbox-poller 등): `common-event`·`common-outbox`.
- JPA 서비스(user·market 등): `common-jpa`(+ Read Replica). Mongo 서비스(chat·notification): `common-mongo`.
- ID 필요 서비스(user 등): `common-id`(Snowflake, `idgen.yml`). 분산락 필요 시 `common-redisson`.

## 7. 테스트 · CI

- `common-jpa`·`common-redis`·`common-redisson` 등은 `common-test`(Testcontainers)로 통합 테스트.
- `common-arch-test`는 산출물 없이 **테스트만** 있는 게이트 모듈. 아키텍처 변경 시 `./gradlew :common:common-arch-test:test`를 반드시 실행한다.
- 개별 컴파일/테스트: `./gradlew :common:common-core:compileJava`, `./gradlew :common:common-jpa:test` 등. `commonCi`/`protobufCi` 같은 집계 task는 **없다**(→ `testing.md`).

## 8. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. common 관련 항목:

- **TODO 3.1** — `common-outbox`의 `DlqStatus.COMSUME_FAILED` 철자(직렬화 계약일 수 있어 임의 수정 금지).
- (참고) **TODO 2.1** — Read Replica 라우팅 인프라는 `common-jpa`에 있으나 user 서비스에 `@ReadReplica` 미적용. 상세는 [`USER.md §10`](USER.md).

## 9. 관련 문서와 rules

- 루트: [`../ARCHITECTURE.md`](../ARCHITECTURE.md) §5, [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 계약/아키텍처 rules: `../../.claude/rules/{external-contracts,architecture,testing}.md`
- 모듈 작업 규칙: [`../../common/CLAUDE.md`](../../common/CLAUDE.md)
- 미해결 관찰: [`../../TODO.md`](../../TODO.md)
