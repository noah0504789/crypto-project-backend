# 아키텍처 규칙

이 파일은 아키텍처·의존성·핵심 패턴 관련 작업 시 읽는다. 전체 구조 설명(사람용)은 `docs/ARCHITECTURE.md`, 코드 스타일은 `docs/CODE_STYLE.md`를 참고한다.

대전제: **대상 모듈의 기존 패키지 구조와 구현 방식을 우선 기준으로 삼는다.** 구조 변경을 명시적으로 승인받지 않았다면 기존 구조를 따르고, 기존 공개 동작을 유지하는 최소 변경을 우선한다.

## 의존 방향
```
adapter-in / api → application → domain
application → outbound port → adapter-out / infra
contract · client · common 은 계약/공통
```
- domain은 프레임워크 비의존(코어만). `chat/chat-domain/build.gradle` 참고.
- application은 domain(api) + contract + 인프라 common에 의존. Infrastructure 구현체에 직접 의존하지 않는다.
- adapter는 application에 의존(domain은 전이 노출).
- domain 객체가 외부 시스템(Repository/Kafka/Redis/gRPC)을 직접 호출하지 않는다.
- 계층 강제는 convention plugin이 아니라 `project(...)` 의존과 `common:common-arch-test`(ArchUnit)로 이루어진다. 아키텍처 변경 시 `:common:common-arch-test:test`를 실행한다.

## 계층별 책임
- adapter-in: REST Controller, gRPC Service, STOMP Controller, Security Handler
- application: UseCase, Command/Query Service, 트랜잭션 경계, 오케스트레이션
- domain: Entity/Aggregate/VO/Domain Event/enum/정책
- adapter-out: JPA/Mongo/Redis/Kafka/Vault/gRPC Client 구현
- contract/client: 외부 공유 DTO, 이벤트 계약, gRPC wrapper

## 핵심 패턴 유지
### Port & Adapter
`application/port/in`(UseCase)·`port/out` 인터페이스를 `adapter/out`에서 구현. 예: `user/.../port/out/UserPersistencePort.java` ↔ `user/.../adapter/out/JpaUserAdapter.java`.

### Command / Query 분리
`*CommandService`/`*QueryService`(+ `*CommandUseCase`/`*QueryUseCase`) 패턴을 유지한다(user/chat/market/notification).

### 트랜잭션 경계
- 상태 변경 application service에 트랜잭션 경계를 둔다.
- named 트랜잭션 매니저를 존중한다: chat `@Transactional("chatMongoTransactionManager")`, outbox `@Transactional("transactionManager")`. 트랜잭션 매니저 이름은 상수/외부 계약 수준으로 다룬다.

### Domain Event → Outbox
- 도메인 메서드는 외부 시스템을 직접 호출하지 않고 이벤트를 남긴다.
- 발행은 `EventUtils.raise(list)` → `@EventListener OutboxEventListListener` → `OutboxService.saveAll` 흐름을 보존한다. Spring `ApplicationEventPublisher`를 직접 쓰지 않는다.

### Outbox / DLQ
- `Outbox`/`Dlq` 상태 변경은 도메인 메서드로만: `markPublished()`, `markFailed()`, `increaseRetryCnt()`, `isRetryExhausted(int)`, `markPublishFailed()`, `markCompleted()`.
- 헤더 `transaction_id`, `dlq_id`, `__TypeId__`, `KafkaHeaders.KEY`는 외부 계약이다(→ `external-contracts.md`).
- 발행 실패를 삼키지 말고 retry 상태 또는 DLQ 전이를 남긴다.
- 근거: `common/common-outbox/.../{outbox,dlq}/domain/`, `outbox-poller/.../`.

### Read Replica 라우팅
- `@ReadReplica`가 명시적 read 라우팅 지시다. `@Transactional(readOnly=true)`만으로 read로 보내지 않는다.
- 이미 write 트랜잭션이 활성이면 내부 `@ReadReplica`도 write 우선.
- 변경 전 확인: `DataSourceContextHolder`, `ReadReplicaAspect`, `ReplicationRoutingDataSource`(`common/common-jpa/`), `user/.../infra/config/DataSourceConfig.java`.
- 현재 `@ReadReplica` 실제 적용은 `market/.../MarketQueryService.getMarkets()` 1곳만 확인됨. user 서비스는 인프라만 있고 미적용 → **확인 필요(임의로 설계/버그로 판정하지 않는다)**.

### Redis Key
- Redis key는 `common-core/RedisKey` enum(pattern + expectedArgCount)으로 관리한다. 임의의 문자열 조립 금지.
- Cluster Hash Tag `{chat}`/`{auth}`/`{session}`를 영향 분석 없이 변경하지 않는다.
- 조회 실패는 `CacheFailOpen`으로 fail-open 가능. command/write 실패는 복구/무효화를 검토한다.
- key pattern 인자 수·hash tag·TTL 테스트를 유지·추가한다.

### 예외 처리
- REST: `common-web/GlobalExceptionHandler`(`@RestControllerAdvice`) 기준을 따른다. 응답 형식(`ErrorResponse`/`ValidationResult`)을 흔들지 않는다.
- gRPC 서버: `common-grpc-server/AbstractGrpcExceptionAdvice` + 서비스별 `@GrpcAdvice`에서 처리한다. gRPC client 오류·Future 연결은 `common-grpc-client`를 사용한다. gRPC 예외를 REST 핸들러에 태우지 않는다. `CANCELLED`/`DEADLINE_EXCEEDED`/`INTERNAL`을 구분한다.
