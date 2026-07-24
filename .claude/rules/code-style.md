# 코드 스타일 규칙

이 파일은 코드를 작성·리팩토링할 때 읽는다. 아래는 `user`·`chat`·`oauth2-*` 모듈에서 실제로 반복되는 컨벤션을 actionable 규칙으로 추린 것이다. 근거·배경 설명(사람용 전체판)은 [`../../docs/CODE_STYLE.md`](../../docs/CODE_STYLE.md)에 있으며 여기서는 반복하지 않는다.

대전제: **대상 모듈의 기존 패턴을 우선한다.** 모듈 간 차이가 있으면 그 모듈의 지배적 패턴을 따르고, 전역 일괄 정리는 별도 작업으로 분리한다. 계층·의존·트랜잭션·Outbox/Redis의 구조 규칙은 `architecture.md`, 계약(문자열/스키마) 규칙은 `external-contracts.md`가 우선한다.

## 네이밍 (접미사로 역할을 드러낸다)

| 접미사 | 역할 | 예 |
|---|---|---|
| `*CommandService` / `*QueryService` | 쓰기 / 읽기 use case | `ChatRoomCommandService`, `UserQueryService` |
| `*EventService` / `*DlqService` | 비동기 이벤트 영속·보상 / DLQ 재처리 | `ChatMessageEventService`, `ChatRoomDlqService` |
| `*UseCase` | inbound port(interface) | `ChatRoomCommandUseCase` |
| `*Port` | outbound port(interface) | `ChatRoomPersistencePort`, `ChatRoomCachePort` |
| `*Adapter` | 외부 시스템 구현체 | `MongoChatMessageAdapter`, `JpaUserAdapter` |
| `*Client` | 소비자용 gRPC wrapper | `GrpcUserClient`, `GrpcChatMessageClient` |
| `*Request`/`*Response`/`*Command`/`*Query`/`*Result`/`*Payload` | DTO | `ChatRoomCreateRequest`, `ListPopularChatRoomsQuery` |
| `*Config`/`*Properties` | Spring config / `@ConfigurationProperties` | `MongoConfig` |

- 식별자 변수는 맥락을 붙인다: `roomId`·`messageId`·`memberId`·`publicId`·`txId`·`dlqId`(그냥 `id`는 지역 최소 범위에서만).
- OAuth2에서 principal name(email)과 도메인 userId, provider `sub`를 이름으로 구분한다(→ `security.md`).

## DTO는 record + 정적 팩토리

- request/response/command/query/result/payload는 **record**를 우선한다.
- validation 애노테이션은 record 컴포넌트에 둔다(`ChatRoomCreateRequest`: `@UniqueChatRoomTitle @NotBlank @Size` 등).
- 단순 변환은 정적 팩토리로: 도메인→응답 `from`/`fromDomain`(`ChatMessageResponse.fromDomain`), 커서 분기 `firstPage`/`nextPage`/`prevPage`(`ListPopularChatRoomsQuery`), 요청→커맨드 `toCommand`(`ChatRoomCreateRequest.toCommand`).
- 도메인↔외부 계약 변환이 복잡하면 **Mapper 클래스**(static `fromDomain`/`toDomain`)로 분리한다(`ChatRoomPayloadMapper`, `ChatMessagePayloadMapper`).
- response는 엔티티 내부 컬렉션을 그대로 노출하지 않는다. 외부 식별자만 내보낸다(user: 내부 PK `id`(Snowflake) 대신 `publicId`(UUID)를 `id`로).
- event/broadcast payload는 외부 계약이다 — 필드명/타입 변경은 `external-contracts.md` 절차. contract 이벤트는 `@JsonCreator`/`@JsonProperty`를 유지한다(`ChatMessageBroadcastEvent`).

## 도메인 모델

- 생성은 정적 팩토리로 모은다: `of*`(`User.ofLocal`/`ofOAuth2`), `create`(`ChatRoom.create`, `ChatMessage.create`), 영속 복원은 `rehydrate*`(`ChatRoom.rehydrateWithLatest`).
- 상태 변경은 **도메인 메서드**로만 표현한다: `ChatRoom.addMember`/`removeMember`/`validateWritable`, `User.updateNickname`/`addRole`, Outbox `markPublished`/`markFailed`. public setter를 열지 않는다.
- **JPA 엔티티**(user): 기본 생성자 `protected`, builder/all-args는 `PRIVATE`, `@Getter`만 public. 관계는 LAZY, 조회는 `findBy*With*`(fetch join)로 N+1을 피한다.
- **Mongo/Redis 기반 도메인**(chat `ChatRoom`/`ChatMessage`): JPA가 아니어도 같은 정신을 적용한다 — 생성은 정적 팩토리, 도메인은 프레임워크/Repository/Redis 세부에 의존하지 않는다(`chat-domain`은 `common-core`만 의존).
- persistence 모델(`MongoChatRoom` 등)과 domain 모델을 분리하고 `fromDomain`/`toDomain` 변환을 명시한다.

## Lombok

- 사용: `@Getter`, `@RequiredArgsConstructor`(생성자 주입), `@Slf4j`, 엔티티의 protected no-args, 제한적 private `@Builder`.
- 피함: `@Data`, 엔티티 public `@Setter`/`@Builder`/`@AllArgsConstructor`, 테스트 편의를 위한 production 가시성 확장.
- 필드 주입(`@Autowired`) 대신 `@RequiredArgsConstructor` + `final` 생성자 주입을 쓴다.

## 예외

- 서비스 예외는 **common 베이스를 상속**한다: `ResourceNotFoundException`(`ChatRoomNotFoundException`, `UserNotFoundException`), `InvalidRequestException`(`InvalidResourceRequestException`), `InfrastructureException`(`ChatPersistenceException`).
- **재시도 마커 예외**를 계층으로 둔다: `Temporary*`(`TemporaryChatPersistenceException extends ChatPersistenceException`)는 `@Retryable(retryFor=...)` 대상, 멱등 충돌(`DuplicateChatMessageException`)은 `noRetryFor`로 제외한다.
- REST 응답 형식은 `common-web/GlobalExceptionHandler`(`ErrorResponse`/`ValidationResult`)를 흔들지 않는다. gRPC는 `@GrpcAdvice`/`BaseGrpcExceptionAdvice`에서 처리하고 `CANCELLED`/`DEADLINE_EXCEEDED`/`RESOURCE_EXHAUSTED`/`INTERNAL`을 구분한다(`GrpcChatMessageExceptionAdvice`).
- 인프라 예외를 삼키지 않는다: Redis 조회는 fail-open 후보, command 실패는 복구/무효화 이벤트, Kafka/DB 실패는 재시도 상태 또는 DLQ 전이(→ `architecture.md`).

## Command/Query·트랜잭션·비동기

- `*CommandUseCase`/`*QueryUseCase` 인터페이스로만 어댑터가 서비스를 호출한다(Repository/Template 직접 주입 금지).
- 상태 변경 서비스에 트랜잭션 경계를 둔다. **named 트랜잭션 매니저 이름은 상수/계약 수준**으로 다룬다(chat `@Transactional("chatMongoTransactionManager")`).
- `@Transactional(readOnly=true)`만으로 read replica로 라우팅되지 않는다 — read 라우팅은 `@ReadReplica` 명시(→ `architecture.md`).
- 비동기 영속/보상은 `@Retryable`(maxAttempts·backoff) + `@Recover`(→ DLQ 이벤트 발행) 패턴을 유지한다. consumer는 idempotent하게 작성한다.

## 상수화

상수화 우선: 외부 계약 문자열(Redis key·Kafka topic/header·STOMP destination·HTTP path/header/cookie·JWT claim·transaction manager name·gRPC client name·properties prefix). 각 성격별 위치에 둔다 — Redis는 `common-core/RedisKey` enum, Kafka는 `common-core/KafkaTopic`, 헤더는 `HttpHeaderKey`/`KafkaHeaderKey`. 상수 class 하나에 성격이 다른 값을 몰아넣지 않는다.

enum 우선: status(`PENDING`/`PUBLISHED`/`FAILED`), dispatch/domain type, role, datasource `READ`/`WRITE`, chat category. 외부 provider claim value·사용자 입력·로그 자연어는 문자열로 둔다.

한 번 쓰는 지역 literal, 로그 메시지, 짧은 helper 내부 값은 상수화하지 않는다.

## 로그·주석

- 로그에는 추적 식별자를 최소 하나 포함한다(`roomId`/`memberId`/`txId`/`dlqId`). 보상 실패는 `[RECOVER-FALLBACK]`처럼 식별 가능한 prefix를 쓴다.
- 주석은 "무엇"보다 **"왜"** 를 쓸 때만. 미구현 목표는 `TODO`로 남기고(현 코드에도 `// TODO: 인가 처리하기`, `// TODO: spec 정의` 존재), 미해결 관찰 항목은 `../../TODO.md`로 모은다.
- 비밀·시크릿을 코드/로그/커밋에 남기지 않는다(→ `git-safety.md`).
