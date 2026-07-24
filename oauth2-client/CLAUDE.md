# oauth2-client — 모듈 작업 지침

이 파일은 `oauth2-client/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/OAUTH2_CLIENT.md`](../docs/modules/OAUTH2_CLIENT.md)를 참고한다.

이 모듈은 **보안 핵심**이다. 로그인/로그아웃/토큰 관련 변경은 대부분 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상(OAuth2/Security)이며, 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

외부 OIDC 로그인 클라이언트 + 내부 토큰 브리지(헥사고날 멀티모듈, 실행 모듈 `oauth2-client-bootstrap`). 담당:

1. 외부 OIDC 로그인(Google/Kakao, Spring `oauth2Login`, STATELESS)
2. 로그인 성공 시 provider 토큰 → 내부 AS token-exchange, refresh 쿠키 + SPA `?accessToken=` redirect
3. `/auth/refresh` 재발급, `/auth/logout`(access 블랙리스트 + refresh 쿠키 삭제 + AuthorizedClient 삭제)
4. AuthorizedClient 상태를 로컬 세션이 아니라 AS Redis에 gRPC로 저장(stateless)
5. OIDC 프로필로 user-service find-or-create(user.v1 gRPC)

토큰 발급·서명·저장은 `oauth2-authorization-server`, JWT 검증은 `spring-cloud-api-gateway`의 몫이다. 자체 DB·gRPC 서버 없음(`grpc.server.enabled: false`). `oauth2-client/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **AuthorizedClient 저장/삭제 키 일치**: 저장(`saveAuthorizedClient`)과 삭제(`removeAuthorizedClient`)의 principalName은 **email**로 일치해야 한다(`CustomOidcUser.getName()`이 email 반환). 이 기준을 바꾸면 로그아웃 시 토큰이 안 지워지는 누수가 생긴다. `CustomOAuth2AuthorizedClientService` ↔ `CustomLogoutSuccessHandler`를 함께 본다(→ `../.claude/rules/security.md`).
- **stateless 유지**: 세션은 `SessionCreationPolicy.STATELESS`. AuthorizedClient는 `CustomOAuth2AuthorizedClientRepository`/`Service`를 통해 AS Redis(gRPC)에 저장한다. 로컬 세션/인메모리 저장으로 되돌리지 않는다.
- **token-exchange 브리지 보존**: 로그인 성공 핸들러는 provider access token을 내부 `my-authorization-server`로 token-exchange 한다. grant 타입·internal client registration id(`oauth2.internal-auth-server.client-registration-id`)를 임의로 바꾸지 않는다.
- **쿠키 계약**: refresh 쿠키는 `httpOnly`+`secure`+`SameSite=None`+`path=/`. 속성 하나만 바꿔도 프론트 크로스사이트 인증이 깨진다. 삭제는 동일 속성 + `maxAge=0`(→ `../.claude/rules/security.md`, external-contracts).
- **provider 확장은 extractor로**: 신규 provider는 `OidcProviderProfileExtractor` 구현(+`supports()`)으로 추가하고, `oauth2-client.yml`의 registration/provider를 함께 넣는다. 프로필 claim 해석을 핸들러에 흩뿌리지 않는다.
- **권한 문자열은 계약**: `UserRoleAuthorityMapper`가 만드는 `ROLE_*`(`RoleKey`)는 게이트웨이 인가와 맞물린다. 접두/기본값(`ROLE_USER`)을 임의로 바꾸지 않는다.
- **access token 전달 방식**: 현재 SPA로 `?accessToken=` 쿼리 전달(노출 이슈는 확인 필요 항목). 전달 방식 변경은 프론트와 함께 결정한다.
- gRPC 계약(`user.v1`, `auth.v1`)은 이 모듈이 **소비자**다. 계약 변경은 서버 쪽(user, oauth2-authorization-server) 모듈에서 external-contracts 절차로 진행한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`...adapter-in/.../config/SecurityFilterChainConfig.java`](oauth2-client-adapter-in/src/main/java/org/example/oauth2/client/adapter/in/config/SecurityFilterChainConfig.java) | oauth2Login/logout/stateless 구성 |
| [`...adapter-in/.../config/OAuth2Config.java`](oauth2-client-adapter-in/src/main/java/org/example/oauth2/client/adapter/in/config/OAuth2Config.java) | authorization resolver, token-exchange/refresh 응답 클라이언트 |
| [`...adapter-in/.../web/AuthController.java`](oauth2-client-adapter-in/src/main/java/org/example/oauth2/client/adapter/in/web/AuthController.java) | `POST /auth/refresh` |
| [`...application/.../oidc/CustomOidcUserService.java`](oauth2-client-application/src/main/java/org/example/oauth2/client/oidc/CustomOidcUserService.java) | OIDC 로드 + user find-or-create |
| [`...application/.../oidc/profile/extractor/`](oauth2-client-application/src/main/java/org/example/oauth2/client/oidc/profile/extractor/) | provider별 프로필 추출(google/kakao) |
| [`...application/.../handler/CustomOAuth2LoginSuccessHandler.java`](oauth2-client-application/src/main/java/org/example/oauth2/client/handler/CustomOAuth2LoginSuccessHandler.java) | token-exchange + 쿠키 + SPA redirect |
| [`...application/.../handler/CustomLogoutSuccessHandler.java`](oauth2-client-application/src/main/java/org/example/oauth2/client/handler/CustomLogoutSuccessHandler.java) | 블랙리스트 + 쿠키 삭제 + AuthorizedClient 삭제 |
| [`...application/.../authorizedclient/CustomOAuth2AuthorizedClientService.java`](oauth2-client-application/src/main/java/org/example/oauth2/client/authorizedclient/CustomOAuth2AuthorizedClientService.java) | AuthorizedClient를 AS Redis(gRPC)에 저장/삭제 |
| [`...adapter-out/.../token/adapter/out/grpc/GrpcAuthServerTokenAdapter.java`](oauth2-client-adapter-out/src/main/java/org/example/oauth2/client/token/adapter/out/grpc/GrpcAuthServerTokenAdapter.java) | auth 서버 gRPC 클라이언트 위임 |
| `../git-config-repo/dynamic/oauth2-client.yml` | registration/provider/redirect-uri/api-path/gRPC |

## 검증 명령

- 컴파일: `./gradlew :oauth2-client:oauth2-client-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :oauth2-client:oauth2-client-adapter-in:test` 등
- 서비스 CI: `./gradlew oauth2ClientCi`

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/OAUTH2_CLIENT.md §12`](../docs/modules/OAUTH2_CLIENT.md)와 [`../TODO.md`](../TODO.md).

- 로그인 성공 시 access token을 `?accessToken=` 쿼리로 전달(URL 노출)
- 로그아웃에서 JWT 서명 검증 실패 시 미검증 파싱으로 subject 추출(만료 토큰 로그아웃 허용 의도?)
- `oauth2-client.yml`의 google/kakao `redirect-uri` localhost 하드코딩(운영 값 주입 방식)
