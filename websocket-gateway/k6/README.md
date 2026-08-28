# WebSocket Gateway k6

현재 Gateway 계약(`/ws`, `/ws-native`, `/msg/chat.send`, `/topic/chat/{roomId}`)에 맞춘 STOMP 부하테스트다.
서버 주소, 방 ID, 사용자 토큰과 writer ID는 저장소에 두지 않고 k6 `-e` 옵션으로 전달한다.

## 스크립트

| 파일 | 용도 |
|---|---|
| `chat-message-fanout-native.js` | Native WebSocket 메시지 전송, ACK·Broadcast·뱃지 수집. **2026-08 측정에 쓴 것** |
| `chat-message-fanout-sockjs.js` | SockJS 메시지 전송. 단일/분산 k6 실행을 모두 지원 |
| `connection-capacity-sockjs.js` | SockJS 연결·구독 유지 한계 확인 |
| `run-cloud.sh` | 클라우드(k6 전용 인스턴스)에서 실행. **2026-08 측정에 쓴 것** |
| `run-local.sh` | 맥에서 실행. JFR 녹화와 docker 제어를 함께 한다 |
| `k6.env.example` | 두 스크립트가 읽는 환경 파일의 틀. 실제 `k6.env` 는 저장소에 두지 않는다 |

### 실행

```bash
./run-cloud.sh <VUS> [MESSAGE_COUNT] [라벨]     # 예: ./run-cloud.sh 100 60 peak-vu100
```

결과는 `results/<라벨>-vu<VUS>-msg<N>-<타임스탬프>.{txt,meta,k6usage}` 로 남는다.

## k6 를 서버와 분리해서 돌린다

로컬에서 k6 를 돌리면 **k6 가 맥 CPU 를 최대 484% 점유해 서버 컨테이너와 경합**했다. 매 실행마다 연결이 1~2개 실패하고 수치 변동이 컸다.

OCI `VM.Standard.E5.Flex`(4 OCPU / 16GB)에 k6 를 올리고 **Tailscale** 로 맥에 연결한다. `WS_BASE_URL` 만 맥의 Tailscale IP 로 바꾸면 된다.

| | 로컬 k6 | 클라우드 k6 |
|---|---:|---:|
| k6 CPU (VU 60) | 247% | **79%** |
| 연결 성공 | 59/60 | **60/60** |

지연에 +126ms 가 더해진다(Tailscale DERP 릴레이 왕복 실측 75ms). 직접 연결(direct)은 확립되지 않아 릴레이 경유다.

## 측정할 때 지킬 것

- **회차마다 웜업을 먼저 돌린다.** 1회차가 항상 가장 나쁘다 — 워밍업이 한 회차로 안 끝난다. 같은 조건 2회가 붙어야 인정한다.
- **실행 전후 swapin 증가량을 기록한다.** `Pages free` 는 파일 캐시가 먹어도 떨어져 단독 지표가 못 된다(→ `TODO.md` 5.15).
- **서버가 직접 센 값과 클라이언트 집계를 분리해 읽는다.** 프레임 수·큐 깊이·거절·유실은 호스트 스왑과 무관하지만 p90·ACK 성공률은 크게 흔들린다.

VU, 메시지 수와 간격만 다른 기존 130/150/200 스크립트는 위 파일로 통합했다.

## 공통 인자

| 환경변수 | 기본값 | 적용 | 설명 |
|---|---:|---|---|
| `WS_BASE_URL` | `wss://localhost:8000` | 공통 | API Gateway WebSocket Origin |
| `ROOM_ID` | 없음 | 공통 | 테스트 방 ID, 필수 |
| `TOKEN_USER_1`, `TOKEN_USER_2` | 없음 | 공통 | 테스트 사용자 Access Token, 필수 |
| `WRITER_ID_1`, `WRITER_ID_2` | 없음 | 공통 | Token에 대응하는 사용자 ID, 필수 |
| `WAIT_CONNECTED_MS` | Native `15000`, SockJS 메시지 `20000`, 연결 `15000` | 공통 | STOMP `CONNECTED` 대기 한도 |
| `VUS` | 메시지 `130`, 연결 `2000` | 공통 | 동시 가상 사용자 수 |
| `ORIGIN` | `http://localhost:5173` | `chat-message-fanout-native.js` | Native WebSocket `Origin` Header |
| `STOMP_HOST` | `WS_BASE_URL`의 host:port | `chat-message-fanout-native.js` | Native STOMP `host` Header |

필수 인자는 셸 히스토리에 남지 않도록 환경 파일로 넘긴다. 세 스크립트 모두 같은 방식이다.

```bash
# k6.env (git에 커밋하지 않는다)
WS_BASE_URL=wss://localhost:8000
ROOM_ID=<room-id>
TOKEN_USER_1=<token-1>
WRITER_ID_1=<user-id-1>
TOKEN_USER_2=<token-2>
WRITER_ID_2=<user-id-2>
```

```bash
set -a && . ./k6.env && set +a
k6 run --insecure-skip-tls-verify \
  -e WS_BASE_URL -e ROOM_ID \
  -e TOKEN_USER_1 -e WRITER_ID_1 -e TOKEN_USER_2 -e WRITER_ID_2 \
  websocket-gateway/k6/chat-message-fanout-native.js
```

`-e NAME`은 값을 적지 않으면 셸 환경변수에서 그대로 가져온다. 아래 예제들은 이 공통 인자가 이미 주입된 상태를 전제한다.

현재 스크립트는 두 인증 사용자를 VU가 번갈아 사용한다. 연결·STOMP·팬아웃 처리량 측정에는 사용할 수 있지만,
사용자별 Rate Limit 검증에서는 모든 VU가 독립 사용자라는 의미가 아니다. 사용자 버킷 검증 시에는 VU별 자격증명 공급 기능을 추가해야 한다.

**ACK 팬아웃 주의.** ACK는 `convertAndSendToUser`로 사용자 단위 전송되므로, 두 계정을 공유하면 한 사용자의 ACK 한 건이
그 사용자로 접속한 모든 세션(약 `VUS / 2`)에 복제된다. 스크립트는 자기 `clientMessageId`만 집계해 수치는 왜곡되지 않지만,
서버가 실제로 내보내는 메시지 수는 실사용보다 크다. 계정을 VU마다 분리하면 이 증폭은 사라진다.

ACK 성공률(`ack_ok_rate`)은 `success=true` ACK만 성공으로 센다. 서버는 검증·처리 실패도 ACK(`success=false`, `errorCode`)로
회신하므로 이 값은 `ack_failed_count`(거절), `ack_timeout_count`(무응답)와 함께 읽는다.

## 메시지 테스트

추가 인자는 다음과 같다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `MESSAGE_COUNT` | `10` | VU당 전송 메시지 수 |
| `MESSAGE_INTERVAL_MS` | `1000` | 메시지 전송 간격 |
| `START_DELAY_MS` | Native `15000`, SockJS `10000` | 연결·구독 안정화 시간 |
| `ACK_TIMEOUT_MS` | `10000` | ACK 정상 도착 기준 |
| `BROADCAST_TIMEOUT_MS` | `10000` | Broadcast 정상 도착 기준 |
| `COLLECT_WINDOW_MS` | Native `30000`, SockJS `60000` | 지연·유실 판정 전 추가 수집 시간 |

기존 Native WebSocket 결과의 130명 조건은 다음 명령으로 재현한다.

```bash
k6 run --insecure-skip-tls-verify \
  -e WS_BASE_URL=wss://localhost:8000 \
  -e ROOM_ID=<room-id> \
  -e TOKEN_USER_1=<token-1> -e WRITER_ID_1=<user-id-1> \
  -e TOKEN_USER_2=<token-2> -e WRITER_ID_2=<user-id-2> \
  -e VUS=130 -e MESSAGE_COUNT=10 -e MESSAGE_INTERVAL_MS=1000 \
  websocket-gateway/k6/chat-message-fanout-native.js
```

`VUS=150` 또는 `VUS=200`만 바꾸면 기존의 다른 부하 단계가 된다. 운영 인증정보는 셸 히스토리에 남지 않도록
별도 환경 파일이나 Secret 주입 방식을 사용한다.

SockJS 단일 프로세스 실행은 `chat-message-fanout-sockjs.js`에 같은 인자를 전달한다. 여러 k6 프로세스로 나눌 때는
각 프로세스의 `LOCAL_VUS` 합이 `TOTAL_VUS`가 되게 설정한다.

```bash
INSTANCE_NAME=k6-a LOCAL_VUS=65 TOTAL_VUS=130 k6 run \
  -e INSTANCE_NAME -e LOCAL_VUS -e TOTAL_VUS \
  -e WS_BASE_URL -e ROOM_ID \
  -e TOKEN_USER_1 -e WRITER_ID_1 -e TOKEN_USER_2 -e WRITER_ID_2 \
  -e MESSAGE_COUNT=10 -e MESSAGE_INTERVAL_MS=1000 \
  websocket-gateway/k6/chat-message-fanout-sockjs.js
```

## 연결 테스트

`connection-capacity-sockjs.js`는 `RAMP_UP`, `HOLD_DURATION`, `RAMP_DOWN`으로 단계 시간을 받고,
연결 후 실제 유지 시간은 `HOLD_SECONDS`로 받는다.

```bash
k6 run --insecure-skip-tls-verify \
  -e WS_BASE_URL -e ROOM_ID \
  -e TOKEN_USER_1 -e WRITER_ID_1 -e TOKEN_USER_2 -e WRITER_ID_2 \
  -e VUS=2000 -e RAMP_UP=10s -e HOLD_DURATION=20s -e RAMP_DOWN=5s -e HOLD_SECONDS=20 \
  websocket-gateway/k6/connection-capacity-sockjs.js
```
