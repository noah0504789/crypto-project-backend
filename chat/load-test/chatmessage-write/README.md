# ChatMessage 쓰기 비용 부하테스트

`chatmessage-event`를 Kafka에 직접 발행해 `ChatMessageEventService` 이후의 Mongo 영속 경로만
측정한다. WebSocket 연결·ACK·브로드캐스트·gateway gRPC·MySQL Outbox를 통과하지 않으므로
메시지 저장과 방 상태 갱신, 기존 membership fan-out의 비용을 분리해 비교할 수 있다.

```text
produce-events.py → Kafka chatmessage-event → chat-service consumer → MongoDB
                              └─ 저장 완료: run 메시지 수 + consumer lag 0
```

## 디렉터리

```text
chat/load-test/chatmessage-write/
├── run.sh                    발행·drain 대기·지표 수집·요약
├── benchmark.env.example     테스트 방 설정 예시
├── tools/
│   ├── produce-events.py     Kafka wire 형식의 이벤트를 일정 rate로 생성
│   ├── reset-data.sh         테스트 방의 메시지·membership·watermark·Redis projection 초기화
│   └── summarize.py          Mongo·Micrometer 전후 차이 요약
└── results/                  로컬 실행 산출물(gitignore)
```

보존할 before/after 원본과 결과 문서는 `chat/load-test-results/chatmessage/write/`로 옮긴다.

## 왜 Kafka에 직접 넣는가

측정 대상 #284~#288은 Micrometer 계측, Kafka batch 소비, Mongo batch insert·방 watermark,
membership Mongo fan-out 제거다. gRPC `storeChatMessage`만 직접 호출하면 Outbox와 Kafka batch
consumer를 우회하고, 기존 WebSocket k6 하네스를 쓰면 ACK·브로드캐스트와 gateway 자원 경합이
결과에 섞인다. Kafka 입력은 개선 전후가 공유하는 가장 가까운 안정 경계다.

이 하네스는 #283 호환을 위해 테스트 방의 `memberIds` 스냅샷을 이벤트에 포함한다. #283은 이를
membership 갱신에 사용하고 #288은 제거된 필드를 무시한다. 따라서 두 이미지에 동일한 wire 입력을
주면서 방 멤버 수에 비례하던 Mongo 문서 쓰기가 사라졌는지 비교할 수 있다.

## 실행

필수 도구는 `bash`, Python 3, `jq`, Docker다. chat-service, Kafka, Mongo replica set,
Mongo exporter, Kafka exporter, Prometheus가 떠 있어야 한다.

```bash
cd chat/load-test/chatmessage-write
cp benchmark.env.example benchmark.env
vi benchmark.env

# 웜업 후 반드시 다시 초기화한다.
YES=1 tools/reset-data.sh
./run.sh 600 100 warmup
YES=1 tools/reset-data.sh

# 기존 k6의 100 VU × 초당 1건 × 60초와 같은 입력률이다.
./run.sh 6000 100 before-01
```

after 이미지를 배포한 뒤 같은 방·멤버 수에서 초기화부터 반복한다. `run.sh`는 시작 시 테스트 방
메시지·room membership·watermark가 남아 있거나 consumer lag이 0이 아니면 실행을 거부한다.

## 결과와 판정

`results/<label>-<timestamp>`에 다음을 남긴다.

| 파일 | 의미 |
|---|---|
| `.meta` | 대상 이미지 digest, 입력 조건, 저장 건수, drain·swapin |
| `.mongo-{before,after}.json` | primary `serverStatus`의 operation·document·transaction 누적값 |
| `.metrics-{before,after}-chat.prom` | chat-service Micrometer 원문 |
| `.prometheus.json` | 실행 구간의 chat persistence·Mongo·Kafka 시계열 |
| `.summary.md` | 전후 차이와 메시지당 write operation 수 |

핵심 판정값은 다음 순서로 본다.

1. 발행 수와 Mongo에 저장된 run 메시지 수가 같고 consumer lag이 0이어야 한다.
2. `opcounters.insert + update` 차이를 저장 메시지 수로 나눠 메시지당 persistence write operation을 비교한다.
   이 저장 경로에서 발생하지 않는 `delete`는 primary의 다른 작업이 섞였는지 확인하는 참고값으로만 본다.
3. after에서는 `chat_message_persistence_batch_*`로 batch 수와 평균 크기, retry를 확인한다.
4. `opcounters.update`는 bulkWrite 안의 update model도 각각 센다. `serverStatus`는 primary 전역
   누적값이므로 같은 MongoDB를 쓰는 다른 부하가 없어야 차이를 chat 비용으로 해석할 수 있다.

#283에는 #284의 custom metric이 없으므로 before 요약의 앱 계측 항목은 `미노출`이 정상이다. 전후 공통
비교값은 Mongo op counter와 저장 건수, Kafka drain이며 custom metric은 after 처리 구조를 설명하는 데 쓴다.

절대 지연이나 운영 처리량을 인증하는 테스트는 아니다. producer와 서버가 같은 개발 호스트를 쓰므로
drain 시간과 swapin은 회차 유효성·이상 징후를 판단하는 보조값으로만 사용한다.

## 의도적으로 측정하지 않는 것

- WebSocket 연결, STOMP ACK·브로드캐스트, gateway 큐와 클라이언트 수신율
- gRPC 저장과 MySQL Outbox 생성·polling 비용
- Redis 메시지 캐시와 activity projector 비용
- API 전체의 종단 지연

위 항목은 `websocket-gateway/k6` 하네스의 책임이다. 이 테스트 결과로 WebSocket 정확성이나 전체
사용자 경로 성능을 주장하지 않는다.
