# CODE_STYLE.md

이 문서는 `crypto-project-backend`의 코드 작성/리팩토링 기준이다. 목표는 코드 취향을 강제하는 것이 아니라, 서비스 간 판단 기준을 맞춰 장애와 리팩토링 비용을 줄이는 것이다.

## 1. 기본 기준

| 항목 | 기준 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.4.x |
| Spring Cloud | 2024.x |
| Test | JUnit 5, Mockito, AssertJ |
| Build | Gradle, version catalog |
| Lombok | 제한적으로 사용 |
| 문서/응답 | 한국어 |

기존 코드와 충돌하는 경우 새 코드부터 이 기준을 적용하고, 대규모 일괄 정리는 별도 작업으로 분리한다.

## 2. 패키지와 계층

권장 구조:

```text
<bounded-context>
├── adapter
│   ├── in
│   └── out
├── application
│   ├── dto
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
│   ├── model
│   ├── event
│   ├── exception
│   └── port
└── infra/config
```

현재 모든 서비스가 정확히 이 모양은 아니다. 기존 서비스에서는 현재 패턴을 우선하고, 새 기능에서 책임을 섞지 않는 것을 더 중요하게 본다.

계층 책임:

| 계층 | 넣을 것 | 넣지 말 것 |
| --- | --- | --- |
| adapter-in | Controller, gRPC service, STOMP endpoint, request/response 변환 | 도메인 정책, DB 직접 접근 |
| application | use case, transaction, orchestration, port 호출 | provider별 claim 구조, Redis key 세부 조립 |
| domain | entity, aggregate, value object, domain event, enum | Spring Web, Repository 구현, Kafka/Redis client |
| adapter-out | JPA/Mongo/Redis/Kafka/gRPC/Vault 구현 | 비즈니스 정책 결정 |
| bootstrap | `Main`, component scan, boot config | 도메인 로직 |

## 3. 네이밍

### 3.1 클래스

| 접미사 | 의미 |
| --- | --- |
| `CommandService` | 상태 변경 use case |
| `QueryService` | 조회 use case |
| `EventService` | domain/application event 처리 |
| `DlqService` | DLQ 복구/재처리 |
| `QueryRepairService` | 캐시 미스 복구(재조회 + warm-up) |
| `*UseCase` | inbound port |
| `*Port` | outbound port 또는 추상화 |
| `*Adapter` | 외부 시스템 구현체 |
| `*Client` | 외부 서비스/gRPC client wrapper |
| `*Properties` | `@ConfigurationProperties` |
| `*Config` | Spring configuration |
| `*Response`, `*Request`, `*Command`, `*Result` | DTO |

### 3.2 메서드

| 동작 | 권장 이름 |
| --- | --- |
| 생성 | `of`, `create`, `from`, `fromPayload`, `fromEntity` |
| 저장/명령 | `save`, `persist`, `join`, `leave`, `hardDelete`, `update` |
| 조회 | `findBy...`, `get...`, `exists...`, `search...` |
| 상태 변경 | `markPublished`, `markFailed`, `increaseRetryCnt`, `markDeleted` |
| cache | `warmUp`, `invalidate`, `recover`, `refresh` |
| event | `publish`, `handle`, `toPayload`, `toEvent` |

### 3.3 변수

- 식별자는 `id`만 쓰기보다 `roomId`, `messageId`, `userId`, `publicId`, `txId`, `dlqId`처럼 맥락을 드러낸다.
- 외부 provider의 subject는 `providerSub` 또는 `sub`를 명확히 구분한다.
- OAuth2 internal user id와 email principal을 혼동하지 않는다.
- 로그에는 추적 가능한 식별자 하나 이상을 포함한다.

### 3.4 시간 변환 메서드

`toInstant()`처럼 **어떤 필드를 변환하는지 이름에 드러나지 않는** 무인자 변환 메서드를 금지한다. 어떤 필드가 어떤 타입으로 나가는지 이름만으로 알 수 있어야 한다.

- **필드 접근 변환기**(무인자 인스턴스 메서드, 자기 필드 하나를 시간 타입으로 변환): **`<필드명><타입토큰>()`**, `to`/`get` prefix 없음.
  - 예: `createdAtInstant()`, `createdAtEpochMillis()`, `createdAtLocalDateTime()`, `updatedAtInstant()`.
- **범용 변환기**(변환 대상을 인자로 받음 → 필드 모호성 없음): **`to<타입토큰>(source)`** 유지.
  - 예: `ServiceTimeConverter.toInstant(dateTime)`, `ServiceTimeConverter.toEpochMillis(dateTime)`.
- **현재 시각**: **`now<타입토큰>()`**.
  - 예: `ClockService.nowLocalDateTime()`.

타입 토큰(반환 타입과 1:1):

| 반환 타입 | 토큰 |
| --- | --- |
| `Instant` | `Instant` |
| `LocalDateTime` | `LocalDateTime` |
| `LocalDate` | `LocalDate` |
| `long`(epoch milli) | `EpochMillis` |
| `long`(epoch second) | `EpochSeconds` |

- 도메인 시각 변환은 존(`Asia/Seoul`) 처리를 직접 하지 말고 `common-core`의 `ServiceTimeConverter`를 거친다.
- Redis 복제 노드의 `master`/`ReadFrom.MASTER` 등 **다른 도메인의 동음이의어**는 이 규칙과 무관하다(시간 변환이 아님).

## 4. DTO/record 기준

request/response/command/result/payload는 record를 우선 검토한다.

```java
public record ChatRoomCreateRequest(
        String title,
        String category
) {
}
```

DTO 기준:

- request validation annotation은 record component에 둔다.
- response는 entity 내부 컬렉션을 그대로 노출하지 않는다.
- 변환이 단순하면 `from` 정적 팩토리를 둔다.
- 변환이 복잡하거나 외부 계약과 도메인 분리가 필요하면 mapper를 둔다.
- event payload는 외부 계약이므로 필드명 변경에 신중한다.

## 5. Entity/Domain Model 기준

### 5.1 JPA Entity

권장:

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Example extends BaseEntity {

    public static Example of(...) {
        return Example.builder()
                .status(ExampleStatus.PENDING)
                .build();
    }

    public void markPublished() {
        this.status = ExampleStatus.PUBLISHED;
    }
}
```

규칙:

- 기본 생성자는 protected.
- public setter 금지.
- public all-args constructor 금지.
- public builder는 새 코드에서 피한다.
- `createdAt`, `updatedAt`은 `BaseEntity` 또는 명확한 auditing 정책을 따른다.
- 상태 변경은 도메인 메서드로 표현한다.
- JPA 관계는 EAGER 기본 사용을 피한다.
- fetch join, projection, `findBy*With...` 메서드로 조회 요구를 표현한다.

### 5.2 Mongo/Redis 중심 도메인

JPA Entity가 아니어도 도메인 모델이면 같은 기준을 적용한다.

- 생성 정책은 정적 팩토리로 모은다.
- eventList/dlqEventList 초기화 누락을 조심한다.
- persistence 모델과 domain 모델이 분리되어 있으면 변환 메서드를 명확히 둔다.
- Redis/Mongo key나 collection 세부사항은 domain에 넣지 않는다.

### 5.3 도메인 서비스 (상태 없는 도메인 로직 분리)

특정 엔티티에 종속되지만 로직을 한곳에 모으면 가독성이 좋아지는 계산/정책은 **상태 없는 도메인 서비스**로 분리할 수 있다.

- 위치는 `*-domain`의 `domain/service`. 프레임워크·Repository·Redis/Mongo에 의존하지 않는다(순수 도메인).
- 상태가 없으면 `private` 생성자 + `static` 메서드(`final class`)로 둔다. 예: `chat`의 `MyChatRoomScoreCalculator`(내 방 정렬 스코어의 unread 가중치·재산정 규칙).
- 분리는 **가독성/응집을 위한 선택**이며, 엄밀히는 관련 엔티티(예: `ChatRoom`)의 도메인 로직이다. 분리했더라도 짝이 되는 엔티티 메서드(`ChatRoom.hasUnread`)와 규칙 일관성을 함께 유지한다.
- 도메인 서비스로 뺄지 엔티티 메서드로 둘지는 응집도로 판단하고, 상태 변경(mutation)은 여전히 엔티티 도메인 메서드에 둔다.

## 6. Lombok 기준

사용 가능:

- `@Getter`
- `@RequiredArgsConstructor`
- `@Slf4j`
- Entity의 protected no-args
- 제한적 private builder

피할 것:

- `@Data`
- Entity public `@Setter`
- Entity public `@Builder`
- Entity public `@AllArgsConstructor`
- 테스트 편의를 위한 production visibility 확장

## 7. 상수화 기준

### 7.1 상수화 우선 후보

- 외부 계약 문자열
- Redis key pattern/hash tag
- Kafka topic/binding/header
- STOMP destination/path
- HTTP header/path/cookie name
- transaction manager name
- gRPC client name
- properties prefix
- JWT claim key

예:

```text
transaction_id
dlq_id
__TypeId__
X-User-Id
Authorization
/auth/refresh
/msg/chat.send
/user/queue/chat/ack
{auth}:blacklist
```

### 7.2 상수화하지 않아도 되는 것

- 한 번만 쓰이는 지역 변수 수준 literal
- 테스트 시나리오 설명용 literal
- 자연어 로그 메시지
- 너무 짧은 private helper 내부 값

### 7.3 위치 선택

| 값 | 위치 |
| --- | --- |
| Redis key | 각 서비스 `RedisKey` enum/key factory |
| Kafka topic/binding | 각 서비스 `KafkaTopic` enum 또는 contract constants |
| Kafka header | event/header constants |
| HTTP path | `ApiPathProperties`, Controller constants |
| Gateway/user header | gateway/common header constants |
| JWT claim | auth/security claim constants |
| transaction manager | infra transaction constants |
| properties prefix | `@ConfigurationProperties` class |
| 상태/분류 | enum |

상수 class 하나에 모든 값을 몰아넣지 않는다. 같은 성격끼리만 모은다.

## 8. enum 기준

enum을 우선 사용할 값:

- status: `PENDING`, `PUBLISHED`, `FAILED`
- dispatch/domain type
- role
- datasource type: `READ`, `WRITE`
- chat room category
- provider/registration id 후보
- Kafka topic/binding 후보
- Redis key type 후보

문자열로 남길 수 있는 값:

- 외부 provider에서 그대로 내려오는 claim value
- 사용자 입력값
- 로그 자연어

## 9. 예외 처리 기준

### 9.0 예외 계층

- 서비스별 예외는 새로 만들기보다 **`common`의 공통 베이스를 상속**한다: not-found는 `ResourceNotFoundException`(`ChatRoomNotFoundException`, `UserNotFoundException`), 잘못된 요청은 `InvalidRequestException`(`InvalidResourceRequestException`), 인프라 실패는 `InfrastructureException`(`ChatPersistenceException`). 상속 베이스로 REST/gRPC 응답 매핑이 일관되게 결정된다.
- **재시도 여부를 예외 타입으로 표현**한다. 일시적 실패는 `Temporary*` 마커 예외를 계층으로 두고(`TemporaryChatPersistenceException extends ChatPersistenceException`) `@Retryable(retryFor = Temporary*.class)` 대상으로 삼는다. 재시도해도 소용없는 멱등 충돌(`DuplicateChatMessageException`)은 `@Retryable(noRetryFor = ...)`로 제외한다.
- 인프라 예외를 서비스 경계에서 도메인/애플리케이션 예외로 **번역**한다(`MongoChatPersistenceExceptionTranslator`가 Mongo 예외를 `Temporary*`/`Duplicate*`로 변환). 원인 예외(cause)를 보존한다.

### 9.1 REST

- 공통 REST 예외 응답은 `common-web`의 `GlobalExceptionHandler` 기준을 따른다.
- 서비스별 예외가 필요하면 handler를 확장하되 응답 형식을 흔들지 않는다.
- validation 오류는 field 단위 정보를 포함한다.
- status code는 예외 이름이 아니라 의미로 결정한다.

### 9.2 gRPC

- gRPC endpoint 예외는 `@GrpcAdvice` 또는 `BaseGrpcExceptionAdvice` 계열에서 처리한다.
- REST handler에 gRPC 예외를 억지로 태우지 않는다.
- `CANCELLED`, `DEADLINE_EXCEEDED`, `INTERNAL`, `RESOURCE_EXHAUSTED`는 의미를 구분한다.

### 9.3 외부 시스템 예외

- Redis 조회 실패: fail-open 후보.
- Redis command/write 실패: 복구 이벤트 또는 invalidate 후보.
- DB 실패: transaction rollback과 retry 가능성 검토.
- Kafka 발행 실패: Outbox/DLQ 상태 전이.
- gRPC 실패: deadline, name resolution, server internal을 구분.

## 10. Transaction 기준

- 상태 변경 application service는 transaction boundary를 명확히 둔다.
- 조회 메서드는 `@Transactional(readOnly = true)`를 사용할 수 있지만 이것만으로 read replica 라우팅하지 않는다.
- read replica는 `@ReadReplica`를 명시한다.
- write transaction 내부에서 read replica로 빠지지 않도록 한다.
- transaction manager 이름은 외부 계약 수준으로 보고 상수화 후보로 둔다.

## 11. Redis 기준

- key pattern은 enum/key factory로 관리한다.
- cluster hash tag를 임의 변경하지 않는다.
- TTL이 필요한 key와 영구 index key를 구분한다.
- Lua script는 원자성 요구가 있는 곳에만 사용한다.
- key 인자 수, hash tag, TTL 정책은 단위 테스트를 작성한다.
- cache fail-open은 조회에 제한한다. command 실패를 조용히 무시하지 않는다.

## 12. Kafka/Outbox/DLQ 기준

- Kafka 직접 발행보다 Outbox를 우선 검토한다.
- domain event -> Outbox -> poller -> Kafka 흐름을 보존한다.
- topic/header/type mapping 변경은 외부 계약 변경이다.
- `StreamBridge`는 단위 테스트에서 mock 처리한다.
- Outbox/DLQ entity 상태 변경은 도메인 메서드로 한다.
- consumer는 중복/재시도 가능성을 고려해 idempotent하게 작성한다.

## 13. gRPC/protobuf 기준

- proto field number는 재사용하지 않는다.
- field 삭제는 reserved를 검토한다.
- proto 변경 후 server/client 영향 범위를 문서화한다.
- gRPC client는 deadline을 명확히 설정한다.
- blocking call이 servlet/request thread를 오래 점유하지 않게 주의한다.
- `protobuf` publish 또는 root build 영향을 확인한다.

## 14. OAuth2/Security 기준

- Spring Security principal name과 도메인 userId를 구분한다.
- `OidcUser#getName()`은 닉네임이 아니라 principal name이다.
- provider별 OIDC claim 해석은 extractor/resolver로 분리한다.
- `OAuth2AuthorizedClientService` 저장 key와 logout 삭제 key를 맞춘다.
- Authorization Server registered client id/secret은 config/Vault 양쪽이 일치해야 한다.
- resource server 설정을 단순 password encoder 사용 때문에 서비스 전체에 켜지 않도록 의존성을 조심한다. 단순 password hashing에는 `spring-security-crypto` 사용을 우선 검토한다.

## 15. WebSocket/STOMP 기준

- destination string은 외부 계약이다.
- ack와 broadcast timeout은 k6 테스트와 맞춰 본다.
- STOMP payload DTO 변경은 프론트와 테스트 영향을 확인한다.
- gRPC save deadline과 STOMP ack timeout은 함께 조정한다.
- 대량 broadcast는 channel contention과 backpressure를 고려한다.

## 16. 테스트 스타일

기본 형태:

```java
@Test
@DisplayName("설명")
void method_condition_expected() {
    // given

    // when

    // then
}
```

규칙:

- `org.junit.Assert` 사용 금지.
- AssertJ 사용.
- Mockito BDD 스타일 `given`, `then` 사용 가능.
- Spring Context는 필요한 테스트에서만 띄운다.
- 외부 시스템 통합 테스트는 `common-test` Testcontainers를 우선 사용한다.
- 테스트 fixture는 의미 있는 상수명을 사용한다.
- 제네릭 메서드 반환값을 AssertJ에 바로 넣어 ambiguous가 나면 타입 힌트를 준다.

예:

```java
assertThat(result.<String>getAttribute("id")).isEqualTo(userId);
```

## 17. Gradle 의존성 기준

- 공통 버전은 `gradle/libs.versions.toml`에 둔다.
- Spring Boot starter가 너무 많은 auto configuration을 켜는지 확인한다.
- 단순 crypto/password 기능이면 `spring-security-crypto`처럼 좁은 의존성을 우선한다.
- `implementation project(':common')` aggregate 의존은 편하지만 의존 그래프가 커진다. 단순화된 모듈이 아닌 곳에서는 필요한 `common:*`만 의존하는 방향을 검토한다.
- `api` 의존은 외부로 타입이 노출될 때만 사용한다.

## 18. 문서/주석 기준

- 코드 주석은 “무엇”보다 “왜”를 설명할 때 쓴다.
- 복잡한 장애 대응, 트랜잭션 정책, 외부 계약은 docs에 남긴다.
- 아직 구현되지 않은 목표는 `목표` 또는 `후보`로 표시한다.
- 문서는 코드보다 앞서가면 안 된다.
