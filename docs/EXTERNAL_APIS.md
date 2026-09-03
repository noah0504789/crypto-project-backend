# 외부 API

이 문서는 외부 시스템 연동과 그에 따른 주요 이벤트 흐름을 정리한다.

## 업비트(Upbit) API

- **웹소켓 연결**을 통해 등록된 market에 대한 **실시간 시세(ticker)를 수집**한다.
- 수집 흐름: `upbit-connector` 서비스가 Upbit WebSocket으로 시세를 받아 Kafka(`upbit-ticker-event`)로 발행한다. `market-detection` 서비스는 이를 소비해 **Kafka Streams**로 단기 이동평균 대비 변화율을 계산하고, 임계값을 넘으면 가격 알림 탐지 이벤트를 발행한다. 이후 `notification` 서비스가 이벤트를 소비해 사용자 알림으로 만든다.
- 상세 흐름은 [`docs/SERVICE_FLOWS.md`](SERVICE_FLOWS.md) §12~15, 모듈별 동작은 [`docs/modules/UPBIT_CONNECTOR.md`](modules/UPBIT_CONNECTOR.md)와 [`docs/modules/MARKET_DETECTION.md`](modules/MARKET_DETECTION.md)를 참고한다.

