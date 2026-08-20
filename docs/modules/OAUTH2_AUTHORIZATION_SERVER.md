# OAUTH2_AUTHORIZATION_SERVER — oauth2-authorization-server 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - RegisteredClient/Grant 설정(`AuthorizationServerConfig`) 변경
>   - 토큰 생성·서명(`TokenConfig`, `Rs256JwtEncoder`) 변경
>   - 토큰 저장 구조(Redis 어댑터·Lua 스크립트, `RedisKey` `{auth}:*`) 변경
>   - gRPC 계약(`protobuf/src/main/proto/auth/v1/auth-service.proto`) 변경
>   - refresh 회전 정책(`RotatingRefreshTokenPolicy`) 또는 `CustomOAuth2AuthorizationService` 변경
>   - `git-config-repo/dynamic/{oauth2-authorization-server,jwt}.yml` 변경

## 1. 문서 목적과 기준 시점

`oauth2-authorization-server` 모듈의 구조·토큰 흐름·계약·근거를 사람과 AI가 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다. 짧은 작업 규칙은 [`../../oauth2-authorization-server/CLAUDE.md`](../../oauth2-authorization-server/CLAUDE.md)에 있다.

## 2. 모듈 역할

내부 **OAuth2 Authorization Server**(Spring Authorization Server 기반). 시스템 내부에서 쓰는 JWT access/refresh 토큰을 발급·회전·저장·무효화한다.

- 지원 Grant: **`TOKEN_EXCHANGE`**(RFC-8693, oauth2-client가 외부 OIDC 로그인 결과를 내부 토큰으로 교환) + **`REFRESH_TOKEN`**(재발급).
- 등록 클라이언트: InMemory **단일** RegisteredClient(`client_secret_basic`). secret은 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`로 앱 기동 시 해시(`{bcrypt}...`)해 저장 — 원본 평문은 Vault(`${my.client-secret}`)에만 있다.
- 서명: **RS256, 개인키는 Vault**에 있고 Config Server `/sign`(Vault Transit) 위임 서명(`Rs256JwtEncoder`).
- 저장: **Redis Cluster**(`{auth}` 해시태그), Lua로 원자적 저장/삭제.
- 대외 인터페이스: **gRPC `auth.v1`**(HTTP는 표준 OAuth2 토큰 엔드포인트만). 게이트웨이·oauth2-client가 gRPC 소비.

로그인 오케스트레이션(외부 OIDC, 쿠키, 리다이렉트)은 `oauth2-client`, JWT 검증은 `spring-cloud-api-gateway`의 몫이다. 이 모듈은 **토큰 발급·저장의 권위 원천**이다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:oauth2-authorization-server:*`(헥사고날 멀티모듈). 실행 모듈 `:oauth2-authorization-server-bootstrap`.
- 실행 클래스: `org.example.oauth2.authorizationserver.Main`.
- app name: `oauth2-authorization-server`. 포트: HTTP `9000`(토큰 엔드포인트), gRPC `19000`.
- 저장소: Redis Cluster. **DB(JPA) 미사용** — 사용자 정보는 gRPC로 user-service에서 조회.
- 서명 대행: Config Server(`sign-uri`, `jwks-uri`)를 통한 Vault Transit.
- Config Server 연동: `application.yml`의 `spring.cloud.config.name: oauth2-authorization-server,eureka-client,jwt,redis,monitoring`.
- 핵심 라이브러리: `spring-security-oauth2-authorization-server`, `spring-boot-starter-web`, `common-redis`, `caffeine`, `user:user-contract`(application), `user:user-client`(adapter-out).

## 4. 모듈 구조 (헥사고날)

**도메인 모듈(`-domain`)이 없다.** 토큰이 Spring Security의 값 타입이라 별도 도메인 엔티티를 두지 않고, application이 Spring Authorization Server 타입 위에서 오케스트레이션한다. 서브도메인은 `authorization`, `token`, `user`.

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `-application` | application | `CustomOAuth2AuthorizationService`, `CustomAuthenticationSuccessHandler`, refresh 정책, token/user 포트, `UserQueryService` | `common-core`, `user-contract`, spring-authorization-server |
| `-adapter-in` | adapter-in | 서버 설정 3종(`AuthorizationServerConfig`/`SecurityFilterChainConfig`/`TokenConfig`), gRPC 서비스 4종 | `common-web`, `common-grpc-server`, `protobuf`, application |
| `-adapter-out` | adapter-out | Redis 토큰 어댑터 4종(+Lua), `Rs256JwtEncoder`(Vault), `GrpcUserQueryAdapter`, infra config | `common-redis`, `user-client`, caffeine |
| `-bootstrap` | 실행 | `Main`, `application.yml` | 위 3개 + config/eureka/bus |
| `-client` | 클라이언트 | `Oauth2AuthorizationServerClient` + `GrpcOauth2AuthorizationServerClient`(blocking API와 Reactor 비의존 `CompletableFuture` blacklist API 제공) | `protobuf`, grpc-client |

의존 방향: adapter-in/out → application. `-client`는 **소비자(gateway, oauth2-client)가 의존**하는 산출물.

## 5. 주요 클래스와 책임

| 클래스 | 계층 | 책임 |
|---|---|---|
| `AuthorizationServerConfig` | adapter-in | issuer, RegisteredClient(grant/TTL/`reuseRefreshTokens(false)`/`SELF_CONTAINED`) |
| `SecurityFilterChainConfig` | adapter-in | `OAuth2AuthorizationServerConfigurer` 조립, 토큰 엔드포인트에 성공 핸들러 연결, client 인증 실패 401 |
| `TokenConfig` | adapter-in | `OAuth2TokenGenerator`(RS256 JwtGenerator+델리게이트), access 토큰 커스터마이저(`roles`,`id` claim 추가) |
| `CustomOAuth2AuthorizationService` | application | `OAuth2AuthorizationService` 구현. refresh 캐시(save), Redis에서 토큰→Authorization 복원(findByToken) |
| `CustomAuthenticationSuccessHandler` | application | 토큰 발급 성공 시 refresh 회전·저장·토큰 응답 작성 |
| `RotatingRefreshTokenPolicy` | application | 매 발급 시 신규 refresh 생성(항상 저장) |
| `Rs256JwtEncoder` | adapter-out | header/payload base64url 구성 → Config Server `/sign`(Vault Transit) 위임 서명 |
| `Redis*TokenAdapter`(4) | adapter-out | access/refresh/blacklist/authorizedClient의 Redis 저장·조회 |
| `Grpc*Service`(4) | adapter-in | `auth.v1` gRPC 서버(§9) |
| `GrpcUserQueryAdapter` | adapter-out | `UserQueryPort` 구현 → `user-client`로 findByEmail |

## 6. 토큰 발급 흐름

HTTP 토큰 엔드포인트(`/oauth2/token`, 포트 9000). Spring Authorization Server 표준 파이프라인 + 커스텀 훅.

### TOKEN_EXCHANGE (외부 로그인 → 내부 토큰)
```
oauth2-client → POST /oauth2/token (grant_type=token-exchange, client_secret_basic)
 → 표준 인증/토큰 생성 (access = JwtGenerator→Rs256JwtEncoder 서명)
 → access 커스터마이저: principal(email)로 user-service gRPC findByEmail → roles, id claim 추가
 → CustomAuthenticationSuccessHandler:
      RotatingRefreshTokenPolicy.resolve → 신규 refresh 생성
      authorizationService.save → refresh 토큰 Redis 캐시
      access+refresh를 OAuth2AccessTokenResponse로 응답
```

### REFRESH_TOKEN (재발급)
```
oauth2-client → POST /oauth2/token (grant_type=refresh_token)
 → CustomOAuth2AuthorizationService.findByToken(REFRESH_TOKEN): Redis에서 email 확인 후 Authorization 복원
 → reuseRefreshTokens(false) + RotatingRefreshTokenPolicy → access·refresh 모두 신규 발급(회전)
```

- refresh 회전이라 재발급마다 refresh도 새로 나온다(탈취 재사용 방지).
- `CustomOAuth2AuthorizationService`는 DB 없이 **Redis를 백엔드로** OAuth2Authorization을 저장/복원한다: access는 `{auth}:claims:*`, refresh는 `{auth}:refreshtoken:*`.

## 7. JWT 서명 (Vault Transit)

`Rs256JwtEncoder`(커스텀 `JwtEncoder`, Nimbus 아님):
1. 헤더(`alg=RS256`, `typ`, `kid=keyName:keyVersion`) + 클레임을 base64url 인코딩. `iat/exp/nbf`는 epoch second로 정규화.
2. `{keyName, keyVersion, headerB64u, payloadB64u}`를 Config Server `sign-uri`(`/sign`)로 POST → Vault Transit이 서명(`sigB64u`) 반환.
3. `header.payload.signature` 조립.

- 개인키가 애플리케이션에 없다(Vault 보관). `kid`는 `my-authorization-server-jwt:1`(jwt.yml). 검증측(게이트웨이)은 Config Server JWKS(`jwks-uri`)로 공개키 조회.
- `jwtRestTemplate` 타임아웃: connect 2000ms / read 3000ms(`HttpClientConfig`, jwt.yml).

## 8. 토큰 저장 (Redis `{auth}`)

키는 `common-core/RedisKey` enum, Cluster 해시태그 `{auth}`로 슬롯 고정.

| 키 | 패턴 | 용도 |
|---|---|---|
| `ACCESS_TOKEN` | `{auth}:accesstoken:%s:%s` (clientRegId, email) | access 토큰 값 |
| `ACCESS_CLAIMS` | `{auth}:claims:%s` (accessToken) | access 클레임 해시(HSET) |
| `REFRESH_TOKEN` | `{auth}:refreshtoken:%s:%s` (clientRegId, email 또는 token) | refresh 정·역방향 조회 |
| `REFRESH_EMAIL_PREFIX` | `{auth}:refreshtoken:%s:` (clientRegId) | prefix |
| `TOKENS_SET` | `{auth}:tokens:%s` (email) | 삭제용 키 인덱스 Set |
| `BLACKLIST_TOKEN_SET` | `{auth}:blacklist` | 블랙리스트 access 토큰 Set |

- **원자성**: 저장/삭제는 Lua 스크립트(`storeTokens.lua`, `storeRefreshToken.lua`, `deleteTokens.lua`)로 실행. `storeTokens.lua`는 Java pre-check와 별개로 스크립트 안에서도 `EXISTS`로 중복 저장을 막고, 클레임 HSET·access SETEX·refresh 정/역 SETEX·인덱스 SADD를 한 번에 처리한다.
- TTL은 jwt.yml(`accessTokenExpirationMs`/`refreshTokenExpirationMs`)에서 온다.
- 블랙리스트 Set은 `RedisSetRegistry`가 Caffeine 캐시(최대 1000, 3일)로 `RedisSet` 핸들을 재사용.

## 9. gRPC 계약 (`auth.v1`)

proto: `protobuf/src/main/proto/auth/v1/auth-service.proto`. 서버: adapter-in의 `Grpc*Service` 4종(포트 19000). 클라이언트: `-client`의 `GrpcOauth2AuthorizationServerClient`(deadline 3500ms). 모든 메서드는 future stub 기반 `CompletableFuture<GrpcResponse>`를 제공하며, future 취소는 진행 중인 gRPC 호출에 전파된다. 공용 client는 Reactor에 의존하지 않고 Gateway가 blacklist 응답을 `Mono<Boolean>`으로 변환한다. 동기 OAuth2 framework 경계는 `GrpcAuthServerTokenAdapter`에서 완료를 기다린 뒤 scalar application 값으로 매핑한다.

| 서비스 | RPC | 용도 | 주 소비자 |
|---|---|---|---|
| `AccessTokenService` | `findValue` | client+username으로 access 토큰 값 조회 | oauth2-client |
| `RefreshTokenService` | `findValue` | refresh 토큰 값 조회 | oauth2-client |
| `BlacklistTokenService` | `register`, `exists` | access 토큰 블랙리스트 등록/조회 | register→oauth2-client(로그아웃), exists→**gateway**(요청마다 검증) |
| `AuthorizedClientService` | `save`, `remove` | AuthorizedClient(토큰 묶음) 저장/삭제 | oauth2-client |

- **소비자 확인됨**: `spring-cloud-api-gateway`(`GrpcBlacklistTokenClientAdapter` → `exists`), `oauth2-client`(`GrpcAuthServerTokenAdapter` → find/save/remove/register).
- 계약 변경은 external-contracts 절차(field number 재사용 금지, 소비자 재빌드). proto 재생성 `./gradlew :protobuf:build`.

## 10. 설정

`git-config-repo/dynamic/oauth2-authorization-server.yml`:
- `server.port: 9000`(TODO 주석: TLS), gRPC `server.port: 19000`.
- `grpc.client.user-client.address`는 공통 `application.yml`의 `uri.discovery.user-service`를 참조한다(plaintext, 16MB).
- `oauth2.registered-client.{id,secret,registration-id}` ← `${my.*}`(Vault/Config).
- `mysql.*` 블록이 있으나 `config.name`에 mysql 미포함 + DB 사용 코드 없음(§14).

`git-config-repo/dynamic/jwt.yml`:
- `key-name: my-authorization-server-jwt`, `key-version: 1`.
- `issuer-uri`는 공통 `application.yml`의 `uri.internal.oauth2-authorization-server`를 참조한다.
- `jwks-uri`/`sign-uri` → Config Server.
- `access-token-expiration-ms` = `7200000`(2시간), `refresh-token-expiration-ms` = `604800000`(7일).

## 11. 테스트 현황

- adapter-in: `OAuth2TokenEndpointIntegrationTest`(토큰 엔드포인트 통합)
- application: `CustomOAuth2AuthorizationServiceTest`, `CustomAuthenticationSuccessHandlerTest`, `RotatingRefreshTokenPolicyTest`
- adapter-out: `Rs256JwtEncoderTest`, `RedisAuthorizedClientAdapter{,Integration}Test`, `RedisRefreshTokenAdapter{,Integration}Test`
- 공통 테스트 설정: `TestRedisConfig`, `TestPropertiesConfig`, `TestObjectMapperConfig`
- (세부는 파일 직접 확인. Redis 통합은 `common-test` Testcontainers 계열.)

## 12. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :oauth2-authorization-server:oauth2-authorization-server-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :oauth2-authorization-server:oauth2-authorization-server-adapter-out:test` 등
- 서비스 CI: `./gradlew oauth2AuthorizationServerCi`(루트 `build.gradle`).
- 전체 build/test, `bootRun`, 배포는 명시적 요청 없이 수행하지 않는다.

## 13. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `protobuf/.../auth/v1/auth-service.proto` | gRPC 외부 계약. gateway·oauth2-client 재빌드 |
| `AuthorizationServerConfig.java` | grant/TTL/refresh 재사용/토큰 포맷 — 인증 전반 |
| `TokenConfig.java` | 토큰 생성·claim(`roles`,`id`) — 게이트웨이 인가와 연동 |
| `Rs256JwtEncoder.java` | 서명 방식(Vault). 깨지면 전 서비스 토큰 검증 실패 |
| `RotatingRefreshTokenPolicy.java` / `CustomOAuth2AuthorizationService.java` | refresh 회전·복원 로직 |
| Lua 스크립트 3종 / `RedisKey`(`{auth}:*`) | 토큰 저장 원자성·키 계약(hash tag) |
| `git-config-repo/dynamic/jwt.yml` | issuer/키/TTL — 발급·검증 양쪽 |

## 14. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. oauth2-authorization-server 관련 항목:

- **TODO 1.1** — `aud` claim 발급/검증 여부 (`TokenConfig`)
- **TODO 1.4** — 토큰 엔드포인트 TLS 미적용 (`server.port: 9000` `# TODO: tsl`)
- **TODO 4.2** — 미사용 mysql 설정 (`oauth2-authorization-server.yml`)

## 15. 관련 문서와 rules

- 루트: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 검증측/소비자: [`API_GATEWAY.md`](API_GATEWAY.md)(blacklist·JWKS·issuer), user 계정 조회 [`USER.md`](USER.md)
- 모듈 작업 규칙: [`../../oauth2-authorization-server/CLAUDE.md`](../../oauth2-authorization-server/CLAUDE.md)
- rules: `../../.claude/rules/{security,external-contracts,architecture,testing}.md`
