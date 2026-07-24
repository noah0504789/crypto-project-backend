# outbox-poller — 모듈 작업 지침

이 파일은 `outbox-poller/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·동작·근거는 [`../docs/modules/OUTBOX_POLLER.md`](../docs/modules/OUTBOX_POLLER.md)를, Outbox 패턴 전반은 [`../docs/modules/COMMON.md §5.1`](../docs/modules/COMMON.md)을 참고한다.

이 모듈은 **전 서비스의 이벤트 발행 경로**다. Kafka/Outbox/DLQ·스키마 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이며, 파급이 크므로 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

Transactional Outbox 패턴의 **공용 릴레이**(단일 모듈, `crypto-bootstrap`). MySQL `event` DB의 `outbox`/`dlq`를 폴링해 Kafka로 발행한다.

- `EventPublisherPort` 빈(`KafkaEventPublisher`)을 가진 **유일한** 서비스다. common-outbox의 `OutboxService`/`DlqService`가 이 포트를 `ObjectProvider`로 지연 주입한다.
- 도메인 로직 없음. 상태 전이는 common-outbox 도메인 메서드(`markPublished`/`markFailed`/`increaseRetryCnt`/`markPublishFailed`)로만.

`outbox-poller/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **발행 헤더·목적지 계약 보존**: `KafkaEventPublisher`의 헤더(`KafkaHeaders.KEY`, `transaction_id`, `dlq_id`, `__TypeId__`)와 목적지(`Outbox.getDestination()`=`aggregateType`=토픽)는 모든 소비자가 의존하는 계약이다. 임의 변경 금지(→ `../.claude/rules/external-contracts.md`).
- **폴링 정책은 Config**: 주기/배치/재시도는 `../git-config-repo/dynamic/outbox-poller.yml`의 `poller.*`. `BROADCAST`(100ms)와 `GENERAL`(2500ms) 분리, DLQ(10000ms)를 유지한다. 스케줄 상수를 코드에 하드코딩하지 않는다.
- **상태 전이는 도메인 메서드로**: 폴링 성공/실패 처리는 common-outbox의 `OutboxService.publishPending`/`DlqService.publishPending`가 담당한다. 여기서 직접 SQL로 상태를 바꾸지 않는다. 실패 시 재시도/`FAILED` 전이 로직(retryCnt, maxRetryCnt)을 우회하지 않는다.
- **at-least-once 전제 유지**: Kafka 트랜잭션이 비활성(주석)이라 send 성공 후 `markPublished` 반영 전 크래시 시 중복 발행이 가능하다. 소비자 멱등성 전제를 깨는 변경(트랜잭션 on/off, 발행 순서)은 전 소비자 영향을 함께 본다.
- **DLQ 제어 상태**: `DlqPollerState`(AtomicBoolean)는 `DlqEventScheduler`의 게이트다. `DlqPollerController`(`PUT /dlq-poller/start|stop`)로 토글, 초기값은 `DlqPollerStateInitializer`가 `poller.dlq.enabled`로 설정. 이 제어 엔드포인트는 인증이 확인되지 않으니(§확인 필요) 노출/통제 변경은 게이트웨이·네트워크 격리와 함께 본다(→ `../.claude/rules/security.md`).
- **event 스키마는 공유 계약**: `outbox-poller/src/main/resources/sql/schema.sql`의 `outbox`/`dlq` 테이블·인덱스는 **모든 서비스가 기록하는 대상**이다. 컬럼/인덱스 변경은 common-outbox 엔티티(`Outbox`/`Dlq`)·생산 서비스 전체와 함께 본다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`.../outbox/OutboxEventScheduler.java`](src/main/java/org/example/outboxpoller/outbox/OutboxEventScheduler.java) | GENERAL/BROADCAST 폴링(`publishPending`) |
| [`.../dlq/DlqEventScheduler.java`](src/main/java/org/example/outboxpoller/dlq/DlqEventScheduler.java) | DLQ 폴링(상태 게이트) |
| [`.../infra/event/KafkaEventPublisher.java`](src/main/java/org/example/outboxpoller/infra/event/KafkaEventPublisher.java) | `EventPublisherPort` 구현(헤더·목적지·StreamBridge) |
| [`.../dlq/DlqPollerController.java`](src/main/java/org/example/outboxpoller/dlq/DlqPollerController.java) · [`DlqPollerState.java`](src/main/java/org/example/outboxpoller/dlq/DlqPollerState.java) | DLQ 런타임 start/stop |
| [`.../infra/datasource/DataSourceConfig.java`](src/main/java/org/example/outboxpoller/infra/datasource/DataSourceConfig.java) | event DB 데이터소스 + `transactionManager` |
| `src/main/resources/sql/schema.sql` | `outbox`/`dlq` 스키마(공유) |
| `../git-config-repo/dynamic/outbox-poller.yml` | 폴링 주기/배치/재시도·트랜잭션·DB |

## 검증 명령

- 컴파일: `./gradlew :outbox-poller:compileJava`
- 테스트: `./gradlew :outbox-poller:test`
- 서비스 CI: `./gradlew outboxPollerCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/OUTBOX_POLLER.md §7`](../docs/modules/OUTBOX_POLLER.md)와 [`../TODO.md`](../TODO.md).

- DLQ 제어 API(`PUT /dlq-poller/start|stop`) 모듈 계층 인증 미확인(정지 시 DLQ 재처리 중단)
