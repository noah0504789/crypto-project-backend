# TODO — 미해결 확인/결정 항목

관찰된 사실을 **주제별 섹션**으로 나누고, 각 섹션 안에서는 **모듈별(`###`)로** 그룹핑했다. 각 항목은 코드/설정 변경 전 사용자 확인이 필요하며, **확정된 결함으로 단정하지 않는다**(리포지토리 공통 원칙: 코드만으로 의도를 알 수 없는 항목을 임의로 설계/버그로 판정하지 않는다).

- `[출처: …]` = 최초 관찰 문서. 여러 문서에서 중복 관찰된 항목은 하나로 합치고 출처를 모두 표기했다.
- 각 항목은 섹션 내 `###` 모듈 헤더로 묶었다(여러 모듈에 걸친 항목은 관련 모듈을 `·`로 함께 표기).
- **이 파일이 확인/결정 항목의 단일 관리처다.** 상세 근거는 각 항목 본문에 통합했고, `docs/modules/*.md`의 "확인 필요 항목" 절은 여기의 관련 TODO 번호만 참조한다(내용 중복 없음).

---

## 1. 인증 · 인가 · 보안

### oauth2-authorization-server

#### 1.1 JWT `aud`/`jti` 검증 부재
게이트웨이 검증 체인은 issuer + blacklist + `id` claim만 확인(`ReactiveJwtDecoderConfig`). `aud`/`jti` 검증 코드는 확인되지 않음. 계약으로 둘지/validator를 추가할지 결정 필요. 현재 issuer가 단일이라 실제 위험도는 미판정.
- `[oauth2-as]` 발급측 분석: `TokenConfig` access 커스터마이저는 `roles`·`id` claim만 명시 추가한다. 표준 `aud`(=client) 포함 여부는 Spring `JwtGenerator` 기본 동작에 의존하며 모듈 커스텀 코드에서 설정하지 않음 → 실제 발급 토큰으로 `aud` 유무 확인 필요.
`[출처: SERVICE_FLOWS.md, ARCHITECTURE.md §2, API_GATEWAY.md §7 / oauth2-authorization-server 분석]`

#### 1.4 토큰 엔드포인트 TLS 미적용
`oauth2-authorization-server.yml`의 `server.port: 9000` 옆에 `# TODO: tsl` 주석. 내부 토큰 엔드포인트(HTTP) TLS 적용 계획 확인 필요.
`[출처: docs/modules/OAUTH2_AUTHORIZATION_SERVER.md §14]`

### oauth2-client

#### 1.5 Access Token URL 노출
- 로그인 성공 redirect에서 `?accessToken=` 쿼리로 토큰 전달(`CustomOAuth2LoginSuccessHandler`).
- WebSocket 핸드셰이크에서 `?access_token=` 쿼리로 토큰 전달(`WebsocketHandshakeAuthWebFilter.java:44`).
- 쿼리 파라미터는 프록시 로그·브라우저 히스토리에 남을 수 있음. 인프라 마스킹 여부와 대체 방식(헤더/서브프로토콜) 도입 여부 확인 필요(브라우저 WebSocket API 제약 고려).
- `[oauth2-client]` 로그인 redirect의 `?accessToken=` 생성 지점 확인됨: `CustomOAuth2LoginSuccessHandler`가 token-exchange 후 `frontend.successRedirectUri`에 쿼리로 붙여 `sendRedirect`.
`[출처: SERVICE_FLOWS.md §3, ARCHITECTURE.md §4, API_GATEWAY.md §6 / oauth2-client 분석]`

#### 1.6 로그아웃 시 JWT 미검증 파싱
`CustomLogoutSuccessHandler.resolveSubject`는 JWT 검증 실패(`JwtValidationException`) 시 서명 미검증으로 subject를 파싱(`parseSubjectWithoutValidation`)해 블랙리스트/토큰 삭제에 사용한다. 만료 토큰으로도 로그아웃을 허용하려는 의도로 보이나, 서명 미검증 파싱을 어디까지 허용할지 확인 필요.
`[출처: docs/modules/OAUTH2_CLIENT.md §12]`

### user

### spring-cloud-config

#### 1.10 `/sign`·JWKS·`/actuator/busrefresh` 엔드포인트 인증 부재
spring-cloud-config는 `POST /sign`(Vault Transit RS256 서명 대행)·`GET /.well-known/jwks.json`·`POST /actuator/busrefresh`(Spring Cloud Bus 설정 전파)를 노출하나, 모듈 adapter-in에 `SecurityFilterChain`이 없다(`SecurityConfig`는 RSA `KeyFactory` bean만 정의). 앱 계층 `DeploymentControlAuthFilter`는 `/internal/deployment/**`만 검사해 이 엔드포인트들을 보호하지 않는다(config bus 워크플로우는 `X-Deploy-Token`을 보내지만 busrefresh 경로에선 검증되지 않음). `/sign`은 임의 `header.payload`를 실 키로 RS256 서명해 유효 토큰 위조로 이어질 수 있고, `busrefresh`는 전 서비스 설정 재로딩을 유발할 수 있다. 현재는 내부 네트워크 격리에 의존하는 것으로 보이나 전제·의도 확인 필요(설계/결함 미판정).
`[출처: docs/modules/SPRING_CLOUD_CONFIG.md §12, docs/CI_CD.md §4 / spring-cloud-config 분석]`

### outbox-poller

#### 1.12 DLQ 제어 API 인증 부재
outbox-poller가 `PUT /dlq-poller/start|stop`(`DlqPollerController`)로 DLQ 폴링을 런타임 토글하나, 모듈 계층 인증(`SecurityFilterChain`)이 확인되지 않는다(스타터는 `web`, security 없음). `stop` 시 DLQ 재처리가 멈춰 실패 이벤트가 적체될 수 있다. 게이트웨이 라우팅(`DlqPollerController`는 게이트웨이 컨트롤러 목록에 있음)/네트워크 격리 전제와 접근 통제 여부 확인 필요(config-server 무인증 엔드포인트 1.10과 같은 성격, 설계/결함 미판정).
`[출처: docs/modules/OUTBOX_POLLER.md §5, §7]`

### websocket-gateway

#### 1.13 STOMP 채팅 Rate Limit 을 측정용으로 꺼 둔 상태다 — 되돌려야 한다

`git-config-repo/dynamic/websocket-gateway.yml`의 `app.rate-limit.chat-message.enabled`를 **`false`로 바꿔 둔 상태**다(팬아웃 용량 재측정용). **측정이 끝나면 `true`로 되돌린다.**

켜두면 측정 자체가 불가능하다. 계획한 조건은 전원이 한 방에 있고 계정 2개를 공유하는데, 한도는 room `30/s`·user `3/s`다.

| | 한도 | VU 100 기준 유입 | 거절률 |
|---|---:|---:|---:|
| room(전원 같은 방) | 30/s | 100/s | 70% |
| user(계정 2개 공유) | 3/s | 계정당 50/s | 94% |

인바운드 대부분이 컨트롤러에서 잘려 gRPC 저장도 팬아웃도 일어나지 않으므로 `C`(팬아웃 처리량)를 잴 수 없다. 그래서 실험을 둘로 나눈다 — **(A) 팬아웃 용량**은 rate limit off, **(B) rate limit 검증**은 VU별 계정을 공급한 별도 실행(→ 5.4의 "VU별 자격증명 공급").

주의: **busrefresh로 반영되지 않는다.** `ChatMessageRateLimitProperties`는 불변 record이고 `RedisChatMessageRateLimiter`가 주입된 인스턴스를 계속 참조하므로, 켜고 끄려면 재배포가 필요하다(executor 설정과 같은 제약 → ADR-003).

되돌리기: `git-config-repo/dynamic/websocket-gateway.yml`의 `enabled: false` → `true`, 주석 제거, **머지 + websocket-gateway 재배포**. 설정은 `label: main` 고정이라 main에 머지해야 반영되고, 도입 PR이 squash 머지되므로 `git revert`가 아니라 값을 되돌리는 새 커밋이다.

**되돌리는 것으로 끝이 아니다 — 한도를 다시 정해야 한다.** 지금 값(room `30/s`·user `3/s`)은 측정 편의로 잡은 수치지 운영 목표에서 역산한 값이 아니다. 운영계 전에 다음을 확정하고 그 결과로 한도를 다시 계산한다.

| 정해야 할 것 | 누가 |
|---|---|
| 목표 동시 접속자 수 | 기획팀 협의 |
| 방 최대 인원 | 기획팀 협의 |
| 목표 TPS(메시지 전송) | 기획팀 협의 |
| 견뎌야 할 피크 배수 | 기획팀 협의 |
| 위 목표를 감당할 인프라 규모·서버 대수 | 예산 범위에서 산정 → 스케일아웃 |

**순서가 중요하다.** 목표 수치 확정 → 그에 맞는 인프라 증설·스케일아웃 → **남는 여유에 맞춰 rate limit 한도 설정**. 리미터는 "인프라가 감당하는 선"을 넘는 유입을 입구에서 잘라내는 장치이므로, 용량을 정하기 전에 한도부터 정하면 근거가 없다.

**이것이 유실 대응의 1차 방어선이다.** rate limit 거절은 `RATE_LIMIT_EXCEEDED` 로 **발신자에게 통보되는 실패**다. 반면 용량을 넘겨 executor 큐가 포화되면 shedding 이 조용히 버린다(통보 경로 없음 — `SERVICE_FLOWS.md` §15.2). 즉 **리미터가 조용한 유실을 시끄러운 거절로 바꾼다.** 용량 산정과 리미터가 제자리를 잡으면 shedding 구간에 도달할 일이 없다는 것이 현재 설계 전제다.

**그래도 불안하면 5.3-b(메시지별 broadcast 순번)를 해야 한다.** 방어선이 뚫렸을 때 클라이언트가 유실을 감지·복구할 유일한 수단이다. 판단 근거와 구현 후보는 **PR #3** 본문에 정리되어 있다.
`[출처: 2026-08-27 재측정 조건 검토 / rate limit 한도와 테스트 조건 대조]`

#### 1.14 게이트웨이 WebSocket 핸드셰이크 Rate Limit 을 측정용으로 올려 둔 상태다 — 되돌려야 한다

`git-config-repo/dynamic/api-gateway.yml`의 `gateway.rate-limit.websocket-handshake`를 **`2/5/1` → `100/300/1`로 올려 둔 상태**다(팬아웃 용량 재측정용). **측정이 끝나면 되돌린다.**

원래 값에서는 측정 자체가 불가능하다. `RateLimitConfig`가 `WEBSOCKET_NATIVE_HANDSHAKE`·`WEBSOCKET_HANDSHAKE` 라우트에 `keyResolvers.user()` 기준으로 리미터를 붙이는데, k6는 계정 2개를 전 VU가 공유한다. 사용자별 `burst-capacity: 5`이므로 **계정당 5개, 총 10개만 통과**한다.

| VU | 기대 연결 | 실제 연결 | 근거 |
|---:|---:|---:|---|
| 20 | 20 | **10** | `ws upgrade status is 101` → ✓10 / ✗10 (2026-08-27 실측) |
| 60·80·100 | 각 60·80·100 | **10** | 계정 2개 × burst 5. VU를 올려도 상한이 같다 |

VU를 올려도 연결이 10개에서 멈추므로 팬아웃(`M²`)이 생기지 않아 `C`를 잴 수 없다. **핸드셰이크는 연결 시 1회만 검사하고 STOMP 전송은 이미 열린 소켓을 쓰므로, 이 값은 팬아웃 처리량 측정 경로에 영향을 주지 않는다.**

1.13(STOMP Rate Limit)과 **원인이 같다** — 계정 2개 공유라는 테스트 구조가 사용자별 리미터 둘 다에 걸렸다. **선행 조건은 해소됐다(2026-08-30)** — 테스트 계정 300개를 VU 당 하나씩 공급하도록 바꿔 계정별 버킷이 더는 부딪히지 않는다. 이제 1.13·1.14 를 원복하고 리미터 자체를 검증할 수 있다.

주의: **busrefresh로 반영되지 않는다.** `GatewayRateLimitProperties`는 불변 record이고 `RedisRateLimiter` 빈이 기동 시점 값으로 라우트별 Config를 등록하므로 **api-gateway 재배포**가 필요하다(1.13과 같은 제약).

되돌리기: `websocket-handshake`를 `replenish-rate: 2` / `burst-capacity: 5` / `requested-tokens: 1`로, 주석 제거, **머지 + api-gateway 재배포**.

**1.13 과 함께 다시 산정한다.** 핸드셰이크 한도는 "동시에 몇 개의 연결을 유지할 것인가"에서 나온다. 목표 동시 접속자 수와 서버 대수가 정해지기 전의 `2/5/1` 은 원래 값일 뿐 근거 있는 목표치가 아니다. 1.13 표의 항목(동접·방 인원·TPS·피크 배수·인프라 규모)을 확정한 뒤 연결 수 기준으로 다시 계산한다.

이 한도가 **연결 자체를 입구에서 막는 가장 바깥 방어선**이다. 여기서 걸리면 그 뒤의 STOMP 큐·팬아웃·유실 경로에 애초에 도달하지 않는다. 맥락은 1.13 과 **PR #3** 을 함께 본다.
`[출처: 2026-08-27 워밍업 실행(VU 20) ws upgrade 실패 50% 원인 규명]`

#### 1.15 STOMP SUBSCRIBE 에 인가 검사가 없다 — 남의 방을 구독할 수 있다

게이트웨이의 `ChannelInterceptor` 구현체는 `WebSocketSessionEventHandler` 하나뿐이고 **`StompCommand.SUBSCRIBE` 를 검사하는 코드가 없다.**

```
SUBSCRIBE /topic/chat/{roomId}
  → 방 멤버 여부를 확인하지 않고 그대로 구독된다
```

인증된 사용자면 **roomId 만 알면 어느 방이든 메시지를 실시간으로 받는다.** roomId 는 Mongo ObjectId 라 추측이 어렵지만, 한 번이라도 노출되면(공유 링크·로그·이전 멤버) 방을 나간 뒤에도 계속 받는다. 나가기(leave)가 구독을 끊지 않기 때문이다.

전송(SEND)은 `ChatRoom.validateWritable(writerId)` 로 막히지만 **읽기 경로에는 같은 검사가 없다.**

대응 후보:

| 방식 | 성격 |
|---|---|
| `ChannelInterceptor` 에서 SUBSCRIBE 가로채 방 멤버십 확인 | 구독 시 1회 검사. 게이트웨이가 방 멤버를 알아야 해서 chat-service 조회(gRPC) 또는 캐시가 필요 |
| 구독 시점 발급 토큰 | 방 입장 API 가 방별 단기 토큰을 주고 SUBSCRIBE 헤더로 검증 |

**뱃지를 토픽으로 바꾸면(→ 5.9) 이 구멍이 뱃지 경로에도 열린다.** 토픽 전환의 선행 조건이다.
`[출처: 2026-08-28 뱃지 합치기 설계 중 ChannelInterceptor 전수 확인]`

---

## 2. 데이터 · 영속성

### market

#### 2.4 카탈로그 쓰기 경로(`changeMarkets`) 미노출
`MarketCommandUseCase.changeMarkets`(카탈로그 create/update/delete + `market-broadcast-event` 캐시 무효화)가 구현·테스트되어 있으나 **인바운드 어댑터(REST/gRPC/Kafka)에 연결되어 있지 않다**. 현재 마켓 카탈로그는 `market-bootstrap/.../sql/schema.sql`의 시드 INSERT로만 채워진다. 관리 엔드포인트/운영 반영 경로 도입 여부 또는 현재가 의도인지 확인 필요.
`[출처: docs/modules/MARKET.md §12]`

---

## 3. 계약 · 직렬화

### notification

#### 3.3 notification DLQ 미소비 · 재시도 부재
`common-core/KafkaTopic.NOTIFICATION`이 `notification-event.dlq`를 정의하나, `notification-service.yml`의 `spring.cloud.function.definition`(`priceAlertDetectedEventConsumer;notificationEventConsumer`)에 **DLQ consumer가 없다**. 또한 `NotificationEventService.handle`은 단순 `@Transactional("notificationMongoTransactionManager")`로 `@Retryable`/`@Recover`가 없다(chat의 재시도→DLQ 복구 패턴 부재). Mongo 영속 실패 시 처리(바인더 기본 재시도/유실 여부)와 DLQ 운영 의도 확인 필요.
`[출처: docs/modules/NOTIFICATION.md §10, §11]`

---

## 4. 배포 · 인프라

### CI/CD (공통)

#### 4.8 Merge Queue 도입 검토
현재는 PR 에서 한 번, 머지 후 또 한 번 CI 가 도는 구조를 **tree 해시 비교 + 이미지 승격**으로 우회하고 있다(`docs/CI_CD.md §2.1`). GitHub **Merge Queue** 는 머지 직전에 "머지된 결과" 를 만들어 CI 를 돌리고 통과해야 머지하므로, 머지 후 CI 가 **구조적으로 불필요**해진다. 우리가 우회한 문제의 정식 해법이다.

도입하려면 룰셋에 merge queue 를 설정하고 `auto-pr.sh` 훅의 auto-merge 흐름(`gh pr merge --auto --squash`)과 맞물리는 부분을 함께 손봐야 한다. 승격 로직(`merge-ci` 의 `Resolve promotion source`)을 제거할 수 있는지도 함께 판단한다. 지금 구조가 동작하고 있으므로 급하지 않다.
`[출처: docs/CI_CD.md §2.1 / #207 설계 논의]`

---

#### 4.9 upbit-connector 첫 배포 전 초기화 필요
`cd.yml` 배포 대상 등록과 infra 저장소의 safe-recreate 스크립트는 추가됐다. 다만 스크립트가 rollback 기준으로 읽는 `service/.deploy/upbit-connector.current-image`(git 미추적)가 러너에 없으면 **첫 배포가 실패한다**. 최초 1회 현재 이미지 다이제스트로 생성해야 한다(스크립트가 안내 메시지를 출력한다). 생성 시점·값 확인 필요.
`[출처: docs/modules/UPBIT_CONNECTOR.md §10]`

---

#### 4.12 에러를 로그로만 삼키는 지점에 알림 경로가 없다
여러 지점이 예외를 잡아 `log.error`만 남기고 흐름을 이어간다. 로그는 남지만 **아무도 모른다**. 현재 모니터링 스택은 Prometheus + Grafana + exporter뿐이고 **Alertmanager도 alert 룰도 없다**(infra 저장소 `monitoring/`).

**방식 결정 필요.** 일반적인 선택지는 셋이다.
1. **메트릭 기반**(권장): 앱은 실패를 Micrometer 카운터로만 올리고, Prometheus 스크랩 → Grafana Alerting 또는 Alertmanager가 임계·지속시간 룰로 판단해 Slack 등으로 발송. 알림 폭주 억제(그룹핑·억제·무음)와 채널 변경이 앱 재배포와 분리된다. 이 저장소는 이미 Prometheus/Grafana가 있어 추가 비용이 가장 작다.
2. **로그 기반**: 수집기(Loki/ELK)에서 ERROR 패턴으로 알림. 이 저장소는 로그·트레이스 스택을 제거해 메트릭 전용이라 새 스택 도입이 선행된다.
3. **앱에서 직접 Slack webhook 호출**: 구현은 가장 짧지만 재시도·레이트리밋·중복 억제가 앱 책임이 되고, 장애 시 알림이 함께 죽는다. 지양.

**적용 대상**(전체 스캔 결과. `log.error`만 남기고 계속 진행하는 지점)

| 위치 | 상황 | 지금 | 알림이 필요한 이유 |
|---|---|---|---|
| `upbit-connector` `UpbitTickerCollectStarter.logStreamTermination` | 수집 스트림 종료 | 로그 | **수집 전면 중단**. 재구독 없이 끝나 시세가 끊긴다 |
| `upbit-connector` `UpbitTickerCollectService.logPublishFailure` | ticker Kafka 발행 실패 | 로그 후 계속 | 지속되면 탐지 입력이 비는데 서비스는 살아 있어 보인다 |
| `upbit-connector` `UpbitWebsocketTickerStreamAdapter.logRetry` | WS 재연결 시도 | 로그 | 반복되면 Upbit 장애·차단 징후. 1회는 정상, 지속이 신호 |
| `common-outbox` `OutboxEventListListener` | 직렬화·DB 저장 실패 | 로그 | **이벤트 유실**. Outbox에 안 들어가면 재발행 경로가 없다 |
| `common-outbox` `DlqEventListListener` | DLQ 적재 실패 | 로그 | 마지막 안전망이 뚫린다 |
| `chat-application` `ChatMessageEventService` · `ChatRoomEventService` | 보상(recover) 실패 | 로그 | 캐시-우선 쓰기의 정합성 복구가 실패한 상태로 남는다 |
| `chat-adapter-in` `GrpcChatMessageExceptionAdvice` | 보상 실패 | 로그 | 위와 같음 |
| `websocket-gateway` `Stomp*Adapter` 3종 | STOMP 푸시 실패 | 로그 | 사용자가 알림·메시지를 못 받는데 서버는 정상으로 보인다 |

착수 시: 각 지점에 카운터를 먼저 심고(이름 규약 필요), 그 다음 룰·채널을 정한다. 카운터 없이 룰부터 만들 수 없다.
`[출처: 2026-08-19 전체 `log.error` 스캔 / infra `monitoring/` 구성 확인]`

---

#### 4.13 서킷 브레이커 도입 검토

gRPC 호출에 deadline(chat 10s, 나머지 3.5s)은 있지만 **연속 실패 시 호출을 끊는 장치가 없다.**
특히 API Gateway는 **모든 인증 요청마다** oauth2-authorization-server에 blacklist 조회 gRPC를 호출하므로,
그 서비스가 죽으면 인증 전체가 멈춘다.

**지금 급하지 않은 이유:** 부하테스트가 잡은 문제는 하류 장애 전파가 아니라 자기 팬아웃 지연이었다(→ 5.3).
deadline이 무한 대기는 이미 막고 있고, gRPC 호출 깊이가 1단계라 전파 사슬이 짧다.

**선행 조건:** 브레이커가 열려도 알 방법이 없다(→ 4.12 알림 경로). **알림이 브레이커보다 먼저다.**

착수 시 결정할 것.

| 호출 | OPEN일 때 정책 | 비고 |
|---|---|---|
| api-gateway → auth-server(blacklist) | fail-open(토큰 통과) vs fail-closed(인증 거부) | **보안 결정.** 통과시키면 로그아웃 토큰이 살아나고, 막으면 인증 전체가 죽는다 |
| websocket-gateway → chat(save) | 실패 ACK 반환 | 경로가 이미 있다 |
| notification → market(수신자 조회) | 알림 생성 스킵 vs 재시도 큐 | |
| oauth2-client → user/auth | 로그인 실패 처리 | |

함정: 실패로 셀 코드 분류(`GrpcFailureCode` 재사용 — `NOT_FOUND` 같은 비즈니스 응답은 제외),
재시도와의 조합 순서(재시도가 브레이커 카운터를 채워 실패를 증폭), 브레이커 상태는 인스턴스별이라는 점.
라이브러리는 Resilience4j(Spring Boot 3 표준).
`[출처: 2026-08-22 부하테스트 후속 논의]`

---

#### 4.14 fork PR 은 이미지 승격 경로를 못 탄다
`ci.yml` 의 머지 승격은 PR 실행이 `pr-<번호>` 이미지를 레지스트리에 push 해둔 것을 전제로 한다. **fork PR 은 GitHub 이 시크릿을 주지 않아 push 가 불가능**하므로(보안 설계) 빌드만 하고 끝나며, 머지 시 승격 대상이 없어 풀빌드로 떨어진다. 현재는 모든 PR 이 같은 저장소 브랜치에서 오므로 실사용 영향이 없다.

외부 기여를 받게 되면 표준 2단계 분리가 필요하다: PR 워크플로(시크릿 없음)가 이미지를 artifact 로 올리고, `workflow_run` 으로 트리거되는 별도 워크플로(base 브랜치 코드로 실행되어 시크릿 보유)가 그 artifact 를 내려받아 push 한다. PR 코드를 실행하지 않으므로 시크릿이 새지 않는다. **`pull_request_target` 은 쓰지 않는다** — 시크릿을 받지만 PR 코드를 체크아웃해 실행하면 그대로 탈취된다.
`[출처: docs/CI_CD.md §2.2 / #207 설계 논의]`

---

#### 4.15 squash 머지 본문이 훅이 넘긴 값과 다르다 — 원인 미확인

`auto-pr.sh` 는 `gh pr merge --auto --squash --body "$CO_AUTHORS"` 로 트레일러만 넘긴다. 그런데
#252~#272 의 머지 커밋 본문에는 **브랜치 커밋 메시지가 그대로 남아 있다**(최대 104줄).

```
훅이 의도한 것   subject = PR 제목 (#N),  body = Co-authored-by 트레일러만
실제 이력        body 에 브랜치 커밋 메시지 전문
```

후보 셋을 못 갈랐다 — 훅이 아예 안 돌았는지(UI 머지), `--auto` 로 예약한 제목·본문이 실제 머지
시점에 적용되지 않는지, `CO_AUTHORS` 가 비어 `--body ""` 가 되면 GitHub 기본 본문으로 떨어지는지.
**확인하려면 실제 PR 을 하나 만들어 머지해야 한다.**

당장 문제는 아니다. 본문이 길게 남는 것 자체는 해롭지 않고, 규칙은 "브랜치 커밋 메시지가 남을 수
있다고 보고 쓴다"로 맞춰 뒀다. **확인이 필요한 쪽은 트레일러다.**

| 범위 | 트레일러 |
|---|---|
| #252~#255 | `Co-authored-by: Claude` — 규칙대로 |
| #256~#272 | `Claude Opus 5` — 모델명이 들어갔다(도구 기본값) |
| #268 · #269 | **없다** — 공동 저작 표시가 조용히 사라진 사례 |

머지된 이력은 재작성하지 않는다(→ `.claude/rules/git-safety.md`). 확인할 것은 트레일러 누락이
훅 경로 때문인지, 아니면 브랜치 커밋에 애초에 없었는지다.
`[출처: 2026-08-30 PR #252~#272 머지 커밋 본문·트레일러 전수 확인]`

---

### upbit-connector

#### 4.11 upbit-connector REST 조회 API 미구현(2단계)
`upbit-connector`는 현재 WebSocket 수집·Kafka 발행만 한다. 도입 당시 합의한 2단계 — **Upbit REST 조회(캔들·호가 등)를 조합해 응답하는 API** — 는 아직 없다. 프론트 차트에 필요한 과거 데이터를 줄 곳이 없는 상태가 유지된다.
- 착수 시 함께 볼 것: Upbit REST 요청 제한(공식 문서 기준 확인 필요)과 그 구현 위치, 응답 캐시(Redis reactive 여부), Gateway route·CORS(외부 계약 → `.claude/rules/external-contracts.md`).
- **예외 처리 계층이 없다.** 이 서비스는 HTTP 엔드포인트가 없어 지금은 필요 없지만, REST를 열면 응답 형식을 맞출 곳이 필요하다. `common-web/GlobalExceptionHandler`는 MVC 어댑터(`*-adapter-in`)만 쓰고 서블릿 계열 예외를 다루므로 WebFlux에서 그대로 재사용할지, WebFlux 전용 advice(또는 `ErrorWebExceptionHandler`)를 둘지 확인 필요. `common-grpc-server`의 gRPC advice는 gRPC 서버가 없어 무관.
- 설계 메모: 캔들은 `to` 파라미터로 과거를 거슬러 여러 번 호출해야 하며 `Flux.expand`로 표현 가능. 동일 구간 동시 요청은 `Mono.cache()`로 합칠 수 있다. 둘 다 미검증 아이디어다.
`[출처: docs/modules/UPBIT_CONNECTOR.md §2·§6 / 모듈 도입 논의]`

---

#### 4.16 upbit-connector 스케일아웃 시 종목 중복 구독·중복 발행

`UpbitWebsocketTickerStreamAdapter`는 기동 시 market gRPC `GetEnabledMarkets`로 조회한 활성 마켓 전체를 구독 대상으로 삼는다(`docs/modules/UPBIT_CONNECTOR.md` §3·§4). 인스턴스 간 구독 대상을 나누는 코드는 확인되지 않는다. 따라서 `upbit-connector`를 2대 이상으로 스케일아웃하면 **모든 인스턴스가 동일한 전체 종목을 각자 Upbit WebSocket에 중복 구독**하고, 각자 `upbit-ticker-event`에 같은 종목의 ticker를 독립적으로 발행할 것으로 보인다(다중 인스턴스 실측은 아직 하지 않음 — 확정 결함으로 단정하지 않는다).

이 경우 하류 `market-detection`은 같은 시점 근방의 동일 종목 ticker를 인스턴스 수만큼 중복 소비한다. `PriceAlertDetectedEvent`의 `event_id`는 market-detection이 탐지 시점에 새로 발급하므로(`docs/modules/MARKET_DETECTION.md` §4.3 컨슈머 멱등 전략), 중복 ticker가 각각 새 탐지 이벤트를 만들면 notification 쪽 Inbox 멱등 처리로도 걸러지지 않고 **같은 가격 변동에 대해 여러 건의 알림이 발행될 수 있다**.

대응 후보:

| 방식 | 성격 |
|---|---|
| 종목 샤딩 | 인스턴스 수 기준 consistent hashing 등으로 종목을 나눠 구독(각 인스턴스가 담당 종목만 WebSocket 구독) |
| 리더 선출 | 인스턴스 중 하나만 실제 수집·발행을 담당하고 나머지는 대기(장애 시 재선출) |

이 서비스는 현재 상태가 없고(§2) Kafka Bus에도 연결하지 않는 설계(§7.1)라, 어느 방식이든 인스턴스 목록 파악(Eureka)과 재분배 트리거를 새로 설계해야 한다. 스케일아웃 계획·필요성 자체가 확정되지 않았다면 우선순위부터 판단 필요.
`[출처: 2026-09-05 upbit-connector 스케일아웃 시나리오 검토 / docs/modules/UPBIT_CONNECTOR.md, MARKET_DETECTION.md]`

---

#### 4.17 스로틀 표본 변경(구간 첫 값 → 마지막 값)이 탐지 결과에 주는 영향 미실측

기존 `market-detection` 내장 스로틀은 발행 구간의 **첫 값**을 통과시켰다. `upbit-connector`의 `sample(7s)`(PR #246)은 구간의 **마지막 값**을 발행한다(`docs/modules/UPBIT_CONNECTOR.md` §4.1·§5). 이동평균·변동률 계산에 들어가는 표본 자체가 바뀐 것인데, 이 차이가 실제 임계값 매칭 결과(과탐지/누락)에 어떤 영향을 주는지는 실측하지 않았다.

착수 시: 동일 구간의 첫 값/마지막 값을 함께 로깅하거나 오프라인으로 재생해 두 표본 선택 방식의 탐지 결과 차이를 비교한다. 차이가 무시할 수준인지, 임계값·window 재조정이 필요한 수준인지에 따라 후속 조치가 갈린다.
`[출처: docs/modules/UPBIT_CONNECTOR.md §4.1·§5·§10]`

---

### oauth2-authorization-server

### spring-cloud-eureka-server

#### 4.3 단일 노드 · self-preservation 비활성
`git-config-repo/infrastructure/eureka-server.yml`이 peer 복제 없는 standalone(`register-with-eureka: false`, `fetch-registry: false`)이고 `enable-self-preservation: false`(eviction 30s). 네트워크 순단 시 정상 인스턴스도 빠르게 축출될 수 있음. 개발/소규모 의도로 보이나 운영 HA(peer)·self-preservation 정책 확인 필요.
`[출처: docs/modules/EUREKA_SERVER.md §9 / spring-cloud-eureka-server 분석]`

### notification

### outbox-poller

#### 4.5 Debezium CDC Outbox 장기 전환 검토
현재는 `outbox-poller`가 event DB의 `PENDING` 레코드를 polling하고 `PUBLISHED`/`FAILED`/`retryCnt`를 직접 관리하는 at-least-once relay를 사용한다. 운영 인프라와 학습 비용을 낮추고 dispatchType별 지연을 독립적으로 제어하기 위한 현재 선택으로 유지한다.

처리량·polling DB 부하·poller 상태 관리 비용이 증가하거나 Kafka Connect 운영 역량이 확보되면 Debezium Outbox Event Router 기반 CDC 전환을 다시 검토한다. 검토 시 MySQL binlog 보존/replication 권한, Kafka Connect HA, connector offset·schema history·lag 모니터링, 장애 후 중복 가능성과 consumer 멱등성을 함께 설계한다. Debezium offset 기반 재개는 전달 누락 위험을 줄이지만, 기본 구성만으로 같은 `transaction_id`의 여러 토픽 발행이 하나의 Kafka transaction으로 원자화되지는 않으므로 단일 Envelope, transaction metadata 기반 집계 또는 후속 Kafka transaction 필요 여부도 별도로 결정한다.
`[출처: docs/modules/OUTBOX_POLLER.md §5 트랜잭션 경계와 보장 수준]`

#### 4.6 `FAILED` Outbox 재처리 경로 추가
`OutboxService.publishPending`은 `PENDING` 레코드만 조회하고 retry를 소진하면 `FAILED`로 전환한다. 실패 레코드는 DB에 보존되지만, 저장소 코드에는 원인 해결 후 `FAILED`를 다시 `PENDING`으로 전환하거나 선택적으로 재처리하는 API·스케줄러·운영 작업이 확인되지 않는다. at-least-once relay가 운영 복구까지 포함해 최종 수렴하려면 재처리 대상 선택, retry count 초기화 여부, 중복 발행 경고·감사 로그와 접근 통제를 포함한 복구 경로를 설계한다.
`[출처: docs/modules/OUTBOX_POLLER.md §5 트랜잭션 경계와 보장 수준]`

---

### 공통 (여러 서비스)

#### 4.7 `LazyConnectionDataSourceProxy` 미적용 서비스 점검

chat 은 `@Transactional` 안에서 MongoDB 를 읽어 커넥션을 그 왕복 내내 붙들고 있었다(점유 실측 5.139초,
커넥션 타임아웃 360건 → 브로드캐스트 유실 10.06%). `LazyConnectionDataSourceProxy` 를 적용해 물리 커넥션
획득을 첫 statement 로 미뤘다.

같은 함정이 남은 서비스를 점검한다 — **트랜잭션 안에서 DB 외 I/O(Mongo·Redis·gRPC·HTTP)를 호출하는지**가 기준이다.

| 서비스 | Lazy | 확인 필요 |
|---|---|---|
| market | 적용됨 | Read Replica 라우팅 배선의 일부로 이미 있음 |
| chat | 적용됨 | 이번 변경 |
| notification | **없음** | 트랜잭션 경계 안 외부 I/O 여부 |
| user | **없음** | 동일 |
| outbox-poller | **없음** | 폴링 트랜잭션이 Kafka 발행을 품는지 |

외부 I/O 가 없더라도 Lazy 자체는 손해가 없다(첫 statement 시점에 획득할 뿐). 다만 커넥션 고갈 시
실패 지점이 트랜잭션 시작에서 첫 statement 로 옮겨지므로, 각 서비스의 재시도·예외 처리 경로를 함께 본다.
`[출처: 2026-08-27 VU 60 부하 측정 / chat 커넥션 점유시간 실측]`

---

## 5. 성능 · 캐시

### notification · chat (공통 인프라)

#### 5.1 Redis `maxmemory-policy`(LFU 축출) 서버 설정 확인·반영
notification master 캐시가 **긴 TTL(7일) + 다수 키(알림당 1키)** 전략으로 바뀌면서, 콜드 항목 교체를 **Redis 서버 LFU 축출**에 의존한다. 서버 정책이 `noeviction`(기본)이면 메모리 포화 시 **쓰기 에러(OOM)** 가 난다. **`maxmemory` + `maxmemory-policy volatile-lfu`**(TTL 있는 키만 LFU 축출) 설정이 필요하다.
- 이 정책은 **해당 Redis 클러스터를 공유하는 전 서비스(auth 토큰·session·chat)에 적용되는 전역 설정**이라 blast radius 가 크다 → 현재 정책 확인 후 반영 여부·값을 결정한다. `volatile-lfu`면 TTL 없는 키는 안 건드려 상대적으로 안전. 클러스터면 노드별 `maxmemory`.
- 코드는 이미 이 전제로 구현됨(긴 TTL). **인프라 설정 미반영 시 메모리 상한 없음** → 우선 확인 필요.
`[근거: docs/modules/NOTIFICATION.md §7]`

#### 5.3-b 방별 순번 — 별도 설계 필요

**현재 상태(2026-09-02): 방 단위 읽음 watermark 기반은 PR #286에서 완료됐다.** `ChatRoom.lastMsgSeq`가 메시지 저장 시 신규 메시지 수만큼 단조 증가하고, hard delete에서는 감소하지 않는다. 이 값은 “방에 새 활동이 있었는가”를 판단하는 기준이지, 각 메시지에 붙는 개별 순번은 아니다.

- 읽음 여부·watermark 거리만 필요하면 이 TODO는 해결된 것으로 본다.
- 브로드캐스트 유실을 클라이언트가 감지하려면 별도 메시지 순번이 필요하다. `lastMsgSeq`만으로는 어떤 메시지가 빠졌는지 알 수 없으므로, ingress/outbox 단계에서 메시지별 순번을 발급하고 broadcast payload 계약을 바꾸는 별도 작업으로 분리한다(→ 5.5).
- `lastMsgSeq`를 브로드캐스트 gap detection용 per-message sequence로 재사용하지 않는다.

팬아웃 처리량 개선(구 5.3)은 배칭으로 해소했다(PR #265, 프레임당 24~34건). 방 단위 읽음 기준도
PR #286에서 추가했지만, **메시지별 broadcast 순번은 아직 없다.**

```java
// ChatMessageEventService — 비동기 이벤트 핸들러
chatRoomPersistencePort.updateMessageState(roomId, newMessageCount, latestCreatedAtMs);
```

현재 `updateMessageState`는 방 단위 `lastMsgSeq`를 원자적으로 갱신하지만 메시지별 순번을 반환하거나
메시지에 저장하지 않는다. 따라서 이 값을 broadcast gap detection용 순번으로 사용하지 않는다.

게이트웨이가 매기는 것도 안 된다 — 2대면 각자 다른 수열을 내서 클라이언트가 뒤섞인 두 수열을 본다.

순번이 없으면 배칭의 유실(Kafka 오프셋 선커밋)을 클라이언트가 감지하지 못한다(→ 5.5). **다음 계약 변경 때 함께 설계한다.**

##### 왜 남아 있는가 — 착수 보류 (2026-09-02)

이 항목은 **유실이 일어난 뒤 클라이언트가 복구하는 2차 수단**이다. 1차 방어선은 따로 있다 — 목표 수치에 맞춘 인프라 산정과 그 여유에 맞춘 rate limit 이며, 그쪽이 제자리를 잡으면 여기 도달할 일이 없다는 것이 현재 설계 전제다. 그래서 **지금은 착수하지 않는다.**

- 방어선 복구가 먼저다 → **1.13**(STOMP rate limit 원복·재산정), **1.14**(핸드셰이크 한도 원복·재산정)
- 유실 발생 여부는 이미 관측된다 → `stomp.executor.rejected{pool, kind}`. 운영 배포 직전 이 지표에 **개발자 알람**을 걸어 대응 경로를 만든다
- 최근 부하(2026-09-01 VU 100 · 멤버 302)에서 실시간 전달은 수신 600,000/600,000 · ACK 6,000/6,000 으로 **유실 0** 이었다

**착수 트리거**: rate limit 을 원복하고 목표 수치대로 인프라를 구성한 상태에서 `stomp.executor.rejected{kind="broadcast"} > 0` 이 관측되면 착수한다.

판단 근거·구현 후보·트레이드오프는 여기 적지 않는다. **1.13·1.14 와 PR #3 본문**을 본다.
`[출처: 2026-08-28 배칭 설계 / PR #265 · 2026-09-02 착수 여부 검토]`

---

#### 5.4-c STOMP CONNECT 가 산발적으로 도달하지 않는다

WebSocket 업그레이드(HTTP 101)는 100% 성공하는데 그 위로 보낸 STOMP CONNECT 에
CONNECTED 응답이 오지 않는다. VU 100 기준 회차당 **1~4개(2~4%)**.

대기를 15초에서 20초로 늘려도 재현되므로 **느린 것이 아니라 프레임이 도달하지 않는 것**이다.
게이트웨이 로그·GC·Redis·api-gateway GC 어디에도 흔적이 없고, 실패 개수가 부하에 비례하지도
않는다.

api-gateway 의 WebSocket 프록시 구간이 의심되지만 **확인하지 않았다.** 메시지 경로와는
별개이므로 재현 조건을 따로 만들어 판정한다.

`[출처: 2026-08-30 VU 100 3회 측정]`

---

#### 5.5 브로드캐스트 유실을 클라이언트가 감지·복구할 경로가 없다

브로드캐스트 push는 DLQ·재시도가 없고 executor 큐 포화 시 버려지는데, **유실을 클라이언트가 감지해 재조회하는 경로가 없다**(2026-08-27 `crypto-project-frontend` 확인).

- `ChatRoomPage.tsx`의 `client.onConnect`는 `subscribeChatRoomMessages`로 **재구독만** 하고 재조회하지 않는다. `onWebSocketClose`·`onStompError`도 `setIsConnected(false)`뿐이다.
- 최근 메시지 로드 `useEffect`의 deps는 `[isLoggedIn, isInvalidRoomId, roomId]` — **마운트·방 변경에만** 돈다. 다른 `getChatMessages` 호출은 `lastMsgId`/`lastCreatedAtMs` 커서를 쓰는 **과거 스크롤** 경로다.
- wire payload `StompChatMessagePayload{messageId, roomId, writerId, content, timestamp, clientMessageId}`에 **방별 순번이 없어** 클라이언트가 갭을 감지할 수단 자체가 없다.

즉 연결을 유지한 채 broadcast가 유실되면 클라이언트는 알지 못하고, 방 재진입·새로고침 전까지 그 메시지가 보이지 않는다. ADR-003이 shedding을 택한 근거는 "재조회로 복구된다"가 아니라 "피크에서 전원을 지연시키는 것보다 일부 유실이 SLO에 유리하다"이며, 갭 복구는 미구현으로 남아 있다.

착수 시 결정할 것: (a) 재연결 시 커서 기반 재조회(프론트만 수정, 갭 감지는 여전히 불가하나 재연결 구간은 덮인다), (b) payload에 방별 순번 추가 후 클라이언트 갭 감지(**STOMP wire payload 변경 = 외부 계약**, 프론트·k6 함께 → `.claude/rules/external-contracts.md`).

`websocket-gateway/CLAUDE.md`와 `docs/modules/WEBSOCKET_GATEWAY.md` §6에 있던 "유실 시 클라이언트 REST 재조회 전제" 서술은 이 확인 결과에 맞춰 같은 커밋에서 정정했다.
`[출처: 2026-08-27 ADR-003 리뷰 중 프론트 구현 대조]`

---

#### 5.9-c 뱃지를 사용자 축으로도 합친다 — 다음 계약 변경 때 같이

conflation(PR #263)은 **방 축**으로만 접었다. 라운드 수는 줄었지만 **라운드당 프레임 수는 그대로**다.

```
전   초당 80라운드 × 멤버 80 = 6,400 프레임
후   초당  5라운드 × 멤버 80 =   400 프레임
      ↑ conflation 이 줄인 것   ↑ 아직 그대로
```

한 사용자가 여러 방에 속하므로 **사용자 축으로 한 번 더 접을 수 있다.** 화면이 채팅방 목록 하나인데 방마다 프레임을 따로 보낼 이유가 없다.

```
방A 멤버 {1,2,3} · 방B 멤버 {2,3,4} 가 같은 창에 갱신될 때

지금    A 3프레임 + B 3프레임 = 6
집계    사용자1[A] · 사용자2[A,B] · 사용자3[A,B] · 사용자4[B] = 4
```

**뒤집는 위치는 flush 안이다.** ingest 때 뒤집으면 메시지마다 멤버 루프가 돌지만(초당 6,400회), flush 때 뒤집으면 살아남은 방에 대해서만 돈다(초당 400회).

**구현이 한 곳에 갇혀 있다.** `CoalescingMyChatRoomBadgeAdapter.flush()` 의 `delegate.send(...)` 한 줄이 "무엇이 나가는가"를 정하는 유일한 지점이다. 버퍼·스케줄러·프로퍼티·지표·단위 테스트는 그대로 산다.

키를 방으로 잡은 것이 이 확장의 전제다. 사용자 키였다면 방별 conflation 자체가 불가능하고, `(방, 멤버)` 키였다면 방 묶음이 사라져 되돌리기 어렵다.

**배칭(#265) 때 같이 넣지 않은 이유**: **부하테스트가 방 1개**라 넣어도 숫자가 안 움직여 검증이 안 된다. 방이 여러 개인 조건을 만들 수 있을 때, 그리고 계약을 또 깨야 할 일이 생길 때 함께 한다(5.3-b 방별 순번과 묶으면 계약을 한 번만 깬다).

---

#### 5.12 서버 컨테이너를 측정용 별도 장비로 분리한다

**목표**: 부하 측정 수치를 하드웨어 한계가 아닌 소프트웨어 한계로 만든다.

현재는 16GB · 6코어 맥 한 대에 인프라(MySQL ×2 · MongoDB ×3 · Redis ×6 · Kafka ×2 · Vault) ·
서비스 8개 · 모니터링을 모두 올려 측정한다. 2026-08-28 측정에서 이 구성이 한계로 드러났다.

| 관측 | 값 |
|---|---|
| 회차별 `swapin` | 4,430 ~ 25,340 MB |
| VU 100 구간 `Pages free` | **43 MB** |
| 게이트웨이 CPU | **1~4%** (계산이 아니라 대기) |
| 회차 간 편차 | VU 100·2대에서 수신률 71.58% ~ 100% |

**CPU 가 노는데 큐가 쌓인다** — 페이지 폴트 대기가 지연에 섞인다는 뜻이다.
게이트웨이를 2대로 늘리면 컨테이너 메모리를 +768MB 요구해 오히려 호스트 압박을 키운다.

k6 부하 발생기는 이미 분리했다. 남은 것은 서버 쪽이다.

**옮길 때 큰 것들**

| 항목 | 주의 |
|---|---|
| MySQL master/replica | GTID 복제 재구성. `mysql-replica-init.sh` 는 멱등이나 볼륨은 새로 만들어진다 |
| MongoDB replica set | 3노드 재구성 |
| Redis Cluster | 6노드 + `redis-cluster-init.sh`(멱등). `nodes.conf` 가 볼륨에 있다 |
| Vault | Role ID / Secret ID 재발급 |
| 시크릿 | `service/.env`(DockerHub 토큰 · Deploy Token · Vault 자격) — 저장소에 없다 |
| self-hosted runner | 현재 맥에 붙어 있다. CD 전체를 다시 봐야 한다 |
| 네트워크 | 서비스 간 `localhost` 전제 |
| `restart` 정책 | 대부분 `no` 라 재부팅 후 수동 복구가 필요하다(같이 정리) |

**측정 장비가 생기면 함께 판정할 것**: outbound 처리량 상한. 1차에서 소켓 write 블로킹을 의심했지만
지금 조건에서는 큐가 비어 있어 현상 자체가 재현되지 않는다. 부하를 더 올려 상한이 다시 보이면
JFR 로 `jdk.SocketWrite` 를 떠서 판정한다 — 지금 호스트는 회차당 swapin 이 10~40GB 라 블로킹이
I/O 때문인지 페이지 폴트 때문인지 못 가른다.

**우선순위는 배칭(5.3)보다 낮다.** 배칭은 전송 횟수를 1/30 로 줄여 자릿수를 바꾸지만,
장비 이전은 하드웨어만큼만 올린다. 팬아웃이 `M²` 인 이상 장비로는 못 이긴다.

```
500명 한 방 → 500² × 2(뱃지) = 500,000 전달/s
```

다만 **절대 수치를 말해야 할 때**(포트폴리오·설명서에 "N명 지원"을 쓸 때)는 필요하다.
`[출처: 2026-08-28 2차 부하테스트 / chat/load-test-results/.../README.md]`

---

#### 5.13 Kafka 컨슈머 그룹이 배포마다 누수된다

`websocket-gateway` 의 브로드캐스트 컨슈머 그룹명이 `${app.instance-id}` 를 포함한다.

```yaml
group: chatmessage-broadcast-${app.instance-id}
```

**배포할 때마다 새 그룹이 생기고 옛 그룹은 영원히 남는다.** 2026-08-28 기준 27개가 쌓여 있었고
(현재 인스턴스와 일치하는 것은 6개), 죽은 그룹들이 lag 111,228 까지 누적한 채 Kafka 가 계속 추적하고 있었다.
수동으로 21개를 삭제했다.

인스턴스별 그룹을 쓰는 것 자체는 의도된 설계다 — 모든 인스턴스가 모든 브로드캐스트를 받아
자기 로컬 세션에만 전달해야 한다. 문제는 **정리 경로가 없다는 것**이다.

검토 후보: 배포 스크립트에서 옛 슬롯 그룹 삭제 / Kafka `offsets.retention.minutes` 조정 /
그룹명을 슬롯 기준(`blue`/`green`)으로 고정해 개수를 상한
`[출처: 2026-08-28 부하 측정 중 Kafka lag 조회]`

---

#### 5.14 inbound 큐 거절만 발신자에게 통보되지 않는다

원칙(발신자가 결과를 모르는 실패를 만들지 않는다)과 경로별 현황은 `docs/SERVICE_FLOWS.md` §9.
**남은 경로는 하나뿐이다.**

| 실패 경로 | 발신자가 아는가 | 상태 |
|---|---|---|
| 검증 실패 · 서버 오류 | ✅ `clientMessageId` 를 담은 실패 ACK | 해소(PR #267) |
| Rate Limit 초과 | ✅ 〃 | 구현돼 있음(측정용으로 꺼 둔 상태 → 1.13) |
| gRPC 실패 · 저장 실패 | ✅ 거절 ACK | 구현돼 있음 |
| ACK 가 brokerChannel 에서 거절 | ✅ 브로커를 안 지난다 | 해소(PR #267) |
| **inbound 큐 거절** | ❌ **모른다** | **남음** |

inbound 큐 거절만 `@MessageExceptionHandler` 그물에 안 걸린다. 컨트롤러에 들어오기 전에
executor 가 버리기 때문이고, 발신자는 ACK 타임아웃으로만 눈치챈다.

**대응: 입구에서 미리 거절하고 이유를 알린다.** 패턴은 이미 있다 — rate limit 이
`clientMessageId` 를 담아 거절 ACK 를 보낸다. 같은 자리에 혼잡 판정을 하나 더 두면 된다.

```java
if (willExceedLatencyBudget()) {
    throw new ChatMessageServerBusyException(request.clientMessageId());   // "SERVER_BUSY"
}
```

얻는 것 셋 — ①사용자가 안다(어느 메시지인지까지) ②입구에서 1건을 자르면 저장·outbox·Kafka·
팬아웃 N건을 통째로 안 만든다(뒤에서 버리는 것보다 N배 싸다) ③통과량이 줄어 거절 통지 자체는
전달된다.

**판정 기준 후보**

| 기준 | 성격 |
|---|---|
| outbound 큐 깊이 | 직접적. 현재 지표로 바로 가능 |
| broker 큐 깊이 | 〃 |
| **지연 예측(큐 ÷ 처리량)** | **SLO 와 직결** — "10초 안에 못 보낼 것 같으면 미리 거절". ADR-003 의 지연 예산과 맞물린다 |

**보류 — 운영 장비로 옮긴 뒤에 한다.** 임계값은 "이 부하에서 이만큼 밀리면 SLO 를 넘긴다"는
실측에서 나오는데, 지금 호스트는 회차당 swapin 이 10~40GB 라 큐 깊이도 그 영향을 받는다.
개발계 수치로 임계값을 박으면 운영계에서 다시 재야 한다(→ 5.12).

남은 절차는 둘이다. ①같은 피크테스트로 진짜 한계를 잰다 ②그 이상은 입구에서 막는다.
`[출처: 2026-08-28 VU 100 측정 / docs/SERVICE_FLOWS.md §9]`

---

## 6. 테스트 공백

### spring-cloud-api-gateway

#### 6.2 `/internal/deployment/**` gateway 레벨 통합 테스트 없음
`DeploymentControlAuthWebFilter`는 gateway에서 JWT `permitAll`인 경로를 `X-Deploy-Token`으로 별도 보호한다. 필터 자체 테스트는 `common-actuator-webflux` 쪽에만 있고, gateway 라우팅·security 체인과 결합된 상태의 통합 테스트가 없다.
`[출처: docs/modules/API_GATEWAY.md §14]`
