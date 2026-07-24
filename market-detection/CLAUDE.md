# market-detection — 모듈 작업 지침

이 파일은 `market-detection/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/MARKET_DETECTION.md`](../docs/modules/MARKET_DETECTION.md)를 참고한다.

Kafka Streams·토픽 바인딩 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

Upbit 실시간 시세를 수집해 이동평균 대비 변동률을 계산하고, 임계값(3/5/7%) 초과 시 `PriceAlertDetectedEvent`를 발행하는 스트림 처리 서비스(**축소형** — `-bootstrap` + `-contract` 2개, 헥사고날 계층 없음, 실행 모듈 `market-detection-bootstrap`).

- DB 없음. 상태는 Kafka Streams WindowStore(`upbit-ticker-store`)로만.
- REST/gRPC 서버 노출 없음. market gRPC 클라이언트로만 구독 대상 마켓을 조회한다.
- 발행 이벤트는 `notification`이 소비한다. 변동률→알림 변환·전송은 이 모듈이 아니다.

`market-detection/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **발행 계약(`PriceAlertDetectedEvent`) 보존**: `market-detection-contract`의 record는 `notification`이 소비하는 외부 계약이다. 필드/토픽(`price-alert-detected-event`)/`PriceAlertDetectedPayloadKeys` 변경은 notification과 함께(external-contracts 절차).
- **공유 임계값 계약**: `common-core/PriceAlertChangeRateThreshold`(`PERCENT_3/5/7`)는 탐지(여기)·수신자 조회(notification)·정확 일치 조회(market)가 공유한다. rate 값/enum명을 바꾸면 세 서비스를 함께 본다.
- **Streams 토폴로지 주의**: 처리 로직은 `UpbitTickerProcessor`(WindowStore 이동평균·변동률)와 `StateStoreConfig`(store retention/window)에 있다. window/retention(`upbit.store.ticker`, `upbit.ticker.alert.window-minutes`)을 바꾸면 산식·상태 크기에 직접 영향. state store 이름 변경은 기존 changelog 토픽 호환을 함께 본다.
- **토픽 바인딩은 계약**: `git-config-repo/dynamic/market-detection.yml`의 `spring.cloud.stream.bindings`(supplier out `upbit-ticker-event`, streams in `upbit-ticker-alert-event`, `price-alert-detected-event-out`)·poller(0.5s)·트랜잭션(`tx-market-*`). **주의: supplier 출력 토픽과 Streams 입력 토픽이 다르고 연결이 확인되지 않는다(§확인 필요) — 토픽명을 만지기 전에 실제 토폴로지를 먼저 확인**한다.
- **수집 안정성 유지**: `UpbitWebsocketListener`의 코드별 스로틀(`ticker-publish-interval`)과 유계 큐(`ticker-queue-capacity`, 드롭 정책)는 Upbit 폭주 대비 백프레셔다. 임의로 무제한 큐/무스로틀로 바꾸지 않는다. 구독 코드는 market gRPC `getEnabledMarkets` 결과에 의존한다.
- **KafkaEvent 발행 규약**: 이벤트는 `KafkaEvent.toMessage()`(파티션 키=code)로 발행하고 목적지는 바인딩이 결정한다. `toMessage()`는 `KafkaHeaders.TOPIC`을 세팅하지 않으니, 목적지 변경은 바인딩에서 한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`.../upbit/UpbitWebsocketClientStarter.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/upbit/UpbitWebsocketClientStarter.java) | ApplicationReady 시 WS 연결 |
| [`.../upbit/UpbitWebsocketListener.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/upbit/UpbitWebsocketListener.java) | 구독(market gRPC)·스로틀·큐 |
| [`.../upbit/UpbitWebsocketService.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/upbit/UpbitWebsocketService.java) | 구독 요청·역직렬화 |
| [`.../adapter/in/stream/KafkaMarketDetectionBinder.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/adapter/in/stream/KafkaMarketDetectionBinder.java) | supplier + KStream consumer 빈 |
| [`.../upbit/UpbitTickerProcessor.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/upbit/UpbitTickerProcessor.java) | WindowStore 변동률·임계 탐지·발행 |
| [`.../infra/config/StateStoreConfig.java`](market-detection-bootstrap/src/main/java/org/example/marketdetection/infra/config/StateStoreConfig.java) | WindowStore 정의 |
| [`market-detection-contract/.../PriceAlertDetectedEvent.java`](market-detection-contract/src/main/java/org/example/marketdetection/contract/event/PriceAlertDetectedEvent.java) | 발행 계약(→ notification) |
| `../git-config-repo/dynamic/market-detection.yml` | Streams 바인딩·토픽·poller·store·트랜잭션 |

## 검증 명령

- 컴파일: `./gradlew :market-detection:market-detection-bootstrap:compileJava`
- 테스트: `./gradlew :market-detection:market-detection-bootstrap:test`(`TopologyTestDriver` 포함)
- 서비스 CI: `./gradlew marketDetectionCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/MARKET_DETECTION.md §6`](../docs/modules/MARKET_DETECTION.md)와 [`../TODO.md`](../TODO.md).

- supplier 출력(`upbit-ticker-event`)과 Streams 입력(`upbit-ticker-alert-event`) 토픽 연결 미확인
- `cd.yml` 배포 대상에 market-detection 누락(TODO 4.1)
