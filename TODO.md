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

### websocket-gateway

#### 2.6 세션 위치 TTL 하드코딩
`websocket-gateway`의 `RedisSessionLocationAdapter.SESSION_TTL`이 `Duration.ofMinutes(3)`으로 하드코딩(`// TODO: 주입받기` 주석). STOMP subscribe마다 `refreshTtl`로 갱신되나 값 자체는 Config 주입이 아니다. 연결 유휴 만료 정책이라 값이 짧으면 활성 세션이 조기 만료될 여지 — Config 주입/값 조정 여부 확인 필요.
`[출처: docs/modules/WEBSOCKET_GATEWAY.md §7, §9]`

---

## 3. 계약 · 직렬화

### notification

#### 3.3 notification DLQ 미소비 · 재시도 부재
`common-core/KafkaTopic.NOTIFICATION`이 `notification-event.dlq`를 정의하나, `notification-service.yml`의 `spring.cloud.function.definition`(`priceAlertDetectedEventConsumer;notificationEventConsumer`)에 **DLQ consumer가 없다**. 또한 `NotificationEventService.handle`은 단순 `@Transactional("notificationMongoTransactionManager")`로 `@Retryable`/`@Recover`가 없다(chat의 재시도→DLQ 복구 패턴 부재). Mongo 영속 실패 시 처리(바인더 기본 재시도/유실 여부)와 DLQ 운영 의도 확인 필요.
`[출처: docs/modules/NOTIFICATION.md §10, §11]`

---

## 4. 배포 · 인프라

### CI/CD (공통)

#### 4.1 배포 대상 누락
`cd.yml` 배포 대상 드롭다운에 `notification-service`, `market-detection` 없음(둘 다 Dockerfile/이미지 존재). 배포 갭 확인 필요.
`[출처: SERVICE_FLOWS.md #7, ARCHITECTURE.md #5, docs/CI_CD.md §3]`

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
