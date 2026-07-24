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

## 4. 폴링 동작

`@EnableScheduling` + `@Scheduled(fixedDelayString=...)`. 정책은 `outbox-poller.yml`의 `poller.*`.

| 스케줄러 · 메서드 | 대상 | fixed-delay | batch | max-retry | 게이트 |
|---|---|---|---|---|---|
| `OutboxEventScheduler.pollGeneral` | Outbox `GENERAL` | 2500ms | 1000 | 3 | `poller.outbox.general.enabled` |
| `OutboxEventScheduler.pollBroadcast` | Outbox `BROADCAST` | 100ms | 1000 | 3 | `poller.outbox.broadcast.enabled` |
| `DlqEventScheduler.poll` | DLQ | 10000ms | 100 | — | `DlqPollerState.isEnabled()` |

- **dispatchType 분리**: 실시간성이 중요한 `BROADCAST`(chat/notification push 등)는 100ms로 빠르게, 일반 `GENERAL`은 2.5s로 폴링한다. `OutboxService.publishPending(dispatchType)`가 `findByDispatchTypeAndStatusOrderByCreatedAtAsc(dispatchType, PENDING, batchSize)`로 오래된 것부터 조회.
- **Outbox 발행 결과**(`OutboxService.publishPending`, `@Transactional("transactionManager")`): 성공 → `markPublished()`; 실패 → `increaseRetryCnt()`, `retryCnt >= maxRetryCnt`면 `markFailed()`. 상태 전이는 같은 트랜잭션에서 영속된다.
- **DLQ 발행 결과**(`DlqService.publishPending`): 성공 → `markPublished()`; 실패 → `markPublishFailed()`(재시도 카운트 개념 없음). PENDING을 `createdAt asc`로 batch 조회.

## 5. 발행 · DLQ 제어

### KafkaEventPublisher (`EventPublisherPort` 구현)
- `publish(Outbox)`: payload = `outbox.getPayload()`(JSON), 헤더 `KafkaHeaders.KEY=partitionKey`, `transaction_id`, `__TypeId__=eventType`. 목적지 = `outbox.getDestination()`(= `aggregateType` = 토픽명). `StreamBridge.send` 실패 시 `OutboxPollerInfrastructureException` → 상위(`OutboxService`)가 잡아 retry/fail 처리.
- `publish(Dlq)`: payload = `dlq.getPayload()`, 헤더 `KEY=aggregateId`, `dlq_id`, `transaction_id`, `__TypeId__=eventType`. 목적지 = `dlq.getDestination()`.
- 헤더 계약(`transaction_id`, `dlq_id`, `__TypeId__`, `KafkaHeaders.KEY`)은 외부 계약 — 소비자(각 서비스의 DLQ consumer 등)와 함께 본다(→ `../../.claude/rules/external-contracts.md`).

### DLQ 런타임 제어 (REST)
- `DlqPollerController`: `PUT /dlq-poller/start`, `PUT /dlq-poller/stop`(경로는 `api-path.dlq-poller.*`). `DlqPollerState`(`AtomicBoolean`)를 토글해 DLQ 폴링을 런타임에 켜고 끈다.
- 초기 상태는 `DlqPollerStateInitializer`(`@PostConstruct`)가 `poller.dlq.enabled`로 설정.
- **관찰**: 이 제어 엔드포인트에 모듈 계층 인증이 확인되지 않는다(스타터는 `web`, security 없음). 정지 시 DLQ 재처리가 멈추므로 접근 통제 전제 확인 필요 → §7, TODO.

## 6. event DB 스키마 (`schema.sql`)

DB `event`(`mysql.event.*`), persistence unit `event`, 단일 write 데이터소스(`spring.datasource.write`) + `transactionManager`(JPA).

- **`outbox`**: `id varchar(36)`, `transaction_id char(36)`, `aggregate_type`, `partition_key`, `payload json`, `event_type`, `domain_type`, `dispatch_type`, `status char(20)`, `retry_cnt`, timestamps. index `idx_outbox_dispatch_type_status_created_at (dispatch_type, status, created_at)` — 폴링 쿼리(`dispatchType+status` 정렬 `createdAt`)에 정합.
- **`dlq`**: `id varchar(26)`(ULID, `ulid-creator`), `source_id`, `event_type`, `aggregate_id`, `aggregate_type`, `transaction_id varchar(26)`, `domain_type`, `status varchar(30)`, `error_message`, `payload json`. index `idx_dlq_status_created_at (status, created_at)`.
- 이 스키마는 모든 서비스의 Outbox/DLQ 기록 대상이자 poller의 폴링 대상이다(공유 계약).

## 7. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. outbox-poller 관련 항목:

- **TODO 1.12** — DLQ 제어 API(`PUT /dlq-poller/start|stop`)에 모듈 계층 인증이 확인되지 않는다. 정지 시 DLQ 재처리가 멈춰 이벤트 적체로 이어질 수 있어, 게이트웨이 라우팅/네트워크 격리 전제와 접근 통제 여부 확인 필요(config-server 무인증 엔드포인트 TODO 1.10과 같은 성격).
- **TODO 4.1**(기존) — 배포 대상 갭은 CI/CD 항목에서 함께 관리.

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
| `KafkaEventPublisher.java` | 발행 헤더·목적지 계약(모든 소비자 영향) |
| `outbox-poller/.../sql/schema.sql` | 전 서비스가 공유하는 event DB 스키마·인덱스 |
| `git-config-repo/dynamic/outbox-poller.yml` | 폴링 주기/배치/재시도·트랜잭션·DB |
| common-outbox `OutboxService`/`DlqService` | 폴링·상태 전이 로직(→ `COMMON.md §5.1`) |

## 11. 관련 문서와 rules

- Outbox 패턴 발행 측·이벤트 구성: [`COMMON.md §5.1`](COMMON.md)
- 루트 흐름: [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md)(§10 Outbox/DLQ), 구조 [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
- 계약/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,architecture,testing}.md`
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
