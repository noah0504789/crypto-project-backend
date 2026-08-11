# MARKET_DETECTION — market-detection 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-25
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - Kafka Streams 바인딩·토픽(`market-detection.yml`의 `spring.cloud.stream.*`) 변경
>   - 생산 계약(`market-detection-contract`의 `PriceAlertDetectedEvent`) 변경
>   - Upbit 수집·처리 로직(`UpbitWebsocketListener`, `UpbitWebsocketService`, `UpbitTickerProcessor`) 변경
>   - 임계값(`common-core/PriceAlertChangeRateThreshold`)·WindowStore(`StateStoreConfig`, `UpbitProperties`) 변경
>   - market gRPC 클라이언트(`MarketClient.getEnabledMarkets`) 사용 변경

## 1. 문서 목적과 기준 시점

이 문서는 `market-detection` 서비스의 구조·데이터 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../market-detection/CLAUDE.md`](../../market-detection/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

Upbit 실시간 시세를 수집해 **단기 이동평균 대비 변동률**을 계산하고, 임계값(0%/3%/5%/7%)을 넘으면 가격 알림 탐지 이벤트(`PriceAlertDetectedEvent`)를 발행하는 스트림 처리 서비스. 발행 이벤트는 `notification`이 소비해 사용자 알림으로 만든다(→ [`NOTIFICATION.md`](NOTIFICATION.md)).

- 저장소(DB) 없음. 상태는 Kafka Streams **WindowStore**(로컬 state store)로만 유지한다.
- REST/gRPC 서버를 노출하지 않는다. market gRPC를 부르는 **클라이언트**로만 쓴다(구독 대상 마켓 조회).

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:market-detection:*`. **축소형 모듈** — 헥사고날 계층 분리 없이 `-bootstrap`(실행) + `-contract`(발행 이벤트 계약) 2개뿐이다. 실행 모듈 `:market-detection:market-detection-bootstrap`(`ext.dockerImageName = "crypto-market-detection"`).
- 실행 클래스: `org.example.marketdetection.Main`(`@SpringBootApplication` + `@ConfigurationPropertiesScan`, 컴포넌트 스캔은 기본값 `org.example.marketdetection`).
- app name: `market-detection`. 포트 `8500`(server.port만, 컨텍스트 경로 없음).
- 핵심 라이브러리: OkHttp(WebSocket 클라이언트), `spring-cloud-stream-binder-kafka-streams`(Kafka Streams), `market-client`(gRPC), `spring-cloud-starter-bus-kafka`.
- Config Server 연동: `spring.cloud.config.name: market-detection,eureka-client,kafka,monitoring`.
- DB가 없는데도 발행 계약(`market-detection-contract`의 `PriceAlertDetectedEvent`가 `AbstractInboxEvent` 상속 → `common-inbox` → `common-jpa`)이 `spring-boot-starter-data-jpa`를 **전이로** classpath에 끌어온다. 그대로 두면 `DataSourceAutoConfiguration`이 강제 활성화돼 datasource url 없이 부팅이 깨진다. 그래서 `market-detection.yml`에서 `spring.autoconfigure.exclude`로 `DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`을 제외한다. **이 제외를 지우면 부팅이 실패한다.**
- 컴포넌트 스캔: `Main`은 `@ComponentScan(basePackages="org.example")` + `@ConfigurationPropertiesScan(basePackages="org.example")`로 common 빈을 넓게 스캔한다. 다만 `common-inbox`의 `InboxService`(JPA Repository 요구) 등 영속 서비스 빈은 이 서비스가 쓰지 않으므로 `org.example.common.(outbox|dlq|inbox).*`를 `excludeFilters`로 제외한다. Inbox 멱등 영속은 소비자(notification)의 몫이고, 이 모듈은 이벤트를 발행만 한다. **이 필터를 지우면 스캔된 서비스 빈이 JPA Repository를 요구해 부팅이 실패한다.**
- Kafka Streams 처리 보장: `processing.guarantee: exactly_once_v2`. 입력 offset, WindowStore 변경, 탐지 이벤트 출력을 하나의 Kafka 트랜잭션으로 처리한다. 브로커 공통 설정(idempotence, acks=all, `isolation.level: read_committed`, native enc/dec)은 `infrastructure/kafka.yml`.

## 4. 데이터 흐름

### 4.1 수집 (Upbit WebSocket → 큐)

- `UpbitWebsocketClientStarter`가 `ApplicationReadyEvent`에서 공통 `application.yml`의 `uri.provider.upbit.websocket`으로 설정된 OkHttp WebSocket을 연다.
- `UpbitWebsocketListener.onOpen` → `UpbitWebsocketService.subscribe`로 **구독 코드 목록**을 보낸다. 구독 코드는 **market gRPC `getEnabledMarkets`**(→ `market.v1`)에서 가져온다(활성 마켓만; 비면 `IllegalStateException`).
- `onMessage` → `UpbitWebsocketService.deserialize`가 `type=ticker`만 `UpbitTickerEvent`로 변환.
- **스로틀링**: 코드별 발행 간격 `ticker-publish-interval`(10s). `tryUpdateTickerLastSent`가 `ConcurrentMap<code, AtomicLong>` + CAS로 간격 미만 이벤트를 버린다.
- **백프레셔**: 유계 큐 `LinkedBlockingQueue`(capacity 100). 가득 차면 `offer` 실패 → 드롭(warn 로그).

### 4.2 발행 (Supplier → `upbit-ticker-event`)

- `upbitTickerEventSupplier`(Spring Cloud Stream `Supplier`)가 **poller 0.5s**(`fixed-delay 500ms`)마다 큐를 `poll`해 `UpbitTickerEvent`를 발행한다. 파티션 키 = 마켓 코드(`KafkaEventFactory.createEventMessage(...)` → `KafkaHeaders.KEY`). 목적지 바인딩 `upbitTickerEventSupplier-out-0` → `upbit-ticker-event`.

### 4.3 처리 (Kafka Streams → 임계 탐지 → `price-alert-detected-event`)

- `upbitTickerAlertEventProcessor`(`Function<KStream<String, UpbitTickerEvent>, KStream<String, PriceAlertDetectedEvent>>`)가 KStream을 `UpbitTickerProcessor`로 `process`한다. 입력 바인딩 `upbitTickerAlertEventProcessor-in-0` → **`upbit-ticker-event`**(Supplier 출력과 동일 토픽), group `upbit-ticker-alert`.
- `UpbitTickerProcessor`(state store `upbit-ticker-store`, persistent WindowStore, retention/window `3m`):
  1. Upbit `tradeTimestamp`(없으면 Kafka record timestamp)가 현재 시각보다 `max-event-age`(10s) 초과해 오래된 이벤트면 상태 저장과 알림 발행 없이 폐기한다.
  2. 윈도우 `[timestamp - 3m, timestamp]`의 저장 시세로 **이동평균** 계산(없으면 현재가로 fallback).
  3. `changeRate = (current - avg) / avg`.
  4. 현재 시세를 store에 `put`.
  5. `PriceAlertChangeRateThreshold.matchedBy(changeRate)`로 **초과한 임계값 전부**(절대값 기준 0%/3%/5%/7%) 매칭.
  6. 매칭된 임계값마다 `PriceAlertDetectedEvent`(code·price·timestamp·avgInterval(=windowMinutes 3)·avgPrice·changeRate·threshold enum명)를 processor context로 forward한다.
  7. 출력 KStream 바인딩 `upbitTickerAlertEventProcessor-out-0`이 `price-alert-detected-event`로 발행한다. WindowStore 갱신·출력 레코드·입력 offset은 Kafka Streams EOS 트랜잭션으로 함께 커밋된다.
- 소비자: `notification`(`price-alert-detected-event`).

0% 임계값은 `abs(changeRate) >= 0.0`이므로 처리 가능한 모든 ticker에서 감지 이벤트를 만든다. 실제 사용자 알림은 notification이 market에 `종목 + 0%`를 설정한 수신자를 조회한 뒤 해당 사용자에게만 생성한다.

### 4.4 흐름도

```
Upbit WS ─(ticker)→ Listener(구독=market gRPC, 10s 스로틀, 큐 100)
   → Supplier(0.5s poll) → Kafka: upbit-ticker-event
   → KStream(upbit-ticker-event) → UpbitTickerProcessor
        10s 초과 stale 폐기 → WindowStore(3m) 이동평균·변동률 → 임계 매칭(0/3/5/7%)
   → Kafka: price-alert-detected-event → [notification]
```

### 4.5 Kafka Streams EOS 적용 배경과 트랜잭션 경계

이 모듈은 Outbox relay와 달리 Kafka 레코드를 소비해 상태 저장소를 갱신하고 다시 Kafka 레코드를 만드는 전형적인 **Consume–Process–Produce** 구조다. 입력 처리는 다음 세 결과를 하나의 논리적 작업으로 만든다.

1. `upbit-ticker-event` 입력 offset 진행
2. `upbit-ticker-store` WindowStore 및 changelog 변경
3. 하나 이상의 `PriceAlertDetectedEvent` 출력

EOS 적용 전에는 `Consumer<KStream<...>>` 내부의 `UpbitTickerProcessor`가 `StreamBridge.send`를 부수 효과로 호출했다. 이 방식은 출력이 Kafka Streams 토폴로지 밖에서 발생하므로, 일반 binder의 `transaction-id-prefix`가 설정되어 있어도 Streams의 입력 offset·state store·출력을 하나의 트랜잭션으로 만들지 못한다. 예를 들어 출력은 성공했지만 offset commit 전에 장애가 발생하면 같은 입력을 다시 처리해 탐지 이벤트가 중복될 수 있다.

현재는 다음과 같이 구성한다.

- binder 빈을 `Function<KStream<String, UpbitTickerEvent>, KStream<String, PriceAlertDetectedEvent>>`로 정의해 출력도 Streams 토폴로지에 포함한다.
- `UpbitTickerProcessor`는 `StreamBridge` 대신 `ProcessorContext.forward`로 탐지 이벤트를 반환한다. 한 ticker가 0%·3%·5%·7%를 모두 충족해 여러 이벤트를 만들더라도 같은 입력 처리 트랜잭션에 포함된다.
- `spring.cloud.stream.kafka.streams.binder.configuration.processing.guarantee=exactly_once_v2`를 사용한다.
- 일반 binder의 `spring.cloud.stream.kafka.binder.transaction.transaction-id-prefix`는 사용하지 않는다. Kafka Streams가 application/task 기준의 transactional producer와 transaction ID를 내부적으로 관리한다.

정상 처리와 장애 처리는 다음과 같다.

```text
입력 poll
 → WindowStore 갱신
 → PriceAlertDetectedEvent forward
 → 출력 레코드 + state changelog + 입력 offset을 Kafka transaction으로 commit
```

처리 도중 예외나 인스턴스 장애가 발생하면 Kafka transaction이 abort된다. `isolation.level=read_committed` consumer에게 abort된 출력은 보이지 않고, commit되지 않은 offset의 입력은 다시 처리된다. state store도 changelog를 기준으로 commit된 상태와 일치하도록 복구된다.

| 범위 | EOS가 보장하는 것 | EOS가 보장하지 않는 것 |
|---|---|---|
| `upbit-ticker-event` → Streams 처리 → `price-alert-detected-event` | 입력 offset, WindowStore/changelog, 출력 레코드의 Kafka 원자성 | 외부 DB·gRPC·HTTP side effect |
| 한 ticker에서 여러 임계값 이벤트 생성 | 출력 이벤트들을 같은 Streams transaction에 포함 | notification consumer의 MongoDB/Outbox 처리 |
| 장애 후 재처리 | abort된 출력 비노출, commit 전 입력 재처리 | 시스템 전체의 end-to-end exactly-once |
| Upbit queue Supplier → `upbit-ticker-event` | 단일 Kafka produce에 공통 producer idempotence 적용 | Supplier 발행과 이후 Streams 처리까지 하나로 묶는 트랜잭션 |

따라서 이 모듈에서 말하는 exactly-once는 **Kafka Streams 처리 구간의 EOS**다. 하류 notification이 수행하는 MySQL Outbox 저장이나 MongoDB 반영까지 포함하는 분산 트랜잭션은 아니며, 하류 consumer의 멱등성·재시도·DLQ는 별도로 필요하다.

### 컨슈머 멱등 전략

| 컨슈머 이벤트 | 하는 일 | 사용한 전략 |
|---|---|---|
| `UpbitTickerEvent` | WindowStore에 시세를 기록하고 이동평균·임계값을 계산해 `PriceAlertDetectedEvent` 발행 | Kafka Streams `exactly_once_v2`로 입력 offset·WindowStore·출력을 하나의 Kafka 트랜잭션으로 처리 |

탐지 결과의 `eventId`는 최초 `PriceAlertDetectedEvent` 생성 시 무작위 UUID로 한 번 생성해 Kafka `event_id` 헤더에만 전달한다. payload에서는 `@JsonIgnore`로 제외하며 Kafka 재전달에서는 header의 같은 ID가 보존되므로 하류 notification Inbox가 중복 알림 생성을 차단한다. 동일 입력이 별도의 새 이벤트로 다시 생성되면 새 ID를 갖는 별개 이벤트로 취급한다. 이 ID는 순서 제어용이 아니며, ticker 순서는 Kafka key인 market code로 같은 파티션에 모은다.

EOS의 트레이드오프는 다음과 같다.

| 선택 | 장점 | 비용·주의점 |
|---|---|---|
| at-least-once + 외부 `StreamBridge` | 구현 단순, transaction overhead 없음 | offset/state/output 사이 부분 성공과 중복 가능 |
| Kafka Streams `exactly_once_v2` | Consume–Process–Produce와 state store를 원자화, abort 레코드 격리 | transaction commit 비용으로 지연·처리량 부담, broker/transaction timeout·producer fencing 운영 고려 |
| Outbox 도입 | 외부 DB 상태와 이벤트 의도를 DB transaction으로 보존 가능 | 이 모듈은 DB가 없고 Kafka state store가 기준이므로 별도 DB/poller 운영 비용이 과함 |

market-detection은 stateful Kafka 처리 결과가 곧 Kafka 출력이고 외부 DB 변경이 없으므로, Outbox보다 Kafka Streams EOS가 문제와 경계에 직접 맞는다.

## 5. 계약

- **생산(외부 계약)**: `market-detection-contract`의 `PriceAlertDetectedEvent`(`AbstractInboxEvent` 상속, `implements KafkaEvent, ProducibleEvent`). 내부 `eventId`는 JSON에서 제외하고 Kafka header로 전달하며, payload는 `{ code, price, timestamp, avgInterval, avgPrice, changeRate, threshold }`로 구성한다. 토픽은 `PRICE_ALERT_DETECTED`(binding `upbitTickerAlertEventProcessor-out-0` → `price-alert-detected-event`), 파티션 키는 `code`다. `toPayload()`는 `PriceAlertDetectedPayloadKeys`(TypedKey)로 키-값 페이로드를 만든다(notification이 web push payload로 전달). 소비자 `notification`과 함께 변경한다(→ `../../.claude/rules/external-contracts.md`).
- **소비(외부)**: market `market.v1 GetEnabledMarkets`(구독 대상). 임계값 enum `common-core/PriceAlertChangeRateThreshold`(`PERCENT_0/3/5/7`)는 market-detection(탐지)·notification(수신자 조회 rate 변환)·market(정확 일치 조회)이 **공유하는 계약**이다.
- **내부 토픽**: `upbit-ticker-event`(수집 원본 — Supplier 출력이자 Kafka Streams 입력). auto-create(`auto-create-topics: true`).

## 6. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. market-detection 관련 항목:

- **TODO 4.1**(기존) — `cd.yml` 배포 대상 드롭다운에 `market-detection` 없음(Dockerfile/이미지 존재).

## 7. 테스트 현황

- `UpbitTickerProcessorTest`, `UpbitTickerProcessorTopologyTest`(`TopologyTestDriver` 계열, `kafka-streams-test-utils`)
- `UpbitWebsocketListenerTest`, `UpbitWebsocketServiceTest`, `UpbitWebsocketServiceExternalIntegrationTest`(외부 의존 통합)
- 테스트 지원: `TestPropertiesConfig`, `TestUpbitExternalDependencyConfig`

## 8. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :market-detection:market-detection-bootstrap:compileJava`
- 테스트: `./gradlew :market-detection:market-detection-bootstrap:test`
- 서비스 CI: `./gradlew marketDetectionCi`(빌드+테스트+ArchUnit 포함)
- 전체 build/test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 9. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `market-detection-contract/.../PriceAlertDetectedEvent.java` · `PriceAlertDetectedPayloadKeys` | notification이 소비하는 발행 계약 |
| `common-core/PriceAlertChangeRateThreshold` | 3서비스 공유 임계값 계약(탐지·수신자 조회·정확 일치) |
| `UpbitTickerProcessor.java` / `StateStoreConfig.java` | 변동률 산식·WindowStore(retention/window) |
| `git-config-repo/dynamic/market-detection.yml` | Streams 바인딩·토픽·poller·store·트랜잭션 |
| `UpbitWebsocketListener.java` | 구독(market gRPC)·스로틀·큐 백프레셔 |

## 10. 관련 문서와 rules

- 루트 흐름: [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md)(§11–13 수집·처리·탐지), 구조 [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
- 상·하류: 구독 대상 [`MARKET.md`](MARKET.md)(gRPC `GetEnabledMarkets`), 소비자 [`NOTIFICATION.md`](NOTIFICATION.md)
- 계약/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,architecture,testing}.md`
