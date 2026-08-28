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
`[출처: SERVICE_FLOWS.md, ARCHITECTURE.md #2, API_GATEWAY.md §18.3 / oauth2-authorization-server 분석]`

#### 1.4 토큰 엔드포인트 TLS 미적용
`oauth2-authorization-server.yml`의 `server.port: 9000` 옆에 `# TODO: tsl` 주석. 내부 토큰 엔드포인트(HTTP) TLS 적용 계획 확인 필요.
`[출처: docs/modules/OAUTH2_AUTHORIZATION_SERVER.md §14]`

### oauth2-client

#### 1.5 Access Token URL 노출
- 로그인 성공 redirect에서 `?accessToken=` 쿼리로 토큰 전달(`CustomOAuth2LoginSuccessHandler`).
- WebSocket 핸드셰이크에서 `?access_token=` 쿼리로 토큰 전달(`WebsocketHandshakeAuthWebFilter.java:44`).
- 쿼리 파라미터는 프록시 로그·브라우저 히스토리에 남을 수 있음. 인프라 마스킹 여부와 대체 방식(헤더/서브프로토콜) 도입 여부 확인 필요(브라우저 WebSocket API 제약 고려).
- `[oauth2-client]` 로그인 redirect의 `?accessToken=` 생성 지점 확인됨: `CustomOAuth2LoginSuccessHandler`가 token-exchange 후 `frontend.successRedirectUri`에 쿼리로 붙여 `sendRedirect`.
`[출처: SERVICE_FLOWS.md #3, ARCHITECTURE.md #4, API_GATEWAY.md §18.2 / oauth2-client 분석]`

#### 1.6 로그아웃 시 JWT 미검증 파싱
`CustomLogoutSuccessHandler.resolveSubject`는 JWT 검증 실패(`JwtValidationException`) 시 서명 미검증으로 subject를 파싱(`parseSubjectWithoutValidation`)해 블랙리스트/토큰 삭제에 사용한다. 만료 토큰으로도 로그아웃을 허용하려는 의도로 보이나, 서명 미검증 파싱을 어디까지 허용할지 확인 필요.
`[출처: docs/modules/OAUTH2_CLIENT.md §12]`

#### 1.7 redirect-uri localhost 하드코딩
`oauth2-client.yml`의 google/kakao `redirect-uri`가 `https://localhost:8000/...`로 하드코딩(kakao에 `# TODO: 주입하기` 주석). 운영 값 주입 방식 확인 필요.
`[출처: docs/modules/OAUTH2_CLIENT.md §12]`

### user

#### 1.9 BCrypt strength 5
`PasswordEncoderConfig`가 `BCryptPasswordEncoder(5)` 사용(기본 10보다 낮음). 성능 의도인지 확인 필요.
`[출처: docs/modules/USER.md §16]`

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
`[출처: 2026-08-27 재측정 조건 검토 / rate limit 한도와 테스트 조건 대조]`

#### 1.14 게이트웨이 WebSocket 핸드셰이크 Rate Limit 을 측정용으로 올려 둔 상태다 — 되돌려야 한다

`git-config-repo/dynamic/api-gateway.yml`의 `gateway.rate-limit.websocket-handshake`를 **`2/5/1` → `100/300/1`로 올려 둔 상태**다(팬아웃 용량 재측정용). **측정이 끝나면 되돌린다.**

원래 값에서는 측정 자체가 불가능하다. `RateLimitConfig`가 `WEBSOCKET_NATIVE_HANDSHAKE`·`WEBSOCKET_HANDSHAKE` 라우트에 `keyResolvers.user()` 기준으로 리미터를 붙이는데, k6는 계정 2개를 전 VU가 공유한다. 사용자별 `burst-capacity: 5`이므로 **계정당 5개, 총 10개만 통과**한다.

| VU | 기대 연결 | 실제 연결 | 근거 |
|---:|---:|---:|---|
| 20 | 20 | **10** | `ws upgrade status is 101` → ✓10 / ✗10 (2026-08-27 실측) |
| 60·80·100 | 각 60·80·100 | **10** | 계정 2개 × burst 5. VU를 올려도 상한이 같다 |

VU를 올려도 연결이 10개에서 멈추므로 팬아웃(`M²`)이 생기지 않아 `C`를 잴 수 없다. **핸드셰이크는 연결 시 1회만 검사하고 STOMP 전송은 이미 열린 소켓을 쓰므로, 이 값은 팬아웃 처리량 측정 경로에 영향을 주지 않는다.**

1.13(STOMP Rate Limit)과 **원인이 같다** — 계정 2개 공유라는 테스트 구조가 사용자별 리미터 둘 다에 걸린다. 근본 해결은 VU별 자격증명 공급(→ 5.4)이며, 그때 1.13·1.14를 함께 원복한다.

주의: **busrefresh로 반영되지 않는다.** `GatewayRateLimitProperties`는 불변 record이고 `RedisRateLimiter` 빈이 기동 시점 값으로 라우트별 Config를 등록하므로 **api-gateway 재배포**가 필요하다(1.13과 같은 제약).

되돌리기: `websocket-handshake`를 `replenish-rate: 2` / `burst-capacity: 5` / `requested-tokens: 1`로, 주석 제거, **머지 + api-gateway 재배포**.
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

### user

#### 2.5 ARCHITECTURE.md §6의 user Read Replica 서술과 코드 불일치
`ARCHITECTURE.md` §6이 user 서비스에 read Hikari + `ReplicationRoutingDataSource`가 구성됐다고 적었으나, `user/user-adapter-out/.../infra/config/DataSourceConfig.java`에는 write DataSource 하나뿐이다(read pool도 routing DataSource도 없다). §8.6 · §11 · `docs/modules/USER.md` §10이 서로를 참조하는 구조라 한 줄만 고치면 나머지 불일치가 남는다. market에는 실제로 `DatasourceConfig`(write+read+routing) + `@ReadReplica` 적용 지점이 있어 서술이 맞으므로, **user만 틀린 것인지 전수 대조 후 정정**한다. ADR-003의 커넥션 예산은 코드 기준(user는 write pool 하나, master write 합계 165)으로 산정했으므로 영향 없다.
`[출처: 2026-08-27 ADR-003 커넥션 예산 산정 중 코드 대조]`

---

## 3. 계약 · 직렬화

### notification

#### 3.3 notification DLQ 미소비 · 재시도 부재
`common-core/KafkaTopic.NOTIFICATION`이 `notification-event.dlq`를 정의하나, `notification-service.yml`의 `spring.cloud.function.definition`(`priceAlertDetectedEventConsumer;notificationEventConsumer`)에 **DLQ consumer가 없다**. 또한 `NotificationEventService.handle`은 단순 `@Transactional("notificationMongoTransactionManager")`로 `@Retryable`/`@Recover`가 없다(chat의 재시도→DLQ 복구 패턴 부재). Mongo 영속 실패 시 처리(바인더 기본 재시도/유실 여부)와 DLQ 운영 의도 확인 필요.
`[출처: docs/modules/NOTIFICATION.md §10, §11]`

---

## 4. 배포 · 인프라

### CI/CD (공통)

#### 4.7 fork PR 은 이미지 승격 경로를 못 탄다
`ci.yml` 의 머지 승격은 PR 실행이 `pr-<번호>` 이미지를 레지스트리에 push 해둔 것을 전제로 한다. **fork PR 은 GitHub 이 시크릿을 주지 않아 push 가 불가능**하므로(보안 설계) 빌드만 하고 끝나며, 머지 시 승격 대상이 없어 풀빌드로 떨어진다. 현재는 모든 PR 이 같은 저장소 브랜치에서 오므로 실사용 영향이 없다.

외부 기여를 받게 되면 표준 2단계 분리가 필요하다: PR 워크플로(시크릿 없음)가 이미지를 artifact 로 올리고, `workflow_run` 으로 트리거되는 별도 워크플로(base 브랜치 코드로 실행되어 시크릿 보유)가 그 artifact 를 내려받아 push 한다. PR 코드를 실행하지 않으므로 시크릿이 새지 않는다. **`pull_request_target` 은 쓰지 않는다** — 시크릿을 받지만 PR 코드를 체크아웃해 실행하면 그대로 탈취된다.
`[출처: docs/CI_CD.md §2.2 / #207 설계 논의]`

#### 4.8 Merge Queue 도입 검토
현재는 PR 에서 한 번, 머지 후 또 한 번 CI 가 도는 구조를 **tree 해시 비교 + 이미지 승격**으로 우회하고 있다(`docs/CI_CD.md §2.1`). GitHub **Merge Queue** 는 머지 직전에 "머지된 결과" 를 만들어 CI 를 돌리고 통과해야 머지하므로, 머지 후 CI 가 **구조적으로 불필요**해진다. 우리가 우회한 문제의 정식 해법이다.

도입하려면 룰셋에 merge queue 를 설정하고 `auto-pr.sh` 훅의 auto-merge 흐름(`gh pr merge --auto --squash`)과 맞물리는 부분을 함께 손봐야 한다. 승격 로직(`merge-ci` 의 `Resolve promotion source`)을 제거할 수 있는지도 함께 판단한다. 지금 구조가 동작하고 있으므로 급하지 않다.
`[출처: docs/CI_CD.md §2.1 / #207 설계 논의]`

#### 4.9 upbit-connector 첫 배포 전 초기화 필요
`cd.yml` 배포 대상 등록과 infra 저장소의 safe-recreate 스크립트는 추가됐다. 다만 스크립트가 rollback 기준으로 읽는 `service/.deploy/upbit-connector.current-image`(git 미추적)가 러너에 없으면 **첫 배포가 실패한다**. 최초 1회 현재 이미지 다이제스트로 생성해야 한다(스크립트가 안내 메시지를 출력한다). 생성 시점·값 확인 필요.
`[출처: docs/modules/UPBIT_CONNECTOR.md §10]`

#### 4.11 upbit-connector REST 조회 API 미구현(2단계)
`upbit-connector`는 현재 WebSocket 수집·Kafka 발행만 한다. 도입 당시 합의한 2단계 — **Upbit REST 조회(캔들·호가 등)를 조합해 응답하는 API** — 는 아직 없다. 프론트 차트에 필요한 과거 데이터를 줄 곳이 없는 상태가 유지된다.
- 착수 시 함께 볼 것: Upbit REST 요청 제한(공식 문서 기준 확인 필요)과 그 구현 위치, 응답 캐시(Redis reactive 여부), Gateway route·CORS(외부 계약 → `.claude/rules/external-contracts.md`).
- **예외 처리 계층이 없다.** 이 서비스는 HTTP 엔드포인트가 없어 지금은 필요 없지만, REST를 열면 응답 형식을 맞출 곳이 필요하다. `common-web/GlobalExceptionHandler`는 MVC 어댑터(`*-adapter-in`)만 쓰고 서블릿 계열 예외를 다루므로 WebFlux에서 그대로 재사용할지, WebFlux 전용 advice(또는 `ErrorWebExceptionHandler`)를 둘지 확인 필요. `common-grpc-server`의 gRPC advice는 gRPC 서버가 없어 무관.
- 설계 메모: 캔들은 `to` 파라미터로 과거를 거슬러 여러 번 호출해야 하며 `Flux.expand`로 표현 가능. 동일 구간 동시 요청은 `Mono.cache()`로 합칠 수 있다. 둘 다 미검증 아이디어다.
`[출처: docs/modules/UPBIT_CONNECTOR.md §2·§6 / 모듈 도입 논의]`

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

### oauth2-authorization-server

#### 4.2 미사용 mysql 설정
`git-config-repo/dynamic/oauth2-authorization-server.yml`에 `mysql.{username,password,db}` 블록이 있으나, `config.name`에 mysql 미포함이고 이 서비스는 DB(JPA)를 쓰지 않음(사용자 정보는 gRPC로 user-service 조회). 설정 잔재 여부 확인 필요.
`[출처: docs/modules/OAUTH2_AUTHORIZATION_SERVER.md §14]`

### spring-cloud-eureka-server

#### 4.3 단일 노드 · self-preservation 비활성
`git-config-repo/infrastructure/eureka-server.yml`이 peer 복제 없는 standalone(`register-with-eureka: false`, `fetch-registry: false`)이고 `enable-self-preservation: false`(eviction 30s). 네트워크 순단 시 정상 인스턴스도 빠르게 축출될 수 있음. 개발/소규모 의도로 보이나 운영 HA(peer)·self-preservation 정책 확인 필요.
`[출처: docs/modules/EUREKA_SERVER.md §9 / spring-cloud-eureka-server 분석]`

### notification

#### 4.4 Gradle 플러그인 `crypto-domain` 사용(application/adapter)
`notification-application`·`notification-adapter-in`·`notification-adapter-out`이 모두 `id 'crypto-domain'` 플러그인을 적용한다(타 서비스는 각각 `crypto-application`/`crypto-adapter`). 동작에는 문제없어 보이나 계층별 convention plugin 규약과 이질적 — ArchUnit/플러그인 설정상 의도인지 확인 필요.
`[출처: docs/modules/NOTIFICATION.md §4]`

### outbox-poller

#### 4.5 Debezium CDC Outbox 장기 전환 검토
현재는 `outbox-poller`가 event DB의 `PENDING` 레코드를 polling하고 `PUBLISHED`/`FAILED`/`retryCnt`를 직접 관리하는 at-least-once relay를 사용한다. 운영 인프라와 학습 비용을 낮추고 dispatchType별 지연을 독립적으로 제어하기 위한 현재 선택으로 유지한다.

처리량·polling DB 부하·poller 상태 관리 비용이 증가하거나 Kafka Connect 운영 역량이 확보되면 Debezium Outbox Event Router 기반 CDC 전환을 다시 검토한다. 검토 시 MySQL binlog 보존/replication 권한, Kafka Connect HA, connector offset·schema history·lag 모니터링, 장애 후 중복 가능성과 consumer 멱등성을 함께 설계한다. Debezium offset 기반 재개는 전달 누락 위험을 줄이지만, 기본 구성만으로 같은 `transaction_id`의 여러 토픽 발행이 하나의 Kafka transaction으로 원자화되지는 않으므로 단일 Envelope, transaction metadata 기반 집계 또는 후속 Kafka transaction 필요 여부도 별도로 결정한다.
`[출처: docs/modules/OUTBOX_POLLER.md §5 트랜잭션 경계와 보장 수준]`

#### 4.6 `FAILED` Outbox 재처리 경로 추가
`OutboxService.publishPending`은 `PENDING` 레코드만 조회하고 retry를 소진하면 `FAILED`로 전환한다. 실패 레코드는 DB에 보존되지만, 저장소 코드에는 원인 해결 후 `FAILED`를 다시 `PENDING`으로 전환하거나 선택적으로 재처리하는 API·스케줄러·운영 작업이 확인되지 않는다. at-least-once relay가 운영 복구까지 포함해 최종 수렴하려면 재처리 대상 선택, retry count 초기화 여부, 중복 발행 경고·감사 로그와 접근 통제를 포함한 복구 경로를 설계한다.
`[출처: docs/modules/OUTBOX_POLLER.md §5 트랜잭션 경계와 보장 수준]`

---

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

#### 5.2 chat 스파이크 시 배치 웜업 도입 검토(선택)
chat 은 **cache-first**(캐시가 Mongo 보다 앞섬)라 miss 엔 줄 stale 이 없어 **SWR 부적합**, 미스 복구는 로드 완료까지 **동기 대기**한다. 방어 도구는 reload 비용에 따라 다르다: **방(`ChatRoomQueryRepairService`)=`SingleFlight`**(싼 point reload → 경량 동기 dedup), **메시지(`ChatMessageQueryRepairService`)=분산락**(무거운 range reload → 전역 1회 보장). 만약 대량 스파이크에서 콜드 miss DB 부하가 실측 병목이 되면, **주기/이벤트 배치 웜업(만료 전 재적재)로 만료 자체를 회피**(인기방 등 hot 대상 한정)를 검토한다.
`[근거: docs/modules/CHAT.md 캐시 절]`

### websocket-gateway

#### 5.3 STOMP 팬아웃 처리량 개선

부하테스트에서 확인된 유일한 실측 병목이다. 같은 방의 모두가 서로의 메시지를 받으므로 **전달 작업이 사용자 수의 제곱으로 는다**
(130명이 각자 초당 1개만 보내도 초당 16,900건). 130명 구간에서 이미 Broadcast p95가 10초를 넘었다.

원인은 자원 고갈이 아니다. 게이트웨이 CPU 20%·GC pause 0·gRPC 저장 실패 0건인데 STOMP outbound 스레드는 상한(96)까지 찼고,
JFR에서 278바이트 쓰는 데 209ms 걸리는 소켓 write 블로킹이 잡혔다. **처리량 ≈ 스레드 수 ÷ 블로킹 시간**이라
`96 ÷ 0.022초 ≈ 4,400건/s`로, 관측된 채널 처리량 6,000건/s와 같은 자릿수다. 요구량은 22,500건/s였다.

후보 셋. 배타적이지 않고 곱해서 효과가 난다.

| 접근 | 성격 | 비용·확인 필요 |
|---|---|---|
| **배치 전송** | 방별 시간창(예: 100ms)으로 묶어 프레임 수를 줄인다. 100ms면 1/15 | STOMP wire payload가 배열이 되어 **외부 계약 변경**(프론트 수정 필요). 방 내 순서 보장·창 유실 범위를 함께 설계 |
| 가상 스레드 | 대기 시간을 회수해 좌변(처리량)을 확장 | **Java 21 업그레이드**(현재 17, `build-logic`·Dockerfile 전 서비스). 그리고 **효과가 0일 수 있다** — 블로킹 구간이 `synchronized`나 JNI 안에 있으면 가상 스레드가 캐리어 스레드에서 분리되지 못하고(pinning) 기존과 똑같이 막힌다. JDK 24(JEP 491)에서 `synchronized` 제약은 해소됐다. 착수 전 `-Djdk.tracePinnedThreads=full`로 Tomcat NIO 경로를 먼저 확인한다 |
| 인스턴스 확장 | 세션을 나눠 가져 인스턴스당 write 감소 | 실측으로 150·200명 구간 avg·p95 41~53% 감소 확인됨 |

스레드를 늘리는 것만으로는 못 이긴다 — **좌변은 선형, 우변은 제곱**이다.
현재 걸어둔 방 Rate Limit 30 msg/s도 우변을 깎는 조치지만 근본 해결은 아니다.
`docs/CODE_STYLE.md` §16(대량 broadcast의 channel contention·backpressure 고려)과 함께 본다.
`[출처: chat/load-test-results/chatmessage/websocket-gateway/README.md, 2026-05-08 부하테스트]`

#### 5.4 부하테스트 재측정 시 보정할 항목

현재 결과는 집계 한계가 있어 지연 지표만 그대로 쓸 수 있다. 재측정할 때 아래를 함께 고친다.

**상태: 미완료.** 재측정 자체를 아직 하지 않았다. 아래 다섯 중 **값이 정해진 것은 앞의 둘뿐**이고(정했을 뿐 실행하지 않았다), 나머지 셋은 미착수다. 재측정을 마치기 전에는 이 항목을 닫지 않는다.

- **`ACK_TIMEOUT_MS`를 gRPC deadline보다 크게 → `11000`으로 정했다.** 지금은 둘 다 10초라 deadline 초과 거절 ACK가 스크립트의
  무응답 처리 뒤에 도착해 `ack_failed_count`로 잡히지 않는다. 거절과 지연이 갈리지 않는다.
  hardDelete 보상은 실패 ACK를 막지 않으므로(`ChatMessageSendService.handleSaveError`가 future를 기다리지 않는다) deadline이 중첩되지는 않는다.
  다만 **실패 ACK 자체가 broker·outbound 큐를 탄다** — outbound 지연이 1초를 넘으면 11초로도 여전히 무응답으로 집계된다.
  결과 해석 시 `stomp.executor` 큐 지표를 함께 본다. 타임아웃을 늘려도 부하 패턴은 안 바뀐다(전송은 `setInterval(send_interval_ms)`로 ACK와 무관하고 `ack_timeout_ms`는 집계 스위퍼 전용).
- **`COLLECT_WINDOW_MS` 확대 → `60000`.** 이 값은 전송을 마친 뒤 소켓을 열어둔 채 broadcast를 더 받는 시간이고, 창이 닫히면
  그때까지 안 온 것이 전부 `미도달`로 집계된다. 즉 미도달은 "영구 유실"이 아니라 "수집 창이 닫힐 때까지 안 온 것"이다.
  1차 130 VU는 발생량 169,000건 ÷ 관측 6,000/s ≈ 28초인데 총 창이 40초(전송 10초 + 수집 30초)라 전부 들어와 미도달 0이 나왔다 — 서버가 안 밀렸다는 뜻이 아니라 창이 컸다는 뜻이다(같은 실행 p95 10.65초).
  새 조건 VU 100·60개는 발생량 600,000건 ÷ 6,000/s = 100초, 전송 구간 60초라 잔여 백로그가 약 40초여서 60초 창으로 덮인다.
- **VU별 자격증명 공급.**(미착수) 계정 2개를 전 VU가 공유해 ACK가 세션 수만큼 복제된다(150 VU 실행 수신 메시지의 약 40%).
  사용자별 Rate Limit 검증에도 필요하다
- **서버 메트릭 교차 검증.**(미착수) `ws.grpc.client.errors{method,code}`로 거절 건수를 실측해 클라이언트 집계와 대조한다
- **미측정 영역 둘.**(미착수) 부하 중 DLQ 적체 여부, `stomp-in` 풀이 32/32로 포화된 이유(인바운드는 초당 150건뿐이라 찰 이유가 없다)
`[출처: chat/load-test-results/chatmessage/websocket-gateway/README.md 「측정값 신뢰 범위」]`

#### 5.7 broker executor 가 스레드를 안 쓰고 큐만 채운다 — 원인 미확정

VU 80 재측정(2026-08-28)에서 `stompBrokerExecutor` 만 포화했다.

| | 값 |
|---|---:|
| broker 거절 | **17,250건** |
| broker 큐 최대 | **3,000 / 3,000** (포화) |
| 같은 시점 broker 활성 스레드 | **5 / 64** |
| broker `pool_size` | 64 (측정 내내 일정) |
| outbound 거절 | 0건 |
| outbound 큐 최대 | 1,019 / 30,000 |

**스레드가 없어서가 아니라 있는데 안 쓰였다.** 같은 스크레이프 시점에 큐 3,000·활성 5다.

JFR(`gateway-vu80.jfr`) 관측:

- broker 스레드 park 41,774건 — 대부분 `LinkedBlockingQueue.take()` 유휴 대기
- 그중 1,052건이 같은 큐의 `takeLock` 경합(`ReentrantLock.lockInterruptibly`, 최대 56ms)
- `jdk.JavaMonitorEnter` 198건 — 1차의 거절 경로 락 경합(3,637건)은 사라졌다

**가설**: 소비자 스레드 64개가 단일 `LinkedBlockingQueue` 의 `takeLock` 을 두고 경합해 실제 소비
속도가 스레드 수에 비례하지 않는다. 다만 락 대기 1,052건은 큐 포화를 설명하기엔 적어 **확정이 아니다.**

**모순 하나**: `executor_completed_tasks_total` 이 누적 2,540,956인데, 메시지당 brokerChannel 통과를
3건(방 브로드캐스트·뱃지·ACK)으로 잡은 추정치(약 5만)와 50배 차이 난다. brokerChannel 을 지나는
실제 메시지 종류를 다시 확인해야 한다 — 세션별 확장 메시지도 이 채널을 지나는지.

**실험 (진행 중)**: 변수 하나만 바꾼다. `broker` 스레드만 조정하고 큐 3,000 은 고정.

VU 80 동일 조건 실측:

| 스레드 | 활성 최대 | 활용률 | 거절 | 큐 최대 | **브로드캐스트 유실** | **ACK 유실** | p90 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 64 | 5 | 7.8% | 17,250 | 3,000 (포화) | 869 (0.23%) | 70 (1.48%) | 5,951ms |
| 32 | 5 | 15.6% | 6,942 | 286 | **316 (0.08%)** | **63 (1.33%)** | **5,705ms** |
| 16 | 8 | 50% | 5,413 | 1,649 | 320 (0.08%) | **1,023 (21.3%)** | 10,649ms |

> **집계 정정**: k6 가 출력하는 `미도달` 을 그대로 유실로 읽으면 안 된다. 기대치를 `VUS × VUS × MESSAGE_COUNT`
> 로 잡는데, 연결에 실패한 VU 가 있으면 분모만 커진다. 64·32 실행은 `ws upgrade` 가 ✗1 이어서
> 실제로는 79명이 79명에게 보냈다(`subscribe_frame_count` 158, `send_count` 4,740).
>
> ```
> k6 기대치     4,800 × 80 = 384,000
> 실제 기대치   4,740 × 79 = 374,460
> ```
>
> 이 보정을 하면 세 구성 모두 **브로드캐스트 유실은 0.1% 내외**다. 처음에 "64에서 10,409 유실"로
> 읽은 것은 착시였고, **구성 간 진짜 차이는 ACK 유실과 지연에 있었다.** 16 은 브로드캐스트를 살리는
> 대신 ACK 를 21.3% 버렸다.

**스레드를 줄였더니 활용률이 오르고 거절이 줄었다.** 락 경합 가설과 방향이 맞는다.
DB 압박도 함께 풀렸다(pending 75 → 18, 커넥션 타임아웃 8.2건 → 0건) — broker 가 덜 막히니 상류가 편해졌다.

**32 가 최적이다.** 거절이 64 대비 60% 줄고 큐 포화가 해소되면서 p90 도 가장 낮다.
16 은 거절이 더 줄었지만 소화 속도가 부족해 ACK 를 21.3% 버렸고 p90 이 10.6초로 SLO 를 넘겼다.

```
64  락 경합으로 5개만 가동 → 큐 포화
32  균형점 — 큐 286, p90 5,705ms
16  8개 가동하지만 처리 속도 부족 → ACK 유실 21.3%, 지연 2배
```

**남은 문제는 스레드 수가 아니라 ACK 가 브로드캐스트와 같은 채널에서 잘린다는 것이다(→ 5.8).**

**근본 해결은 배칭이다(→ 5.3).** 이 실험은 원인 규명이며, 배칭 설계에도 brokerChannel 통과량이
어떻게 결정되는지 알아야 한다.

되돌리기: `broker` 를 `core-size: 64, max-size: 64` 로, 주석 제거, 머지 + websocket-gateway 재배포.
`[출처: 2026-08-28 VU 80 재측정 / gateway-vu80.jfr]`

---

#### 5.8 ACK 가 브로드캐스트와 같은 채널에서 잘렸다 — 해소함

`ExecutorConfig` 는 ACK 풀에만 `CallerRunsPolicy` 를 걸어 "ACK 는 버리지 않는다"를 의도했다.
**방어 위치가 틀렸다.**

```
gRPC save 성공
  → chatMessageAckExecutor      CallerRunsPolicy — 여기선 안 버림
    → SimpMessagingTemplate.convertAndSendToUser()
      → brokerChannel           shedding 핸들러. 여기서 버려진다
        → clientOutboundChannel → 소켓
```

`SimpMessagingTemplate`(= `brokerMessagingTemplate` 빈)은 brokerChannel 을 물고 만들어진다.
ACK 와 브로드캐스트가 **같은 채널·같은 거절 핸들러**를 쓴다. ack executor 는 넘겨주기만 하므로
거기 건 정책은 실효가 없었다.

VU 80 / broker 16 실측에서 **ACK 21.3%(1,023건)** 가 유실됐다. 같은 실행의 브로드캐스트 유실은 0.08%다.

| 유실 | 회복 |
|---|---|
| 브로드캐스트 | 방 재진입 시 Mongo 조회 — **회복 O** |
| Kafka lag | 따라잡음 — **회복 O** |
| **ACK** | 발신자가 영영 모름. 실패로 오인해 재전송하면 중복 — **회복 X** |

**회복 불가능한 쪽을 버리고 있었다.** 양으로도 ACK 는 브로드캐스트의 80분의 1이라 보호 비용이 가장 싸다.

**대응**: brokerChannel 거절 정책을 `CallerRunsPolicy` 로 바꿨다. 호출자가 브로드캐스트면 Kafka
컨슈머 스레드가(→ lag, 회복 가능), ACK 면 ack executor 스레드가 직접 실행한다.
대가는 지연 증가이며 재측정으로 확인한다.

**근본 해결은 배칭(→ 5.3)이다.** 브로드캐스트 태스크가 줄면 채널에 여유가 생겨 ACK 가 저절로 산다.
`[출처: 2026-08-28 VU 80 측정 / SimpMessagingTemplate 배선 확인]`

---

#### 5.9 k6 가 뱃지 이벤트를 관측하지 않았다 — 구독 추가함

`StompMyChatRoomBadgeAdapter` 는 **멤버마다** `convertAndSendToUser` 를 호출한다.

```java
for (String memberId : command.memberIds()) {
    stompTemplate.convertAndSendToUser(memberId, destination, payload);
}
```

| | brokerChannel 태스크 |
|---|---|
| 채팅 브로드캐스트 | 1건 (토픽 → SimpleBroker 가 구독자 N명으로 확장) |
| **뱃지** | **멤버 수만큼** |
| ACK | 1건 |

**뱃지만 broker 에서 O(멤버수)다.** 실사용에서 방 멤버가 80명이면 메시지당 80 broker 태스크가 된다.

k6 는 `/user/queue/chat/badge` 를 구독하지 않아 이 부하가 측정에서 빠져 있었다. 구독을 추가했다.

주의: 구독하면 **서버 부하가 실제로 늘어난다.** 이전에는 구독자가 없어 SimpleBroker 가 확장을
0건으로 끝냈다. 따라서 뱃지 구독 이후의 측정은 이전 회차와 직접 비교할 수 없는 **새 기준선**이다.

집계는 채팅 지표와 분리한다(`badge_received_count`) — 발행 단위(멤버별)와 전달 대상(그 사용자의
모든 세션)이 달라 같은 분모로 볼 수 없다. 계정 공유로 인한 증폭은 VU별 자격증명(→ 5.4) 이후에 해소된다.
`[출처: 2026-08-28 StompMyChatRoomBadgeAdapter 확인]`

##### 5.9-a 방 단위 합치기 — 적용함

**뱃지는 내용이 아니라 상태다.** 같은 방에 100ms 사이 30건이 들어와도 마지막 1건만 보내면 화면 결과가 같다. 채팅 메시지(배칭 → 5.3)는 전부 전달해야 하지만 뱃지는 버릴 수 있다.

시세 피드에서 말하는 **conflation** 이다. 배칭과 다르다 — 배칭은 30건을 묶어 전부 전달하고, conflation 은 29건을 버리고 1건만 전달한다. 그래서 메모리가 다르다: 배칭은 창 안 건수만큼 쌓여 부하에 비례하지만, conflation 은 방 개수만큼만 잡고 트래픽과 무관하다.

**이 프로젝트에 같은 패턴이 이미 있다.** `UpbitTickerCollectService` 가 종목별 시세를 같은 방식으로 줄인다.

```java
source.groupBy(UpbitTickerEvent::code)
      .flatMap(codeGroup -> codeGroup
              .sample(properties.websocket().tickerPublishInterval())   // 7s
              .onBackpressureLatest()
              .concatMap(this::publish), Integer.MAX_VALUE);
```

| upbit-connector | 뱃지 | 하는 일 |
|---|---|---|
| `groupBy(code)` | `ConcurrentHashMap` 키 = roomId | 키별로 칸을 나눈다 |
| `.sample(7s)` | 200ms flush + last-write-wins | 구간 마지막 1건만 내보낸다 |
| `.onBackpressureLatest()` | `scheduleWithFixedDelay` | 소비가 느리면 최신만 남긴다 |
| `.concatMap(publish)` | 방별 단일 슬롯 | 키 안에서 순서 유지 |

`upbit-connector` 는 WebFlux 라 `Flux.sample` 이 바로 붙지만, 게이트웨이는 서블릿 기반이고 뱃지 유입이 Kafka 컨슈머 콜백(블로킹)이라 `Flux` 가 없다. 리액티브 파이프라인을 새로 세워 얻는 것이 `sample` 하나뿐이라 맵 + 스케줄러로 직접 구현한다.

**동작이 완전히 같지는 않다.** `groupBy` 는 그룹마다 타이머가 따로 돌고 `flatMap` 으로 그룹 간 병렬이지만, 여기는 **전역 타이머 하나에 단일 스레드가 전 방을 순회**한다. 창마다 전 방이 동시에 나가 스파이크가 생기고, 한 방이 느리면 뒷 방이 밀린다. 반대로 유휴 방은 매 flush 의 `remove` 로 즉시 사라져 `groupBy` 보다 낫다. **방 수가 많아지면 타이머 분산과 병렬화를 검토한다 — 지금 부하테스트는 방 1개라 드러나지 않는다.**

합치기가 성립하는 근거는 **payload 에 개인별 값이 없다는 것**이다.

```java
public record StompMyChatRoomBadgePayload(String roomId, String lastMsgContent, Instant lastMsgCreatedAt) {}
```

`memberIds` 는 라우팅에만 쓰이고 payload 에 안 들어간다. **80명이 받는 바이트가 완전히 같다.** 개인별 필드(내 안읽음 수 등)가 생기면 이 최적화는 깨진다.

VU 80(방 1개, 초당 80건) 기준 예상:

| | broker 태스크/초 |
|---|---:|
| 채팅 브로드캐스트 | 80 |
| 뱃지 (현재) | **6,400** (전체의 98.8%) |
| 뱃지 (200ms 합치기) | **400** — 16배 감소 |

`CoalescingMyChatRoomBadgeAdapter` 가 `MyChatRoomBadgePort` 를 `@Primary` 로 구현해 기존 어댑터를 감싼다. 방별 `ConcurrentHashMap` 에 last-write-wins 로 담고 전용 스케줄러가 창마다 드레인한다.

- `scheduleWithFixedDelay` 를 쓴다. brokerChannel 이 `CallerRunsPolicy` 라 flush 스레드가 broker 태스크를 직접 실행하며 창보다 오래 걸릴 수 있는데, `scheduleAtFixedRate` 면 밀린 실행이 연달아 터진다. fixedDelay 는 느려진 만큼 창이 넓어져 합치는 양이 늘고 **스스로 배압이 된다.**
- Kafka 키가 roomId 라 같은 방은 순서가 보장되지만, 파티션 재할당을 대비해 `lastMsgCreatedAt` 으로 한 번 더 거른다.
- 전용 `ScheduledExecutorService` 를 쓴다. 게이트웨이에 `@EnableScheduling` 이 없고, 공용 스케줄러를 새로 켜면 flush 가 막힐 때 다른 스케줄 작업까지 같이 멈춘다.

**대가**: Kafka 오프셋이 실제 전송보다 먼저 커밋된다. 게이트웨이가 죽으면 버퍼가 유실된다. **뱃지는 방 목록 재조회로 회복되므로 허용**한다 — 회복 불가능한 ACK(→ 5.8)에는 같은 기법을 쓰지 않는다.

지표: `chat.badge.coalesced`(덮어써서 안 나간 건수) · `chat.badge.flushed`(실제 전송) · `chat.badge.pending`(대기 중인 방 수).

되돌리기: `websocket.badge.coalesce.enabled: false`. busrefresh 로는 안 되고 재배포가 필요하다(불변 record + `@PostConstruct` 스케줄러).

##### 5.9-b 토픽 전환 — 보류

`convertAndSendToUser` N건을 `/topic/chat/badge/{roomId}` 1건으로 바꾸면 broker 태스크가 N → 1 이 된다. payload 가 방 단위라 **기술적으로는 가능하다.**

보류하는 이유는 **구독 모델이 바뀌기 때문**이다.

```
지금    /user/queue/chat/badge         1건 구독으로 내 모든 방 뱃지 수신
토픽    /topic/chat/badge/{roomId}     내가 속한 방 N개를 각각 구독
```

로그인 시 방 목록만큼 SUBSCRIBE, 초대·퇴장 시 구독 추가·해제가 필요해 **프론트 계약이 바뀐다.** 게다가 SUBSCRIBE 인가가 없어(→ 1.15) 토픽으로 옮기면 남의 방 뱃지를 받을 수 있다.

**5.9-a 로 broker 여유가 확보되면 하지 않는다.** 재측정으로 판단한다.

##### 5.9-c 사용자 축 집계 — 배칭(5.3) 때 같이 검토

5.9-a 는 **방 축**으로만 접었다. 라운드 수는 줄었지만 **라운드당 프레임 수는 그대로**다.

```
전   초당 80라운드 × 멤버 80 = 6,400 프레임
후   초당  5라운드 × 멤버 80 =   400 프레임
      ↑ 5.9-a 가 줄인 것      ↑ 아직 그대로
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

**지금 하지 않는 이유**: payload 가 배열이 되어 **프론트 계약이 바뀐다**(배칭 5.3 과 같은 등급). 그리고 **현재 부하테스트는 방이 1개**라 이 최적화를 넣어도 숫자가 안 움직인다. 계약은 배칭 때 한 번만 깬다.

##### 5.9-d flush 소요 시간을 재지 않고 있다 — 병렬화 판단 근거가 없다

전역 타이머 하나에 **단일 스레드가 전 방을 순회**한다. `upbit-connector` 의 `groupBy(code).sample(7s)` 는 그룹마다 타이머가 따로 돌고 `flatMap` 으로 병렬이지만, 채팅방은 무한히 늘어 타이머를 방마다 두면 폭증한다. 전역 하나를 고른 이유다.

대가 둘:

| | 내용 |
|---|---|
| 스파이크 | 모든 방이 같은 순간에 나간다. 창마다 봉우리 |
| 전파 | 한 방이 `CallerRunsPolicy` 로 막히면 뒷 방이 전부 정지 |

두 번째가 실제 위험이다. `brokerChannel` 이 포화하면 flush 스레드가 broker 태스크를 직접 실행하며 µs 작업이 수십~수백 ms 가 된다.

**다만 자기 모순적이다** — conflation 자체가 그 포화를 없애려고 넣은 것이라, 효과가 있으면 이 상황이 안 온다. 그래서 지금 병렬화하는 것은 안 일어날 문제에 코드를 더하는 셈이다.

**필요한 것은 병렬화가 아니라 관측이다.**

```java
Timer.builder("chat.badge.flush")   // 한 사이클 소요 시간
```

```
flush 2ms    → 방이 10배 늘어도 여유
flush 150ms  → 창 200ms 에 근접. 타이머 분산·병렬화 시점
```

**코드는 4줄이지만 데이터는 나중에 못 얻는다** — 재배포 + 부하테스트 한 회차가 더 든다. 3차 측정에는 못 넣었고(2026-08-28 판단), 다음 회차에 넣는다.

병렬화가 필요해지면 드레인은 단일 스레드로 두고 전송만 작은 풀로 뺀다. 맵은 여전히 한 스레드만 만지므로 `ConcurrentHashMap` 경합은 0으로 유지된다. **다만 이득은 제한적이다** — 목적지가 `brokerChannel` 큐 하나라 `putLock` 에서 다시 만난다(5.7 에서 `takeLock` 으로 이미 확인한 벽의 반대편). 병렬화의 목적은 처리량이 아니라 "한 방 때문에 전부 정지"를 막는 것이다.

---

#### 5.10 outbound executor 도 스레드를 안 쓰고 큐만 채운다

k6 를 클라우드로 분리한 뒤(맥에서 k6 가 CPU 484% 를 점유하던 오염 제거) VU 80 을 재측정했다.
**커넥션·broker 는 해소됐고 outbound 만 남았다.**

| | 값 |
|---|---:|
| outbound 큐 최대 | **4,232** |
| outbound 활성 스레드 | **17 / 96 (17.7%)** |
| outbound 거절 | 0 |
| broker 큐 최대 | 40 (여유) |
| broker 활성 | 2 / 32 |
| DB pending | **0** |
| DB 점유 | 1초 |

broker 가 64 스레드일 때 활성 5/64(7.8%)로 막혔던 것과 **같은 구간**이고, 32 로 줄여 해소됐다
(→ 5.7). 소비자 스레드가 단일 `LinkedBlockingQueue` 의 `takeLock` 을 두고 경합하는 구조가 같다.

**결과**: 유실은 없는데 지연이 폭발한다. VU 80 2회 측정 모두 재현됐다.

```
1회차   수신률 99.98%  p90 31,507ms  ACK 42.5%
2회차   수신률 100%    p90 38,648ms  ACK 35.3%
```

큐 30,000 이 다 받아내서 버리지 않는 대신 소화를 못 해 밀린다.

**실험 결과 — 가설이 틀렸다.** VU 80 동일 조건(클라우드 k6):

| 스레드 | 활성 최대 | 활용률 | p90 | ACK | 유실 |
|---:|---:|---:|---:|---:|---:|
| 96 | 17 | 17.7% | 31,507~38,648ms | 35~43% | 0~0.02% |
| 32 | **14** | 44% | **45,833ms** | **28.3%** | 0% |

**활용률은 올랐지만 활성 절대값이 17 → 14 로 줄었고 지연은 나빠졌다.**
broker 는 64→32 에서 활성이 5→8 로 **늘어** 개선됐는데 outbound 는 정반대다.

```
broker    락 경합 O   스레드 줄이니 실제 가동이 늘었다
outbound  락 경합 X   스레드 줄이니 여유만 사라졌다
```

**같은 증상(활성 스레드 낮음)인데 원인이 달랐다.** outbound 가 17개까지밖에 못 도는 이유는
스레드 경합이 아니라 다른 데 있다 — 1차 JFR 에서 관측된 **소켓 write 블로킹**(278바이트에 209ms)이
후보다. I/O 대기로 스레드가 묶이면 개수를 늘려도 줄여도 통과량이 안 바뀐다.

주의: 워밍업이 부족한 회차는 못 쓴다. 32 첫 실행은 유실 31.45%·p90 76,648ms 였으나
워밍업 후 재측정에서 유실 0%·p90 45,833ms 로 바뀌었다. **회차마다 워밍업을 반드시 선행한다.**

**다음 실험**: 32 와 96 사이인 64 를 찍어 곡선을 닫는다. 큐는 계속 30,000 고정.

되돌리기: `core-size: 96, max-size: 96` 로 복귀, 주석 제거, 머지 + websocket-gateway 재배포.
`[출처: 2026-08-28 클라우드 k6(Tailscale) VU 80 2회 측정]`

---

#### 5.11 k6 를 클라우드로 분리해 측정 오염을 제거했다

로컬 실행 시 k6 가 맥 CPU 를 최대 484%(4.8코어/6코어) 점유해 서버 컨테이너와 경합했다.
그 결과 매 실행마다 `ws upgrade` 가 1~2개 실패하고(연결 78/80) 수치 변동이 컸다.

OCI `VM.Standard.E5.Flex`(4 OCPU / 16GB)에 k6 를 올리고 Tailscale 로 맥에 연결했다.

| | 로컬 k6 | 클라우드 k6 |
|---|---:|---:|
| k6 CPU | **247%** (VU 60) | **79%** |
| 연결 성공 | 59/60 | **60/60** |
| p90 (VU 60) | 773ms | 899ms |

**연결이 처음으로 100% 됐고 집계 보정이 불필요해졌다.** 지연 +126ms 는 Tailscale DERP 릴레이
왕복(실측 75ms)에서 온다. 직접 연결(direct)은 확립되지 않아 릴레이 경유다.

측정 조건: `WS_BASE_URL` 만 맥의 Tailscale IP 로 바꾼다. k6 버전은 양쪽 모두 v2.2.0 이다.
`[출처: 2026-08-28 클라우드 분리 전후 VU 60 비교]`

---

#### 5.14 조용히 사라지는 실패를 없앤다 — 입구에서 거절하고 알린다

**원칙: 발신자가 결과를 모르는 실패를 만들지 않는다.**

현재 침묵하는 경로가 둘이다.

| 경로 | 발신자가 아는가 |
|---|---|
| inbound 큐 거절 | ❌ 핸들러 진입 전이라 `@MessageExceptionHandler` 그물에 안 걸린다 |
| ACK 가 brokerChannel 에서 거절 | ❌ 저장은 성공했는데 응답만 사라진다 (→ 5.8) |

VU 100 측정에서 **ACK 성공률이 14.38%** 였다. 저장은 거의 다 됐는데 발신자 대부분이 결과를 모른다.
실패로 오인해 재전송하면 중복 메시지가 된다.

**대응: 입구에서 미리 거절하고 이유를 알린다.**

`StompController` 에 이미 같은 패턴이 있다 — rate limit 이 `clientMessageId` 를 담아 거절 ACK 를 보낸다.

```java
if (!rateLimiter.isAllowed(...)) {
    throw new ChatMessageRateLimitExceededException(request.clientMessageId());
}
```

```java
@MessageExceptionHandler(ChatMessageRateLimitExceededException.class)
@SendToUser("/queue/chat/ack")
→ ofFailure(clientMessageId, "RATE_LIMIT_EXCEEDED")
```

여기에 backpressure 판정을 더한다.

```java
if (willExceedLatencyBudget()) {
    throw new ChatMessageServerBusyException(request.clientMessageId());   // "SERVER_BUSY"
}
```

**얻는 것 셋**

1. **사용자가 안다** — 침묵 대신 "서버 혼잡, 재시도" + 어느 메시지인지(`clientMessageId`)
2. **부하가 실제로 준다** — 입구에서 1건을 자르면 저장·outbox·Kafka·팬아웃 outbound N건을 통째로 안 만든다. 뒤에서 버리는 것보다 N배 싸다
3. **거절 ACK 가 살아난다** — 통과량이 줄어 채널에 여유가 생기므로, 거절 통지 자체는 전달된다

**판정 기준 후보**

| 기준 | 성격 |
|---|---|
| outbound 큐 깊이 | 직접적. 현재 지표로 바로 가능 |
| broker 큐 깊이 | |
| **지연 예측(큐 ÷ 처리량)** | **SLO 와 직결.** "10초 안에 못 보낼 것 같으면 미리 거절" |

세 번째가 ADR-003 의 SLO 정의와 맞물린다.

**함께 볼 것**: `VALIDATION_ERROR` · `SERVER_ERROR` ACK 는 `clientMessageId` 가 `null` 이라
어느 메시지가 실패했는지 짚지 못한다. 재전송 로직을 만들려면 이것도 채워야 한다
(`StompChatMessageAckResponse.ofFailure(null, ...)`).
`[출처: 2026-08-28 VU 100 측정 ACK 14.38% / SERVICE_FLOWS.md §15]`

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

k6 는 이미 분리했다(→ 5.11). 남은 것은 서버 쪽이다.

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

**우선순위는 배칭(5.3)보다 낮다.** 배칭은 전송 횟수를 1/30 로 줄여 자릿수를 바꾸지만,
장비 이전은 하드웨어만큼만 올린다. 팬아웃이 `M²` 인 이상 장비로는 못 이긴다.

```
500명 한 방 → 500² × 2(뱃지) = 500,000 전달/s
```

다만 **절대 수치를 말해야 할 때**(포트폴리오·설명서에 "N명 지원"을 쓸 때)는 필요하다.
`[출처: 2026-08-28 2차 부하테스트 / chat/load-test-results/.../2026-08-28/README.md]`

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

#### 5.5 브로드캐스트 유실을 클라이언트가 감지·복구할 경로가 없다

브로드캐스트 push는 DLQ·재시도가 없고 executor 큐 포화 시 버려지는데, **유실을 클라이언트가 감지해 재조회하는 경로가 없다**(2026-08-27 `crypto-project-frontend` 확인).

- `ChatRoomPage.tsx`의 `client.onConnect`는 `subscribeChatRoomMessages`로 **재구독만** 하고 재조회하지 않는다. `onWebSocketClose`·`onStompError`도 `setIsConnected(false)`뿐이다.
- 최근 메시지 로드 `useEffect`의 deps는 `[isLoggedIn, isInvalidRoomId, roomId]` — **마운트·방 변경에만** 돈다. 다른 `getChatMessages` 호출은 `lastMsgId`/`lastCreatedAtMs` 커서를 쓰는 **과거 스크롤** 경로다.
- wire payload `StompChatMessagePayload{messageId, roomId, writerId, content, timestamp, clientMessageId}`에 **방별 순번이 없어** 클라이언트가 갭을 감지할 수단 자체가 없다.

즉 연결을 유지한 채 broadcast가 유실되면 클라이언트는 알지 못하고, 방 재진입·새로고침 전까지 그 메시지가 보이지 않는다. ADR-003이 shedding을 택한 근거는 "재조회로 복구된다"가 아니라 "피크에서 전원을 지연시키는 것보다 일부 유실이 SLO에 유리하다"이며, 갭 복구는 미구현으로 남아 있다.

착수 시 결정할 것: (a) 재연결 시 커서 기반 재조회(프론트만 수정, 갭 감지는 여전히 불가하나 재연결 구간은 덮인다), (b) payload에 방별 순번 추가 후 클라이언트 갭 감지(**STOMP wire payload 변경 = 외부 계약**, 프론트·k6 함께 → `.claude/rules/external-contracts.md`).

`websocket-gateway/CLAUDE.md`와 `docs/modules/WEBSOCKET_GATEWAY.md` §6에 있던 "유실 시 클라이언트 REST 재조회 전제" 서술은 이 확인 결과에 맞춰 같은 커밋에서 정정했다.
`[출처: 2026-08-27 ADR-003 리뷰 중 프론트 구현 대조]`
