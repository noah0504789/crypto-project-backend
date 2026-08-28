# 원본 로그

`chat-message-fanout-native.js` 실행 결과 그대로다. 요약 해석은 [상위 README](../README.md).

| 디렉터리 | 무엇 | 회차 |
|---|---|---|
| `local/` | 맥에서 k6 를 돌린 회차 | 2차 초반 (2026-08-27 ~ 08-28 오전) |
| `cloud/` | OCI 인스턴스에서 돌린 회차 | 2차 후반 ~ 3차, 스케일아웃 VU 100 |

파일 이름은 `<라벨>-vu<VUS>-msg<메시지수>-<타임스탬프>` 이고 확장자별로 나뉜다.

```
.txt        k6 요약 출력 (수신률·지연·ACK·배칭 지표)
.meta       실행 조건과 시작·종료 시각
.k6usage    k6 프로세스의 메모리·CPU 표본 (클라우드 회차만)
```

## 유실 — 4차 원본은 없다

**배칭(#265)·거절 태그(#266)·ACK 분리(#267) 적용 후의 회차, 그리고 피크 찾기 회차의 원본이 없다.**

```
없는 회차   batching-vu80 · batching-vu100 ×3 · tagged-vu100
            peak-vu120 ×2 · peak-vu120-2gw ×2 · peak-vu100-2gw · peak-vu110-2gw
```

OCI 인스턴스에만 있었고 **인스턴스를 종료하기 전에 내려받지 않았다.** 맥으로 백업한 것은 2026-08-28 15:29 KST 시점까지다.

**수치는 전부 [상위 README §7](../README.md) 에 옮겨져 있다** — 곡선·거절 내역(`kind` 별)·큐 깊이·활성 스레드·프레임 수·swapin·인스턴스별 분산. 매 회차 결과에서 그대로 전사했다. **잃은 것은 제3자가 원문으로 대조할 수단이고, 판정 근거 자체는 남아 있다.**

재현하려면 [`websocket-gateway/k6/run-cloud.sh`](../../../../../../websocket-gateway/k6/run-cloud.sh) 로 같은 조건을 다시 돌리면 된다.

## JFR

`jfr/gateway-vu80.jfr` 하나만 둔다. 8개(gateway/chat × VU 40·60·80·100)를 떴지만 합계 57MB 라 **판단의 근거가 된 것만** 남긴다.

이 파일이 **"처리량이 초당 바이트가 아니라 초당 프레임에 묶여 있다"** 는 결론의 근거다.

```
jdk.SocketWrite    278바이트 쓰는 데 209ms
jdk.JavaMonitorEnter  198건 (1차의 거절 경로 락 경합 3,637건은 사라진 뒤)
```

나머지 7개는 맥 `~/k6_chatmessage/results/` 에만 있다.

```bash
jfr summary gateway-vu80.jfr
jfr print --events jdk.SocketWrite gateway-vu80.jfr | head -50
```
