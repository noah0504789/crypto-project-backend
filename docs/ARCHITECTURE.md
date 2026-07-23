# ARCHITECTURE

이 문서는 `crypto-project-backend`의 전체 시스템 구조와 모듈 관계를 사람이 읽기 위한 설명 문서다.
모든 내용은 현재 브랜치의 실제 코드·설정을 근거로 하며, 각 항목에 관련 파일 경로를 함께 표기한다.
문서와 코드가 어긋나면 **코드가 기준**이다. 코드만으로 의도를 알 수 없는 항목은 §11에 `확인 필요`로 분리했다.

---

## 1. 개요

- 언어/런타임: Java 17
- 프레임워크: Spring Boot **3.4.0**, Spring Cloud **2024.0.2** (`gradle/libs.versions.toml`)
- 빌드: Gradle 멀티프로젝트 + `build-logic` precompiled convention plugin (별도 included build, `settings.gradle:2`)
- 아키텍처: 헥사고날(포트/어댑터) 멀티모듈
- 인프라: Eureka(서비스 디스커버리), Spring Cloud Config(git + Vault), gRPC(net.devh), Kafka(Spring Cloud Stream / Kafka Streams), MySQL, MongoDB, Redis Cluster + Redisson
- 주요 라이브러리 버전: gRPC 1.64.0 / protobuf 3.25.3 / grpc-spring 3.1.0.RELEASE / Kafka 3.8.0 / Redisson 3.40.0 / Spring Vault 4.0.2 / OkHttp 4.12.0 / Caffeine 3.1.8 / ArchUnit 1.4.2 / Testcontainers 1.21.0 (`gradle/libs.versions.toml`)

발견된 Gradle 프로젝트: 루트 포함 **77개**(root + 76 subproject, `settings.gradle`). 실행 가능한 Spring Boot 애플리케이션은 **12개**다.

---

## 2. 시스템 구성

12개 실행 서비스와 인프라 구성 요소는 다음과 같다.

```
                         [ Frontend / Client ]
                                  │  REST · WebSocket(STOMP)
                                  ▼
                     ┌───────────────────────────┐
                     │  spring-cloud-api-gateway  │  JWT Resource Server, 라우팅, CORS, X-User-Id 전파
                     └───────────────────────────┘
             ┌────────────┬───────────┬────────────┬───────────────┐
             ▼            ▼           ▼            ▼               ▼
        oauth2-client   user       chat       websocket-gateway  (기타 REST)
             │           │(gRPC)    │(gRPC)        │(gRPC→chat)
             ▼           ▼          ▼              ▼
   oauth2-authorization-server (gRPC auth.v1)   ...
             │
   ───────────────────────── 비동기(Kafka) ─────────────────────────
   market-detection → (price-alert-detected-event) → notification → (web-notification-broadcast-event) → websocket-gateway
   각 서비스 Outbox → outbox-poller → Kafka → 소비 서비스

   인프라: spring-cloud-eureka-server(디스커버리) · spring-cloud-config(Config+Vault)
   저장소: MySQL(user·market·outbox) · MongoDB(chat·notification) · Redis Cluster(auth·chat·session)
```

서비스별 상세 역할은 §4를, 흐름은 `docs/SERVICE_FLOWS.md`를 참고한다.

---

## 3. 모듈 구조와 계층

### 3.1 도메인 서비스의 모듈 분리

각 도메인 서비스는 헥사고날 계층을 Gradle 서브모듈로 분리한다(`settings.gradle`).

| 서브모듈 | 역할 |
|---|---|
| `*-domain` | Entity/Aggregate/VO/Enum/정책. 프레임워크 비의존(코어만) |
| `*-application` | UseCase(포트 in), 포트 out, Command/Query 서비스, 트랜잭션 경계 |
| `*-adapter-in` | REST Controller, gRPC Service, STOMP Controller, Kafka Consumer 바인더 |
| `*-adapter-out` | JPA/Mongo/Redis/Kafka/gRPC Client 등 아웃바운드 어댑터 |
| `*-client` | 다른 서비스가 소비하는 gRPC 클라이언트 래퍼 |
| `*-contract` | 외부 공유 DTO·이벤트 계약 |
| `*-bootstrap` | 실행 모듈. 계층 조립 + Spring Boot Application |

서비스별 존재 계층은 일정하지 않다. 예: `market-detection`은 `-bootstrap`/`-contract`만 있는 축소형이다(§4).

### 3.2 의존 방향

표본 `build.gradle`로 확인한 실제 의존 방향(안쪽 domain을 향함):

- `*-contract` → `common:common-outbox` (`chat/chat-contract/build.gradle`)
- `*-domain` → `common:common-core`만 (프레임워크 비의존, `chat/chat-domain/build.gradle`)
- `*-application` → `*-domain`(`api`) + `*-contract` + 인프라 common + 영속/Kafka 라이브러리 (`chat/chat-application/build.gradle`)
- `*-adapter-out` → `*-application`(도메인은 전이 노출) (`chat/chat-adapter-out/build.gradle`)
- `*-bootstrap` → domain + application + adapter-in + adapter-out + actuator/cloud client (`chat/chat-bootstrap/build.gradle`)

### 3.3 build-logic convention plugin

`build-logic/src/main/groovy/`의 precompiled plugin 5종:

- `crypto-common-library.gradle`: 기반. `java-library` + `io.spring.dependency-management`, Boot/Cloud BOM, Lombok, `spring-boot-starter-test`, Java 17, `-parameters`
- `crypto-domain.gradle` / `crypto-application.gradle` / `crypto-adapter.gradle`: **현재는 `crypto-common-library`만 적용하는 의미상 별칭**(내용 동일)
- `crypto-bootstrap.gradle`: 실행 모듈. `org.springframework.boot` 적용, `bootJar` 활성/`jar` 비활성, `buildInfo()`, `bootRun`

계층 강제는 plugin이 아니라 각 모듈의 `project(...)` 의존으로 이루어진다. 아키텍처 규칙 위반은 `common:common-arch-test`(ArchUnit)가 모든 서비스 CI에서 검증한다(§10).

> 확인 필요: `common:common-arch-test`의 실제 ArchUnit 규칙 내용은 이번 조사에서 파일을 직접 열람하지 못했다(존재·CI 게이트 역할만 확인).

---

## 4. 서비스 카탈로그

실행 애플리케이션 12개. 클래스는 모두 `org.example.*.Main`(`@SpringBootApplication`). **서버 port는 로컬 yml이 아니라 원격 Config(`git-config-repo`)에서 주입되므로 로컬 코드만으로는 확인 불가**이며, 예외로 `spring-cloud-config`만 `server.port: 8888`을 로컬에 명시한다.

| 서비스 | 실행 모듈 | app name | 저장소/외부 | gRPC 서버 | gRPC 클라이언트 소비 |
|---|---|---|---|---|---|
| user | user-bootstrap | user-service | MySQL(Read Replica 인프라) | `user.v1` | — |
| oauth2-authorization-server | …-bootstrap | oauth2-authorization-server | Redis, Vault Transit | `auth.v1` | `user.v1` |
| oauth2-client | oauth2-client-bootstrap | oauth2-client | 외부 OAuth(Google/Kakao) | — | `auth.v1`, `user.v1` |
| spring-cloud-api-gateway | (단일) | api-gateway | — | — | `auth.v1` |
| chat | chat-bootstrap | chat-service | MongoDB, Redis | `chatmessage.v1` | — |
| websocket-gateway | …-bootstrap | websocket-gateway | Redis | — | `chatmessage.v1` |
| market | market-bootstrap | market-service | MySQL | `market.v1` | — |
| market-detection | …-bootstrap | market-detection | Upbit WebSocket | — | `market.v1` |
| notification | notification-bootstrap | notification-service | MongoDB | — | `market.v1` |
| outbox-poller | (단일) | outbox-poller | MySQL, Kafka | — | — |
| spring-cloud-config | …-bootstrap | (config server) | git, Vault | — | — |
| spring-cloud-eureka-server | (단일) | eureka-server | — | — | — |

특이 애노테이션: chat/outbox-poller `@EnableScheduling`, spring-cloud-config `@EnableConfigServer`, eureka-server `@EnableEurekaServer`.

### 서비스별 역할 요약

- **user**: 로컬 회원가입/OAuth2 가입, 프로필 조회·수정, 권한. REST base `/user`(컨텍스트 `/api/v1`), gRPC `user.v1`. (`user/user-adapter-in/.../web/UserController.java`, `user/user-adapter-in/.../grpc/GrpcUserService.java`) — **상세: `docs/modules/USER.md`**
- **oauth2-authorization-server**: 내부 OAuth2 Authorization Server. `TOKEN_EXCHANGE` + `REFRESH_TOKEN` 그랜트, Vault Transit RS256 서명, Redis 토큰 저장, gRPC `auth.v1`. (`oauth2-authorization-server-adapter-in/.../config/`, `-adapter-out/.../token/adapter/out/`) — **상세: `docs/modules/OAUTH2_AUTHORIZATION_SERVER.md`**
- **oauth2-client**: 외부 OIDC 로그인(Google/Kakao) → 내부 AS token-exchange, refresh/logout, `OAuth2AuthorizedClient`(Redis) 관리. (`oauth2-client-adapter-in/.../config/`, `-application/.../authorizedclient/`)
- **spring-cloud-api-gateway**: Reactive Gateway + JWT Resource Server. 라우팅·CORS·`X-User-Id` 전파·blacklist 검증. (`spring-cloud-api-gateway/.../config/ReactiveRouteConfig.java`, `ReactiveJwtDecoderConfig.java`, `CorsConfig.java`)
- **chat**: 채팅방/메시지. MongoDB + Redis 캐시, gRPC `chatmessage.v1`, Outbox → Kafka, DLQ 소비. (`chat/chat-adapter-in`, `chat/chat-adapter-out/.../persistence/MongoChatMessageAdapter.java`)
- **websocket-gateway**: STOMP 게이트웨이. `chatmessage.v1` gRPC 클라이언트, Kafka broadcast consumer → STOMP push. (`websocket-gateway/.../adapter/in/websocket/`, `.../adapter/in/stream/KafkaWebsocketGatewayBinder.java`)
- **market**: 마켓 카탈로그·가격알림 설정. MySQL, gRPC `market.v1`(MarketService, PriceAlertSettingService). (`market/market-adapter-in/.../grpc/`)
- **market-detection**: Upbit WebSocket 수집 + Kafka Streams 변동률 탐지 → `PriceAlertDetectedEvent` 발행. (`market-detection/.../upbit/`, `.../adapter/in/stream/KafkaMarketDetectionBinder.java`)
- **notification**: 알림 생성·저장·전달. Kafka consumer, MongoDB, `market.v1`(수신자 조회) gRPC 클라이언트. (`notification/.../adapter/in/stream/KafkaNotificationBinder.java`, `notification/notification-adapter-out/.../grpc/PriceAlertRecipientQueryAdapter.java`)
- **outbox-poller**: 모든 서비스의 Outbox/DLQ 레코드를 폴링 → Kafka 발행. (`outbox-poller/.../outbox/OutboxEventScheduler.java`, `.../infra/event/KafkaEventPublisher.java`)
- **spring-cloud-config**: Config Server(git + Vault), JWKS 엔드포인트, Vault Transit 서명 대행. (`spring-cloud-config/.../jwks/adapter/in/JwksController.java`, `-adapter-out/.../vault/`)
- **spring-cloud-eureka-server**: 서비스 디스커버리.

---

## 5. 공통 모듈(common-*)

`common`(부모)은 11개 common-* 모듈을 `api`로 재수출하는 파사드다(`common/build.gradle`, `common-test`·`common-arch-test`·`common-actuator-*`는 제외).

| 모듈 | 역할 | 주요 산출물 |
|---|---|---|
| common-core | 계약 enum·공통 예외 | `RedisKey`, `KafkaTopic`, `KafkaHeaderKey`, `StompDestination`, `JwtClaimKey`, `InfrastructureException`/`InvalidRequestException`/`ResourceNotFoundException` |
| common-jpa | JPA + Read Replica 라우팅 | `ReadReplica`, `ReadReplicaAspect`, `DataSourceContextHolder`, `ReplicationRoutingDataSource`, `BaseEntity` |
| common-event | 이벤트 계약/발행 유틸 | `KafkaEvent`, `EventUtils`, `HandleableEvent`, `RecoverableEvent` |
| common-web | REST 예외 처리 | `GlobalExceptionHandler`(`@RestControllerAdvice`) |
| common-grpc | gRPC 예외 처리 | `BaseGrpcExceptionAdvice`, `GrpcExceptionTranslator` |
| common-outbox | Outbox/DLQ 도메인·서비스 | `Outbox`, `Dlq`, `OutboxService`, `OutboxEventListListener` |
| common-redis | Redis 코덱·Fail-open | `RedisValueCodec`, `RedisHashCodec`, `CacheFailOpen(Aspect)` |
| common-redisson | 분산락 | `DistributedLockExecutor`, `RedissonConfig` |
| common-id | Snowflake ID 생성 | (직접 열람 안 함, `idgen.yml`/user에서 사용) |
| common-mongo | Mongo 설정 | (직접 열람 안 함, chat/notification 사용) |
| common-test | 테스트 픽스처 | testcontainers/embedded mysql·redis·mongo (직접 열람 안 함) |
| common-arch-test | ArchUnit 규칙 | 모든 서비스 CI 게이트 (규칙 내용 직접 열람 안 함) |
| common-actuator-core/webmvc/webflux | 모니터링 공통 | (직접 열람 안 함) |
| common-util | 유틸리티 | 미분석 |

---

## 6. 데이터 저장소

- **MySQL**: user, market, outbox-poller. `spring.sql.init`로 `classpath:sql/schema.sql` 초기화. 스키마 힌트:
  - `user`: PK `id`(Snowflake), `public_id` UUID unique(updatable=false), `email` not null. `role`(name unique), `user_role`. (`user/user-adapter-out/.../JpaUser.java`, `JpaRole.java`, `JpaUserRole.java`)
  - `market`: unique `uk_markets_market_code`. (`market/market-adapter-out/.../JpaMarket.java`)
  - `price_alert_setting`: 복합 unique `(user_public_id, market_id)` + index `(market_id)`. (`.../JpaPriceAlertSetting.java`)
- **MongoDB**: chat, notification.
  - `chat_room`: CompoundIndex `{category, msgCnt, _id}` partial `{deleted:false}`, `title` unique partial. (`chat/chat-adapter-out/.../MongoChatRoom.java`)
  - `chat_message`, `chat_room_membership`, `notification`, `notification_recipient`.
- **Redis Cluster**: 6노드 구성(`git-config-repo/infrastructure/redis.yml`). 키는 `common-core/RedisKey` enum으로 중앙 관리. Cluster Hash Tag로 슬롯 고정: `{chat}`, `{auth}`, `{session}`.
- **Read Replica 인프라**: `common-jpa`에 라우팅 DataSource가 구현되어 있고 user 서비스에 write/read Hikari + `ReplicationRoutingDataSource`가 구성됨(`user/user-adapter-out/.../infra/config/DataSourceConfig.java`). 단, user에는 `@ReadReplica`가 적용된 지점이 없어 조회도 write 노드로 라우팅된다(라우팅 트리거는 `@ReadReplica`+Aspect이며 `@Transactional(readOnly=true)`만으로는 read로 가지 않음). 실제 `@ReadReplica` 적용 현황은 §8.6과 §11, 상세는 `docs/modules/USER.md §10` 참조.

---

## 7. 서비스 간 통신

### 7.1 gRPC (net.devh, `discovery:///` 주소)

proto 4개(`protobuf/src/main/proto/**`)와 서버/클라이언트 매핑:

| proto | 서버 구현 모듈 | 클라이언트 소비 모듈 |
|---|---|---|
| `market.v1`(MarketService.GetEnabledMarkets, PriceAlertSettingService.FindReceiverIds) | market-adapter-in | notification-adapter-out, market-detection, market 자체 |
| `chatmessage.v1`(save, HardDelete) | chat-adapter-in | websocket-gateway-adapter-out |
| `user.v1`(FindByEmail, SignUpOauth2) | user-adapter-in | oauth2-authorization-server, oauth2-client |
| `auth.v1`(Access/Refresh/Blacklist/AuthorizedClient) | oauth2-authorization-server-adapter-in | spring-cloud-api-gateway, oauth2-client |

클라이언트에 deadline 정책 적용 예: `.withDeadlineAfter(3500, MILLISECONDS)`(`user/user-client/.../GrpcUserClient.java`). 예외는 REST가 아닌 `BaseGrpcExceptionAdvice` + 서비스별 `@GrpcAdvice`로 처리.

### 7.2 Kafka (Spring Cloud Stream / Streams)

- 공통 설정: `git-config-repo/infrastructure/kafka.yml`(멱등 producer acks=all, JsonDeserializer, isolation read_committed).
- 이벤트 헤더 계약(`common-core/KafkaHeaderKey`): `transaction_id`, `__TypeId__`, `dlq_id`, `KafkaHeaders.KEY`(partition key).
- 토픽 카탈로그(`common-core/KafkaTopic`): `chatroom-event(.dlq)`, `chatroom-broadcast-event`, `chatmessage-event(.dlq)`, `chatmessage-broadcast-event`, `notification-event(.dlq)`, `web-notification-broadcast-event`, `market-broadcast-event`, `price-alert-detected-event`. market-detection 내부: `upbit-ticker-event`, `upbit-ticker-alert-event`.
- Kafka Streams: `market-detection`의 `KafkaMarketDetectionBinder` + `UpbitTickerProcessor`(WindowStore `upbit-ticker-store`로 이동평균·변동률 계산).

### 7.3 REST

게이트웨이가 외부 경로를 rewrite하여 하위 서비스의 `/api/v1/...`로 전달. 컨트롤러: `UserController`, `ChatRoomController`/`ChatMessageController`, `MarketController`/`PriceAlertSettingController`, `NotificationController`, `AuthController`(oauth2-client), `DlqPollerController`(outbox-poller), `JwksController`(config). 예외 응답은 `ErrorResponse`/`ValidationResult`(common-core).

### 7.4 WebSocket / STOMP

`websocket-gateway/.../adapter/in/websocket/config/StompConfig.java`:
- 엔드포인트 `/ws`(SockJS), `/ws-native`. 브로커 `/topic`·`/queue`, appPrefix `/msg`, userPrefix `/user`.
- 핸드셰이크에서 `X-User-Id`(게이트웨이 주입)로 STOMP Principal 결정.
- Destination 계약(`common-core/StompDestination`): `/topic/chat/`(prefix), `/queue/chat/badge`, `/queue/chat/ack`, `/topic/notification/`(prefix). 인바운드 `@MessageMapping("/chat.send")`.

---

## 8. 공통 아키텍처 패턴

### 8.1 Port & Adapter
`application/port/in`(UseCase)·`port/out`(Persistence/Cache) 인터페이스를 `adapter/out`에서 구현. 예: `user/.../port/out/UserPersistencePort.java` ↔ `user/.../adapter/out/JpaUserAdapter.java`.

### 8.2 Command / Query 분리
`*CommandService`/`*QueryService`(+ `*CommandUseCase`/`*QueryUseCase`)로 분리. user/chat/market/notification 전 서비스 적용.

### 8.3 트랜잭션 경계
애플리케이션 서비스에 `@Transactional`. named 트랜잭션 매니저 사용 — chat은 `@Transactional("chatMongoTransactionManager")`(+`@Retryable`/`@Recover`), outbox는 `@Transactional("transactionManager")`.

### 8.4 Domain Event → Outbox
Spring `ApplicationEventPublisher`를 직접 쓰지 않고 `EventUtils.raise(list)` → `@EventListener OutboxEventListListener` → `OutboxService.saveAll`로 Outbox 테이블에 기록(`common-outbox`). 이벤트는 `AbstractOutboxEvent`/`AbstractOutboxEventList` 상속.

### 8.5 Outbox / DLQ
- `Outbox`(`common-outbox/.../outbox/domain/Outbox.java`): 도메인 메서드 `markPublished()`, `markFailed()`, `increaseRetryCnt()`, `isRetryExhausted(int)`. `getDestination()`이 `aggregateType`(=Kafka topic) 반환.
- `Dlq`(`.../dlq/domain/Dlq.java`): `markPublished()`, `markPublishFailed()`, `markCompleted()`, `markFailed(String)`. 상태 enum `DlqStatus`.
- `outbox-poller`가 `@Scheduled`로 general/broadcast/dlq를 분리 폴링 → `KafkaEventPublisher`(StreamBridge)로 발행. DLQ 폴러 on/off는 `DlqPollerController`.

### 8.6 Read Replica 라우팅
`@ReadReplica` + `ReadReplicaAspect`(이미 write 트랜잭션이 활성이면 라우팅하지 않음) + `DataSourceContextHolder`(ThreadLocal 중첩 카운팅) + `ReplicationRoutingDataSource`. `@Transactional(readOnly=true)`만으로는 라우팅되지 않는다. 실제 `@ReadReplica` 적용 지점은 §11 참조.

### 8.7 Redis Key 관리
`common-core/RedisKey` enum이 `pattern` + `expectedArgCount`를 보유하고 `keyFor(...)`가 인자 개수를 검증. Hash Tag `{chat}`/`{auth}`/`{session}`로 클러스터 슬롯 고정. 캐시 조회 실패는 `CacheFailOpen`으로 fail-open 처리 가능.

### 8.8 예외 처리
- REST: `common-web/GlobalExceptionHandler`(`@RestControllerAdvice`) — 검증 실패→400, ResourceNotFound→204, InvalidRequest→404, Infrastructure→500 등.
- gRPC: `common-grpc/BaseGrpcExceptionAdvice`(`@GrpcExceptionHandler`) — 도메인 예외를 gRPC Status로 매핑. 서비스별 `@GrpcAdvice` 하위 클래스.

---

## 9. 구성·설정 관리

- 런타임 서비스는 로컬에 `application-*.yml` 프로파일 파일을 두지 않고, `spring-cloud-config`(`configserver:http://crypto-spring-cloud-config:8888`)에서 프로파일을 조합해 받는다.
- Config Server 백엔드: git(`${CONFIG_REPO_URI}`, 검색 경로 root/`dynamic`/`infrastructure`, label `main`) + Vault(AppRole, KV v2 `secret`, Transit 서명). (`spring-cloud-config/.../bootstrap/src/main/resources/application.yml`)
- 설정 저장소 `git-config-repo/`:
  - `dynamic/`(12): `api-gateway.yml`, `chat-service.yml`, `idgen.yml`, `jwt.yml`, `market-detection.yml`, `market-service.yml`, `notification-service.yml`, `oauth2-authorization-server.yml`, `oauth2-client.yml`, `outbox-poller.yml`, `user-service.yml`, `websocket-gateway.yml`
  - `infrastructure/`(8): `eureka-client.yml`, `eureka-server.yml`, `frontend.yml`, `kafka.yml`, `mongo.yml`, `monitoring.yml`, `mysql.yml`, `redis.yml`
- 실제 secret은 git-config-repo에 커밋되지 않고 `${...}` 플레이스홀더만 존재(Vault에서 주입). 보안 상세는 `.claude/rules/security.md` 참고.

---

## 10. 빌드 · CI · 배포 개요

- 확인한 `build.gradle` 78개, `application*.yml` 계열 24개(런타임 12 + 테스트 12).
- CI task(루트 `build.gradle`): 서비스별 `chatCi`·`userCi`·`marketCi`·`notificationCi`·`oauth2AuthorizationServerCi`·`oauth2ClientCi`·`websocketGatewayCi`·`gatewayCi`·`springCloudConfigCi`·`marketDetectionCi`·`outboxPollerCi`·`eurekaServerCi`, 전체 집계 `serviceCi`. 각 CI가 `:common:common-arch-test:test`(ArchUnit)를 포함. `commonCi`/`protobufCi`는 없음.
- 영향 모듈 계산: `scripts/ci/affected_modules*.py`(pytest 대상)가 변경 파일 → 영향 모듈 → gradle/docker task를 산출.
- GitHub Actions 5개(`.github/workflows/`): `ci.yml`(빌드/테스트/도커 이미지 push), `cd.yml`(수동 배포, self-hosted/production), `spring-cloud-config-bus.yml`(config 변경 시 busrefresh), `production-environment-test.yml`, `self-hosted-runner-test.yml`.
- Dockerfile 12개(bootstrap/단일 실행 모듈마다, `FROM eclipse-temurin:17-jre`). **repo 내 docker-compose 파일은 없음**(compose는 별도 infra repo).
- proto 생성: `protobuf` 모듈이 `com.google.protobuf`로 stub 생성 후 `protos`를 mavenLocal에 publish.

---

## 11. 근거 경로 색인 (주요)

- 모듈 정의: `settings.gradle`
- convention plugin: `build-logic/src/main/groovy/*.gradle`
- CI task: 루트 `build.gradle`
- 계약 enum: `common/common-core/.../enums/{RedisKey,KafkaTopic,KafkaHeaderKey,StompDestination,JwtClaimKey}.java`
- Outbox/DLQ: `common/common-outbox/.../{outbox,dlq}/domain/`, `outbox-poller/.../`
- Read Replica: `common/common-jpa/.../{annotation,aop,datasource}/`
- gRPC proto: `protobuf/src/main/proto/{market,chatmessage,user,auth}/v1/*.proto`
- Gateway: `spring-cloud-api-gateway/.../config/`
- STOMP: `websocket-gateway/.../adapter/in/websocket/config/StompConfig.java`
- 설정 저장소: `git-config-repo/{dynamic,infrastructure}/`
