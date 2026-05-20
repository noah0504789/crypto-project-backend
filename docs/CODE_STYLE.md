# crypto-project Code Style

## 1. 목적

이 문서는 `crypto-project` 전체 마이크로서비스에서 공통으로 지킬 코드 스타일과 리팩토링 기준을 정의한다.

목표:

- 서비스별 코드 컨벤션 일관성 확보
- 테스트 작성 방식 통일
- 정적 상수, enum, properties 관리 기준 정립
- Entity, DTO, Service, Repository 계층 책임 명확화
- Outbox/DLQ, read replica, OAuth2, WebSocket 같은 핵심 패턴 보존
- Codex 또는 사람이 리팩토링할 때 같은 판단 기준 사용

## 2. 기본 기술 기준

- Java 17
- Spring Boot 3.x
- Spring Cloud 2024.x
- Gradle
- JUnit 5
- Mockito
- AssertJ
- Lombok

서비스별 build script는 독립 Gradle 프로젝트 기준으로 유지한다. 공통 버전은 가능하면 version catalog 기준으로 정리한다.

## 3. 패키지와 계층

권장 계층:

```text
adapter.in / api
application
domain
adapter.out / infra
common
```

현재 서비스별 패키지명이 완전히 통일되어 있지 않으므로, 새 코드 작성 시 기존 서비스의 패턴을 우선 따른다.

기준:

- Controller, gRPC endpoint, STOMP endpoint는 inbound adapter로 본다.
- Service는 application 계층에 둔다.
- Repository interface가 Spring Data 자체라면 adapter/out에 둘 수 있다.
- Port interface를 둔 서비스에서는 application/port/in, application/port/out 구조를 유지한다.
- Redis, Mongo, Kafka, Vault, gRPC client 구현체는 infra 또는 adapter.out으로 본다.
- 도메인 모델은 외부 기술 의존을 최소화한다.

## 4. 네이밍

### 4.1 Service

- 명령 변경: `CommandService`
- 조회: `QueryService`
- 이벤트 처리: `EventService`
- DLQ 재처리: `DlqService`
- 외부 client wrapper: `*Client`
- adapter 구현체: `*Adapter`

예:

- `ChatRoomCommandService`
- `ChatRoomQueryService`
- `ChatRoomEventService`
- `ChatRoomDlqService`
- `MongoChatRoomAdapter`
- `RedisChatRoomAdapter`

### 4.2 메서드

- 생성: `of`, `from`, `fromPayload`, `fromMongo`, `fromRedis`
- 상태 변경: `markPublished`, `markFailed`, `increaseRetryCnt`, `markPublishFailed`
- cache 복구: `warmUp`, `recoverUpdate`, `invalidateInfo`, `invalidateActivity`
- 삭제: soft delete와 hard delete를 이름으로 구분한다.

### 4.3 변수

- id 문자열은 의미를 붙인다. `id`만 써도 문맥이 명확한 경우를 제외하면 `roomId`, `messageId`, `publicId`, `txId`, `dlqId`를 사용한다.
- 외부 계약 식별자는 축약하지 않는다.
- 로그에는 `roomId`, `messageId`, `txId`, `dlqId`, `keyName`, `clientRegistrationId`처럼 추적 가능한 값을 포함한다.

## 5. DTO와 record

- request/response/payload DTO는 record 사용을 우선 검토한다.
- DTO는 가능하면 변환 팩토리를 가진다.

예:

```java
public record UserResponse(...) {

    public static UserResponse from(User user) {
        ...
    }
}
```

기준:

- request validation annotation은 record component에 둔다.
- response 변환 로직이 복잡해지면 mapper 분리를 검토한다.
- DTO에서 Entity 내부 컬렉션을 그대로 노출하지 않는다.

## 6. Entity와 도메인 모델

### 6.1 JPA Entity

JPA Entity 기준:

- 기본 생성자는 `protected`
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 우선
- public `@AllArgsConstructor` 금지
- 정적 팩토리 메서드 우선
- builder가 필요하면 `@Builder(access = AccessLevel.PRIVATE)` 우선
- `createdAt`, `updatedAt`은 `BaseEntity` 사용 우선
- 상태 변경은 도메인 메서드로 수행

권장 예:

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Outbox extends BaseEntity {

    public static Outbox of(...) {
        return Outbox.builder()
                ...
                .status(OutboxStatus.PENDING)
                .retryCnt(0)
                .build();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }
}
```

### 6.2 Mongo/Redis 중심 도메인 모델

`chat`의 `ChatRoom`, `ChatMessage`처럼 JPA Entity가 아닌 도메인 모델도 다음 기준을 따른다.

- 생성 정책은 정적 팩토리로 모은다.
- 이벤트 발생은 도메인 메서드로 표현한다.
- `eventList`와 `dlqEventList` 초기화 누락을 주의한다.
- public builder는 테스트 편의와 캡슐화 사이의 trade-off가 있으므로 새 코드에서는 private builder를 우선 검토한다.

### 6.3 관계 매핑

- JPA 관계는 `EAGER`를 기본값으로 두지 않는다.
- 필요한 경우 fetch join, `findBy*WithRoles`, projection을 검토한다.
- cascade/orphanRemoval은 aggregate 소유 관계가 명확한 경우에만 사용한다.

## 7. Lombok 기준

사용 가능:

- `@Getter`
- `@RequiredArgsConstructor`
- `@Slf4j`
- DTO/테스트 fixture의 제한적 `@Builder`

주의:

- Entity에 public `@Setter` 금지
- Entity에 public `@Builder` 금지 우선
- Entity에 public `@AllArgsConstructor` 금지
- 불필요한 `@Data` 금지
- `@Builder.Default`는 기본값 누락 위험이 있는 경우에만 사용한다.

## 8. 상수화 기준

### 8.1 상수화할 값

반복되거나 외부 계약인 값은 상수화 후보이다.

- property prefix
- path
- header name
- Kafka topic/destination/binding name
- transaction manager name
- Redis key pattern
- cookie name
- OAuth2 registration id
- gRPC client name
- Lua script bean name

현재 주요 후보:

- `transaction_id`
- `dlq_id`
- `__TypeId__`
- `X-User-Id`
- `chatMongoTransactionManager`
- `transactionManager`
- `/auth/refresh`
- `/queue/chat/ack`

### 8.2 상수화하지 않을 값

- 한 번만 쓰이고 의미가 지역적인 값
- 테스트 안에서 시나리오를 설명하기 위한 literal
- 로그 메시지의 자연어 문장
- 단순 HTTP status body 문구

### 8.3 위치 선택

| 값 성격 | 위치 |
| --- | --- |
| Redis key | `RedisKey` enum |
| Kafka topic/binding | `KafkaTopic` enum 또는 event contract constants |
| HTTP path | Controller 내부 constants 또는 API path constants |
| header | `Headers`, `KafkaHeaders`, `GatewayHeaders` 같은 constants class |
| transaction manager | infra transaction constants |
| properties prefix | `@ConfigurationProperties` class |
| 상태/타입/분류 | enum |

클래스 상단에 무분별하게 상수를 모으지 않는다. 같은 성격의 값끼리만 모은다.

## 9. enum 기준

enum을 우선 사용할 값:

- 상태값: `PENDING`, `PUBLISHED`, `FAILED`
- dispatch type: `GENERAL`, `BROADCAST`
- domain type: `CHAT`
- role: `USER`, `ADMIN`
- datasource type: `READ`, `WRITE`
- chat room category
- Kafka topic/binding
- Redis key pattern

문자열로 남겨도 되는 값:

- 사용자가 입력하는 값
- 외부 provider에서 동적으로 내려오는 값
- 한 번만 쓰는 테스트 fixture

enum에는 필요한 경우 다음 메서드를 둔다.

- `keyFor(...)`
- `destination(...)`
- `getTopicName()`
- `getBindingName()`

Redis key enum은 인자 수 검증을 포함한다.

## 10. Properties 기준

- 외부 설정은 `@ConfigurationProperties`로 받는 것을 우선한다.
- `@Value`는 단순하고 변경 가능성이 낮은 값에만 제한적으로 쓴다.
- config repo의 key 구조와 properties class 이름을 맞춘다.
- timeout, TTL, path, redirect URI, pool size는 하드코딩보다 properties 후보로 본다.

예:

- `app.redis.*` -> `AppRedisProperties`
- `jwt.*` -> `JwtProperties`
- `poller.outbox.*` -> `OutboxPollerProperties`
- `upbit.*` -> `UpbitProperties`

## 11. 트랜잭션 기준

### 11.1 일반 기준

- command service는 transaction boundary 후보이다.
- query service는 `@Transactional(readOnly = true)`를 사용한다.
- 단순 cache write는 DB transaction과 묶을지 별도로 둘지 장애 정책을 먼저 정한다.

### 11.2 명시 transaction manager

명시 transaction manager는 외부 계약처럼 취급한다.

- `chatMongoTransactionManager`: chat Mongo transaction
- `transactionManager`: JPA/event DB transaction

Outbox/DLQ 관련 transaction manager는 임의로 제거하지 않는다.

### 11.3 read replica

- `@Transactional(readOnly = true)`만으로 read replica를 타지 않는다.
- read DB 라우팅은 `@ReadReplica`가 명시된 경우에만 수행한다.
- `DataSourceContextHolder` depth 기반 scope 구조를 유지한다.

## 12. Outbox/DLQ 기준

### 12.1 Outbox

- Outbox Entity는 `BaseEntity`를 상속한다.
- 생성 시 status는 `PENDING`, retry count는 `0`이다.
- 상태 변경은 도메인 메서드로만 수행한다.
- destination은 aggregateType 또는 명확한 topic/binding 값으로 해석한다.

필수 도메인 메서드:

- `markPublished`
- `markFailed`
- `increaseRetryCnt`
- `isRetryExhausted`

### 12.2 DLQ

- DLQ Entity는 `BaseEntity`를 상속한다.
- status는 enum으로 관리한다.
- 재발행 성공/실패 상태 변경은 도메인 메서드로 수행한다.

필수 도메인 메서드:

- `markPublished`
- `markPublishFailed`

### 12.3 이벤트 발행

- EventPublisher는 `StreamBridge`를 직접 감싼다.
- `StreamBridge`, Repository, Publisher는 단위 테스트에서 mock 처리한다.
- Kafka header name은 constants 후보이다.
- `streamBridge.send(...)` 결과가 false이면 예외를 던진다.

## 13. 예외 처리 기준

### 13.1 REST

- Controller 계층 예외 응답은 `GlobalExceptionHandler`에서 처리한다.
- `GlobalExceptionHandler`가 없는 서비스는 후보로 표시한다.
- validation 실패는 `ValidationResult` 같은 일관된 응답 DTO로 변환한다.
- 커스텀 예외는 의미 있는 HTTP status와 매핑한다.

우선 검토 대상:

- `user`: `UserNotFoundException`
- `chat`: `ChatRoomNotFoundException`, validation
- `oauth2-client`: refresh/logout 실패 응답
- `spring-cloud-config`: Vault/JWKS/sign 실패
- `market-detection`: 외부 websocket 예외

### 13.2 gRPC

- gRPC endpoint 예외는 `@GrpcAdvice`에서 처리한다.
- gRPC status는 도메인 의미에 맞춘다.
- deadline/cancel/persist/cache 실패를 구분한다.
- 보상 작업이 필요한 경우 실패 로그에 대상 id를 포함한다.

### 13.3 예외 메시지

예외 메시지는 디버깅 가능한 식별자를 포함한다.

예:

- `userPublicId`
- `roomId`
- `messageId`
- `txId`
- `dlqId`
- `keyName`
- `clientRegistrationId`

## 14. 로깅 기준

- 외부 시스템 실패는 warn 또는 error로 남긴다.
- 재시도 소진, DLQ 발행 실패, 보상 실패는 error이다.
- 중복 이벤트 skip처럼 정상적으로 흡수 가능한 상황은 warn 또는 debug를 사용한다.
- 로그에는 식별자를 key-value 형태로 남긴다.

권장:

```java
log.warn("cache update failed. roomId={}, memberId={}", roomId, memberId, e);
```

피할 것:

```java
log.warn("fail");
```

## 15. 테스트 스타일

### 15.1 공통 기준

- JUnit 5 사용
- Mockito 사용
- AssertJ 사용
- 테스트 메서드에는 한글 `@DisplayName` 작성
- given / when / then 주석 구조 사용
- 테스트명은 영어 메서드명, 설명은 한글 DisplayName 허용

### 15.2 단위 테스트

단위 테스트는 Spring Context를 띄우지 않는다.

기준:

- `@ExtendWith(MockitoExtension.class)`
- 외부 의존성은 `@Mock`
- 테스트 대상은 `@InjectMocks` 또는 직접 생성
- Repository, Publisher, Client, StreamBridge는 mock
- Entity와 도메인 메서드는 실제 객체로 검증

### 15.3 슬라이스/통합 테스트

필요한 경우에만 사용한다.

- Controller request/response 검증: `@WebMvcTest`
- JDBC 라우팅 검증: `@JdbcTest`
- OAuth2/Gateway E2E: 제한적 `@SpringBootTest`
- Kafka Streams topology: 필요한 경우 Spring context 허용
- Testcontainers는 외부 시스템 의존이 실제 behavior 검증에 필요할 때만 사용

### 15.4 Assertion

- AssertJ `assertThat`, `assertThatThrownBy` 우선
- JUnit4 `org.junit.Assert` 사용 금지
- JUnit5 Assertions는 필요한 경우만 사용

### 15.5 테스트 실행

가능하면 서비스 단위로 실행한다.

```bash
cd chat
./gradlew test
```

특정 테스트:

```bash
cd user
./gradlew test --tests ReadReplicaRoutingIntegrationTest
```

## 16. Controller 기준

- path는 Controller 수준에서 응집한다.
- 반복 path는 constants 후보이다.
- request body는 `@Valid`를 사용한다.
- header 기반 identity는 `@RequestHeader("X-User-Id")`를 사용하되 header name 상수화를 검토한다.
- paging/cursor 응답은 `CursorPage<T>` 패턴을 유지한다.
- 빈 목록 응답에서 `items=null`을 유지할지 `items=[]`로 바꿀지는 API 계약 변경이므로 별도 논의한다.

## 17. Redis 기준

- Redis key는 enum으로 관리한다.
- key 생성 시 placeholder 인자 수를 검증한다.
- cluster hash tag는 유지한다. 예: `{chat}:...`, `{auth}:...`
- Lua script 결과는 `Boolean.TRUE.equals(result)` 방식으로 null-safe하게 검토한다.
- Redis command 실패는 RuntimeException으로 감싼 뒤 상위 복구 정책으로 넘긴다.
- cache 조회 실패는 `@CacheFailOpen` 적용 후보이다.

## 18. Kafka 기준

- topic name, binding name, destination은 enum/constants로 관리한다.
- producer message에는 partition key를 명확히 넣는다.
- type mapping을 쓰는 이벤트는 `__TypeId__` 계약을 변경하지 않는다.
- consumer에서는 header 누락 가능성을 고려한다.
- DLQ consumer는 성공 시 complete, 실패 시 fail 상태 전이를 남긴다.

## 19. gRPC 기준

- proto 변경은 외부 계약 변경으로 본다.
- `protobuf` 프로젝트 publish 영향 범위를 먼저 확인한다.
- request/response 변환은 mapper 또는 DTO factory로 모은다.
- gRPC client 예외는 서비스 계층에서 의미 있는 예외로 변환한다.
- deadline/cancel은 일반 internal error와 구분한다.

## 20. 코드 변경 원칙

- 기능 변경 없는 리팩토링에서는 public API를 바꾸지 않는다.
- 큰 변경 전에는 분석 결과와 계획을 먼저 제시한다.
- 한 PR은 한 주제만 다룬다.
- 공통 패턴을 바꿀 때는 다른 서비스에서 같은 패턴을 검색한다.
- 테스트 실패 시 root cause를 먼저 분석하고 최소 수정한다.
