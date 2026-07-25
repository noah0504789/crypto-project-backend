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

### user · spring-cloud-api-gateway

#### 1.8 신뢰 헤더(X-User-Id, X-From) 위조 가능성과 하위 서비스 신뢰
- 게이트웨이가 생성·추가하는 헤더는 `X-User-Id`, `X-From`, `X-Gateway`. `IdentityPropagationGlobalFilter`는 인증된 요청에 한해 `X-User-Id`를 `set`(덮어쓰기).
- `permitAll` 경로(예: `/user/**` 대부분)로 들어온 **인증되지 않은** 요청에서 클라이언트가 보낸 `X-User-Id`·`X-From`을 제거하는 코드는 게이트웨이 모듈에서 확인되지 않음.
- **user 분석으로 확인된 소비 방식** `[user]`: `UserController`는 `X-User-Id`(`HttpHeaderKey.USER_ID_VALUE`)를 `publicId`로 **그대로 신뢰**하고 재검증하지 않는다(`/me/profile` GET·PATCH). (PATCH `/me/profile`의 게이트웨이 `hasRole(USER)` 누락은 해소됨 — `ReactiveSecurityConfig`에 PATCH 규칙 추가. 남은 미해결은 아래 헤더 신뢰/제거 여부.)
- **남은 확인 대상**: (1) permitAll 경로에서 외부 `X-User-Id`·`X-From` 제거 여부, (2) 각 하위 서비스의 신뢰/재검증 방식, (3) 게이트웨이 우회(네트워크) 직접 접근 차단 여부(인프라 설정).
- **결정 전까지** 헤더 강제 제거·하위 서비스 재검증 로직을 추가하지 않는다.
`[출처: API_GATEWAY.md §18.1, §18.4 / user 분석]`

#### 1.9 BCrypt strength 5
`PasswordEncoderConfig`가 `BCryptPasswordEncoder(5)` 사용(기본 10보다 낮음). 성능 의도인지 확인 필요.
`[출처: docs/modules/USER.md §16]`

### spring-cloud-config

#### 1.10 `/sign`·JWKS·`/actuator/busrefresh` 엔드포인트 인증 부재
spring-cloud-config는 `POST /sign`(Vault Transit RS256 서명 대행)·`GET /.well-known/jwks.json`·`POST /actuator/busrefresh`(Spring Cloud Bus 설정 전파)를 노출하나, 모듈 adapter-in에 `SecurityFilterChain`이 없다(`SecurityConfig`는 RSA `KeyFactory` bean만 정의). 앱 계층 `DeploymentControlAuthFilter`는 `/internal/deployment/**`만 검사해 이 엔드포인트들을 보호하지 않는다(config bus 워크플로우는 `X-Deploy-Token`을 보내지만 busrefresh 경로에선 검증되지 않음). `/sign`은 임의 `header.payload`를 실 키로 RS256 서명해 유효 토큰 위조로 이어질 수 있고, `busrefresh`는 전 서비스 설정 재로딩을 유발할 수 있다. 현재는 내부 네트워크 격리에 의존하는 것으로 보이나 전제·의도 확인 필요(설계/결함 미판정).
`[출처: docs/modules/SPRING_CLOUD_CONFIG.md §12, docs/CI_CD.md §4 / spring-cloud-config 분석]`

### chat

#### 1.11 채팅방 명령·조회 인가 부재
`ChatRoomController`의 `create` 위에 `// TODO: 여기 아래로부터 인가 처리하기` 주석이 있고, `create`/`update`/`delete`에 소유자(host) 검증이 없다(`update`/`delete`는 `X-User-Id`조차 받지 않아 임의 사용자가 타인 방을 수정/삭제할 여지). 방 상세(`GET /room/{roomId}`)·메시지 목록(`GET /room/{roomId}/messages`) 조회에도 멤버십 인가 검사가 없다. 게이트웨이 인가 정책과 함께 확인 필요(설계/결함 미판정). 메시지 `save`(gRPC)는 `ChatRoom.validateWritable`로 멤버십을 검증하므로 쓰기 경로와 대비된다.
`[출처: docs/modules/CHAT.md §9, §16]`

### outbox-poller

#### 1.12 DLQ 제어 API 인증 부재
outbox-poller가 `PUT /dlq-poller/start|stop`(`DlqPollerController`)로 DLQ 폴링을 런타임 토글하나, 모듈 계층 인증(`SecurityFilterChain`)이 확인되지 않는다(스타터는 `web`, security 없음). `stop` 시 DLQ 재처리가 멈춰 실패 이벤트가 적체될 수 있다. 게이트웨이 라우팅(`DlqPollerController`는 게이트웨이 컨트롤러 목록에 있음)/네트워크 격리 전제와 접근 통제 여부 확인 필요(config-server 무인증 엔드포인트 1.10과 같은 성격, 설계/결함 미판정).
`[출처: docs/modules/OUTBOX_POLLER.md §5, §7]`

---

## 2. 데이터 · 영속성

### chat

#### 2.3 인기방 인기도 산식 미정
`ChatRoom.popularity()`가 `msgCnt`를 그대로 반환하며 `// TODO: spec 정의 및 주입받기` 주석이 있다. 이 값이 Redis 인기방 zset(`CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX`) 및 Mongo `idx_category_msgCnt` 정렬 스코어로 쓰인다. 최종 인기도 산식(메시지 수 외 최근성·멤버 수 등 반영 여부)과 주입 방식 확인 필요.
`[출처: docs/modules/CHAT.md §12, §14]`

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

### market-detection

#### 3.4 upbit 수집 토픽과 Streams 입력 토픽 불연속
`market-detection.yml`에서 supplier 출력은 `upbit-ticker-event`(`upbitTickerEventSupplier-out-0`)인데 Kafka Streams 입력은 `upbit-ticker-alert-event`(`upbitTickerAlertEventConsumer-in-0`)로 **토픽명이 다르고, 둘을 잇는 바인딩/설정이 코드·`git-config-repo` 어디에서도 확인되지 않는다**. `KafkaEvent.toMessage()`도 `KafkaHeaders.TOPIC`을 세팅하지 않아 목적지는 바인딩(`upbit-ticker-event`)으로 고정된다. 현 구성만으로는 수집 이벤트가 Streams 입력까지 도달하는 경로가 불연속(외부/미배포 브리지·인프라 토픽 설정·잔재 가능성). `docs/SERVICE_FLOWS.md §11–12`도 같은 불연속을 보인다 → 실제 토폴로지 확인 필요.
`[출처: docs/modules/MARKET_DETECTION.md §4, §6]`

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

---

## 5. 확인 완료 (참고 · 조치 불필요)

- notification의 market gRPC 소비 구조 확인됨: `PriceAlertRecipientQueryAdapter`(adapter-out)가 `PriceAlertRecipientQueryPort`를 구현하고 `market-client`의 `PriceAlertSettingClient.findReceiverIds(...)` 호출. `[출처: ARCHITECTURE.md #8]`
