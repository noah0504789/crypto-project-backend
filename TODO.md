# TODO

각 항목에는 원본 출처 문서를 태그로 표기했다(`[출처: 문서명 §절]`). 역추적이 필요하면 해당 문서의 git 히스토리를 참고한다.

---

## SERVICE_FLOWS.md 관련

1. **Access Token TTL 7일**(`git-config-repo/dynamic/jwt.yml`, `604800000ms`). 주석상 access 의도는 2h로 보이나 현재 값과 다름 → 확인 필요. `[출처: SERVICE_FLOWS.md]`
2. **JWT `aud` 검증 미확인**: 게이트웨이는 issuer + blacklist + `id` claim만 검증(`ReactiveJwtDecoderConfig`). `aud`/`jti` 검증 코드 미확인 → 계약으로 유지할지 확인 필요. `[출처: SERVICE_FLOWS.md]`
3. **Access Token URL 노출**: 로그인 성공 redirect에서 `?accessToken=` 쿼리로 토큰 전달(`CustomOAuth2LoginSuccessHandler`) → 확인 필요. `[출처: SERVICE_FLOWS.md]`
4. **`{noop}` client secret**: Authorization Server가 client secret을 평문 저장(InMemory 단일 client) → 확인 필요. `[출처: SERVICE_FLOWS.md]`
5. **`DlqStatus.COMSUME_FAILED` 철자**: 오타로 보이나 직렬화 계약일 수 있음 → 변경 시 계약 영향, 확인 필요. `[출처: SERVICE_FLOWS.md]`
6. **user Read Replica 미적용**: 라우팅 인프라는 있으나 `@ReadReplica`가 user에는 적용되지 않음(현재 확인된 적용처는 `market/.../MarketQueryService.getMarkets()` 1곳) → 확인 필요. `[출처: SERVICE_FLOWS.md]`
7. **배포 대상 누락**: `cd.yml`에 `notification-service`, `market-detection` 배포 항목 없음 → 확인 필요. `[출처: SERVICE_FLOWS.md]`

---

## ARCHITECTURE.md 관련

| # | 관찰된 사실(근거) | 상태 |
|---|---|---|
| 1 | Read Replica 라우팅 인프라는 구성됨(`user/.../infra/config/DataSourceConfig.java`, `common-jpa`). `@ReadReplica` 실제 적용은 `market/.../MarketQueryService.getMarkets()` 1곳에서만 확인. user의 `UserQueryService`는 `@Transactional(readOnly=true)`만 사용(Aspect 기준 read 라우팅 안 됨). | 확인 필요(의도/결함 미판정) |
| 2 | 게이트웨이 JWT 검증은 issuer + blacklist + `id` claim만 확인됨(`spring-cloud-api-gateway/.../ReactiveJwtDecoderConfig.java`). `aud`/`jti` 검증은 확인되지 않음(기존 문서는 계약으로 기술). | 확인 필요 |
| 3 | access/refresh 토큰 TTL이 `604800000ms`(7일)로 설정(`git-config-repo/dynamic/jwt.yml`). 주석상 access는 2h 의도로 보이나 현재 값과 차이. | 확인 필요 |
| 4 | 로그인 성공 시 access token을 SPA redirect의 `?accessToken=` 쿼리 파라미터로 전달(`oauth2-client/.../CustomOAuth2LoginSuccessHandler`). | 확인 필요(노출 위험) |
| 5 | `cd.yml` 배포 대상 드롭다운에 `notification-service`, `market-detection` 없음(둘 다 Dockerfile/이미지 존재). | 확인 필요(배포 갭) |
| 6 | Authorization Server가 client secret을 `{noop}`(평문)로 저장(`oauth2-authorization-server-adapter-in/.../AuthorizationServerConfig.java`, InMemory 단일 client). | 확인 필요 |
| 7 | `DlqStatus`에 `COMSUME_FAILED` 철자(오타로 보임)가 있으며 외부 계약(직렬화 값)일 수 있음. | 확인 필요(변경 시 계약 영향) |
| 8 | notification의 market gRPC 소비 구조는 확인됨: `PriceAlertRecipientQueryAdapter`(adapter-out)가 `PriceAlertRecipientQueryPort`를 구현하고 `market-client`의 `PriceAlertSettingClient.findReceiverIds(...)` 호출. (`notification/notification-adapter-out/.../grpc/PriceAlertRecipientQueryAdapter.java:17,27`) | 사실 확인 완료 |

---

## API_GATEWAY.md 관련

### 신뢰 Header(X-User-Id, X-From) 위조 가능성과 하위 서비스 신뢰 방식
`[출처: API_GATEWAY.md §18.1]`
- **현재 확인된 사실**: Gateway가 생성·추가하는 헤더는 `X-User-Id`, `X-From`, `X-Gateway` 세 가지다(§10). `IdentityPropagationGlobalFilter`는 인증된 요청에 한해 `X-User-Id`를 `set`(덮어쓰기)한다(`IdentityPropagationGlobalFilter.java:25-29`). `permitAll` 경로(예: `/user/**` 대부분)로 들어오는, 인증되지 않은 요청에서 클라이언트가 원래 보낸 `X-User-Id`·`X-From`을 명시적으로 제거하는 코드는 이 모듈에서 확인되지 않았다. 이 값을 하위 서비스(user-service, chat-service, websocket-gateway, oauth2-client)가 어떻게 처리하는지는 이 모듈 코드에 없다.
- **잠재적 위험**: 하위 서비스가 이 헤더들을 "Gateway를 거쳐 인증된 요청"의 근거로 무조건 신뢰한다면, 인증 없이 `permitAll` 경로로 임의의 값을 주입할 수 있는 경로가 될 수 있다.
- **아직 확인하지 못한 범위**:
  - `permitAll` 경로에서 클라이언트가 보낸 `X-User-Id`·`X-From`이 제거되는지.
  - 각 하위 서비스가 이 헤더를 인증 또는 내부 요청의 근거로 신뢰하는지, 별도로 재검증하는지.
  - 각 하위 서비스에 Gateway를 거치지 않은 직접 접근(네트워크 우회)이 차단되어 있는지 — 인프라/네트워크 설정 확인이 필요하며 이 모듈 코드로는 판단할 수 없다.
- **다음 확인 대상**: user-service, chat-service, websocket-gateway, oauth2-client 분석에서 각각 (1) 헤더 소비 코드, (2) 신뢰 여부, (3) Gateway 우회 시 네트워크 차단 여부를 확인한다.
- **코드 변경 전 사용자 결정 필요**: 위 확인 결과가 나오기 전까지 `X-User-Id`·`X-From` 강제 제거나 하위 서비스 재검증 로직을 추가하지 않는다.

### WebSocket access_token 쿼리 파라미터 노출
`[출처: API_GATEWAY.md §18.2]`
- **현재 확인된 사실**: WebSocket 핸드셰이크는 `?access_token=<JWT>` 쿼리 파라미터로 토큰을 전달한다(`WebsocketHandshakeAuthWebFilter.java:44`). URL 쿼리 파라미터는 프록시 로그, 브라우저 히스토리 등에 남을 수 있는 위치다.
- **잠재적 위험**: 로그·프록시 경유 시 토큰 노출 가능성(일반적인 쿼리 토큰 패턴의 특성).
- **아직 확인하지 못한 범위**: 실제 인프라(프록시/로드밸런서/로그 수집기)가 쿼리 파라미터를 마스킹하는지 여부.
- **다음 확인 대상**: 인프라 로깅 설정, websocket-gateway 분석 시 대체 인증 방식 검토.
- **코드 변경 전 사용자 결정 필요**: 대체 방식(서브프로토콜 헤더 등) 도입 여부 — 브라우저 WebSocket API 제약도 함께 고려 필요.

### JWT audience(aud) 검증 부재
`[출처: API_GATEWAY.md §18.3]`
- **현재 확인된 사실**: `ReactiveJwtDecoderConfig`의 검증 체인에 issuer/blacklist/`id` claim 검증만 있고 `aud` 검증은 없다(§7).
- **잠재적 위험**: 확인 불가 — 현재 issuer가 단일(`oauth2-authorization-server` 하나)이므로 실제 위험도는 판단하지 않는다.
- **아직 확인하지 못한 범위**: 토큰 발급 시 `aud` claim이 실제로 포함되는지(oauth2-authorization-server 분석 대상).
- **다음 확인 대상**: oauth2-authorization-server의 토큰 발급 로직에서 `aud` claim 존재 여부.
- **코드 변경 전 사용자 결정 필요**: `aud` validator 추가 여부.

### permitAll 범위와 하위 서비스 인가 일치 여부
`[출처: API_GATEWAY.md §18.4]`
- **현재 확인된 사실**: `/user/**`, `/chat/**`는 `hasRole(USER)`가 걸린 일부 GET 경로를 제외하면 gateway 레벨에서 `permitAll`이다(§8).
- **잠재적 위험**: 확인 불가 — gateway가 열어둔 만큼 하위 서비스가 자체 인가를 보완하는지는 이 모듈 코드로 알 수 없다.
- **아직 확인하지 못한 범위**: user-service, chat-service 자체의 Spring Security/인가 설정.
- **다음 확인 대상**: user-service, chat-service 분석에서 자체 인가 로직 확인.
- **코드 변경 전 사용자 결정 필요**: gateway와 하위 서비스 중 어느 쪽이 인가의 최종 책임을 갖는지 설계 확인.
