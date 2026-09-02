# ChatMessage 쓰기 비용 측정 결과

WebSocket 경로를 제외하고 Kafka에 `chatmessage-event`를 직접 발행해 ChatMessage 소비·Mongo 영속
경계만 비교한 결과다. 상세 원본은 회차별 `2026-09-02/raw/`에 보관한다.

## 최종 비교

두 회차 모두 테스트 방 1개, 멤버 302명, 초당 100건, 총 6,000건 조건으로 실행했다. before는
#283 이미지, after는 #291까지 반영된 이미지다. 두 회차 모두 6,000건 저장과 Kafka lag 0을 확인했고,
after는 `chat_room`의 메시지 수·`msgCnt`·`latestMsgSeq`도 모두 6,000으로 검증했다.

처음 실행한 after-01은 #291 이전 이미지에서 방 watermark 네이밍 버그가 드러나 비교에 사용할 수
없었다. 따라서 동일한 조건으로 #291 수정 이미지를 배포한 뒤 다시 실행한 after-02를 정상 after
결과로 채택했다.

| 지표 | before (#283) | after (#291) | 변화 |
|---|---:|---:|---:|
| Mongo 메시지 persistence write op | 1,824,039 | 6,000 | 99.67% 감소, 약 304배 감소 |
| 메시지당 persistence write op | 304.007 | 1.000 | 99.67% 감소 |
| Mongo transaction commit | 6,000 | 4,652 | 22.5% 감소 |
| 평균 persistence batch 크기 | 단건 | 1.29건 | 배치 처리 확인 |
| Kafka drain 완료 | 389초 | 68초 | 82.5% 단축, 약 5.7배 개선 |
| host swap-in | 46,946MB | 4,589MB | 90.2% 감소 |

### 해석

- **#284 계측**은 after에서 batch·신규 메시지·retry를 확인할 수 있게 했다. before에는 해당 앱
  계측이 없어 `미노출`이다.
- **#285 배치 영속**은 6,000개 단건 트랜잭션을 4,652개 batch 처리로 줄였고, 메시지당 Mongo
  persistence 비용을 `304.007 → 1.000`으로 낮췄다.
- **#286 방 watermark**는 membership 점수 fan-out 대신 방 단위 상태를 누적한다. after에서
  `messages = msgCnt = latestMsgSeq = 6,000`으로 누적 정합성을 확인했다.
- **#287 projector / #288 projection 전환**은 메시지 저장 트랜잭션에서 멤버별 membership write를
  제거하고 Redis projection으로 분리했다. before의 약 181만 Mongo update가 after에는 메시지
  저장 경로에서 사라졌다.
- drain 시간과 swap-in도 크게 줄었지만, 개발 호스트의 전역 자원과 background 작업 영향을 받으므로
  보조 지표로만 해석한다. 제품 처리량·운영 메모리 개선을 단일 회차로 인증하는 수치는 아니다.

## 원본

| 회차 | 이미지 | 결과 |
|---|---|---|
| before-01 | `noah0504/crypto-chat-service:01dbc8f` | [raw](2026-09-02/raw/) |
| after-02 | `noah0504/crypto-chat-service:latest` (#291) | [raw](2026-09-02/raw/) |

워밍업 회차와 정합성 버그가 있던 after-01 회차는 비교 원본으로 보관하지 않는다.
