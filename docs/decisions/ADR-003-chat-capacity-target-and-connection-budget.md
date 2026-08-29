# ADR-003: chat 실시간 경로의 용량 목표와 커넥션 예산

- 상태: 채택됨
- 범위: chat 메시지 실시간 경로(websocket-gateway STOMP 팬아웃, chat outbox 기록)와 MySQL master 커넥션 배분

## 맥락과 결정

**(1차 측정 시점 서술 — 정정은 아래 「재측정 결과」)** 부하테스트에서 확인된 병목은 자원 고갈이 아니라 팬아웃 처리량이었다(게이트웨이 CPU 20%, GC pause 0, gRPC 저장 실패 0건인데 STOMP outbound 스레드는 상한까지 참). 같은 방의 모두가 서로의 메시지를 받으므로 전달량이 인원의 제곱으로 늘고, 이 구조에서는 "몇 명까지 되는가"를 감으로 정할 수 없다.

한편 풀 크기와 커넥션 수는 그동안 서비스별로 따로 키워 왔고, 그 결과 MySQL master의 write pool 합계가 서버 `max_connections`를 이미 넘긴 상태였다.

세 가지를 결정한다.

1. **용량 목표를 방 인원 상한 `M`으로 선언하고 `M ≤ √(C/r)`로 산정한다.** `C`는 실측 팬아웃 처리량, `r`은 사용자당 초당 발화 수다.
2. **커넥션은 서비스별 요구가 아니라 DB 단위 예산에서 배분한다.** 각 서비스가 독립적으로 pool을 키우지 않는다.
3. **두 축 모두 Little's Law로 산정하되 방향이 다르다.** DB는 `L = λ × W`로 필요 커넥션을 구하고, STOMP는 `λ = L ÷ W`로 처리량 한계를 구한다.

## 근거와 결과

### Little's Law를 양방향으로 쓰는 이유

| 축 | 형태 | 미지수 | 계산 |
|---|---|---|---|
| DB 커넥션 | `L = λ × W` | `L`(필요 커넥션) | 트랜잭션이 짧고 소요시간을 알 수 있으므로 필요 동시성을 구한다 |
| STOMP 팬아웃 | `λ = L ÷ W` | `λ`(처리량) | 스레드 수가 상한으로 고정돼 있으므로 그 상한이 내는 처리량을 구한다 |

- **DB**: chat 목표를 300 msg/s로 두고 outbox 기록은 메시지당 트랜잭션 1건, 커넥션 1건이다. 트랜잭션 소요를 넉넉히 50ms로 잡으면 `L = 300 × 0.05 = 15`다.

> **정정 (2026-08-27 측정)**: 위 `W = 50ms` 가정과 "커넥션 점유가 길어질 경로가 없다"는 서술은 **틀렸다.**
> `ChatMessageCommandService.save`는 `@Transactional` 안에서 `chatRoomPersistencePort.findById`(MongoDB)를
> 호출한 뒤 outbox를 INSERT한다. `LazyConnectionDataSourceProxy`가 없어 트랜잭션 시작 시점에 커넥션을 잡고
> **Mongo 왕복 내내 붙들고 있었다.** VU 60(60 msg/s) 실측은 다음과 같다.
>
> | 지표 | 실측 |
> |---|---:|
> | `hikaricp_connections_usage_seconds_max` | **5.139초** |
> | `hikaricp_connections_acquire_seconds_max` | 5.343초 |
> | `hikaricp_connections_active` 최대 | 30 (풀 포화) |
> | `hikaricp_connections_pending` 최대 | **118** |
> | `hikaricp_connections_timeout_total` | **360건** |
>
> 커넥션 타임아웃 360건(전송 3,600건의 10.0%)이 저장 실패로 이어져 브로드캐스트 유실
> 10.06%(21,720/216,000)를 만들었다. 같은 실행에서 `stomp_executor_rejected_total{pool="outbound"}`는
> **0건**이었다 — **병목은 팬아웃이 아니라 커넥션 점유시간이었다.**
>
> 대응은 풀 확대가 아니라 점유시간 단축이다. `LazyConnectionDataSourceProxy`를 적용해 물리 커넥션 획득을
> 첫 statement까지 미루면 Mongo 왕복이 점유에서 빠진다. 적용 후 재측정으로 `W`를 다시 잡고 이 절의 수치를
> 갱신한다.
- **STOMP**: outbound 스레드 96개, 실측 소켓 write 블로킹 22ms면 `λ = 96 ÷ 0.022 ≈ 4,400/s`다. 관측된 채널 처리량 6,000/s와 같은 자릿수다.

### 커넥션 예산

MySQL은 **master/replica 2대**다(`git-config-repo/infrastructure/mysql.yml`: write → `mysql-master`, read → `mysql-replica`). 커넥션은 서버가 가진 자원이므로 **예산도 서버별로 따로 센다.** 각 서버의 `max_connections`는 기본 151이고, 관리·복제·모니터링 exporter 접속 몫으로 16을 남겨 서버당 **앱 예산 135**로 둔다.

**mysql-master (write pool)**

| 서비스 | 변경 전 max | 변경 후 max |
|---|---:|---:|
| chat | 120 | **30** |
| notification | 15 | 15 |
| market | 10 | 10 |
| user | 10 | 10 |
| outbox-poller | 10 | 10 |
| **합계** | **165** | **75** |

**mysql-replica (read pool)**

| 서비스 | max |
|---|---:|
| market | 10 |
| **합계** | **10** |

read DataSource를 만드는 서비스는 **market 하나뿐**이다(`market-adapter-out/.../infra/config/DatasourceConfig.java`의 `readDataSource` + `ReplicationRoutingDataSource`). 나머지 넷은 write DataSource만 선언한다 — `mysql.yml`에 `spring.datasource.read.*`가 정의돼 있어도 빈을 만들지 않으면 풀은 생기지 않는다.

- 변경 전 master 합계 165는 서버 상한 151을 이미 넘겼다. chat·notification은 `min-idle == max-size`라 그만큼을 상주시키므로, 변경 전 master의 `minimum-idle` 합계만으로도 141이었다(120 + 15 + 2 + 2 + 2).
- chat만 120 → 30으로 내려 master 합계 75가 되고 앱 예산 135 안에 들어온다. 위 `L = 15` 계산 대비 2배 여유다.
- **어느 서비스든 pool을 키우려면 이 표를 함께 갱신하고, write와 read가 서로 다른 서버 예산임을 구분한다.** replica 풀을 master 예산에 합산하지 않는다.

### 용량 목표(SLO 후보)

같은 방에서 `M`명이 각자 초당 `r`건을 보낼 때 **메시지 도착 p95 ≤ 10초**를 목표로 둔다.

- 요구 전달량 = `M² × r`, 능력 = `C` → `M ≤ √(C/r)`
- **1차 측정 기준** `C`는 약 6,000 broadcast/s(버스트) → `r = 1`일 때 `M ≈ 77`. **아래에서 정정한다.**

**이 값은 후보이며 지속 부하 재측정 전까지 확정하지 않는다.** 1차 측정은 10초 발송 + 30초 드레인 버스트였다. 130명 실행의 총 169,000건을 6,000/s로 소화하면 28초로 40초 수집 창 안에 들어오므로, 지속 한계가 아니라 창 크기 덕에 통과한 값이다. 재측정 조건과 보정 항목은 `TODO.md` 5.4를 따른다.

#### 재측정 결과 (2026-08-30)

**`M ≈ 77` 이라는 추정은 틀렸다.** 60초 지속 부하에서 게이트웨이 1대로 **`M = 100`, 유실 0, ACK 실패 0** 을 세 회차 연속 재현했다.

| VU | 접속 | 전송 | ACK 성공 | ACK 실패 | 수신 / 기대 | p90 |
|---:|---:|---:|---:|---:|---|---:|
| 100 | 96 | 5,760 | **100%** | **0** | 552,960 / 552,960 | 8.64s |
| 100 | 99 | 5,940 | **100%** | **0** | 588,060 / 588,060 | 14.5s |
| 100 | 98 | 5,880 | **100%** | **0** | 576,240 / 576,240 | 4.95s |

**`C` 를 고정 상수로 본 것이 오류였다.** `M ≤ √(C/r)` 은 `C` 가 고정일 때만 성립하는데, **배칭이 `C` 자체를 바꿨다.**

```
배칭 전   전달 1건 = 프레임 1개        C 는 초당 프레임 수에 묶인다
배칭 후   전달 24~34건 = 프레임 1개    같은 프레임 예산으로 그만큼을 나른다
```

처리량이 "초당 바이트"가 아니라 **"초당 프레임"** 에 묶여 있었기 때문이다(1차 JFR: 278바이트 write 에 209ms). 그래서 요구량을 프레임 단위로 깎으면 `M` 이 올라간다.

**`M = 100` 을 만든 것은 팬아웃 개선만이 아니다.** 이번 측정에서 게이트웨이 STOMP 큐는 0~2, 거절은 모든 `kind` 에서 0 으로 팬아웃 경로는 여유가 있었다. 실제로 막고 있던 것은 셋이었다.

| 원인 | 성격 | 조치 |
|---|---|---|
| 멤버십 갱신이 멤버마다 Mongo 왕복 | 쓰기 비용이 **방 멤버 수**에 비례 | bulkWrite 한 번(PR #270) |
| outbox 행이 멤버 목록을 통째로 적재 | 행 크기가 **방 멤버 수**에 비례 | 구독 레지스트리로 대체(PR #271) |
| 컨테이너 메모리 1,792MB 미만 → SerialGC 자동 선택 | full GC 가 **초 단위** 정지 | ParallelGC 명시 |

앞의 둘은 **접속자 수가 아니라 방 멤버 수**에 비례한다. 방 멤버가 2명이던 이전 측정에서는 보이지 않았다.

**`M` 의 상한은 아직 모른다.** 100 을 넘겨 재려면 호스트가 먼저 무너져 서버 한계와 구분되지 않는다.

**지연 수치도 이 호스트의 것이다.** 16GB 맥에 컨테이너 23개를 올린 상태라 같은 조건 3회에서 p90 이 4.95초~14.5초로 3배 흔들리고, 회차별 swapin(10.9~14.9GB)과 함께 움직인다. **유실 0·ACK 실패 0 은 서버가 직접 센 값이라 이 흔들림과 무관하다.** 피크·SLO 는 운영계에서 다시 잰다.

### 지연 예산 분할이 큐 크기를 정한다

목표 10초를 두 구간으로 나눈다.

| 구간 | 예산 | 큐 |
|---|---:|---|
| inbound + gRPC save + Kafka + broker | 5초 | inbound 600(300 msg/s × 2초), broker 3,000(300 msg/s × 10초) |
| outbound 큐 대기 | 5초 | outbound 30,000 = 6,000/s × 5초 |

broker 큐는 **팬아웃 이전** 메시지를 세고 outbound 큐는 **팬아웃 이후** 전송을 센다. 세는 대상이 달라서 크기 차이가 난다. outbound 30,000은 항목당 약 1KB로 잡아 약 30MB이며 게이트웨이 `Xmx 512m` 안이다(부하 중 heap 사용률 확인 대상).

**큐를 키우는 것은 지연 예산을 소진하는 것이지 처리량을 늘리는 것이 아니다.** `C`를 올리지 못하면 큐는 더 오래 기다리게 할 뿐이다.

### 결과

- 큐가 포화하면 팬아웃 태스크는 **버리고 카운터만 남긴다**(`stomp.executor.rejected{pool,kind}`). **`kind` 로 무엇이 버려졌는지 갈라 읽는다** — 브로드캐스트 1건은 방 전원, ACK 1건은 발신자가 결과를 영영 모른다, 뱃지는 다음 창이 덮는다. 브로드캐스트 push는 DLQ·재시도가 없는 best-effort 계약이다. 다만 **클라이언트가 유실을 감지해 재조회하는 경로는 아직 없다** — 방 재진입·새로고침 시에만 REST 조회로 복구되고, wire payload(`StompChatMessagePayload`)에 방별 순번이 없어 갭 감지 자체가 불가능하다. 이 상태에서 shedding을 택한 이유는 피크에서 전원을 지연시키는 것보다 일부 유실이 위 SLO에 유리하기 때문이며, 클라이언트 갭 복구는 `TODO.md` 5.5로 남긴다.
- **ACK 는 `brokerChannel` 을 지나지 않는다.** `LocalSessionCache` 에서 세션과 구독 ID 를 찾아 `clientOutboundChannel` 로 직접 보낸다. `chatMessageAckExecutor` 의 `CallerRunsPolicy` 는 그 앞단이라 브로커 큐 포화를 막지 못했다 — **방어 위치가 틀렸었다.** 배칭·conflation 으로 나머지를 줄이자 ACK 가 broker 태스크의 97% 가 됐고, 거절 태그가 그것을 지목했다.
- 실행기 설정은 `git-config-repo/dynamic/`에 있지만 **busrefresh로는 반영되지 않는다.** 풀은 `initialize()` 시점에 만들어지고 이 코드베이스는 `@RefreshScope`를 쓰지 않는다. 값 변경은 재배포로 반영한다.
- **300명 목표는 배칭 없이 불가능하다.** 요구 90,000/s 대 능력 6,000/s다. **배칭은 적용돼 있다**(방별 100ms, [부하테스트 문서 §3-0](../../chat/load-test-results/chatmessage/websocket-gateway/README.md)). STOMP wire payload 가 봉투(`{roomId, messages[]}`)가 되는 **외부 계약 변경**이었고 프론트를 함께 고쳤다. 2026-08-30 실측 프레임당 24~34건이다.
- **입구 거절(백프레셔)은 운영계 이전 후에 넣는다**(→ `TODO.md` 5.14). 임계값은 "이 부하에서 이만큼 밀리면 SLO 를 넘긴다"는 실측에서 나오는데, 개발계 수치로 박으면 운영계에서 다시 재야 한다. 그때까지 inbound 큐 거절은 발신자에게 통보되지 않는다.

## 관련 근거

- 실패 경로 전체 표(거절이 어디서 무엇을 잃는가): [`../SERVICE_FLOWS.md` §15](../SERVICE_FLOWS.md). **거절 카운터는 `pool` 태그로 분리해 읽는다** — broker 1건은 방 전원, outbound 1건은 1명이다.
- 측정 원본: [`chat/load-test-results/chatmessage/websocket-gateway/README.md`](../../chat/load-test-results/chatmessage/websocket-gateway/README.md) — **결과는 §2**
- 후속 과제: `TODO.md` 5.4(VU별 자격증명), 5.5(클라이언트 갭 복구), 5.12(측정 환경 분리), 5.14(입구 거절)
- 설정: `git-config-repo/dynamic/websocket-gateway.yml`, `git-config-repo/dynamic/chat-service.yml`, `git-config-repo/infrastructure/mysql.yml`
- 구현: `websocket-gateway/websocket-gateway-adapter-in/.../websocket/config/{ExecutorConfig,StompExecutorProperties}.java`
- 관련 규칙: `docs/CODE_STYLE.md` §16(대량 broadcast의 channel contention·backpressure)
