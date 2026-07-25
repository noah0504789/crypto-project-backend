# market — 모듈 작업 지침

이 파일은 `market/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/MARKET.md`](../docs/modules/MARKET.md)를 참고한다.

## 모듈 역할과 적용 범위

마켓 카탈로그·가격알림 설정의 소유 서비스(헥사고날 멀티모듈, 실행 모듈 `market-bootstrap`). 두 서브도메인 `market`(카탈로그)·`price-alert-setting`을 담당한다:

1. 활성 마켓 목록 조회(REST `GET /markets`, gRPC `GetEnabledMarkets`)
2. 내 가격알림 설정 조회/변경(REST `GET`·`PUT /price-alerts/me`)
3. 알림 수신자 조회(gRPC `FindReceiverIds`)
4. 카탈로그 변경 시 캐시 무효화 브로드캐스트(`market-broadcast-event`)

실제 변동률 탐지·알림 발송은 이 모듈이 아니다(`market-detection`, `notification`). market은 그들이 gRPC로 부르는 **카탈로그/설정 저장소**다. `market/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **의존 방향 유지**: adapter-in/out → application → domain. `market-domain`은 프레임워크 비의존(코어만). 서비스에서 JPA Repository를 직접 주입하지 않고 `*PersistencePort`(`JpaMarketAdapter`/`JpaPriceAlertSettingAdapter`) 경유(→ `../.claude/rules/architecture.md`).
- **Command/Query 분리 유지**: `MarketCommandService`/`MarketQueryService`, `PriceAlertSettingCommandService`/`PriceAlertSettingQueryService`. UseCase 인터페이스로만 어댑터가 호출한다.
- **도메인 vs JPA 엔티티 역할 주의**: 이 모듈의 도메인 모델(`Market`/`PriceAlertSetting`)은 `rehydrate`만 있는 **얇은 읽기 모델**이고, 생성/수정 로직은 JPA 엔티티(`JpaMarket.create/update`, `JpaPriceAlertSetting.create/update`)에 있다. user/chat과 배치가 다르니 기존 패턴을 따른다(무리하게 도메인으로 옮기지 않는다). 상태 변경은 엔티티 `update()` 메서드로 하고 public setter를 열지 않는다.
- **캐시 무효화 흐름 보존**: 활성 마켓은 Caffeine `@Cacheable("markets", key 'enabled')`로 서빙하고, 카탈로그 변경은 `MarketCatalogChangedEvent`(Outbox → `market-broadcast-event`)로 발행 → 각 인스턴스가 `marketEventConsumer`(group `market-broadcast-${app.instance-id}`, 인스턴스마다 고유)로 받아 `@CacheEvict`한다. 이 **per-instance 그룹**을 공유 그룹으로 바꾸면 일부 인스턴스만 evict되어 캐시 불일치가 난다 — 변경 금지(→ `../.claude/rules/external-contracts.md`). 캐시 이름은 `MarketCacheNames.MARKETS` 상수로만.
- **Outbox 발행 보존**: 카탈로그 쓰기는 `OutboxEventListPublishPort.publish`로 발행한다. `MarketCatalogChangedEvent`는 `getDomainType()=MARKET`을 override한다(공용 기본값 `CHAT` 방지) — 새 이벤트 추가 시 domainType override를 잊지 않는다.
- **gRPC 계약(`market.v1`) 변경은 external-contracts 절차**: `../protobuf/.../market/v1/market-service.proto` 변경 시 소비자(`market-detection`, `notification`)를 함께 재빌드하고 field number 재사용을 금지한다. proto 재생성: `./gradlew :protobuf:build`. `FindReceiverIds`의 `target_change_rate`는 문자열로 전달되어 `BigDecimal` 정확 일치로 조회되니, 정밀도(스키마 `DECIMAL(5,4)`)와 매칭 규칙을 함께 본다.
- **스키마·인덱스는 계약**: `market-bootstrap/.../sql/schema.sql`의 unique(`uk_markets_market_code`, `uk_price_alert_setting_user_public_id_market_id`)·FK·시드(5종 마켓)를 영향 분석 없이 바꾸지 않는다. `user_public_id`는 `BINARY(16)`(UUID), `target_change_rate`는 `DECIMAL(5,4)`.
- **REST 경로·포트·DB 설정은 원격 Config**: `../git-config-repo/dynamic/market-service.yml`(포트 REST 8200/gRPC 18200, `mysql.market.*`, stream 바인딩). 경로를 바꾸면 게이트웨이 route/security와 함께 검토한다.
- **X-User-Id 신뢰**: `PriceAlertSettingController`는 게이트웨이가 넣는 `X-User-Id`를 `UUID`(publicId)로 그대로 신뢰한다. 헤더 소비/신뢰 방식 변경은 게이트웨이와 함께 본다(→ `../.claude/rules/security.md`, `../docs/modules/API_GATEWAY.md`).
- **Read Replica 배선 유지**: `DatasourceConfig`가 write(mysql-primary)/read(mysql-replica) 2 Hikari + `ReplicationRoutingDataSource` + `LazyConnectionDataSourceProxy`(`@Primary`)로 EMF를 구성한다. `MarketQueryService.getMarkets()`의 `@ReadReplica`가 이 배선으로 실제 read 노드로 라우팅된다(lazy proxy가 statement 시점에 결정). 라우팅 로직 자체는 `common-jpa` 소관 — 데이터소스/트랜잭션 경계 변경 시 함께 본다(→ `../.claude/rules/architecture.md`).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`market-adapter-in/.../web/MarketController.java`](market-adapter-in/src/main/java/org/example/market/adapter/in/web/MarketController.java) | `GET /markets` |
| [`market-adapter-in/.../web/PriceAlertSettingController.java`](market-adapter-in/src/main/java/org/example/market/adapter/in/web/PriceAlertSettingController.java) | 내 설정 조회/변경 |
| [`market-adapter-in/.../grpc/GrpcMarketService.java`](market-adapter-in/src/main/java/org/example/market/adapter/in/grpc/GrpcMarketService.java) | gRPC `GetEnabledMarkets` |
| [`market-adapter-in/.../grpc/GrpcPriceAlertSettingService.java`](market-adapter-in/src/main/java/org/example/market/adapter/in/grpc/GrpcPriceAlertSettingService.java) | gRPC `FindReceiverIds` |
| [`market-application/.../service/MarketQueryService.java`](market-application/src/main/java/org/example/market/application/service/MarketQueryService.java) | 활성 마켓 조회(`@ReadReplica`+`@Cacheable`) |
| [`market-application/.../service/MarketCommandService.java`](market-application/src/main/java/org/example/market/application/service/MarketCommandService.java) | 카탈로그 변경 + Outbox 발행 |
| [`market-application/.../service/MarketEventService.java`](market-application/src/main/java/org/example/market/application/service/MarketEventService.java) | 카탈로그 변경 이벤트 → `@CacheEvict` |
| [`market-application/.../service/PriceAlertSettingQueryService.java`](market-application/src/main/java/org/example/market/application/service/PriceAlertSettingQueryService.java) | 내 설정·수신자 조회 |
| [`market-adapter-out/.../persistence/JpaPriceAlertSettingAdapter.java`](market-adapter-out/src/main/java/org/example/market/adapter/out/persistence/JpaPriceAlertSettingAdapter.java) | 설정 영속(코드→마켓 매핑, 배치 create/update/delete) |
| [`market-adapter-out/.../infra/config/DatasourceConfig.java`](market-adapter-out/src/main/java/org/example/market/infra/config/DatasourceConfig.java) | write 데이터소스 + `transactionManager` |
| `../git-config-repo/dynamic/market-service.yml` | REST 경로·포트·DB·Kafka 설정(Config Server 원격) |
| `../protobuf/src/main/proto/market/v1/market-service.proto` | gRPC `market.v1` 계약 |
| `market-bootstrap/src/main/resources/sql/schema.sql` | 스키마·unique·FK·시드 |

## 검증 명령

- 컴파일: `./gradlew :market:market-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :market:market-adapter-in:test`, `:market:market-application:test`, `:market:market-adapter-out:test`
- 서비스 CI: `./gradlew marketCi`(빌드+테스트+ArchUnit 포함)

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/MARKET.md §12`](../docs/modules/MARKET.md)와 [`../TODO.md`](../TODO.md).

- `MarketCommandUseCase.changeMarkets`가 인바운드 어댑터에 미연결(카탈로그 쓰기 경로 미노출, 현재 시드로만 채움)
