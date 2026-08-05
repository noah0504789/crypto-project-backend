# notification — 모듈 작업 지침

이 파일은 `notification/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/NOTIFICATION.md`](../docs/modules/NOTIFICATION.md)를 참고한다.

이 모듈은 Kafka 소비/생산·Outbox·트랜잭션에 걸쳐 있어, 관련 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

알림 생성·저장·조회의 소유 서비스(헥사고날 멀티모듈, 실행 모듈 `notification-bootstrap`). 담당:

1. `price-alert-detected-event`(market-detection) 소비 → 가격 알림 생성
2. market gRPC로 수신자 조회 → 사용자별 알림 fan-out 저장(MongoDB)
3. `web-notification-broadcast-event` 발행(→ websocket-gateway push)
4. 내 알림함 조회/읽음 처리(REST)

변동률 탐지(`market-detection`), 실시간 push 전송(`websocket-gateway`)은 이 모듈이 아니다. notification은 **gRPC 서버를 노출하지 않고**, market을 부르는 클라이언트로만 gRPC를 쓴다. `notification/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **의존 방향 유지**: adapter-in/out → application → domain. `notification-domain`은 프레임워크 비의존. 서비스에서 Mongo Repository/Template을 직접 주입하지 않고 `NotificationPersistencePort`(`MongoNotificationAdapter`) 경유(→ `../.claude/rules/architecture.md`).
- **Command/Query 분리 유지**: `PriceAlertNotificationCommandService`(탐지→생성), `NotificationCommandService`(읽음), `NotificationQueryService`(인박스), `NotificationEventService`(영속). UseCase 인터페이스로만 어댑터가 호출한다.
- **Outbox fan-out 흐름 보존**: 알림 생성은 `OutboxEventListPublishPort.publish(NotificationEventList)`로 `NotificationSaveEvent`(영속)+`WebNotificationBroadcastEvent`(push)를 함께 발행 → outbox-poller → Kafka. Kafka로 직접 쏘지 않는다. 이벤트는 `@JsonCreator`/`@JsonProperty` 직렬화 계약을 유지한다(→ `../.claude/rules/external-contracts.md`).
- **Consumer 멱등 경계 유지**: `PriceAlertNotificationCommandService.create`의 `@Transactional("transactionManager")`이 `InboxService.save`의 `(consumer_name,event_id)` unique 중복 검사와 Outbox 기록을 같은 event DB 트랜잭션으로 처리한다. 중복 예외는 트랜잭션 프록시 밖인 `KafkaNotificationBinder`가 성공으로 종료한다. consumer 식별자는 `PriceAlertNotificationCreateCommand.CONSUMER_NAME`이 소유한다. `NotificationSaveEvent`는 `notificationId`와 `(notificationId,receiverId)` 자연 키 upsert로 Mongo 재전달을 흡수한다. Inbox를 Mongo 저장과 분산 트랜잭션처럼 취급하지 않는다.
- **트랜잭션 매니저 이름은 계약**: Mongo write 경로는 `@Transactional("notificationMongoTransactionManager")`(`MongoConfig`). 이름 변경/기본 매니저 대체 금지. Outbox(MySQL) 쪽은 `transactionManager`(JPA).
- **수신자 조회는 market 계약**: `PriceAlertRecipientQueryAdapter` → `market-client.PriceAlertSettingClient.findReceiverIds(marketCode, targetChangeRate)`. 임계값 문자열은 `PriceAlertChangeRateThreshold.toBigDecimal`(scale 4)로 변환하며 market은 **정확 일치**로 조회한다. 임계값 enum(`PERCENT_0/3/5/7`)이나 정밀도를 바꾸면 market·market-detection과 함께 본다.
- **생산 계약(`WebNotificationBroadcastEvent`)**: websocket-gateway가 역직렬화하는 push payload다. 필드/토픽(`web-notification-broadcast-event`) 변경은 websocket-gateway와 함께(external-contracts 절차).
- **인박스 읽기 분리 유지**: 최신 페이지는 `primaryMongoTemplate`, 과거 페이지는 `secondaryMongoTemplate`(`secondaryPreferred`)로 읽는다. 이 primary/secondary 분리를 임의로 바꾸지 않는다.
- **도메인 상태 변경은 도메인/영속 메서드로**: 알림 생성은 `Notification.createPriceAlert`, 읽음은 recipient `updateFirst`(`read=false`일 때만). public setter를 열지 않는다.
- **Mongo 인덱스는 계약**: `notification`/`notification_recipient`의 unique(`{notificationId, receiverId}`)·인박스 커서 인덱스를 영향 분석 없이 바꾸지 않는다.
- **REST 경로·포트·Kafka·DB 설정은 원격 Config**: `../git-config-repo/dynamic/notification-service.yml`(포트 8300, stream 바인딩, Outbox용 `mysql.event.*` write pool, `mongo.notification.*`, `notification.cache.ttl`, `notification.persistence.batch-size`, `app.grpc.market-client.deadline`). notification의 Kafka 생산은 Outbox를 거치므로 binder `transaction-id-prefix`는 비활성 상태다. X-User-Id(UUID) 신뢰 방식 변경은 게이트웨이와 함께(→ `../.claude/rules/security.md`).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`notification-adapter-in/.../stream/KafkaNotificationBinder.java`](notification-adapter-in/src/main/java/org/example/notification/adapter/in/stream/KafkaNotificationBinder.java) | `priceAlertDetectedEventConsumer`, `notificationEventConsumer` |
| [`notification-adapter-in/.../web/NotificationController.java`](notification-adapter-in/src/main/java/org/example/notification/adapter/in/web/NotificationController.java) | 인박스 조회·읽음 |
| [`notification-application/.../service/PriceAlertNotificationCommandService.java`](notification-application/src/main/java/org/example/notification/application/service/PriceAlertNotificationCommandService.java) | 탐지→알림 생성·수신자 fan-out·Outbox 발행 |
| [`notification-application/.../service/NotificationEventService.java`](notification-application/src/main/java/org/example/notification/application/service/NotificationEventService.java) | `NotificationSaveEvent`→Mongo 영속 |
| [`notification-application/.../service/NotificationQueryService.java`](notification-application/src/main/java/org/example/notification/application/service/NotificationQueryService.java) | 인박스 커서 조회 |
| [`notification-adapter-out/.../grpc/PriceAlertRecipientQueryAdapter.java`](notification-adapter-out/src/main/java/org/example/notification/adapter/out/grpc/PriceAlertRecipientQueryAdapter.java) | market gRPC 수신자 조회 |
| [`notification-adapter-out/.../persistence/MongoNotificationRecipientRepositoryImpl.java`](notification-adapter-out/src/main/java/org/example/notification/adapter/out/persistence/MongoNotificationRecipientRepositoryImpl.java) | 인박스 커서·bulk 저장·읽음(primary/secondary 분리) |
| [`notification-contract/.../event/WebNotificationBroadcastEvent.java`](notification-contract/src/main/java/org/example/notification/contract/event/WebNotificationBroadcastEvent.java) | push 브로드캐스트 계약(→ websocket-gateway) |
| `../git-config-repo/dynamic/notification-service.yml` | 포트·Kafka·Mongo/MySQL 설정(Config Server 원격) |

## 검증 명령

- 컴파일: `./gradlew :notification:notification-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :notification:notification-adapter-in:test`, `:notification:notification-application:test`, `:notification:notification-adapter-out:test`
- 서비스 CI: `./gradlew notificationCi`(빌드+테스트+ArchUnit 포함)

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/NOTIFICATION.md §11`](../docs/modules/NOTIFICATION.md)와 [`../TODO.md`](../TODO.md).

- `notification-event.dlq` 토픽은 있으나 DLQ consumer 바인딩·`@Retryable`/`@Recover` 부재(chat과 대비) → 영속 실패 시 처리 방식 불명확
- application/adapter 모듈이 Gradle 플러그인 `crypto-domain` 사용(타 서비스와 이질적)
