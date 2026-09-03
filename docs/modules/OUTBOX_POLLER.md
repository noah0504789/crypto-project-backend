# OUTBOX_POLLER — outbox-poller 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-25
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - 폴링 스케줄·정책(`outbox-poller.yml`의 `poller.*`, `OutboxEventScheduler`, `DlqEventScheduler`) 변경
>   - 발행 로직(`KafkaEventPublisher`) 또는 common-outbox의 `OutboxService`/`DlqService.publishPending` 변경
>   - event DB 스키마(`outbox-poller/.../sql/schema.sql`의 `outbox`/`dlq`) 변경
>   - DLQ 제어 API(`DlqPollerController`, `DlqPollerState`) 변경

## 1. 문서 목적과 기준 시점

이 문서는 `outbox-poller` 서비스의 구조·동작·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../outbox-poller/CLAUDE.md`](../../outbox-poller/CLAUDE.md)에 있으며 여기서는 반복하지 않는다. Outbox 패턴의 **발행(write) 측**과 이벤트/엔티티 구성은 [`COMMON.md §5.1`](COMMON.md)에 있고, 이 문서는 **릴레이(poller) 측**을 다룬다.

## 2. 모듈 역할

Transactional Outbox 패턴의 **공용 릴레이**. 모든 서비스가 자기 트랜잭션에서 MySQL `event` DB의 `outbox`/`dlq` 테이블에 기록한 이벤트를, outbox-poller가 주기적으로 폴링해 Kafka로 발행한다. 서비스는 Kafka로 직접 쏘지 않고 이 릴레이에 위임한다.

- **`EventPublisherPort` 빈(`KafkaEventPublisher`)을 가진 유일한 서비스.** 그래서 common-outbox의 `OutboxService`/`DlqService`는 이 포트를 `ObjectProvider`로 **지연·선택 주입**한다(생산 서비스에는 이 빈이 없어 발행하지 않고 기록만 함).
- 저장소: MySQL `event` DB(`mysql.event.*`). 도메인 로직 없음 — 상태 전이(`markPublished`/`markFailed` 등)는 common-outbox 도메인 메서드로만.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:outbox-poller`. **단일 모듈**(`crypto-bootstrap`, 서브모듈 없음). `ext.dockerImageName = "crypto-outbox-poller"`.
- 실행 클래스: `org.example.outboxpoller.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`, **`@EnableScheduling`**).
- app name: `outbox-poller`. 포트 `9200`.
- 의존: `common-core`, `common-outbox`(도메인·서비스·포트), `spring-boot-starter-web`, `data-jpa`, `stream-kafka`, `ulid-creator`(DLQ id), config/bus/prometheus.
- Config Server 연동: `spring.cloud.config.name: outbox-poller,mysql,kafka,monitoring`. 스키마는 `spring.sql.init`(`classpath:sql/schema.sql`, `mode: always`)로 `outbox`/`dlq` 생성.
- Kafka: `default-binder: kafka`. **Kafka 트랜잭션은 비활성**(설정에 `transaction-id-prefix`가 주석 처리) — 브로커 공통 idempotence/acks=all(`infrastructure/kafka.yml`)에 의존하는 at-least-once 릴레이(§6).

의존성 전체 그래프는 [`docs/dependencies.html`](../dependencies.html)에서 확인할 수 있다.

## 4. 폴링 동작

`@EnableScheduling` + `@Scheduled(fixedDelayString=...)`. 정책은 `outbox-poller.yml`의 `poller.*`.

`OutboxPollerProperties`는 `@Validated`로 바인딩하며 `general`/`broadcast` 중첩 객체는 `@Valid @NotNull`, 각 숫자 정책은 박싱 타입 + `@Positive`로 검증한다. 코드 기본값은 두지 않으므로 dispatch 설정 전체·일부가 누락되거나 0/음수이면 ApplicationContext 생성이 실패해 폴러가 잘못된 값으로 기동하지 않는다.

| 스케줄러 · 메서드 | 대상 | fixed-delay | batch | max-retry | 게이트 |
|---|---|---|---|---|---|
| `OutboxEventScheduler.pollGeneral` | Outbox `GENERAL` | 5000ms | 1000 | 3 | `poller.outbox.general.enabled` |
| `OutboxEventScheduler.pollBroadcast` | Outbox `BROADCAST` | 300ms | 1000 | 3 | `poller.outbox.broadcast.enabled` |
| `DlqEventScheduler.poll` | DLQ | 10000ms | 100 | — | `DlqPollerState.isEnabled()` |

- **dispatchType 분리**: 실시간성이 중요한 `BROADCAST`(chat/notification push 등)는 300ms로 빠르게, 일반 `GENERAL`은 5s로 폴링한다. `OutboxService.publishPending(dispatchType)`가 `findByDispatchTypeAndStatusOrderByCreatedAtAsc(dispatchType, PENDING, batchSize)`로 오래된 것부터 조회.
- **Outbox 발행 결과**(`OutboxService.publishPending`, `@Transactional("transactionManager")`): 성공 → `markPublished()`; 실패 → `increaseRetryCnt()`, `retryCnt >= maxRetryCnt`면 `markFailed()`. 상태 전이는 같은 트랜잭션에서 영속된다.
- **DLQ 발행 결과**(`DlqService.publishPending`): 성공 → `markPublished()`; 실패 → `markPublishFailed()`(재시도 카운트 개념 없음). PENDING을 `createdAt asc`로 batch 조회.

## 5. 발행 · DLQ 제어

### KafkaEventPublisher (`EventPublisherPort` 구현)
- 메시지 조립은 common-event의 `KafkaEventFactory`에 위임하고, Publisher는 목적지 선택·`StreamBridge.send`·실패 변환만 담당한다.
- `publish(Outbox)`: Factory 입력은 payload = `outbox.getPayload()`(JSON), `KEY=partitionKey`, `event_id=outbox.id`, `transaction_id`, `__TypeId__=eventType`. 목적지 = `outbox.getDestination()`(= `aggregateType` = 토픽명). `StreamBridge.send` 실패 시 `OutboxPollerInfrastructureException` → 상위(`OutboxService`)가 잡아 retry/fail 처리.
- `publish(Dlq)`: Factory 입력은 payload = `dlq.getPayload()`, `KEY=aggregateId`, `event_id=dlq.id`, `dlq_id`, `transaction_id`, `__TypeId__=eventType`. 목적지 = `dlq.getDestination()`.
- 헤더 계약(`event_id`, `transaction_id`, `dlq_id`, `__TypeId__`, `KafkaHeaders.KEY`)은 외부 계약 — 소비자(각 서비스의 DLQ consumer 등)와 함께 본다(→ `../../.claude/rules/external-contracts.md`).

### DLQ 런타임 제어 (REST)
- `DlqPollerController`: `PUT /dlq-poller/start`, `PUT /dlq-poller/stop`(경로는 `api-path.dlq-poller.*`). `DlqPollerState`(`AtomicBoolean`)를 토글해 DLQ 폴링을 런타임에 켜고 끈다.
- `DlqPollerState` 자체와 `poller.dlq.enabled`의 초기값은 모두 `false`다. 재기동 후 DLQ 재발행은 자동으로 시작하지 않으며, 운영자가 `PUT /dlq-poller/start`를 호출한 경우에만 실행된다.
- **관찰**: 이 제어 엔드포인트에 모듈 계층 인증이 확인되지 않는다(스타터는 `web`, security 없음). 정지 시 DLQ 재처리가 멈추므로 접근 통제 전제 확인 필요 → §7, TODO.

### 트랜잭션 경계와 보장 수준

이 모듈은 **Kafka 다중 레코드 원자성보다 at-least-once 전달과 최종 수렴을 선택**한다. 같은 요청에서 파생된 Outbox 이벤트는 `transaction_id`로 논리적으로 묶이지만, poller는 `transaction_id` 단위가 아니라 `dispatchType + PENDING` 조건으로 조회하고 각 레코드를 `StreamBridge.send`로 개별 발행한다.

| 단계 | 트랜잭션·보장 | 보장하지 않는 것 |
|---|---|---|
| 생산 서비스의 `OutboxEventListListener` → `OutboxService.saveAll` | 같은 EventList의 Outbox 레코드를 event DB에 한 번에 저장 | MongoDB 등 다른 저장소와 event DB 사이의 2PC |
| `OutboxService.publishPending` | JPA `transactionManager`가 조회 후 `PUBLISHED`/`FAILED`/`retryCnt` 상태 변경을 묶음 | 이 JPA 트랜잭션과 Kafka send의 원자적 커밋 |
| `KafkaEventPublisher.publish` | Outbox 레코드 하나당 한 번의 Kafka send. 공통 producer의 idempotence·`acks=all` 사용 | 같은 `transaction_id`의 여러 토픽/레코드를 하나의 Kafka 트랜잭션으로 묶는 것 |
| 소비 단계 | consumer별 retry·DLQ·멱등 처리로 최종 수렴 | 서로 다른 consumer와 저장소의 분산 트랜잭션 |

`outbox-poller.yml`의 일반 binder `transaction-id-prefix`는 의도적으로 비활성 상태다. 이를 활성화하는 것만으로 Outbox의 `transaction_id`가 Kafka transaction ID가 되거나 같은 요청의 이벤트가 자동으로 묶이지 않는다. `transaction_id` 단위 원자 발행을 도입하려면 조회·스케줄링을 그룹 단위로 바꾸고, 해당 그룹의 여러 send를 명시적인 Kafka 트랜잭션 경계 안에서 수행해야 한다. 현재 `GENERAL`과 `BROADCAST`는 요구 지연 시간이 달라 별도 스케줄러가 처리하므로 같은 `transaction_id`가 두 경로로 나뉠 수 있다.

현재 선택의 결과는 다음과 같다.

- **장점**: 구조와 운영이 단순하고, 실시간 `BROADCAST`와 일반 이벤트의 polling 주기를 독립적으로 최적화할 수 있다. 일시 실패한 레코드는 다음 polling에서 다시 시도하므로 관련 이벤트 사이에 시간 차이가 생겨도 최종적으로 모두 발행되는 방향으로 수렴한다.
- **부분 발행**: 같은 `transaction_id`의 일부 이벤트가 먼저 `PUBLISHED`되고 나머지가 `PENDING` 또는 `FAILED`일 수 있다. 따라서 “모두 동시에 보이거나 모두 보이지 않음”은 보장하지 않는다.
- **중복 가능성**: Kafka 발행 후 DB 상태 커밋 전에 장애가 발생하면 같은 Outbox가 다시 선택되어 재발행될 수 있다. Kafka producer idempotence는 한 producer session의 전송 재시도 중복을 줄이지만, 이후 polling에서 발생한 애플리케이션 수준 재발행까지 제거하지 않는다.
- **운영 복구**: retry를 소진한 Outbox는 삭제되지 않고 `FAILED`로 남아 조회·원인 분석이 가능하다. 다만 현재 poller는 `PENDING`만 자동 조회하며, `FAILED`를 다시 `PENDING`으로 전환하는 애플리케이션 API/작업은 코드에서 확인되지 않는다. 최종 수렴에는 상태 복구를 위한 운영 절차나 재처리 기능이 별도로 필요하다.
- **consumer 전제**: 발행 및 소비 재시도로 중복이 가능하므로 consumer는 이벤트 식별자를 기준으로 멱등하게 처리해야 한다.

`transaction_id`는 현재 Kafka 트랜잭션 ID가 아니라 **한 요청에서 파생된 이벤트들의 correlation ID**다. 요청 단위 조회, 부분 실패 추적, 관련 이벤트 재처리 후보 식별에 사용하며 Kafka 헤더 `transaction_id`로 하류에도 전달한다. 향후 원자 발행이 실제 요구사항이 되면 그룹 조회의 기준으로 재사용할 수 있다.

대안은 다음과 같다.

| 선택지 | 얻는 것 | 비용·한계 |
|---|---|---|
| 현재 poller 유지 | at-least-once, 상태 가시성, dispatchType별 지연 최적화 | 일시적 부분 발행과 중복 허용, FAILED 운영 복구 필요 |
| `transaction_id` 단위 Kafka transaction | 같은 그룹의 Kafka 레코드를 모두 commit/abort | GENERAL/BROADCAST 분리 재설계, DB 상태와 Kafka 사이 중복 구간은 여전히 존재 |
| 단일 Envelope 이벤트 | 요청 단위 발행을 레코드 하나로 단순화 | 이벤트 계약·소비자 결합 증가, 독립 확장·재시도 어려움 |
| Debezium CDC Outbox | MySQL binlog offset 기반 재개, 애플리케이션 poller/발행 상태 로직 축소 | Kafka Connect·connector·binlog 운영 부담, 기본 구성만으로 다중 토픽 원자성은 생기지 않음 |

현재 요구사항은 관련 이벤트가 같은 순간에 노출되는 것보다 **보존된 이벤트를 재시도·운영 복구하여 최종 전달하는 것**을 우선하므로, at-least-once poller를 유지한다.

## 6. event DB 스키마 (`schema.sql`)

DB `event`(`mysql.event.*`), persistence unit `event`, 단일 write 데이터소스(`spring.datasource.write`) + `transactionManager`(JPA).

| 테이블 | 컬럼 | 인덱스 · 제약 | 역할 |
|---|---|---|---|
| `outbox` | `id varchar(36)`, `transaction_id char(36)`, `aggregate_type`, `partition_key`, `payload json`, `event_type`, `domain_type`, `dispatch_type`, `status char(20)`, `retry_cnt`, timestamps | `idx_outbox_dispatch_type_status_created_at (dispatch_type, status, created_at)` | 발행 대기 이벤트. 인덱스는 폴링 쿼리(`dispatchType`+`status`, `createdAt` 정렬)에 정합 |
| `dlq` | `id varchar(26)`(ULID, `ulid-creator`), `source_id`, `event_type`, `aggregate_id`, `aggregate_type`, `transaction_id varchar(26)`, `domain_type`, `status varchar(30)`, `error_message`, `payload json` | `idx_dlq_status_created_at (status, created_at)` | consumer 처리 실패 이벤트의 재처리 대상 |
| `inbox` | `consumer_name`, `event_id` | `(consumer_name, event_id)` unique | 비멱등 consumer 처리 선점. 동일 consumer의 동시·재전달 중복을 막고, 처리 결과 Outbox write와 같은 event DB 트랜잭션에서 commit/rollback |

이 스키마는 모든 서비스의 Outbox/DLQ 기록 대상이자 poller의 폴링 대상이다(공유 계약).

## 7. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. outbox-poller 관련 항목:

- **TODO 1.12** — DLQ 제어 API(`PUT /dlq-poller/start|stop`)에 모듈 계층 인증이 확인되지 않는다. 정지 시 DLQ 재처리가 멈춰 이벤트 적체로 이어질 수 있어, 게이트웨이 라우팅/네트워크 격리 전제와 접근 통제 여부 확인 필요(config-server 무인증 엔드포인트 TODO 1.10과 같은 성격).
- **TODO 4.5** — 현재 at-least-once poller를 유지하되 처리량·DB polling 부하·상태 관리 비용이 커지고 Kafka Connect 운영 역량이 확보되면 Debezium CDC Outbox 전환을 재검토한다.
- **TODO 4.6** — retry를 소진해 `FAILED`가 된 Outbox를 원인 해결 후 안전하게 다시 처리할 수 있는 운영·애플리케이션 복구 경로를 추가한다.

## 8. 테스트 현황

- `OutboxEventSchedulerTest`(폴링·발행·상태 전이)
- `KafkaEventPublisherTest`(메시지/헤더/목적지, 발행 실패 예외)

## 9. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :outbox-poller:compileJava`
- 테스트: `./gradlew :outbox-poller:test`
- 서비스 CI: `./gradlew outboxPollerCi`(빌드+테스트+ArchUnit 포함)
- 전체 build/test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 10. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| common-event `KafkaEventFactory.java` | 발행 헤더 계약(모든 소비자 영향) |
| `KafkaEventPublisher.java` | 발행 목적지·전송·실패 처리(모든 소비자 영향) |
| `outbox-poller/.../sql/schema.sql` | 전 서비스가 공유하는 event DB 스키마·인덱스 |
| `git-config-repo/dynamic/outbox-poller.yml` | 폴링 주기/배치/재시도·트랜잭션·DB |
| common-outbox `OutboxService`/`DlqService` | 폴링·상태 전이 로직(→ `COMMON.md §5.1`) |

## 11. 관련 문서와 rules

- Outbox 패턴 발행 측·이벤트 구성: [`COMMON.md §5.1`](COMMON.md)
- 루트 흐름: [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md)(§11 Outbox/DLQ), 구조 [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
- 계약/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,architecture,testing}.md`
