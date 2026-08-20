# MARKET — market 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - REST 경로(`MarketController`/`PriceAlertSettingController`) 또는 `market-service.yml` 변경
>   - gRPC 계약(`protobuf/src/main/proto/market/v1/market-service.proto`) 변경
>   - Kafka 바인딩(`market-service.yml`의 `spring.cloud.stream.*`) 또는 `common-core/KafkaTopic`의 market 항목 변경
>   - 도메인/JPA 모델(`Market`, `PriceAlertSetting`, `JpaMarket`, `JpaPriceAlertSetting`) 또는 스키마(`market-bootstrap/.../sql/schema.sql`) 변경
>   - 캐시(`CacheConfig`, `MarketCacheNames`, `MarketEventService`)·Read Replica(`MarketQueryService`, `DatasourceConfig`) 변경

## 1. 문서 목적과 기준 시점

이 문서는 `market` 서비스의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../market/CLAUDE.md`](../../market/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

마켓 카탈로그와 가격알림 설정의 소유 서비스. 두 서브도메인을 담당한다.

1. **market(카탈로그)**: 거래 대상 마켓 목록(코드·심볼·이름·활성 여부). 활성 마켓 조회를 REST/gRPC로 노출한다.
2. **price-alert-setting(가격알림 설정)**: 사용자별 마켓 변동률 알림 설정(내 설정 조회·변경 REST, 수신자 조회 gRPC).

외부에는 **REST**(게이트웨이 경유)와 **gRPC**(`market.v1`, 내부 서비스용) 두 인터페이스를 노출한다. gRPC 소비자는 `market-detection`(구독할 마켓 목록 조회)과 `notification`(알림 수신자 조회)이다. 실제 변동률 탐지·알림 발송은 이 서비스가 아니라 `market-detection`/`notification`의 몫이다 — market은 **카탈로그/설정 저장소**다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:market:*` (헥사고날 멀티모듈). 실행 모듈은 `:market:market-bootstrap`(`ext.dockerImageName = "crypto-market-service"`).
- 실행 클래스: `org.example.market.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`).
- app name: `market-service`. 포트: REST `8200`, gRPC `18200`. 컨텍스트 경로 `/api/v1`.
- 저장소: **MySQL**(`market` DB). 스키마는 `spring.sql.init`(`schema-locations: classpath:sql/schema.sql`, `mode: always`)로 초기화하고 마켓 5종을 시드한다.
- 조회 캐시: **Caffeine**(로컬 인메모리, `spring-boot-starter-cache`). Redis/Mongo는 쓰지 않는다.
- Config Server 연동: `spring.cloud.config.name: market-service,eureka-client,mysql,kafka,monitoring`. 공유 `api-contract.*`는 Config Repository 루트 `application.yml`에서 자동 병합된다.
- 부트스트랩 의존성: `common-actuator-webmvc`, Config Client, Eureka Client, `spring-cloud-starter-bus-kafka`, Micrometer/Prometheus.

## 4. 모듈 구조 (헥사고날)

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `market-domain` | domain | `Market`, `PriceAlertSetting`(프레임워크 비의존, rehydrate 팩토리) | `common-core` |
| `market-application` | application | UseCase/Service, Port(in/out), Command/Result, 캐시 이름·설정, Outbox 이벤트 | `market-domain`(api), `common-outbox`, caffeine, starter-cache |
| `market-adapter-in` | adapter-in | REST(`Market`/`PriceAlertSettingController`), gRPC(`GrpcMarketService`, `GrpcPriceAlertSettingService`), Kafka 바인더 | `common-web`, `common-event`, `common-grpc`, `protobuf`, `market-application` |
| `market-adapter-out` | adapter-out | JPA 영속(`JpaMarket*`, `JpaPriceAlertSetting*`), `DatasourceConfig` | `common-id`, `common-jpa`, `market-application`, `market-client` |
| `market-bootstrap` | 실행 | `Main`, `application.yml`, `schema.sql`, messages | 위 4개 + actuator/config/eureka/bus/prometheus |
| `market-client` | 클라이언트 | future stub 기반 소비자용 gRPC 클라이언트(`MarketClient`/`GrpcMarketClient`, `PriceAlertSettingClient`/`GrpcPriceAlertSettingClient`) | `protobuf`, `common-grpc-client`, grpc-client-starter |
| `market-contract` | 계약 | 서비스 간 이벤트/응답용 `contract.market.MarketResponse` | (없음) |

의존 방향: adapter-in/out → application → domain. `market-client`/`market-contract`는 **소비자용 산출물**로, market 자신이 아니라 `market-detection`·`notification`이 의존한다. (특이: `market-adapter-out`이 `market-client`에 의존 — gRPC 클라이언트를 어댑터에서 참조.)

## 5. 주요 클래스와 책임

| 클래스 | 경로(요약) | 책임 |
|---|---|---|
| `MarketController` | `market-adapter-in/.../web/MarketController.java` | `GET /markets`(활성 마켓 목록) |
| `PriceAlertSettingController` | `market-adapter-in/.../web/PriceAlertSettingController.java` | 내 설정 조회/변경(§6) |
| `GrpcMarketService` | `market-adapter-in/.../grpc/GrpcMarketService.java` | gRPC `GetEnabledMarkets`(§7) |
| `GrpcPriceAlertSettingService` | `market-adapter-in/.../grpc/GrpcPriceAlertSettingService.java` | gRPC `FindReceiverIds`(§7) |
| `KafkaMarketBinder` | `market-adapter-in/.../stream/KafkaMarketBinder.java` | `marketCatalogChangedBroadcastEventConsumer`(카탈로그 변경 브로드캐스트 소비) |
| `MarketQueryService` | `market-application/.../service/MarketQueryService.java` | 활성 마켓 조회(`@ReadReplica`+`@Cacheable`) |
| `MarketCommandService` | `market-application/.../service/MarketCommandService.java` | 카탈로그 변경(`@Transactional`) + Outbox 발행 |
| `MarketEventService` | `market-application/.../service/MarketEventService.java` | 카탈로그 변경 이벤트 수신 → `@CacheEvict`(§8) |
| `PriceAlertSettingQueryService` | `market-application/.../service/PriceAlertSettingQueryService.java` | 내 설정 조회, 수신자 조회 |
| `PriceAlertSettingCommandService` | `market-application/.../service/PriceAlertSettingCommandService.java` | 내 설정 create/update/delete(`@Transactional`) |
| `JpaMarketAdapter` / `JpaPriceAlertSettingAdapter` | `market-adapter-out/.../persistence/` | `*PersistencePort` 구현 |
| `CacheConfig` / `MarketCacheNames` | `market-application/.../config`·`.../cache` | Caffeine 매니저, 캐시 이름 상수 |
| `GrpcMarketClient` / `GrpcPriceAlertSettingClient` | `market-client/.../` | `CompletableFuture<GrpcResponse>`를 제공하는 소비자용 gRPC 클라이언트(deadline 3500ms) |

## 6. REST API 계약

컨텍스트 `/api/v1`. 컨트롤러에 `@RequestMapping` 베이스가 없어 경로가 그대로 붙는다.

| 메서드 | 전체 경로 | 헤더/요청 | 응답 |
|---|---|---|---|
| GET | `/api/v1/markets` | (없음) | 200 `List<MarketResponse>` |
| GET | `/api/v1/price-alerts/me` | `X-User-Id`(UUID) | 200 `MyPriceAlertSettingsResponse` |
| PUT | `/api/v1/price-alerts/me` | `X-User-Id`(UUID) + `PriceAlertSettingChangeRequest` | 204 No Content |

- `X-User-Id`는 게이트웨이가 검증된 JWT의 `id` claim에서 주입(`common-core/HttpHeaderKey.USER_ID_VALUE`). 컨트롤러는 이 값을 `UUID`(publicId)로 그대로 신뢰한다.
- **`MarketResponse`**(web, `adapter-in/.../web/dto`): `{ id, marketCode, symbol, koreanName, englishName }`(enabled 미노출 — 애초에 enabled=true만 조회).
- **`PriceAlertSettingChangeRequest`**: `{ creates[], updates[], deletes[] }` 배치. 각 항목 검증 — `code` `@NotBlank`, `targetChangeRate` `@NotNull`+`@DecimalMin("0.00")`+`@DecimalMax("1.00")`. delete는 `code`만. 메시지는 `messages,common-validation-messages`(한국어).
- 조회(`getMySettings`)는 **활성 마켓에 연결된 설정만** 반환한다(비활성/삭제된 마켓의 설정은 필터링). 변경(`changeMySettings`)도 활성 마켓 코드에 대해서만 create/update하고, 미존재/중복은 skip한다.

## 7. gRPC 계약 (`market.v1`)

proto: `protobuf/src/main/proto/market/v1/market-service.proto`. 서버 구현은 adapter-in의 두 `@GrpcService`. 소비자: `market-detection`, `notification`.

| 서비스 · RPC | 요청 | 응답 | 소비자 · 용도 |
|---|---|---|---|
| `MarketService.GetEnabledMarkets` | `GrpcGetEnabledMarketsRequest{}` | `GrpcGetEnabledMarketsResponse{repeated GrpcMarket}` | **upbit-connector**(`UpbitWebsocketTickerStreamAdapter`) — 구독할 마켓 목록 |
| `PriceAlertSettingService.FindReceiverIds` | `GrpcFindPriceAlertReceiversRequest{market_code, target_change_rate}` | `GrpcFindPriceAlertReceiversResponse{repeated receiver_ids}` | **notification**(`PriceAlertRecipientQueryAdapter`) — 알림 수신자 |

- `GrpcMarket`: `{ id, market_code, symbol, korean_name, english_name }`.
- `FindReceiverIds`: `market_code` blank 또는 `target_change_rate` blank/파싱 실패 시 `INVALID_ARGUMENT`. 수신자는 `receiver_ids`(UUID 문자열)로 반환. **`target_change_rate`는 `BigDecimal` 정확 일치**로 조회한다(설정의 `enabled=true` + 동일 rate만 매칭) — market-detection이 이산 임계 rate를 보낸다는 전제.
- 클라이언트(`GrpcMarketClient`) deadline `3500ms`. 예외는 `common-grpc`의 advice 계열.
- **계약 주의**: 이 proto는 외부 계약이다(→ market-detection·notification). field number 재사용 금지, 변경 시 server(market)·client 재빌드. 상세 절차는 `../../.claude/rules/external-contracts.md`.

## 8. 조회 캐시와 분산 무효화

활성 마켓 목록은 자주 읽히고 거의 안 바뀌므로 **로컬 Caffeine 캐시**로 서빙하고, 카탈로그가 바뀌면 **Kafka 브로드캐스트로 전 인스턴스의 로컬 캐시를 evict**한다.

- **캐시 적재**: `MarketQueryService.getMarkets()` — `@Cacheable(cacheNames="markets", key="'enabled'")`. Caffeine `maximumSize=200`, `expireAfterWrite=30일`(`CacheConfig`). 캐시 이름은 `MarketCacheNames.MARKETS` 상수.
- **무효화 발행**: `MarketCommandService.changeMarkets()`가 DB 반영 후 `MarketCatalogChangedBroadcastEvent`를 Outbox로 발행(`OutboxEventListPublishPort` → `outbox-poller` → Kafka `market-broadcast-event`). 이벤트는 `getDomainType()=MARKET`, `getDispatchType()=BROADCAST`를 override한다.
- **무효화 수신**: 각 market 인스턴스가 `marketCatalogChangedBroadcastEventConsumer`로 `market-broadcast-event`를 소비한다. consumer group이 **`market-broadcast-${app.instance-id}`(인스턴스마다 고유)** 라서 모든 인스턴스가 같은 메시지를 각자 받아 `MarketEventService.handle` → `@CacheEvict(cacheNames="markets", key="'enabled'")`로 자기 로컬 캐시를 비운다. → 로컬 캐시의 클러스터 정합성 확보.

### 컨슈머 멱등 전략

| 컨슈머 이벤트 | 하는 일 | 사용한 전략 |
|---|---|---|
| `MarketCatalogChangedBroadcastEvent` | 각 market 인스턴스의 활성 마켓 Caffeine 캐시 무효화 | 동일 cache key 반복 eviction을 허용하는 자연 멱등 연산 |

이 consumer는 인스턴스별 고유 Kafka group을 사용하므로 모든 인스턴스가 같은 이벤트를 각각 받아야 한다. 따라서 공유 `inbox`를 적용하면 첫 인스턴스 외의 캐시 무효화가 차단될 수 있어 사용하지 않는다. Kafka `event_id`는 추적에만 사용하고, 멱등성은 반복 eviction 자체로 확보한다.

## 9. 도메인 · 영속성 · 스키마

### 도메인 모델
- **`Market`**(`market-domain`): `id`, `marketCode`, `symbol`, `koreanName`, `englishName`, `enabled`, `createdAt`/`updatedAt`. 정적 `rehydrate(...)`만 제공(읽기 전용 복원). 생성/수정 로직은 JPA 엔티티(`JpaMarket`)가 보유한다.
- **`PriceAlertSetting`**(`market-domain`): `id`, `userPublicId`(UUID), `marketId`, `enabled`, `targetChangeRate`(BigDecimal), timestamps. `rehydrate(...)`만.
- 두 도메인 모델은 **상태 변경 메서드가 없는 얇은 모델**이고, 생성/수정(`create`/`update`)은 JPA 엔티티에 있다 — user/chat의 "도메인 메서드로 상태 변경" 패턴과 다르므로 변경 시 주의(§CODE_STYLE 5.1 대비).

### JPA 매핑 · 스키마 (`schema.sql`)
- **`JpaMarket`**(`@Table("market")`, `extends BaseEntity`): `id`(IDENTITY), `market_code`(unique `uk_markets_market_code`, len 30), `symbol`, `korean_name`, `english_name`, `enabled`. `create`/`update`/`toDomain` 보유.
- **`JpaPriceAlertSetting`**(`@Table("price_alert_setting")`): `id`(`@SnowflakeId`), `user_public_id`(BINARY(16)), `market`(`@ManyToOne(LAZY)` FK → market), `enabled`, `target_change_rate`(DECIMAL(5,4)). unique `(user_public_id, market_id)`, index `market_id`(+스키마의 `user_public_id` index), FK `fk_price_alert_setting_market`.
- 조회 최적화: `findAllByUserPublicIdWithMarket`, `findAllByUserPublicIdAndMarketCodeIn`은 `join fetch s.market`로 N+1 회피. 수신자 조회는 `findReceiverIdsByMarketCodeAndTargetChangeRate`(join + `enabled=true`).
- 시드: `schema.sql`이 `KRW-BTC/ETH/SOL/XRP/DOGE` 5종을 `INSERT ... ON DUPLICATE KEY UPDATE`로 심는다.

## 10. 트랜잭션 · Read Replica 현황

- 쓰기: `MarketCommandService.changeMarkets`, `PriceAlertSettingCommandService.changeMySettings` 각 `@Transactional`(기본 `transactionManager`). `MarketCommandService`의 Market 변경과 공용 `JpaOutbox(catalog="event")` 저장은 동일 MySQL 서버·connection을 사용해 `market.*`와 `event.outbox`에 걸친 하나의 로컬 트랜잭션으로 커밋·롤백된다. market 계정에는 `event.outbox`의 `SELECT, INSERT` 권한이 필요하다.
- `DatasourceConfig`: `spring.datasource.write`(mysql-primary)·`spring.datasource.read`(mysql-replica) 두 `HikariDataSource`를 만들고, `ReplicationRoutingDataSource`(`WRITE`/`READ` 라우팅) → `LazyConnectionDataSourceProxy`(`@Primary`)로 EMF에 바인딩한다. `JpaTransactionManager("transactionManager")`.
- `MarketQueryService.getMarkets()`의 `@ReadReplica`는 이제 실제로 동작한다: `ReadReplicaAspect`가 read 스코프를 세팅하고, lazy proxy가 statement 시점에 `ReplicationRoutingDataSource`를 통해 read 노드로 라우팅한다(단, `@Cacheable` 캐시 히트 시에는 DB 자체를 타지 않는다). 이미 write 트랜잭션이 활성이면 write 우선(`ReadReplicaAspect`).

## 11. 검증 · 예외

- 요청 검증: `PriceAlertSettingChangeRequest`의 `@Valid` 중첩 + `@NotBlank`/`@NotNull`/`@DecimalMin`/`@DecimalMax`. REST 응답 형식은 `common-web/GlobalExceptionHandler`가 관장.
- gRPC 검증: `GrpcPriceAlertSettingService`가 blank/파싱 실패를 `Status.INVALID_ARGUMENT`로 직접 반환.
- 예외: `MarketException`(base) / `MarketPersistException`(Outbox 발행 실패 등). `updateMarkets`에서 일부 대상 미존재 시 `IllegalArgumentException`.

## 12. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. market 관련 항목:

- **TODO 2.4** — `MarketCommandUseCase.changeMarkets`(카탈로그 쓰기 + `market-broadcast-event` 캐시 무효화)가 **인바운드 어댑터에 연결되어 있지 않다**(REST/gRPC/Kafka 트리거 부재). 현재 카탈로그는 `schema.sql` 시드로만 채워진다. 관리 엔드포인트 도입 여부/현황 확인 필요.
- **게이트웨이 라우트·인가(해결됨)** — 과거 `GET /markets`·`GET`·`PUT /price-alerts/me`가 게이트웨이 라우트·인가 부재로 `denyAll`이었다. 이제 `ReactiveRouteConfig.marketRoutes`(`lb://market-service`, rewrite `/api/v1/${seg}`) + `ReactiveSecurityConfig`(`GET /markets` permitAll, `/price-alerts/**` `hasRole(USER)`)로 노출·보호된다.

## 13. 테스트 현황

- application: `MarketCommandServiceTest`, `PriceAlertSettingCommandServiceTest`, `PriceAlertSettingQueryServiceTest`
- adapter-in: `PriceAlertSettingControllerWebMvcTest`
- adapter-out: `JpaMarketAdapterTest`, `JpaPriceAlertSettingAdapterTest`

(세부 내용은 이 문서 검증 범위 밖. 필요 시 파일을 직접 확인한다.)

## 14. 컴파일 · 테스트 · CI 명령

- 컴파일(가장 좁게): `./gradlew :market:market-application:compileJava` 등 서브모듈 단위.
- 서브모듈 테스트: `./gradlew :market:market-application:test`, `:market:market-adapter-in:test`, `:market:market-adapter-out:test`.
- 서비스 CI(빌드+테스트+ArchUnit): `./gradlew marketCi`.
- 집계 `:market:test`는 대체로 빈 task다 — 서브모듈 또는 `marketCi`로 실행한다.
- 전체 build/test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 15. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `protobuf/.../market/v1/market-service.proto` | gRPC 외부 계약. 변경 시 market-detection·notification 재빌드 |
| `common-core/KafkaTopic`(`MARKET_CATALOG_CHANGED_BROADCAST`) · `market-service.yml` stream 바인딩 | 캐시 무효화 브로드캐스트 계약 |
| `schema.sql` | DB 스키마·unique(`uk_markets_market_code`, price_alert 복합 unique)·FK·시드 |
| `JpaMarket`/`JpaPriceAlertSetting` 매핑 | 인덱스·FK·precision(DECIMAL(5,4)) |
| `MarketQueryService`/`CacheConfig`/`MarketEventService` | 캐시 적재·무효화 정합성 |
| `git-config-repo/dynamic/market-service.yml` | REST 경로·포트·DB·Kafka. 게이트웨이 route와 함께 봐야 함 |

## 16. 관련 문서와 rules

- 루트 구조/흐름: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), 코드 스타일 [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 소비자 서비스: `upbit-connector`(활성 마켓 구독 조회), `notification`(알림 수신자 조회) — 상세는 [`UPBIT_CONNECTOR.md`](UPBIT_CONNECTOR.md), [`NOTIFICATION.md`](NOTIFICATION.md)
- 계약/보안/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,security,architecture,testing}.md`
