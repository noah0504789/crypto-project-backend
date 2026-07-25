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

Upbit 실시간 시세를 수집해 **단기 이동평균 대비 변동률**을 계산하고, 임계값(3%/5%/7%)을 넘으면 가격 알림 탐지 이벤트(`PriceAlertDetectedEvent`)를 발행하는 스트림 처리 서비스. 발행 이벤트는 `notification`이 소비해 사용자 알림으로 만든다(→ [`NOTIFICATION.md`](NOTIFICATION.md)).

- 저장소(DB) 없음. 상태는 Kafka Streams **WindowStore**(로컬 state store)로만 유지한다.
- REST/gRPC 서버를 노출하지 않는다. market gRPC를 부르는 **클라이언트**로만 쓴다(구독 대상 마켓 조회).

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:market-detection:*`. **축소형 모듈** — 헥사고날 계층 분리 없이 `-bootstrap`(실행) + `-contract`(발행 이벤트 계약) 2개뿐이다. 실행 모듈 `:market-detection:market-detection-bootstrap`(`ext.dockerImageName = "crypto-market-detection"`).
- 실행 클래스: `org.example.marketdetection.Main`(`@SpringBootApplication` + `@ConfigurationPropertiesScan`, 컴포넌트 스캔은 기본값 `org.example.marketdetection`).
- app name: `market-detection`. 포트 `8500`(server.port만, 컨텍스트 경로 없음).
- 핵심 라이브러리: OkHttp(WebSocket 클라이언트), `spring-cloud-stream-binder-kafka-streams`(Kafka Streams), `market-client`(gRPC), `spring-cloud-starter-bus-kafka`.
- Config Server 연동: `spring.cloud.config.name: market-detection,eureka-client,kafka,monitoring`.
- Kafka 트랜잭션: `transaction-id-prefix: tx-market-${app.instance-id}`. 브로커 공통 설정(idempotence, acks=all, `isolation.level: read_committed`, native enc/dec)은 `infrastructure/kafka.yml`.

## 4. 데이터 흐름

### 4.1 수집 (Upbit WebSocket → 큐)

- `UpbitWebsocketClientStarter`가 `ApplicationReadyEvent`에서 OkHttp WebSocket(`wss://api.upbit.com/websocket/v1`)을 연다.
- `UpbitWebsocketListener.onOpen` → `UpbitWebsocketService.subscribe`로 **구독 코드 목록**을 보낸다. 구독 코드는 **market gRPC `getEnabledMarkets`**(→ `market.v1`)에서 가져온다(활성 마켓만; 비면 `IllegalStateException`).
- `onMessage` → `UpbitWebsocketService.deserialize`가 `type=ticker`만 `UpbitTickerEvent`로 변환.
- **스로틀링**: 코드별 발행 간격 `ticker-publish-interval`(3s). `tryUpdateTickerLastSent`가 `ConcurrentMap<code, AtomicLong>` + CAS로 간격 미만 이벤트를 버린다.
- **백프레셔**: 유계 큐 `LinkedBlockingQueue`(capacity 100). 가득 차면 `offer` 실패 → 드롭(warn 로그).

### 4.2 발행 (Supplier → `upbit-ticker-event`)

- `upbitTickerEventSupplier`(Spring Cloud Stream `Supplier`)가 **poller 0.5s**(`fixed-delay 500ms`)마다 큐를 `poll`해 `UpbitTickerEvent`를 발행한다. 파티션 키 = 마켓 코드(`KafkaEvent.toMessage()` → `KafkaHeaders.KEY`). 목적지 바인딩 `upbitTickerEventSupplier-out-0` → `upbit-ticker-event`.

### 4.3 처리 (Kafka Streams → 임계 탐지 → `price-alert-detected-event`)

- `upbitTickerAlertEventConsumer`(`Consumer<KStream<String, UpbitTickerEvent>>`)가 KStream을 `UpbitTickerProcessor`로 `process`한다. 입력 바인딩 `upbitTickerAlertEventConsumer-in-0` → **`upbit-ticker-event`**(Supplier 출력과 동일 토픽), group `upbit-ticker-alert`.
- `UpbitTickerProcessor`(state store `upbit-ticker-store`, persistent WindowStore, retention/window `3m`):
  1. 윈도우 `[timestamp - 3m, timestamp]`의 저장 시세로 **이동평균** 계산(없으면 현재가로 fallback).
  2. `changeRate = (current - avg) / avg`.
  3. 현재 시세를 store에 `put`.
  4. `PriceAlertChangeRateThreshold.matchedBy(changeRate)`로 **초과한 임계값 전부**(절대값 기준 3%/5%/7%) 매칭.
  5. 매칭된 임계값마다 `PriceAlertDetectedEvent`(code·price·timestamp·avgInterval(=windowMinutes 3)·avgPrice·changeRate·threshold enum명)를 `StreamBridge.send("price-alert-detected-event-out", ...)`로 발행.
- 소비자: `notification`(`price-alert-detected-event`).

### 4.4 흐름도

```
Upbit WS ─(ticker)→ Listener(구독=market gRPC, 3s 스로틀, 큐 100)
   → Supplier(0.5s poll) → Kafka: upbit-ticker-event
   → KStream(upbit-ticker-event) → UpbitTickerProcessor
        WindowStore(3m) 이동평균·변동률 → 임계 매칭(3/5/7%)
   → Kafka: price-alert-detected-event → [notification]
```

## 5. 계약

- **생산(외부 계약)**: `market-detection-contract`의 `PriceAlertDetectedEvent`(record, `implements KafkaEvent, ProducibleEvent`). 필드 `{ code, price, timestamp, avgInterval, avgPrice, changeRate, threshold }`, 토픽 `PRICE_ALERT_DETECTED`(binding `price-alert-detected-event-out` → `price-alert-detected-event`), 파티션 키 = `code`. `toPayload()`는 `PriceAlertDetectedPayloadKeys`(TypedKey)로 키-값 페이로드를 만든다(notification이 web push payload로 전달). 소비자 `notification`과 함께 변경한다(→ `../../.claude/rules/external-contracts.md`).
- **소비(외부)**: market `market.v1 GetEnabledMarkets`(구독 대상). 임계값 enum `common-core/PriceAlertChangeRateThreshold`(`PERCENT_3/5/7`)는 market-detection(탐지)·notification(수신자 조회 rate 변환)·market(정확 일치 조회)이 **공유하는 계약**이다.
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
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
