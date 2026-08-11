# Architecture Decision Records

이 디렉터리는 시스템 전반에 영향을 주고, 코드만으로 선택 이유를 알기 어려우며, 되돌리기 비용이 큰 결정을 기록한다.

| ADR | 상태 | 결정 |
|---|---|---|
| [ADR-001](ADR-001-grpc-for-interservice-synchronous-calls.md) | 채택됨 | 서비스 간 동기 호출에 gRPC contract/client 모듈 사용 |
| [ADR-002](ADR-002-outbox-for-kafka-delivery.md) | 채택됨 | 도메인 이벤트의 Kafka 전달에 Outbox + outbox-poller 사용 |

전역 구조에 영향을 주지 않는 구현·버그 수정·기존 convention 준수는 ADR 대상이 아니다. 미확정 검토 사항은 `TODO.md`에 기록한다.
