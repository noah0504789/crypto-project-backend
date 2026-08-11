# ADR-002: 도메인 이벤트의 Kafka 전달에 Outbox + outbox-poller 사용

- 상태: 채택됨 (기존 구현을 문서화)
- 범위: 도메인 상태 변경 뒤 Kafka 이벤트 전달과 DLQ 처리

## 맥락과 결정

도메인 변경과 외부 Kafka 발행을 분리해 전달 실패·재시도·추적을 관리한다. Kafka로 전달되는 도메인 이벤트는 직접 발행하지 않고 **Domain Event → Outbox 저장 → outbox-poller → Kafka** 흐름을 사용한다.

현재 `EventUtils.raise(list)` → `OutboxEventListListener` → `OutboxService`가 Outbox를 기록하고, `outbox-poller`가 general/broadcast/DLQ를 폴링해 `KafkaEventPublisher`로 전달한다.

## 근거와 결과

- Outbox 레코드로 발행 실패·재시도·DLQ 상태를 추적한다.
- producer 비즈니스 로직은 브로커 전송 세부를 직접 갖지 않는다.
- `common-outbox`의 상태 전이 메서드로 실패 처리를 일관되게 한다.
- topic/header/payload/`__TypeId__` 변경은 producer·consumer·DLQ·설정의 계약 변경이다.
- 상태 필드를 직접 수정하거나 실패를 로그만 남기고 삼키지 않는다. 의미적 적합성은 `arch-reviewer`, 관련 테스트, 계약 영향 조사를 함께 사용한다.

## 관련 근거

- `docs/ARCHITECTURE.md` §8.4–8.5
- `docs/modules/COMMON.md`의 Outbox/DLQ 절
- `.claude/rules/architecture.md`, `.claude/rules/external-contracts.md`
