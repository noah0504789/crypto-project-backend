# NOTIFICATION — notification 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-25
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - REST 경로(`NotificationController`) 변경
>   - Kafka 바인딩(`notification-service.yml`의 `spring.cloud.stream.*`) 또는 `common-core/KafkaTopic`의 notification 항목 변경
>   - 소비 계약(`market-detection-contract`의 `PriceAlertDetectedEvent`), 생산 계약(`WebNotificationEvent`) 변경
>   - market gRPC 클라이언트(`PriceAlertRecipientQueryAdapter`, `market.v1 FindReceiverIds`) 또는 임계값 매핑(`PriceAlertChangeRateThreshold`) 변경
>   - 도메인/Mongo 모델(`Notification`, `NotificationRecipient`, `MongoNotification*`) 또는 인덱스 변경

## 1. 문서 목적과 기준 시점

이 문서는 `notification` 서비스의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../notification/CLAUDE.md`](../../notification/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

알림의 생성·저장·조회 소유 서비스. 현재 유일한 알림 종류는 **가격 알림(PRICE_ALERT)** 이다(도메인에 `SYSTEM` 타입 자리만 존재).

1. `market-detection`이 발행한 변동률 탐지 이벤트(`price-alert-detected-event`)를 소비해 알림을 생성한다.
2. `market` gRPC로 수신자(해당 마켓·임계값 알림을 켠 사용자)를 조회해 사용자별 알림 레코드를 fan-out 저장한다(MongoDB).
3. 실시간 push용 브로드캐스트 이벤트(`web-notification-broadcast-event`)를 발행한다(→ `websocket-gateway`).
4. 사용자별 알림함(inbox) 조회·읽음 처리를 REST로 노출한다.

이 서비스는 **gRPC 서버를 노출하지 않는다**(gRPC는 market을 부르는 클라이언트로만 사용). 변동률 탐지는 `market-detection`, 실시간 push 전송은 `websocket-gateway`의 몫이다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:notification:*` (헥사고날 멀티모듈). 실행 모듈은 `:notification:notification-bootstrap`(`ext.dockerImageName = "crypto-notification-service"`).
- 실행 클래스: `org.example.notification.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`).
- app name: `notification-service`. 포트: REST `8300`(gRPC 서버 없음). 컨텍스트 경로 `/api/v1`.
- 저장소: **MongoDB**(`notification` DB, authSource `notification` — `notification`·`notification_recipient` 컬렉션) + **MySQL**(Outbox 이벤트 저장, `common-outbox` 경유, `DatasourceConfig`의 `spring.datasource.write`).
- Config Server 연동: `spring.cloud.config.name: notification-service,eureka-client,mysql,mongo,kafka,monitoring`. 공유 `api-contract.*`는 Config Repository 루트 `application.yml`에서 자동 병합된다.
- Kafka binder 트랜잭션은 비활성 상태다. notification은 Kafka를 직접 발행하지 않고 생산 이벤트를 MySQL Outbox에 저장하며, 실제 Kafka 발행은 outbox-poller가 담당한다.
- 부트스트랩 의존성: `common-actuator-webmvc`, Config Client, Eureka Client, `spring-cloud-starter-bus-kafka`, Micrometer/Prometheus.

## 4. 모듈 구조 (헥사고날)

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `notification-domain` | domain | `Notification`, `NotificationRecipient`, `NotificationMessagePart`, `NotificationType` | `common-core` |
| `notification-application` | application | UseCase/Service, Port(in/out), 이벤트/페이로드, Command/Query/Result | `notification-domain`(api), `notification-contract`, `common-outbox`, `common-mongo`, caffeine/cache |
| `notification-adapter-in` | adapter-in | REST(`NotificationController`), Kafka 바인더(`KafkaNotificationBinder`) | `common-web`, `market-detection-contract`, `notification-application` |
| `notification-adapter-out` | adapter-out | Mongo 영속(`MongoNotification*`), market gRPC 클라이언트 어댑터, ObjectId 생성기, config | `common-id`, `common-mongo`, `market-client`, `notification-application` |
| `notification-bootstrap` | 실행 | `Main`, `application.yml` | 위 4개 + actuator/config/eureka/bus/prometheus |
| `notification-contract` | 계약 | 생산 이벤트(`WebNotificationEvent`, `WebNotificationPayload`) | `common-core`, `common-outbox` |

- 의존 방향: adapter-in/out → application → domain. adapter-in은 `market-detection-contract`(소비 이벤트 타입), adapter-out은 `market-client`(gRPC 소비)에 의존.
- **관찰**: `notification-application`·`-adapter-in`·`-adapter-out`이 모두 Gradle 플러그인 `crypto-domain`을 쓴다(타 서비스는 각각 `crypto-application`/`crypto-adapter` 사용). 동작에는 문제없어 보이나 컨벤션 상 이질적 → §11, TODO.

## 5. 핵심 흐름 — 탐지 이벤트 → fan-out 알림

```
market-detection: PriceAlertDetectedEvent  →  Kafka: price-alert-detected-event
  → [notification] priceAlertDetectedEventConsumer
      → PriceAlertNotificationCommandService.create
          1) Notification.createPriceAlert(...)  (제목/본문/messageParts 포맷)
          2) market gRPC FindReceiverIds(code, threshold)  → 수신자 UUID 목록
          3) NotificationEventList = NotificationSaveEvent(영속) + WebNotificationEvent(push)
          4) OutboxEventListPublishPort.publish → (MySQL Outbox) → outbox-poller → Kafka
  → [notification] notificationEventConsumer (Kafka: notification-event)
      → NotificationEventService.handle  @Transactional("notificationMongoTransactionManager")
          → MongoDB: notification + notification_recipient(bulk) 저장
  → WebNotificationEvent (Kafka: web-notification-broadcast-event)
      → [websocket-gateway] 온라인 사용자에게 STOMP push
```

- 쓰기는 **Outbox 경유**(chat/market과 동일 패턴, `../modules/COMMON.md §5.1` 참조). 영속(`NotificationSaveEvent`)과 push(`WebNotificationEvent`)를 하나의 `NotificationEventList`로 묶어 발행한다.
- 수신자 조회는 `PriceAlertRecipientQueryAdapter` → `market-client`의 `PriceAlertSettingClient.findReceiverIds(marketCode, targetChangeRate)`. 임계값 문자열(`PERCENT_3/5/7`)은 `PriceAlertChangeRateThreshold.toBigDecimal`로 `0.0300/0.0500/0.0700`(scale 4)로 변환해 market의 **정확 일치** 조회에 쓴다(→ `MARKET.md §7`).
- `NotificationSaveEvent`/`WebNotificationEvent`는 `@JsonCreator`/`@JsonProperty` 직렬화 계약(Outbox payload). `NotificationSaveEvent`는 `notification-event`(+`.dlq`), `WebNotificationEvent`는 `web-notification-broadcast-event`.

## 6. REST API 계약

컨텍스트 `/api/v1`. 컨트롤러에 `@RequestMapping` 베이스 없음.

| 메서드 | 전체 경로 | 헤더/파라미터 | 응답 |
|---|---|---|---|
| GET | `/api/v1/notifications/me` | `X-User-Id`(UUID), `limit`(기본 10), 커서(`NotificationCursor`) | 200 `CursorPage<NotificationResponse>` |
| PATCH | `/api/v1/notifications/{notificationId}/read` | `X-User-Id`(UUID), path `notificationId` | 변경됨 204 / 대상 없음 404 |

- `X-User-Id`는 게이트웨이가 검증된 JWT의 `id` claim에서 주입(`common-core/HttpHeaderKey.USER_ID_VALUE`). 컨트롤러는 `UUID`(receiverId)로 그대로 신뢰한다.
- 인박스 조회는 커서 페이지네이션이며 컨트롤러가 `limit+1`로 조회 후 `CursorPages.from(...)`로 다음 커서 유무를 판정한다.
- `read`는 본인(receiverId)의 recipient 레코드가 `read=false`일 때만 갱신되며, 갱신 0건이면 404.

## 7. 조회 · 읽음 처리 (MongoDB)

인박스는 **`notification_recipient`(사용자별 fan-out 레코드)를 페이지 조회한 뒤, 대응 `notification` 본문을 로드**해 `NotificationInboxItem`으로 합친다(2단계).

- 최신 페이지(`listLatestInboxItems`): recipient를 `receiverId` 기준 `deliveredAt desc, _id desc`로 정렬해 `limit` 조회 — **`primaryMongoTemplate`**(primary read).
- 이전 페이지(`listInboxItemsBefore`): 커서(`deliveredAt < ts` 또는 `= ts && _id < lastId`) 조건 — **`secondaryMongoTemplate`**(`secondaryPreferred`, replica read). 즉 최신은 primary, 과거 페이지는 secondary로 읽기 부하를 분리한다.
- 읽음(`markAsRead`): `notificationId + receiverId + read=false` 조건 `updateFirst`로 `read=true, readAt` 설정. `modifiedCount>0`이면 성공.
- 저장: `save`(notification 단건) + `saveRecipients`(recipient `BulkOperations.UNORDERED`, `BATCH_SIZE=1000`).

## 8. 도메인 모델

- **`Notification`**(`notification-domain`): `id`(ObjectId hex), `type`, `title`, `message`, `messageParts:List<NotificationMessagePart>`, `link`, `payload:Map`, `deleted`/`deletedAt`, `createdAt`. 팩토리 `createPriceAlert(...)`가 변동률→방향(상승/하락)·`%.1f%%` 포맷과 **리치 텍스트 조각(messageParts)** 을 만든다. `rehydrate(...)`로 복원.
- **`NotificationRecipient`**: `id`, `notificationId`, `receiverId`(UUID), `read`/`readAt`, `deliveredAt`. 알림 1건이 수신자 수만큼 fan-out된다.
- **`NotificationMessagePart`**(record): `{ text, bold, lineBreakAfter }`. 정적 `plain`/`bold`. 프론트가 볼드 등 서식을 렌더링하도록 본문을 조각으로 표현.
- **`NotificationType`**: `PRICE_ALERT`, `SYSTEM`(현재 생성 경로는 PRICE_ALERT만).
- 도메인 모델은 `create*`/`rehydrate` 정적 팩토리 + `@Getter`, private builder(상태 변경 메서드는 최소).

## 9. Mongo 스키마 · 인덱스

DB `notification`(authSource `notification`). `MongoConfig`가 커넥션 풀(min 20/max 200), primary read, 스네이크케이스, `notificationMongoTransactionManager`(replica-set 트랜잭션)를 구성. `autoIndexCreation=true`.

| 컬렉션 | 인덱스 | 비고 |
|---|---|---|
| `notification` | `idx_deleted_created` `{deleted:1, createdAt:-1}`, `idx_type_deleted_created` `{type:1, deleted:1, createdAt:-1}` | soft-delete(`deleted`/`deletedAt`), `payload:Map` 보관 |
| `notification_recipient` | unique `ux_..._notification_receiver` `{notificationId:1, receiverId:1}`; `idx_receiver_delivered` `{receiverId:1, deliveredAt:-1}`; `idx_receiver_read_delivered` `{receiverId:1, read:1, deliveredAt:-1}`; `idx_notification` `{notificationId:1}` | 수신자별 인박스 커서·읽음 필터·fan-out 조회 |

- unique `(notificationId, receiverId)`가 동일 알림의 수신자 중복 저장을 막는다(멱등 기반).

### 컨슈머 멱등 전략

| 컨슈머 이벤트 | 하는 일 | 사용한 전략 |
|---|---|---|
| `PriceAlertDetectedEvent` | 새 알림 ID를 생성하고 `NotificationSaveEvent`·`WebNotificationEvent`를 Outbox에 기록 | `(consumer_name,event_id)` unique Inbox를 Outbox 저장과 같은 event DB 트랜잭션에서 선점; 중복이면 알림 생성 전에 성공 종료 |
| `NotificationSaveEvent` | 알림 본문과 사용자별 수신자 레코드를 MongoDB에 저장 | `notificationId` 문서 저장 + `(notificationId,receiverId)` 자연 키 bulk upsert; 같은 Mongo 트랜잭션에서 반복 저장을 동일 결과로 수렴 |

market-detection은 최초 이벤트 생성 시 무작위 UUID를 만들어 Kafka `event_id` 헤더에 기록하고, notification Binder는 payload가 아니라 이 헤더를 Command에 전달한다. Inbox INSERT가 성공한 consumer만 알림 ID 생성과 Outbox fan-out을 수행하며, 처리 중 실패하면 Inbox row와 Outbox row가 함께 롤백된다. `NotificationSaveEvent`는 event DB와 MongoDB를 하나의 트랜잭션으로 묶지 않고 도메인 자연 키로 재전달을 흡수한다. 기존 수신자 레코드는 `$setOnInsert` upsert로 보존하므로 이미 읽은 알림의 상태를 중복 이벤트가 되돌리지 않는다.

## 10. Kafka 계약

토픽 카탈로그: `common-core/KafkaTopic`. 바인딩: `notification-service.yml`.

| 토픽 | 방향 | 이벤트 | 처리 |
|---|---|---|---|
| `price-alert-detected-event` | 소비(group `notification`) | `PriceAlertDetectedEvent`(market-detection 생산) | `priceAlertDetectedEventConsumer` → 알림 생성·fan-out |
| `notification-event` (`.dlq`) | 소비(group `notification`) | `NotificationSaveEvent` | `notificationEventConsumer` → Mongo 영속 |
| `web-notification-broadcast-event` | 생산(Outbox) | `WebNotificationEvent{payload, notificationId}` | **websocket-gateway** 소비 → STOMP push |

- consumer 함수: `priceAlertDetectedEventConsumer`, `notificationEventConsumer`(둘 다 `ack-mode: record`, `start-offset: latest`).
- `WebNotificationPayload`: `{ type, title, body, createdAtMs, link, typedPayload }`. `TypedPayload`는 탐지 원본(가격·평균·변동률 등)을 키-값으로 실어 프론트에 전달.

## 11. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. notification 관련 항목:

- **TODO 3.3** — `notification-event`에 `.dlq` 토픽이 정의돼 있으나 **DLQ consumer 바인딩이 없고**, `NotificationEventService.handle`에 `@Retryable`/`@Recover`가 없다(단순 `@Transactional`). chat이 갖춘 재시도→DLQ 복구 경로가 없어, Mongo 영속 실패 시 처리 방식(바인더 기본 재시도/유실 여부)이 불명확 → 확인 필요.
- **TODO 4.4** — `notification-application`·`-adapter-in`·`-adapter-out`이 Gradle 플러그인 `crypto-domain`을 사용(타 서비스는 `crypto-application`/`crypto-adapter`). ArchUnit/플러그인 규약상 의도인지 확인 필요.
- **게이트웨이 라우트·인가(해결됨)** — 과거 `GET /notifications/me`·`PATCH /notifications/{id}/read`가 게이트웨이 라우트·인가 부재로 `denyAll`이었다. 이제 `ReactiveRouteConfig.notificationRoutes`(`lb://notification-service`, rewrite `/api/v1/${seg}`) + `ReactiveSecurityConfig`(`/notifications/**` `hasRole(USER)`)로 노출·보호된다. (프론트는 실시간 알림을 STOMP로 받고, 이 인박스 REST는 새로고침 후 조회 등에 사용 가능.)

## 12. 테스트 현황

- domain: `NotificationTest`
- application: `NotificationQueryServiceTest`, `PriceAlertNotificationCommandServiceTest`
- adapter-in: `NotificationControllerMvcTest`
- adapter-out: `MongoNotificationAdapterTest`, `MongoNotificationRecipientRepositoryImplTest`

## 13. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :notification:notification-application:compileJava` 등 서브모듈 단위.
- 서브모듈 테스트: `./gradlew :notification:notification-application:test`, `:notification:notification-adapter-in:test`, `:notification:notification-adapter-out:test`, `:notification:notification-domain:test`.
- 서비스 CI: `./gradlew notificationCi`(빌드+테스트+ArchUnit 포함).
- 전체 build/test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 14. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `market-detection-contract`(`PriceAlertDetectedEvent`) · `common-core/KafkaTopic` | 소비 이벤트/토픽 계약. market-detection과 함께 |
| `notification-contract/.../WebNotificationEvent`·`WebNotificationPayload` | websocket-gateway가 역직렬화하는 push 계약 |
| `common-core/PriceAlertChangeRateThreshold` | 임계값 enum. market `FindReceiverIds` 정확 일치와 맞물림 |
| `MongoNotification`/`MongoNotificationRecipient` 인덱스 | 인박스 커서·unique·읽음 필터 |
| `git-config-repo/dynamic/notification-service.yml` | 포트·Kafka 바인딩·Mongo/MySQL. 게이트웨이 route와 함께 |

## 15. 관련 문서와 rules

- 루트 구조/흐름: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), 코드 스타일 [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 상·하류: 알림 소스 [`MARKET.md`](MARKET.md)(수신자 조회 gRPC), 실시간 push는 `websocket-gateway`(모듈 문서 미작성), Outbox 흐름 [`COMMON.md §5.1`](COMMON.md)
- 계약/보안/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,security,architecture,testing}.md`
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
