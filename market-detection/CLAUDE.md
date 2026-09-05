# market-detection — 모듈 작업 지침

이 파일은 `market-detection/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/MARKET_DETECTION.md`](../docs/modules/MARKET_DETECTION.md)를 참고한다.

Kafka Streams·토픽 바인딩 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

Upbit 시세(`upbit-ticker-event`)를 소비해 이동평균 대비 변동률을 계산하고, 임계값(3/5/7%) 초과 시 `PriceAlertDetectedEvent`를 발행하는 **스트림 처리 전용** 서비스.

| 모듈 | 역할 |
|---|---|
| `-application` | `PriceAlertDetectionService`(port/in `PriceAlertDetectUseCase`), 계산용 `dto/PricePoint`·`dto/PriceChange`, 설정 `PriceAlertDetectionProperties` |
| `-adapter-in` | Kafka Streams 글루(`KafkaMarketDetectionBinder`, `PriceAlertDetectionProcessor`), state store 정의 |
| `-bootstrap` | `Main`만 |
| `-contract` | `PriceAlertDetectedEvent`(→ notification) |

**adapter-out은 두지 않는다.** WindowStore는 `ProcessorContext.init()`에서 얻는 Streams 토폴로지 소유물이라 스프링 빈이 아니다. 포트-아웃으로 빼면 어댑터가 어댑터를 참조하게 돼 ArchUnit에 걸린다. Streams 처리 전체를 인바운드 어댑터 하나로 본다.

**수집은 이 서비스가 하지 않는다.** Upbit WebSocket 접속·스로틀·발행은 `upbit-connector`로 이관됐다(→ `../docs/modules/UPBIT_CONNECTOR.md`). 여기에 수집 코드를 다시 넣지 않는다.

- DB 없음. 상태는 Kafka Streams WindowStore(`upbit-ticker-store`)로만.
- REST/gRPC 서버 노출 없음. 외부 시스템 접속도 없다(구독 대상 조회는 `upbit-connector` 몫).
- 발행 이벤트는 `notification`이 소비한다. 변동률→알림 변환·전송은 이 모듈이 아니다.

`market-detection/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **발행 계약(`PriceAlertDetectedEvent`) 보존**: `market-detection-contract`의 클래스는 `AbstractInboxEvent`를 상속하며 notification이 소비하는 외부 계약이다. `eventId`는 최초 이벤트 생성 시 무작위 UUID로 생성되어 Kafka `event_id` 헤더에만 전달되고 payload에서는 제외된다. notification은 header를 Inbox 식별자의 단일 기준으로 사용한다. 필드/토픽(`price-alert-detected-event`)/`PriceAlertDetectedPayloadKeys` 변경은 notification과 함께(external-contracts 절차).
- **공유 임계값 계약**: `common-core/PriceAlertChangeRateThreshold`(`PERCENT_3/5/7`)는 탐지(여기)·수신자 조회(notification)·정확 일치 조회(market)가 공유한다. rate 값/enum명을 바꾸면 세 서비스를 함께 본다.
- **계층 경계 유지**: 변동률·임계 판정과 이벤트 생성은 `-application`, WindowStore 접근·forward·헤더는 `-adapter-in`이다. 상태·불변식을 가진 도메인 모델이 없어 `-domain` 모듈은 두지 않는다(`PricePoint`·`PriceChange`는 계산용 값 객체라 `application/dto`에 있다). Kafka Streams 타입(`Processor`, `WindowStore`)을 application·domain으로 들이지 않는다.
- **Streams 토폴로지 주의**: window/retention(`price-alert-detection.store`, `price-alert-detection.window-minutes`)을 바꾸면 산식·상태 크기에 직접 영향. **state store 이름(`upbit-ticker-store`)은 changelog 토픽과 묶여 있어 바꾸지 않는다.**
- **시간 설정 검증 유지**: `max-event-age`와 store duration은 양수여야 하고, retention은 store window와 탐지 window를 모두 포함해야 한다. 잘못된 값은 시작 시 `PriceAlertDetectionProperties` 검증으로 차단한다.
- **토픽 바인딩은 계약**: `git-config-repo/dynamic/market-detection.yml`의 `spring.cloud.stream.bindings`(Streams processor in/out `upbit-ticker-event` → `price-alert-detected-event`)·Kafka Streams EOS(`exactly_once_v2`)를 함께 본다.
- **DataSource/JPA 자동설정 제외 유지**: 이 서비스는 DB가 없지만 발행 계약(`PriceAlertDetectedEvent` → `AbstractInboxEvent` → `common-inbox` → `common-jpa`)이 `spring-boot-starter-data-jpa`를 전이로 끌어온다. `market-detection.yml`의 `spring.autoconfigure.exclude`(`DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`)를 제거하면 `DataSourceAutoConfiguration`이 강제 활성화돼 부팅이 실패한다. 지우지 않는다(상세: `../docs/modules/MARKET_DETECTION.md §3`).
- **common 영속 서비스 빈 스캔 제외 유지**: `Main`은 `@ComponentScan(basePackages="org.example")` + `@ConfigurationPropertiesScan(basePackages="org.example")`로 common 빈을 넓게 스캔하되, `org.example.common.(outbox|dlq|inbox).*`를 `excludeFilters`로 제외한다. `common-inbox`의 `InboxService`가 JPA Repository를 요구하는데 이 서비스는 이벤트 생성(발행)만 하고 Inbox 영속은 소비자(notification) 몫이라 필요 없다. 이 필터를 지우면 부팅이 실패한다.
- **소비 타입은 선언된 타입으로 결정된다**: `upbit-ticker-event`의 값은 `upbit-connector-contract`의 `UpbitTickerEvent`이며, Kafka `__TypeId__` 헤더는 이 바인딩에서 전달되지 않는다(관찰 근거: `../docs/modules/UPBIT_CONNECTOR.md` §6.1). 함수 시그니처 타입을 바꾸면 곧 계약 변경이다.
- **KafkaEvent 발행 규약**: Streams 처리 결과는 KStream key(`code`)와 value로 발행한다. 목적지는 바인딩이 결정하므로 토픽 변경은 `market-detection.yml`에서 한다.
- **시간 조회**: `System.nanoTime()`·`System.currentTimeMillis()`를 직접 호출하지 않고 `common-time`의 `Clock`을 주입받는다.
- **조용히 넘어가는 분기는 관측 가능해야 한다**: `PriceAlertDetectionProcessor`의 `tradeTimestamp` 폴백·`isProcessable` 탈락·stale 폐기는 Micrometer 카운터(`price.alert.detection.*`, `docs/modules/MARKET_DETECTION.md §4.5`)로 노출한다. 폴백은 `[price-alert]` 태그로 첫 발생·100건 주기 warn 로그도 남기지만, 예외는 던지지 않는다(Streams `process()` 예외는 태스크를 죽인다). 새로운 방어적 스킵 분기를 추가할 때도 로그 폭주 없이 카운터부터 두는 것을 기본으로 한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`market-detection-application/.../dto/PriceChange.java`](market-detection-application/src/main/java/org/example/marketdetection/application/dto/PriceChange.java) | 이동평균·변동률·임계 매칭(순수 계산) |
| [`market-detection-application/.../dto/PricePoint.java`](market-detection-application/src/main/java/org/example/marketdetection/application/dto/PricePoint.java) | state store 값 타입 |
| [`market-detection-application/.../PriceAlertDetectionService.java`](market-detection-application/src/main/java/org/example/marketdetection/application/service/PriceAlertDetectionService.java) | stale 판정·탐지 이벤트 생성 |
| [`market-detection-adapter-in/.../PriceAlertDetectionProcessor.java`](market-detection-adapter-in/src/main/java/org/example/marketdetection/adapter/in/stream/PriceAlertDetectionProcessor.java) | WindowStore 접근·forward·헤더 |
| [`market-detection-adapter-in/.../KafkaMarketDetectionBinder.java`](market-detection-adapter-in/src/main/java/org/example/marketdetection/adapter/in/stream/KafkaMarketDetectionBinder.java) | KStream 함수 빈 |
| [`market-detection-adapter-in/.../StateStoreConfig.java`](market-detection-adapter-in/src/main/java/org/example/marketdetection/infra/config/StateStoreConfig.java) | WindowStore 정의 |
| [`market-detection-contract/.../PriceAlertDetectedEvent.java`](market-detection-contract/src/main/java/org/example/marketdetection/contract/event/PriceAlertDetectedEvent.java) | 발행 계약(→ notification) |
| `../git-config-repo/dynamic/market-detection.yml` | Streams 바인딩·토픽·store·트랜잭션 |

## 검증 명령

- 컴파일: `./gradlew :market-detection:market-detection-bootstrap:compileJava`
- 계산·설정 단위 테스트: `./gradlew :market-detection:market-detection-application:test`
- Streams 토폴로지 테스트: `./gradlew :market-detection:market-detection-adapter-in:test`
- 부팅 스모크: `./gradlew :market-detection:market-detection-bootstrap:test`
- 서비스 CI: `./gradlew marketDetectionCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/MARKET_DETECTION.md §6`](../docs/modules/MARKET_DETECTION.md)와 [`../TODO.md`](../TODO.md).

- 현재 없음
