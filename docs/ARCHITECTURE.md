# crypto-project Architecture

## 1. 프로젝트 성격

`crypto-project`는 여러 Java/Spring 기반 마이크로서비스를 한 작업 디렉토리 아래에서 함께 관리하는 프로젝트이다.

최상위 하위 디렉토리의 각 Gradle 프로젝트는 하나의 독립 마이크로서비스로 본다. 일부 디렉토리는 서비스가 아니라 운영/설정/공통 산출물 역할을 한다.

## 2. 최상위 구성

| 경로 | 역할 |
| --- | --- |
| `chat` | 채팅방, 채팅 메시지, Mongo/Redis 캐시, Outbox/DLQ 생산 및 소비 |
| `user` | 사용자, 역할, gRPC 사용자 조회, MySQL read/write replica 라우팅 |
| `outbox-poller` | event DB의 Outbox/DLQ를 Kafka destination으로 발행 |
| `websocket-gateway` | STOMP/WebSocket gateway, 채팅 메시지 gRPC 호출, Kafka broadcast 이벤트 소비 |
| `oauth2-authorization-server` | OAuth2 Authorization Server, JWT 발급, refresh/access token Redis 저장, token gRPC API |
| `oauth2-client` | OAuth2 login client, refresh/logout endpoint, token exchange, user 연동 |
| `spring-cloud-api-gateway` | 외부 진입점, JWT resource server, route, identity propagation |
| `spring-cloud-config` | Spring Cloud Config Server, Vault 연동, JWKS/sign endpoint |
| `spring-cloud-eureka-server` | Eureka service discovery |
| `market-detection` | Upbit ticker 수집, Kafka Streams state store, notification event 발행 |
| `protobuf` | gRPC proto/stub publication |
| `git-config-repo` | Spring Cloud Config가 읽는 중앙 설정 저장소 |
| `docker-compose` | local infra/service/monitoring 실행 구성 |
| `html` | 로컬 테스트용 정적 화면/샘플 |
| `gradle` | 버전 카탈로그 등 Gradle 기준 프로젝트 |

## 3. 서비스 간 흐름

### 3.1 외부 요청 흐름

```text
browser/client
  -> spring-cloud-api-gateway
  -> user / chat / oauth2-client / websocket-gateway
```

- API Gateway는 JWT를 검증하고 사용자 식별자를 downstream header로 전파한다.
- 주요 사용자 식별 header는 `X-User-Id`이다.
- REST 서비스는 직접 인증 로직을 중복 구현하기보다 gateway에서 전달된 식별자를 신뢰하는 구조를 우선한다.

### 3.2 WebSocket 채팅 메시지 저장 흐름

```text
websocket-gateway
  -> chat gRPC save
  -> chat cache/persistence/outbox event
  -> outbox-poller
  -> Kafka broadcast topic
  -> websocket-gateway consumer
  -> STOMP destination
```

- `websocket-gateway`는 STOMP payload를 검증하고 chat service gRPC를 호출한다.
- `chat`은 메시지 저장, 방 메시지 카운트, membership score 갱신, broadcast outbox 생성을 담당한다.
- Kafka broadcast event는 다시 `websocket-gateway`에서 소비되어 사용자/방 destination으로 전달된다.

### 3.3 Outbox/DLQ 흐름

```text
domain method
  -> AbstractOutboxEventList / AbstractDlqEventList
  -> Spring ApplicationEvent
  -> OutboxEventListListener / DlqEventListListener
  -> event DB
  -> outbox-poller
  -> Kafka destination
```

- 도메인 메서드는 외부 시스템을 직접 호출하지 않고 이벤트를 만든다.
- `chat` 내부 listener는 이벤트 리스트를 직렬화해 Outbox 또는 DLQ 테이블에 저장한다.
- `outbox-poller`는 `PENDING` 상태 Outbox/DLQ를 batch로 조회해 Kafka로 발행한다.
- 발행 성공/실패 상태 변경은 Entity 도메인 메서드로 처리한다.

### 3.4 OAuth2 흐름

```text
browser
  -> oauth2-client
  -> external provider / oauth2-authorization-server
  -> oauth2-authorization-server Redis token store
  -> api-gateway JWT validation
```

- `oauth2-client`는 외부 provider login 이후 internal authorization server와 token exchange를 수행한다.
- refresh token은 httpOnly secure cookie로 내려간다.
- access token은 gateway에서 검증된다.
- blacklist/refresh/access token 관련 조회는 authorization server gRPC API를 통해 수행된다.

### 3.5 Config/Discovery 흐름

```text
service
  -> spring-cloud-config
  -> git-config-repo + Vault
  -> eureka registration/discovery
```

- 각 서비스 `application.yml`은 `spring.config.import=configserver:...`를 사용한다.
- `git-config-repo`는 공통 설정 조합의 중심이다.
- secret 성격의 값은 Vault 또는 환경 변수에서 공급하는 방향을 유지한다.

## 4. 계층 구조 원칙

서비스별 패키지 이름은 아직 완전히 통일되어 있지 않지만, 장기 기준은 다음 계층으로 본다.

```text
api / adapter.in
  -> REST Controller, gRPC endpoint, STOMP endpoint, Request/Response DTO

application
  -> UseCase, CommandService, QueryService, EventService, transaction boundary

domain
  -> Entity, aggregate, value object, domain event, enum, domain policy

infra / adapter.out
  -> Repository implementation, Redis, Mongo, Kafka, Vault, gRPC client, DataSource

common
  -> 공통 예외, validation, enum, event utility, clock, logging, config helper
```

의존 방향은 가능하면 다음처럼 유지한다.

```text
adapter.in -> application -> domain
adapter.out -> application port + domain
application -> application port + domain
domain -> 외부 기술 의존 최소화
```

단, 현재 코드에는 기존 구조상 `domain`이 DTO/adapter 객체를 참조하는 부분이 있다. 이 부분은 즉시 대규모 이동하지 않고 멀티모듈 전환 시 단계적으로 줄인다.

## 5. 서비스별 구조 메모

### 5.1 chat

- 가장 복잡한 도메인 서비스이며 `adapter`, `application`, `domain`, `common`, `infra`, `outbox`, `dlq` 구조가 혼재한다.
- `ChatRoom`, `ChatMessage`는 순수 JPA Entity가 아니라 Mongo/Redis 중심 도메인 모델이다.
- Cache read는 fail-open, command cache 실패는 outbox/DLQ 복구 이벤트로 보완하는 방향이다.
- Mongo transaction manager 이름은 `chatMongoTransactionManager`이다.
- event DB Outbox/DLQ 저장에는 `transactionManager`를 사용한다.

### 5.2 user

- MySQL JPA 기반 서비스이다.
- `User`, `Role`, `UserRole`은 JPA Entity이며 `BaseEntity`를 사용한다.
- `@ReadReplica` 기반 라우팅은 `DataSourceContextHolder`, `ReadReplicaAspect`, `ReplicationRoutingDataSource` 흐름을 유지한다.
- `@Transactional(readOnly = true)`만으로 read replica를 타지 않는다. `@ReadReplica`가 명시된 경우만 read DB로 라우팅한다.

### 5.3 outbox-poller

- Outbox/DLQ 발행 책임만 가진다.
- `Outbox`, `Dlq` Entity는 `BaseEntity`를 상속하고 상태 변경 도메인 메서드를 가진다.
- `@Transactional("transactionManager")`는 발행 상태 변경과 retry count 변경의 일관성 때문에 임의 변경하지 않는다.

### 5.4 websocket-gateway

- STOMP endpoint, WebSocket session tracking, broadcast event consumer 역할을 가진다.
- Redis에는 user/session location을 저장한다.
- chat 저장은 gRPC client를 통해 chat service에 위임한다.
- Kafka broadcast event를 받아 STOMP destination으로 전달한다.

### 5.5 oauth2-authorization-server

- Spring Authorization Server 기반이다.
- access token claim, refresh token, blacklist token 저장은 Redis adapter가 담당한다.
- refresh token 정책은 `RefreshTokenPolicy`와 구현체로 분리한다.
- JWT sign은 config server의 sign/JWKS 기능과 연결된다.

### 5.6 oauth2-client

- 외부 OAuth2 provider와 internal authorization server 사이의 login/token exchange client이다.
- refresh endpoint와 logout handler가 access/refresh token 재발급, blacklist 등록, cookie 삭제를 처리한다.
- redirect URI, refresh path 등 외부 계약 path는 properties 또는 constants 후보이다.

### 5.7 spring-cloud-api-gateway

- route, JWT validation, CORS, identity propagation을 담당한다.
- `X-User-Id` header는 downstream 서비스의 인증 컨텍스트 역할을 한다.
- path authorization rule은 enum 또는 route constants 후보이다.

### 5.8 spring-cloud-config

- Git config repo와 Vault를 조합한다.
- JWKS 공개키 조회와 JWT signing endpoint가 있다.
- Vault keyName 등 외부 식별자는 예외 메시지에 포함해야 한다.

### 5.9 market-detection

- Upbit websocket 이벤트를 받아 Kafka Streams state store로 윈도우 평균/변동률을 계산한다.
- threshold 초과 시 web notification event를 Kafka binding으로 발행한다.
- 외부 websocket serialize/deserialize 예외는 의미 있는 커스텀 예외를 사용한다.

## 6. 데이터 저장소와 책임

| 저장소 | 사용 서비스 | 책임 |
| --- | --- | --- |
| MySQL primary/replica | `user`, `outbox-poller`, `chat` event DB | 사용자 영속성, Outbox/DLQ, read replica 라우팅 |
| Mongo replica set | `chat` | 채팅방/메시지 원장 및 query source |
| Redis cluster | `chat`, `websocket-gateway`, `oauth2-*` | cache, session location, token store, blacklist |
| Kafka | `chat`, `outbox-poller`, `websocket-gateway`, `market-detection` | domain event, broadcast event, ticker event |
| Vault | `spring-cloud-config` | secret, signing key |

## 7. 이벤트 계약

반복되는 Kafka header는 외부 계약으로 본다.

| Header | 의미 |
| --- | --- |
| `KafkaHeaders.KEY` | partition key |
| `transaction_id` | outbox/DLQ 처리 correlation id |
| `dlq_id` | DLQ 재처리 대상 id |
| `__TypeId__` | Spring Kafka JSON type mapping |

topic/destination/binding name은 enum 또는 constants로 모은다.

- `chat`의 `KafkaTopic`
- `websocket-gateway`의 `KafkaTopic`
- `market-detection`의 `KafkaTopic`

동일 문자열을 여러 서비스에서 공유하는 경우 바로 공통 모듈로 옮기기보다 먼저 각 서비스 내 constants를 맞춘 뒤, 멀티모듈 전환 시 `common-event-contract` 후보로 분리한다.

## 8. 장애 처리 원칙

- 외부 시스템 실패를 삼키는 경우 반드시 로그를 남기고 복구 이벤트 또는 상태 전이를 남긴다.
- Redis cache 조회 실패는 fail-open 가능하다.
- Redis command 실패는 단순 무시하지 않고 cache recover/invalidate event 후보로 처리한다.
- Mongo/MySQL 영속성 실패는 retry 대상 예외를 좁히고, retry exhausted 이후 DLQ 이벤트를 남긴다.
- gRPC deadline/cancel은 보상 작업 필요 여부를 명확히 구분한다.

## 9. 멀티모듈 전환 방향

멀티모듈 전환은 한 번에 전체 서비스를 이동하지 않는다. 먼저 서비스 하나 또는 공통 계약 하나를 대상으로 한다.

우선 후보:

1. `protobuf`: 이미 독립 publish 단위이므로 유지/정리 우선
2. 공통 event header constants: `transaction_id`, `dlq_id`, `__TypeId__`
3. 공통 Redis key helper 패턴: `expectedArgCount`, `keyFor`, validation
4. 공통 exception response model
5. test support: Testcontainers initializer, config server/eureka 비활성화 설정

모듈 책임 기준:

| 모듈 | 책임 |
| --- | --- |
| `domain` | 기술 독립 도메인 모델, enum, domain event |
| `application` | use case, port, transaction boundary |
| `infra` | adapter implementation, repository, Redis, Kafka, gRPC client |
| `api` | REST/gRPC/STOMP inbound adapter |
| `common` | 공통 contract, exception, validation, test support |

## 10. 변경 시 주의사항

- 독립 Git 저장소가 여러 개 있으므로 서비스별 git 상태를 확인한다.
- 다른 사람이 만든 변경은 되돌리지 않는다.
- 공통 패턴을 바꾸기 전에는 전체 서비스에서 같은 패턴을 검색한다.
- Outbox/DLQ transaction manager와 read replica routing 구조는 임의 단순화하지 않는다.
- public API, Kafka topic/header, Redis key, gRPC proto는 외부 계약으로 보고 변경 전 영향 범위를 문서화한다.
