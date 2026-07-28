# OAUTH2_CLIENT — oauth2-client 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - 시큐리티/로그인 구성(`SecurityFilterChainConfig`, `OAuth2Config`) 변경
>   - 로그인·로그아웃 핸들러(`CustomOAuth2LoginSuccessHandler`, `CustomLogoutSuccessHandler`) 변경
>   - OIDC 프로필 추출(`*OidcProviderProfileExtractor`, `CustomOidcUserService`) 변경
>   - AuthorizedClient 저장 방식(`CustomOAuth2AuthorizedClientService`) 또는 토큰 gRPC(`GrpcAuthServerTokenAdapter`) 변경
>   - `git-config-repo/dynamic/oauth2-client.yml`(registration/provider/api-path) 변경

## 1. 문서 목적과 기준 시점

`oauth2-client` 모듈의 구조·로그인 흐름·계약·근거를 사람과 AI가 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다. 짧은 작업 규칙은 [`../../oauth2-client/CLAUDE.md`](../../oauth2-client/CLAUDE.md)에 있다.

## 2. 모듈 역할

외부 OIDC 로그인 **클라이언트** + 내부 토큰 브리지. 사용자를 외부 provider(Google/Kakao)로 로그인시키고, 그 결과를 내부 `oauth2-authorization-server`의 토큰으로 교환(RFC-8693 token-exchange)해 프론트에 전달한다.

- 외부 OIDC 로그인(Spring Security `oauth2Login`, **STATELESS** 세션).
- 로그인 성공 시: provider 프로필(sub/email/nickname) 추출 → user-service gRPC로 **find-or-create** → 내부 AS로 token-exchange → refresh 쿠키 설정 + SPA로 `?accessToken=` redirect.
- 토큰 재발급(`/auth/refresh`), 로그아웃(`/auth/logout`: access 블랙리스트 + refresh 쿠키 삭제 + AuthorizedClient 삭제).
- **AuthorizedClient 상태를 로컬 세션이 아니라 AS의 Redis에 gRPC로 저장**(stateless).

토큰 발급·서명·저장의 권위 원천은 `oauth2-authorization-server`, JWT 검증은 `spring-cloud-api-gateway`다. 이 모듈은 **외부 로그인 ↔ 내부 토큰의 브리지**이며 자체 DB가 없다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:oauth2-client:*`(헥사고날 멀티모듈). 실행 모듈 `:oauth2-client-bootstrap`.
- 실행 클래스: `org.example.oauth2.client.Main`. app name: `oauth2-client`. 포트 `8900`.
- **gRPC 서버 없음**(`grpc.server.enabled: false`). gRPC 클라이언트로만 동작: `user-service`(`user.v1`), `oauth2-authorization-server`(`auth.v1`).
- 저장소 없음(상태는 AS Redis에 위임). Config Server 연동: `spring.cloud.config.name: oauth2-client,eureka-client,jwt,frontend,monitoring`.
- 핵심 라이브러리: `spring-boot-starter-oauth2-client`, `spring-boot-starter-web`, `common-core`, `user-contract`.

## 4. 모듈 구조 (헥사고날)

`-domain`/`-client`/`-contract` 모듈 없음(순수 인증 클라이언트). 서브도메인: `oidc`, `authorizedclient`, `token`, `user`, `handler`.

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `-application` | application | OIDC 프로필 추출, 로그인/로그아웃 핸들러, AuthorizedClient(Redis/gRPC), token/user 서비스·포트 | `common-core`, `user-contract`, `spring-boot-starter-oauth2-client` |
| `-adapter-in` | adapter-in | 시큐리티/OAuth2 설정, `AuthController`(`/auth/refresh`) | `common-web`, application |
| `-adapter-out` | adapter-out | `GrpcAuthServerTokenAdapter`(→auth server client), `GrpcUserAdapter`(→user client) | `oauth2-authorization-server-client`, `user-client` |
| `-bootstrap` | 실행 | `Main`, `application.yml` | 위 3개 + config/eureka/bus |

의존 방향: adapter-in/out → application. adapter-out이 두 gRPC 클라이언트(`user-client`, `oauth2-authorization-server-client`)를 소비한다.

## 5. 주요 클래스와 책임

| 클래스 | 계층 | 책임 |
|---|---|---|
| `SecurityFilterChainConfig` | adapter-in | `oauth2Login`(stateless), 커스텀 resolver/service/handler 연결, `/auth/logout` |
| `OAuth2Config` | adapter-in | authorization request resolver(google `access_type=offline`), token-exchange/refresh 응답 클라이언트 |
| `CustomOidcUserService` | application | OIDC 로드 → 프로필 추출 → user find-or-create → `CustomOidcUser` 생성 |
| `OidcProviderProfileResolver` + `*Extractor` | application | provider별 sub/email/nickname 추출(google/kakao) |
| `CustomOAuth2LoginSuccessHandler` | application | provider 토큰 → 내부 AS token-exchange → refresh 쿠키 + SPA redirect(`?accessToken=`) |
| `CustomLogoutSuccessHandler` | application | access 블랙리스트 등록 + refresh 쿠키 삭제 + AuthorizedClient 삭제 |
| `CustomOAuth2LoginFailureHandler` | application | 실패 시 프론트 실패 URL로 redirect |
| `CustomOAuth2AuthorizedClientService` | application | AuthorizedClient 저장/조회/삭제를 AS(Redis) gRPC로 위임 |
| `AuthController` | adapter-in | `POST /auth/refresh` 재발급 |
| `RefreshTokenService` | application | refresh 재발급(refresh grant), refresh 쿠키 생성/삭제 |
| `GrpcAuthServerTokenAdapter` | adapter-out | `AuthServerTokenPort` 구현 → `Oauth2AuthorizationServerClient` |
| `GrpcUserAdapter` | adapter-out | `UserPort` 구현 → `UserClient`(find/signUp) |
| `UserRoleAuthorityMapper` | application | 역할 문자열 → `ROLE_*` GrantedAuthority(기본 `ROLE_USER`) |

## 6. 주요 흐름

### 6.1 로그인 (외부 OIDC → 내부 토큰)
```
GET /oauth2/authorization/{google|kakao}  → 외부 provider 인가
 → 콜백 /login/oauth2/code/*
 → CustomOidcUserService.loadUser:
     provider 프로필 추출(sub/email/nickname)
     userQueryService.findByEmail → 없으면 userCommandService.signUpOauth2 (user.v1 gRPC)
     roles → GrantedAuthority, CustomOidcUser(getName()=email)
 → CustomOAuth2LoginSuccessHandler:
     provider access token → 내부 AS token-exchange(my-authorization-server)
     refresh 쿠키 설정 + sendRedirect(frontend successRedirectUri?accessToken=...)
```
- AuthorizedClient는 로그인 시 `CustomOAuth2AuthorizedClientService.saveAuthorizedClient` → AS Redis(gRPC)에 저장. principalName = **email**.

### 6.2 재발급 (`POST /auth/refresh`)
```
refresh 쿠키 추출 → RefreshTokenService.reissue:
   internal client(my-authorization-server)로 refresh grant → AS
 → 새 access(Authorization 헤더) + 새 refresh 쿠키, 201 Created
```

### 6.3 로그아웃 (`POST /auth/logout`)
```
Authorization Bearer access → subject(email) 해석
 → BlacklistTokenService.register(access)        (AS gRPC, 이후 게이트웨이가 차단)
 → refresh 쿠키 삭제(maxAge 0)
 → authorizedClientService.removeAuthorizedClient(email)  (AS Redis 삭제)
```
- AuthorizedClient **저장 기준(email) = 삭제 기준(email)**으로 일치(`CustomOidcUser.getName()`이 email 반환). 저장/삭제 키 불일치 이슈 없음(확인됨).

## 7. 인증/쿠키/식별 계약

- **refresh 토큰 쿠키**(`RefreshTokenService`): name=`AuthTokenKey.REFRESH_TOKEN_COOKIE`, `httpOnly`, `secure`, `SameSite=None`, `path=/`, `maxAge=refreshTokenExpirationMs`. 삭제는 동일 속성 + `maxAge=0`. (security.md의 쿠키 계약과 일치)
- **access 토큰 전달**: 로그인 성공 시 SPA로 `?accessToken=` 쿼리(§6.1). URL 노출 이슈는 TODO 1.5.
- **principalName = email**: AuthorizedClient 저장/조회/삭제 키. 로그인·로그아웃 양쪽 일치.
- **provider 지원**: google, kakao만 확인(`supports()`), + 내부 `my-authorization-server`(token-exchange grant).
- 내부 client registration id는 `oauth2.internal-auth-server.client-registration-id`(=`my-authorization-server`).

## 8. 설정 (Config Server: `oauth2-client.yml`)

- `server.port: 8900`, `grpc.server.enabled: false`.
- gRPC 클라이언트: `user-client`(→user-service), `oauth2-authorization-server-client`(→oauth2-authorization-server), 둘 다 plaintext/16MB.
- `spring.security.oauth2.client.registration`: `google`, `kakao`(client_secret_post), `my-authorization-server`(token-exchange grant, client_secret_basic). provider issuer/token-uri 포함.
- `api-path.auth`(`/auth/**`, logout, refresh), `api-path.oauth2`(authorization/callback base).
- 자격은 `${google.*}`, `${kakao.*}`, `${my.*}`(Vault/Config). 공개·내부·provider URI는 공통
  `git-config-repo/application.yml`의 `${uri.*}`를 사용하고, 로그인 성공·실패 프론트 redirect는
  `frontend.yml`(`FrontendProperties`)이 공통 frontend origin을 참조해 구성한다.

## 9. 테스트 현황

- adapter-in: `AuthControllerTest`, E2E `AuthLogoutE2ETest`, `AuthRefreshE2ETest`, `OAuth2AuthorizationRedirectE2ETest`
- application: `CustomLogoutSuccessHandlerTest`, `CustomOAuth2LoginSuccessHandlerTest`, `CustomOidcUserServiceTest`, `CustomOAuth2AuthorizedClientTokenServiceTest`, `RefreshTokenServiceTest`, `Google/KakaoOidcProviderProfileExtractorTest`
- 다수의 `Test*DependencyConfig`로 외부 의존(gRPC/security) 대체.

## 10. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :oauth2-client:oauth2-client-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :oauth2-client:oauth2-client-adapter-in:test` 등
- 서비스 CI: `./gradlew oauth2ClientCi`(루트 `build.gradle`).
- 전체 build/test, `bootRun`, 배포는 명시적 요청 없이 수행하지 않는다.

## 11. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `SecurityFilterChainConfig.java` | 로그인/로그아웃/세션 정책. 인증 진입 전반 |
| `CustomOAuth2LoginSuccessHandler.java` | token-exchange·쿠키·SPA redirect(access token 전달) |
| `CustomLogoutSuccessHandler.java` | 블랙리스트·쿠키 삭제·AuthorizedClient 삭제(로그아웃 무결성) |
| `CustomOAuth2AuthorizedClientService.java` | 저장/삭제 키(email) 일치 — 불일치 시 로그아웃 누락 |
| `*OidcProviderProfileExtractor.java` | provider별 claim 해석. 신규 provider 추가 지점 |
| `git-config-repo/application.yml` | 공개·내부·discovery·provider·datastore URI 정본 |
| `git-config-repo/dynamic/oauth2-client.yml` | registration/provider/redirect-uri/api-path (`${uri.*}` 소비) |

## 12. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. oauth2-client 관련 항목:

- **TODO 1.5** — access token URL 노출 (로그인 성공 `?accessToken=` 전달)
- **TODO 1.6** — 로그아웃 시 JWT 미검증 파싱 (`CustomLogoutSuccessHandler.resolveSubject`)
- **TODO 1.7** — redirect-uri localhost 하드코딩 (`oauth2-client.yml`)

## 13. 관련 문서와 rules

- 루트: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 연관 서비스: 토큰 발급/저장 [`OAUTH2_AUTHORIZATION_SERVER.md`](OAUTH2_AUTHORIZATION_SERVER.md), 계정 [`USER.md`](USER.md), 검증/blacklist [`API_GATEWAY.md`](API_GATEWAY.md)
- 모듈 작업 규칙: [`../../oauth2-client/CLAUDE.md`](../../oauth2-client/CLAUDE.md)
- rules: `../../.claude/rules/{security,external-contracts,architecture,testing}.md`
- 미해결 관찰: [`../../TODO.md`](../../TODO.md)
