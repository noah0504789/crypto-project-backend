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

#### 1.2 토큰 TTL 7일
access/refresh TTL이 `604800000ms`(7일)로 설정(`git-config-repo/dynamic/jwt.yml`, `access-token-expiration-ms`·`refresh-token-expiration-ms` 둘 다). 주석상 access 의도는 2h로 보이나 값과 다름. `AuthorizationServerConfig`의 `TokenSettings`가 이 값을 그대로 사용. 의도 확인 필요.
`[출처: SERVICE_FLOWS.md #1, ARCHITECTURE.md #3 / oauth2-authorization-server 분석]`

#### 1.3 `{noop}` client secret
Authorization Server가 client secret을 `{noop}`(평문) 접두로 저장(`AuthorizationServerConfig.registeredClient()`, InMemory 단일 client). secret 원본은 `oauth2.registered-client.secret` ← Vault `${my.client-secret}`. 의도 확인 필요.
`[출처: SERVICE_FLOWS.md #4, ARCHITECTURE.md #6 / oauth2-authorization-server 분석]`

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
- **user 분석으로 확인된 소비 방식** `[user]`: `UserController`는 `X-User-Id`(`HttpHeaderKey.USER_ID_VALUE`)를 `publicId`로 **그대로 신뢰**하고 재검증하지 않는다(`/me/profile` GET·PATCH). 또한 PATCH `/me/profile`는 게이트웨이 `hasRole(USER)` 목록(GET 전용)에 없어 `/user/**` permitAll로 처리될 수 있음.
- **남은 확인 대상**: (1) permitAll 경로에서 외부 `X-User-Id`·`X-From` 제거 여부, (2) 각 하위 서비스의 신뢰/재검증 방식, (3) 게이트웨이 우회(네트워크) 직접 접근 차단 여부(인프라 설정).
- **결정 전까지** 헤더 강제 제거·하위 서비스 재검증 로직을 추가하지 않는다.
`[출처: API_GATEWAY.md §18.1, §18.4 / user 분석]`

#### 1.9 BCrypt strength 5
`PasswordEncoderConfig`가 `BCryptPasswordEncoder(5)` 사용(기본 10보다 낮음). 성능 의도인지 확인 필요.
`[출처: docs/modules/USER.md §16]`

### spring-cloud-config

#### 1.10 `/sign`·JWKS 엔드포인트 인증 부재
spring-cloud-config는 `POST /sign`(Vault Transit RS256 서명 대행)·`GET /.well-known/jwks.json`을 노출하나, 모듈 adapter-in에 `SecurityFilterChain`이 없다(`SecurityConfig`는 RSA `KeyFactory` bean만 정의). `/sign`은 임의 `header.payload`를 실 키로 RS256 서명해 주므로, 접근 통제가 없으면 유효 토큰 위조로 이어질 수 있다. 현재는 내부 네트워크 격리에 의존하는 것으로 보이나 전제·의도 확인 필요(설계/결함 미판정).
`[출처: docs/modules/SPRING_CLOUD_CONFIG.md §12 / spring-cloud-config 분석]`

---

## 2. 데이터 · 영속성

### user

#### 2.1 Read Replica 미적용
라우팅 인프라는 구성됨(`user/.../DataSourceConfig.java`, `common-jpa`). 그러나 user에는 `@ReadReplica`가 적용된 지점이 없어 조회도 write 노드로 라우팅된다(`UserQueryService`는 `@Transactional(readOnly=true)`만 사용 — Aspect 기준 read 라우팅 트리거 아님). 현재 `@ReadReplica` 실제 적용 확인처는 `market/.../MarketQueryService.getMarkets()` 1곳. 의도/결함 미판정. 라우팅 인프라(`@ReadReplica`·`ReadReplicaAspect`·`ReplicationRoutingDataSource`)는 `common-jpa` 소관. 상세 `docs/modules/USER.md §10`, 인프라 `docs/modules/COMMON.md §5.3`.
`[출처: SERVICE_FLOWS.md #6, ARCHITECTURE.md #1 / user 분석, docs/modules/COMMON.md §5.3 (common-jpa)]`

#### 2.2 nickname DB unique 부재
닉네임 유일성이 애플리케이션 검증(`UniqueUserNicknameValidator.existsByNickname`)에만 의존하고 `schema.sql`에 unique 제약이 없음(unique는 `public_id`, `email`만). 동시 요청 시 중복 삽입 여지(check-then-act). DB unique 백스톱 필요 여부 확인.
`[출처: docs/modules/USER.md §16]`

---

## 3. 계약 · 직렬화

### 공통 (Outbox/DLQ)

#### 3.1 `DlqStatus.COMSUME_FAILED` 철자
`common-outbox`의 `DlqStatus` 값. 오타로 보이나 직렬화 계약(저장된 값)일 수 있음. 변경 시 계약 영향 → 확인 필요.
`[출처: SERVICE_FLOWS.md #5, ARCHITECTURE.md #7 / docs/modules/COMMON.md §8 (common-outbox)]`

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

---

## 5. 확인 완료 (참고 · 조치 불필요)

- notification의 market gRPC 소비 구조 확인됨: `PriceAlertRecipientQueryAdapter`(adapter-out)가 `PriceAlertRecipientQueryPort`를 구현하고 `market-client`의 `PriceAlertSettingClient.findReceiverIds(...)` 호출. `[출처: ARCHITECTURE.md #8]`
