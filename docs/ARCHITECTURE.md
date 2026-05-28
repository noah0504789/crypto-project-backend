# ARCHITECTURE.md

이 문서는 `crypto-project-backend`의 현재 구조, 서비스 간 흐름, 모듈 경계, 핵심 인프라 패턴을 정리한다. 아직 완전히 통일되지 않은 부분은 “현재 구조”와 “권장 방향”을 구분한다.

## 1. 프로젝트 개요

`crypto-project-backend`는 채팅, 사용자, OAuth2 인증, WebSocket gateway, Config/Discovery, Outbox worker, market detection을 포함하는 Java/Spring 기반 마이크로서비스 프로젝트다.

기술 기준:

| 항목 | 기준 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.4.x |
| Spring Cloud | 2024.x |
| Build | Gradle multi-project + version catalog |
| Service discovery | Eureka |
| Config | Spring Cloud Config + `git-config-repo` + Vault |
| Sync RPC | gRPC/protobuf |
| Async messaging | Kafka / Spring Cloud Stream |
| Persistence | MySQL, MongoDB |
| Cache/token | Redis cluster |
| Monitoring | Actuator, Micrometer, Prometheus |

## 2. 최상위 구조

```text
crypto-project-backend
├── build-logic
├── common
├── protobuf
├── chat
├── websocket-gateway
├── user
├── oauth2-client
├── oauth2-authorization-server
├── spring-cloud-api-gateway
├── spring-cloud-config
├── spring-cloud-eureka-server
├── outbox-poller
├── market-detection
├── git-config-repo
├── docker-compose*.yml
├── gradle
└── docs
```

### 2.1 Gradle 구조

루트 `settings.gradle`가 전체 서비스를 include한다. 각 서비스는 다시 `domain`, `application`, `adapter-in`, `adapter-out`, `bootstrap`, `client`, `contract` 등으로 나뉜다.

`build-logic`은 다음 convention plugin을 제공한다.

| plugin | 용도 |
| --- | --- |
| `crypto-common-library` | java-library, dependency-management, lombok, test 기본값 |
| `crypto-bootstrap` | Spring Boot 실행 모듈, bootJar/buildInfo 설정 |
| `crypto-domain` | 현재는 common library alias |
| `crypto-application` | 현재는 common library alias |
| `crypto-adapter` | 현재는 common library alias |

`*-bootstrap` 모듈의 `bootJar` 산출물은 부모 서비스의 `build/libs`로 모이도록 설정되어 있다.

## 3. 서비스/모듈 맵

| 경로 | 성격 | 주요 책임 |
| --- | --- | --- |
| `common` | 공통 라이브러리 집합 | core, jpa, event, web, grpc, id, outbox, redis, redisson, mongo, test |
| `protobuf` | gRPC 계약 | proto 정의와 Java stub 생성 |
| `chat` | 마이크로서비스 | 채팅방/메시지 도메인, Mongo/Redis, gRPC, REST, Outbox/DLQ |
| `websocket-gateway` | 마이크로서비스 | STOMP/WebSocket endpoint, chat gRPC 호출, Kafka broadcast 소비 |
| `user` | 마이크로서비스 | 사용자/역할, MySQL, read replica, gRPC/REST user API |
| `oauth2-client` | 마이크로서비스 | 외부 OAuth login, internal token exchange, refresh/logout endpoint |
| `oauth2-authorization-server` | 마이크로서비스 | internal OAuth2 Authorization Server, JWT, token Redis store, token gRPC API |
| `spring-cloud-api-gateway` | 마이크로서비스 | HTTPS gateway, route, JWT resource server, identity propagation |
| `spring-cloud-config` | 마이크로서비스 | Config Server, Vault 연동, JWKS/sign endpoint |
| `spring-cloud-eureka-server` | 마이크로서비스 | Eureka registry |
| `outbox-poller` | worker | Outbox/DLQ polling, Kafka 발행 |
| `market-detection` | 마이크로서비스/worker | Upbit websocket ticker 수집, Kafka Streams, notification event |
| `git-config-repo` | 설정 저장소 | Spring Cloud Config backend |

## 4. 계층 구조

권장 계층은 다음과 같다.

```text
adapter-in / api
  -> application
      -> domain
  -> adapter-out / infra
contract / client
common
```

### 4.1 inbound adapter

- REST Controller
- gRPC Service
- STOMP Controller
- Security handler/filter entrypoint
- request/response DTO

예:

```text
chat/chat-adapter-in/.../web/ChatRoomController.java
chat/chat-adapter-in/.../grpc/ChatMessageGrpcService.java
user/user-adapter-in/.../grpc/UserGrpcService.java
websocket-gateway/.../stomp/StompController.java
oauth2-client/.../web/AuthController.java
```

### 4.2 application

- use case orchestration
- transaction boundary
- command/query service
- domain event publishing trigger
- external port 호출

예:

```text
ChatRoomCommandService
ChatRoomQueryService
ChatMessageCommandService
UserQueryService
Oauth2UserSignUpService
CustomOidcUserService
```

### 4.3 domain

- aggregate/entity/value object
- domain event
- enum/policy
- 도메인 메서드 기반 상태 변경

예:

```text
ChatRoom
ChatMessage
User
Role
Outbox
Dlq
```

### 4.4 outbound adapter

- JPA/Mongo/Redis repository 구현
- Kafka/StreamBridge publisher
- gRPC client adapter
- Vault adapter
- Redis Lua script adapter
- datasource/config

## 5. 주요 런타임 흐름

### 5.1 외부 REST 요청

```text
browser/client
  -> spring-cloud-api-gateway
  -> downstream service
```

Gateway는 JWT resource server 역할을 수행한다. access token을 검증하고 downstream 서비스로 사용자 식별 정보를 전달한다. 서비스 내부 REST endpoint는 가능한 한 gateway에서 검증된 identity를 신뢰하고 중복 인증 로직을 늘리지 않는다.

주의할 계약:

- Gateway route path
- `Authorization: Bearer ...`
- user identity header
- JWT claim: `sub`, `id`, `roles`, `jti`, `aud`, `iss`

### 5.2 OAuth2 로그인/토큰 흐름

```text
browser
  -> oauth2-client
  -> external provider(Google/Kakao)
  -> oauth2-client CustomOidcUserService
  -> user-service gRPC find/signUp
  -> oauth2-authorization-server token endpoint/token exchange
  -> Redis token store
  -> browser receives access token + refresh cookie
  -> spring-cloud-api-gateway validates JWT
```

역할 분리:

| 컴포넌트 | 책임 |
| --- | --- |
| `oauth2-client` | 외부 provider login, OIDC profile 해석, internal auth server 연동 |
| `user` | 사용자 조회/가입, role 제공 |
| `oauth2-authorization-server` | internal token 발급/저장/검증 보조 API |
| `spring-cloud-api-gateway` | resource server로 access token 검증 |

중요 규칙:

- `CustomOidcUser#getName()`은 Spring Security principal name이다.
- OAuth2AuthorizedClient 저장 기준과 로그아웃 삭제 기준은 동일해야 한다.
- provider별 claim 구조는 `OidcProviderProfileExtractor`로 분리한다.
- refresh cookie 속성 변경은 브라우저/E2E 영향이 크다.

### 5.3 WebSocket 채팅 흐름

```text
browser STOMP client
  -> websocket-gateway /ws or /ws-native
  -> /msg/chat.send
  -> websocket-gateway application service
  -> chat gRPC save
  -> chat Mongo/Redis/domain event/outbox
  -> outbox-poller
  -> Kafka broadcast destination
  -> websocket-gateway Kafka consumer
  -> /topic/chat/{roomId}, /user/queue/chat/ack
```

핵심 계약:

| Destination/Path | 의미 |
| --- | --- |
| `/ws`, `/ws-native` | websocket entrypoint |
| `/msg/chat.send` | STOMP application destination |
| `/topic/chat/{roomId}` | room broadcast |
| `/user/queue/chat/ack` | user ack queue |

성능 테스트는 `websocket-gateway/k6` 아래에 있으며, connection-only, light message, burst message 시나리오가 있다.

### 5.4 Chat persistence/cache/outbox 흐름

```text
ChatRoom/ChatMessage domain method
  -> domain event list
  -> Spring ApplicationEvent listener
  -> Outbox/DLQ table
  -> outbox-poller
  -> Kafka
```

`chat`은 MongoDB를 채팅방/메시지 원장 또는 query source로 사용하고 Redis를 조회 최적화/인덱스/상태 cache로 사용한다. Redis 실패는 fail-open과 복구 이벤트를 구분해야 한다.

대표 Redis 성격:

- 채팅방 info hash
- title uniqueness index set
- popular/recent sorted set
- last-read hash
- message access/cache key

### 5.5 Outbox/DLQ 발행 흐름

```text
domain event
  -> AbstractOutboxEventList / AbstractDlqEventList
  -> OutboxEventListListener / DlqEventListListener
  -> MySQL event DB
  -> outbox-poller
  -> StreamBridge/Kafka
  -> status transition
```

Kafka header 계약:

| Header | 의미 |
| --- | --- |
| `transaction_id` | outbox 처리 correlation id |
| `dlq_id` | DLQ 재처리 대상 id |
| `__TypeId__` | Spring JSON type mapping |
| `KafkaHeaders.KEY` | partition key |

### 5.6 User read/write replica 흐름

```text
application method
  -> @ReadReplica aspect
  -> DataSourceContextHolder(ThreadLocal depth)
  -> ReplicationRoutingDataSource
  -> WRITE or READ DataSource
```

정책:

- 기본은 WRITE.
- `@ReadReplica`가 있어야 READ 후보가 된다.
- `@Transactional(readOnly = true)`만으로 READ로 보내지 않는다.
- 이미 write transaction이 활성화된 경우 내부 `@ReadReplica`는 무시하고 WRITE를 우선한다.

### 5.7 Config/Discovery 흐름

```text
service bootstrap
  -> spring-cloud-config
  -> git-config-repo + Vault
  -> Eureka registration
  -> discovery:///service-name gRPC or gateway route
```

주의:

- `git-config-repo` 값과 Vault secret path가 맞아야 한다.
- config 변경은 실행 중인 컨테이너에 자동 반영되지 않을 수 있다. 재시작 또는 refresh 정책이 필요하다.
- secret은 Git에 두지 않는다.

### 5.8 Market detection 흐름

```text
Upbit websocket
  -> UpbitWebsocketListener
  -> UpbitTickerEvent
  -> Kafka Streams processor/state store
  -> UpbitTickerAlertEvent / WebNotificationEvent
```

`market-detection`은 외부 websocket 연결과 stream processing 성격이 강하므로 네트워크 예외, 재연결, state store 테스트를 분리해서 다룬다.

## 6. 공통 모듈 역할

| 모듈 | 역할 | 주의 |
| --- | --- | --- |
| `common-core` | 공통 exception, ErrorResponse, validation, ApiPath/Jwt/Redis properties | 너무 많은 기술 의존을 넣지 않는다 |
| `common-jpa` | `BaseEntity`, `@ReadReplica`, datasource routing | write transaction 우선 정책 유지 |
| `common-event` | event marker와 notification event | 서비스별 상세 payload를 무리하게 끌어오지 않는다 |
| `common-web` | REST exception handler, converter | WebFlux와 Servlet 차이를 구분한다 |
| `common-grpc` | gRPC exception advice | REST handler와 분리한다 |
| `common-id` | Snowflake ID provider/generator | properties binding과 provider 초기화 테스트 필수 |
| `common-outbox` | Outbox/DLQ 공통 도메인/서비스 | Kafka 발행 구현은 worker/adapter에서 다룬다 |
| `common-redis` | fail-open, hash operation, codec | key enum은 각 서비스 책임과 공통 책임을 구분한다 |
| `common-redisson` | distributed lock executor | lock은 복구가 어려운 정합성 구간에만 사용한다 |
| `common-mongo` | Mongo converter/naming strategy | Mongo 도메인 모델을 common에 넣지 않는다 |
| `common-test` | Testcontainers | 테스트 전용 모듈로 main 코드가 의존하지 않는다 |
| `common-arch-test` | ArchUnit 후보 | 아키텍처 테스트는 현실 구조와 목표 구조를 구분한다 |

## 7. 외부 인프라 맵

| 인프라 | 사용 모듈 | 목적 |
| --- | --- | --- |
| MySQL | user, outbox-poller, chat outbox | user/role, outbox/dlq, read/write replica |
| MongoDB | chat | 채팅방/메시지 저장/조회 |
| Redis cluster | oauth2-*, chat, websocket-gateway | token, blacklist, cache, session/location |
| Kafka | chat, outbox-poller, websocket-gateway, market-detection | event, broadcast, stream |
| Vault | spring-cloud-config, oauth2-authorization-server 연계 | secret, signing/JWKS |
| Eureka | 대부분 서비스 | service discovery |
| Prometheus/Grafana | 운영/테스트 | metrics/observability |

## 8. 외부 계약 관리

계약 변경은 코드 수정보다 먼저 영향 범위를 잡는다.

| 계약 | 변경 시 확인 |
| --- | --- |
| Proto | server/client rebuild, published artifact, gateway/websocket/user/oauth2 영향 |
| Kafka event | producer/consumer type, `__TypeId__`, topic/binding, DLQ |
| Redis key | hash slot, TTL, migration, logout/cache cleanup |
| HTTP API | gateway route, CORS, frontend, tests |
| JWT claim | authorization server, gateway, websocket, downstream header |
| DB schema | schema.sql, JPA mapping, existing data migration |

## 9. 현재 정리 우선순위

1. 문서 기준과 실제 코드의 용어 통일: adapter-in/out, application, domain, bootstrap.
2. 외부 계약 문자열 상수화: Kafka headers, gateway headers, STOMP destinations, Redis key pattern.
3. 서비스별 `GlobalExceptionHandler`/`@GrpcAdvice` 일관성 점검.
4. OAuth2 principal name, Redis authorized client key, logout 삭제 기준 통일.
5. Read replica 정책의 테스트 유지: write transaction 우선.
6. `common` aggregate dependency 정리: 단순 모듈이 아닌 곳에서 aggregate `common` 의존을 줄인다.
7. gRPC deadline/exception mapping과 k6/Prometheus 지표 문서화.
