# WebSocket Gateway k6 — 채팅 팬아웃 부하테스트 하네스

현재 Gateway 계약(`/ws`, `/ws-native`, `/msg/chat.send`, `/topic/chat/{roomId}`)에 맞춘 STOMP 부하테스트다.
**측정 결과와 판정은 [`chat/load-test-results/.../README.md`](../../chat/load-test-results/chatmessage/websocket-gateway/README.md)에 있고, 여기는 그 결과를 만든 도구다.**

```
k6/
├── run.sh                                측정 진입점(JFR 녹화 · swapin 기록 포함)
├── k6.env.example                        접속 주소·ROOM_ID 틀. 실제 k6.env 는 저장소에 두지 않는다
├── scenarios/chat-message-fanout-native.js   본 측정 시나리오
├── tools/                                계정 발급 · 방 멤버 등록 · 데이터 초기화 · 지표 수집
└── legacy/                               SockJS 시절 시나리오와 이전 실행 스크립트
```

### 저장소에 넣는 것과 넣지 않는 것

| | 어디에 | 왜 |
|---|---|---|
| 도구·시나리오·실행 스크립트 | 저장소 | 재현 수단 |
| **보존할 회차의 결과** | [`chat/load-test-results/chatmessage/websocket-gateway/`](../../chat/load-test-results/chatmessage/websocket-gateway/) | 문서가 근거로 삼는 값. 2026-08-30 회차 원본 43개가 여기 있다 |
| `results/` | 로컬만(`.gitignore`) | 웜업·스모크·프로브까지 매 회차 쌓이는 작업 디렉터리다(JFR 포함 70MB 규모). 회차가 끝나면 **볼 회차만 골라 위로 옮긴다** |
| `accounts/` | 로컬만(`.gitignore`) | 파일 내용이 **실제 서명된 토큰 300개**다. 마스킹하면 재현에 쓸 수 없으므로 형식만 아래에 남긴다 |
| `k6.env` | 로컬만(`.gitignore`) | 접속 주소·방 ID |

**k6 로그 자체에는 토큰이 남지 않는다**(확인함) — 시나리오가 찍는 것은 VU 번호와 목적지뿐이다.
그래서 결과 파일은 그대로 옮겨 커밋할 수 있다.

`accounts/test-users.json` 형식(`mint-test-users.py` 산출물, 시나리오가 VU 순서로 나눠 쓴다):

```json
[
  { "userId": "00000000-0000-0000-0000-000000000000", "token": "eyJhbGciOi..." },
  { "userId": "11111111-1111-1111-1111-111111111111", "token": "eyJhbGciOi..." }
]
```

## 순서

```bash
cp k6.env.example k6.env && vi k6.env      # WS_BASE_URL · ROOM_ID
tools/mint-test-users.py 300               # 테스트 계정 300개 + 토큰 발급 → accounts/
tools/verify-test-users.py                 # 서명이 실제로 검증되는지 오프라인 확인
tools/seed-room-members.sh                 # 그 계정들을 ROOM_ID 방 멤버로 등록
./run.sh 20 30 warmup                      # 웜업(버린다)
./run.sh 100 60 run                        # 측정
```

되돌릴 때:

```bash
REMOVE=1 tools/seed-room-members.sh        # 방 멤버에서 제거
tools/reset-chat-data.sh                   # 메시지·멤버십·Redis 캐시 초기화
```

결과는 `results/<라벨>-vu<VUS>-msg<N>-<타임스탬프>.{txt,meta}` 로 남는다. `.meta` 에 대상 컨테이너와
**회차 중 swapin 증가량**이 들어간다.

## 계정을 VU 마다 따로 쓴다

이전 측정은 계정 2개에 VU 100 을 붙였다. 그러면 한 계정에 세션이 50개씩 달리고
`convertAndSendToUser` 로 나가는 것(ACK·뱃지)이 세션 수만큼 증폭된다. 실제 사용자 100명이면
세션은 계정당 1개다. **즉 ACK 부하가 50배 부풀려진 값을 재고 있었다.**

계정별 rate limit(→ `../../TODO.md` 1.13)도 같은 이유로 켤 수 없었다. 계정을 나누면 켤 수 있다.

`accounts/test-users.json` 이 없으면 시나리오는 `k6.env` 의 `TOKEN_USER_1/2` 로 폴백한다(= 이전 회차 조건).
요약 출력의 `distinct_accounts` 로 어느 쪽이었는지 확인한다.

## user DB 는 건드리지 않는다

채팅 쓰기 권한 검사는 `ChatRoom.validateWritable` 하나뿐이고 그것이 보는 것은 `chat_room.memberIds` 안에
writerId 가 있는지다. 게이트웨이는 JWT 의 `id` 클레임만 본다(`RequiredUserIdClaimValidator`). 둘 다 user
테이블을 거치지 않는다. 그래서 필요한 것은 **UUID 와 그 UUID 를 담은 서명된 토큰**뿐이다.

`chat_room_membership` 행은 첫 메시지에서 upsert 되므로 미리 만들지 않는다. 닉네임은 조회 경로에서만
붙으므로 전송·브로드캐스트 측정에는 영향이 없다.

> **`tools/mint-test-users.py` 는 운영과 같은 Vault transit 키로 토큰을 서명한다.** config-server 의 `/sign`
> 과 같은 절차이며, config-server 를 띄우지 않으려고 Vault 를 직접 부를 뿐이다. **이 도구를 쓸 수 있다는 것은
> Vault AppRole 자격을 가졌다는 뜻이고, 그것만으로 임의 사용자의 유효 토큰을 만들 수 있다**(→ `../../TODO.md` 1.10).
> 개발 환경 전용으로만 쓰고 발급한 토큰은 `accounts/` 밖으로 내보내지 않는다.

## 자격증명

| 어디서 | 무엇 | 쓰는 곳 |
|---|---|---|
| `crypto-project-infra/service/.env` | `VAULT_ROLE_ID`·`VAULT_SECRET_ID` | 토큰 서명(`mint-test-users.py`) |
| `crypto-project-infra/infra/.env` | `MONGO_ROOT_*` | 방 멤버 등록·데이터 초기화 |

스크립트가 직접 읽고 값은 출력하지 않는다. 경로는 `SERVICE_ENV_FILE`·`INFRA_ENV_FILE` 로 바꿀 수 있다.
Vault·Mongo 컨테이너만 떠 있으면 되고 서비스 전체를 띄울 필요는 없다.

## 측정할 때 지킬 것

- **회차마다 웜업을 먼저 돌린다.** 1회차가 항상 가장 나쁘다 — 워밍업이 한 회차로 안 끝난다. 같은 조건 2회가 붙어야 인정한다.
- **실행 전후 swapin 증가량으로 회차 유효성을 판정한다.** `run.sh` 가 `.meta` 에 남긴다. `Pages free` 는 파일 캐시가 먹어도 떨어져 단독 지표가 못 된다 — 그것만 보다 정상 회차를 두 번 무효로 판정한 적이 있다(웜업 직후 6,013MB → 63MB 인데 그 회차는 정상이었다).

  | 측정 중 swapin 증가 | 판정 | 그때 관측 |
  |---|---|---|
  | 32,470 MB | 무효 | p90 41.9초인데 서버는 전 풀 거절 0 · CPU 0.5% |
  | 8,614 ~ 9,541 MB | 신뢰 불가 | 같은 조건 2회에서 p90 42.2초 vs 16.7초 |
  | 수백 MB 이하 | 유효 | 이 호스트에서는 미달성 |

  **유실·거절·큐 깊이·프레임 수는 서버가 직접 센 값이라 스왑과 무관하다.** 지연을 못 재는 회차에서도 그 지표로는 판정할 수 있다.
- **서버가 직접 센 값과 클라이언트 집계를 분리해 읽는다.** 프레임 수·큐 깊이·거절·유실은 호스트 스왑과 무관하지만 p90·ACK 성공률은 크게 흔들린다.
- **계측 자체가 측정을 바꾼다.** 1초마다 프로세스를 띄우는 샘플러(`tools/sample-outbox.sh`)를 붙인 회차만 ACK 100% → 8% 로 무너진 적이 있다. 이미 떠 있는 Prometheus·`jcmd` 를 먼저 쓴다.

## k6 를 서버와 분리해서 돌린다

로컬에서 k6 를 돌리면 **k6 가 맥 CPU 를 최대 484% 점유해 서버 컨테이너와 경합**했다. 매 실행마다 연결이
1~2개 실패하고 수치 변동이 컸다. OCI `VM.Standard.E5.Flex`(4 OCPU / 16GB)에 k6 를 올리고 **Tailscale** 로
맥에 연결하면 `WS_BASE_URL` 만 맥의 Tailscale IP 로 바꾸면 된다.

| | 로컬 k6 | 클라우드 k6 |
|---|---:|---:|
| k6 CPU (VU 60) | 247% | **79%** |
| 연결 성공 | 59/60 | **60/60** |

지연에 +126ms 가 더해진다(Tailscale DERP 릴레이 왕복 실측 75ms). 클라우드에서는 JFR·docker 제어가 없는
`legacy/run-cloud.sh` 를 썼다 — 서버가 맥에 있어 그쪽에서 따로 떠야 한다.

## 시나리오 인자

`run.sh` 가 아래를 export 한다. 직접 `k6 run` 할 때만 신경 쓰면 된다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `WS_BASE_URL` | `wss://localhost:8000` | API Gateway WebSocket Origin |
| `ROOM_ID` | 없음(필수) | 테스트 방 ID |
| `TEST_USERS_FILE` | `../accounts/test-users.json` | 시나리오 파일 기준 상대 경로 |
| `VUS` · `MESSAGE_COUNT` | `run.sh` 인자 | 동시 VU 수 · VU 당 전송 수 |
| `MESSAGE_INTERVAL_MS` | `1000` | 전송 간격 |
| `ACK_TIMEOUT_MS` · `BROADCAST_TIMEOUT_MS` | `11000` · `10000` | 정상 도착 기준(초과는 `late` 로 분리 집계) |
| `COLLECT_WINDOW_MS` | `60000` | 전송 종료 후 추가 수집 시간 |
| `WAIT_CONNECTED_MS` | `15000` | STOMP `CONNECTED` 대기 한도 |
| `START_DELAY_MS` | `15000` | 연결·구독 안정화 시간 |
| `ORIGIN` · `STOMP_HOST` | 프론트 Origin · `WS_BASE_URL` host | Native WebSocket 헤더 |

## legacy/

| 파일 | 용도 |
|---|---|
| `chat-message-fanout-sockjs.js` | SockJS 전송. 여러 k6 프로세스로 나눌 때 `LOCAL_VUS` 합이 `TOTAL_VUS` 가 되게 준다 |
| `connection-capacity-sockjs.js` | SockJS 연결·구독 유지 한계 확인(`RAMP_UP`·`HOLD_DURATION`·`RAMP_DOWN`·`HOLD_SECONDS`) |
| `run-cloud.sh` · `run-local.sh` | 2026-08-28 회차까지 쓴 실행 스크립트. `run.sh` 로 대체됐다 |

이 셋은 계정 2개 공유를 전제로 한다. **새 측정에는 쓰지 않는다.**
