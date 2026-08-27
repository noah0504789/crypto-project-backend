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

- **`ACK_TIMEOUT_MS`를 gRPC deadline보다 크게.** 지금은 둘 다 10초라 deadline 초과 거절 ACK가 스크립트의 무응답 처리 뒤에 도착해
  `ack_failed_count`로 잡히지 않는다. 거절과 지연이 갈리지 않는다
- **`COLLECT_WINDOW_MS` 확대.** 미도달은 "영구 유실"이 아니라 "수집 창이 닫힐 때까지 안 온 것"이다. 창을 늘려야 유실과 지연이 갈린다
- **VU별 자격증명 공급.** 계정 2개를 전 VU가 공유해 ACK가 세션 수만큼 복제된다(150 VU 실행 수신 메시지의 약 40%).
  사용자별 Rate Limit 검증에도 필요하다
- **서버 메트릭 교차 검증.** `ws.grpc.client.errors{method,code}`로 거절 건수를 실측해 클라이언트 집계와 대조한다
- **미측정 영역 둘.** 부하 중 DLQ 적체 여부, `stomp-in` 풀이 32/32로 포화된 이유(인바운드는 초당 150건뿐이라 찰 이유가 없다)
`[출처: chat/load-test-results/chatmessage/websocket-gateway/README.md 「측정값 신뢰 범위」]`
