# TODO — 미해결 확인/결정 항목

관찰된 사실을 **주제별로** 모았다. 각 항목은 코드/설정 변경 전 사용자 확인이 필요하며, **확정된 결함으로 단정하지 않는다**(리포지토리 공통 원칙: 코드만으로 의도를 알 수 없는 항목을 임의로 설계/버그로 판정하지 않는다).

- `[출처: …]` = 최초 관찰 문서. 여러 문서에서 중복 관찰된 항목은 하나로 합치고 출처를 모두 표기했다.
- `[user]` = 이번 user 서비스 분석에서 확인/추가된 항목. 상세는 `docs/modules/USER.md §16`.

---

## 1. 인증 · 인가 · 보안

### 1.1 JWT `aud`/`jti` 검증 부재
게이트웨이 검증 체인은 issuer + blacklist + `id` claim만 확인(`ReactiveJwtDecoderConfig`). `aud`/`jti` 검증 코드는 확인되지 않음. 발급 시 `aud` claim이 실제로 포함되는지(oauth2-authorization-server)와 함께, 계약으로 둘지/validator를 추가할지 결정 필요. 현재 issuer가 단일이라 실제 위험도는 미판정.
`[출처: SERVICE_FLOWS.md, ARCHITECTURE.md #2, API_GATEWAY.md §18.3]`

### 1.2 Access Token URL 노출
- 로그인 성공 redirect에서 `?accessToken=` 쿼리로 토큰 전달(`CustomOAuth2LoginSuccessHandler`).
- WebSocket 핸드셰이크에서 `?access_token=` 쿼리로 토큰 전달(`WebsocketHandshakeAuthWebFilter.java:44`).
- 쿼리 파라미터는 프록시 로그·브라우저 히스토리에 남을 수 있음. 인프라 마스킹 여부와 대체 방식(헤더/서브프로토콜) 도입 여부 확인 필요(브라우저 WebSocket API 제약 고려).
`[출처: SERVICE_FLOWS.md #3, ARCHITECTURE.md #4, API_GATEWAY.md §18.2]`

### 1.3 토큰 TTL 7일
access/refresh TTL이 `604800000ms`(7일)로 설정(`git-config-repo/dynamic/jwt.yml`). 주석상 access 의도는 2h로 보이나 값과 다름. 의도 확인 필요.
`[출처: SERVICE_FLOWS.md #1, ARCHITECTURE.md #3]`

### 1.4 `{noop}` client secret
Authorization Server가 client secret을 `{noop}`(평문)로 저장(InMemory 단일 client, `AuthorizationServerConfig`). 의도 확인 필요.
`[출처: SERVICE_FLOWS.md #4, ARCHITECTURE.md #6]`

### 1.5 신뢰 헤더(X-User-Id, X-From) 위조 가능성과 하위 서비스 신뢰
- 게이트웨이가 생성·추가하는 헤더는 `X-User-Id`, `X-From`, `X-Gateway`. `IdentityPropagationGlobalFilter`는 인증된 요청에 한해 `X-User-Id`를 `set`(덮어쓰기).
- `permitAll` 경로(예: `/user/**` 대부분)로 들어온 **인증되지 않은** 요청에서 클라이언트가 보낸 `X-User-Id`·`X-From`을 제거하는 코드는 게이트웨이 모듈에서 확인되지 않음.
- **user 분석으로 확인된 소비 방식** `[user]`: `UserController`는 `X-User-Id`(`HttpHeaderKey.USER_ID_VALUE`)를 `publicId`로 **그대로 신뢰**하고 재검증하지 않는다(`/me/profile` GET·PATCH). 또한 PATCH `/me/profile`는 게이트웨이 `hasRole(USER)` 목록(GET 전용)에 없어 `/user/**` permitAll로 처리될 수 있음.
- **남은 확인 대상**: (1) permitAll 경로에서 외부 `X-User-Id`·`X-From` 제거 여부, (2) 각 하위 서비스의 신뢰/재검증 방식, (3) 게이트웨이 우회(네트워크) 직접 접근 차단 여부(인프라 설정).
- **결정 전까지** 헤더 강제 제거·하위 서비스 재검증 로직을 추가하지 않는다.
`[출처: API_GATEWAY.md §18.1, §18.4 / user 분석]`

### 1.6 `[user]` BCrypt strength 5
`PasswordEncoderConfig`가 `BCryptPasswordEncoder(5)` 사용(기본 10보다 낮음). 성능 의도인지 확인 필요.
`[출처: docs/modules/USER.md §16]`

---

## 2. 데이터 · 영속성

### 2.1 Read Replica 미적용 (user)
라우팅 인프라는 구성됨(`user/.../DataSourceConfig.java`, `common-jpa`). 그러나 user에는 `@ReadReplica`가 적용된 지점이 없어 조회도 write 노드로 라우팅된다(`UserQueryService`는 `@Transactional(readOnly=true)`만 사용 — Aspect 기준 read 라우팅 트리거 아님). 현재 `@ReadReplica` 실제 적용 확인처는 `market/.../MarketQueryService.getMarkets()` 1곳. 의도/결함 미판정. 상세 `docs/modules/USER.md §10`.
`[출처: SERVICE_FLOWS.md #6, ARCHITECTURE.md #1 / user 분석]`

### 2.2 `[user]` nickname DB unique 부재
닉네임 유일성이 애플리케이션 검증(`UniqueUserNicknameValidator.existsByNickname`)에만 의존하고 `schema.sql`에 unique 제약이 없음(unique는 `public_id`, `email`만). 동시 요청 시 중복 삽입 여지(check-then-act). DB unique 백스톱 필요 여부 확인.
`[출처: docs/modules/USER.md §16]`

---

## 3. 계약 · 직렬화

### 3.1 `DlqStatus.COMSUME_FAILED` 철자
오타로 보이나 직렬화 계약(저장된 값)일 수 있음. 변경 시 계약 영향 → 확인 필요.
`[출처: SERVICE_FLOWS.md #5, ARCHITECTURE.md #7]`

---

## 4. 배포 · 인프라

### 4.1 배포 대상 누락
`cd.yml` 배포 대상 드롭다운에 `notification-service`, `market-detection` 없음(둘 다 Dockerfile/이미지 존재). 배포 갭 확인 필요.
`[출처: SERVICE_FLOWS.md #7, ARCHITECTURE.md #5]`

---

## 5. 확인 완료 (참고 · 조치 불필요)

- notification의 market gRPC 소비 구조 확인됨: `PriceAlertRecipientQueryAdapter`(adapter-out)가 `PriceAlertRecipientQueryPort`를 구현하고 `market-client`의 `PriceAlertSettingClient.findReceiverIds(...)` 호출. `[출처: ARCHITECTURE.md #8]`
