# 보안 규칙

이 파일은 인증·인가·Secret·OAuth2/JWT 관련 작업 시 읽는다. Secret 출력·커밋 금지 등 무조건 적용 규칙은 `git-safety.md`(항상 로드)에 있다. 보안 변경은 수정 전 Plan Mode 분석 대상이다.

## OAuth2 / JWT 핵심 규칙
- Spring Security **principal name과 도메인 userId를 같은 값으로 가정하지 않는다**. `OidcUser#getName()`은 principal name이다.
- provider별 OIDC claim 해석은 extractor/resolver로 분리한다(`oauth2-client/.../oidc/profile/extractor/`).
- `OAuth2AuthorizedClient` 저장 기준과 logout 삭제 기준을 일치시킨다(`CustomOAuth2AuthorizedClientService` ↔ `CustomLogoutSuccessHandler`).
- Token claim 변경은 API Gateway·WebSocket Gateway·하위 서비스에 영향(→ `external-contracts.md`).
- Authorization Server registered client id/secret은 config/Vault 양쪽이 일치해야 한다.

## 구조 (현재 확인된 사실)
- **Authorization Server**: `TOKEN_EXCHANGE` + `REFRESH_TOKEN` 그랜트, InMemory 단일 client, Vault Transit RS256 서명, rotating refresh(`reuseRefreshTokens(false)`). 근거 `oauth2-authorization-server-adapter-in/.../config/`.
- **OAuth2 Client**: 외부 OIDC(Google/Kakao) → RFC-8693 token-exchange로 내부 토큰 발급, AuthorizedClient는 Redis(auth 서버 gRPC 경유).
- **Resource Server(gateway)**: JWKS(Config Server) + issuer + blacklist + `id` claim 검증. `IdentityPropagationGlobalFilter`가 `id` claim → `X-User-Id` 전파.
- **토큰 저장**: Redis(`{auth}:accesstoken`, `{auth}:refreshtoken`, `{auth}:blacklist`).

## Cookie (외부 동작 계약)
Refresh token 쿠키 속성을 계약으로 취급한다: `httpOnly`, `secure`, `SameSite=None`, `path=/`, domain 미설정(host-only). 근거 `oauth2-client-application/.../token/application/service/RefreshTokenService.java`. 변경은 프론트 E2E에 영향.

## Secret / Vault
- 설정은 Spring Cloud Config(git + Vault AppRole, KV v2 + Transit)에서 로드. 실제 secret은 `git-config-repo`에 두지 않고 `${...}` 플레이스홀더만 사용한다.
- 단순 password hashing에는 `spring-security-crypto` 같은 좁은 의존성을 우선 검토하고, resource server 설정이 불필요하게 켜지지 않도록 의존성을 조심한다.

## 참고 (확정 사실)
- Access TTL 2h(`7200000`), Refresh TTL 7일(`604800000`) — `git-config-repo/dynamic/jwt.yml`.

## 확인 필요 (사실 그대로 기록, 설계/결함 판정 금지 · 이번 작업에서 코드 미수정)
- 게이트웨이에서 `aud`/`jti` 검증 미확인.
- 로그인 성공 redirect에서 access token을 `?accessToken=` 쿼리로 전달.
- client secret을 `{noop}`(평문)로 저장.
위 항목은 운영 위험 후보로 문서화하되, 사용자 확인 전까지 코드/설정을 변경하지 않는다.
