# oauth2-authorization-server — 모듈 작업 지침

이 파일은 `oauth2-authorization-server/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/OAUTH2_AUTHORIZATION_SERVER.md`](../docs/modules/OAUTH2_AUTHORIZATION_SERVER.md)를 참고한다.

이 모듈은 **보안 핵심**이다. 변경은 대부분 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상(OAuth2/Security)이며, 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

내부 OAuth2 Authorization Server(Spring Authorization Server 기반). 담당:

1. 내부 JWT access/refresh 토큰 **발급**(Grant: `TOKEN_EXCHANGE` + `REFRESH_TOKEN`, 단일 InMemory client)
2. RS256 **서명**(개인키는 Vault, Config Server `/sign` 위임 — `Rs256JwtEncoder`)
3. 토큰 **저장/복원**(Redis `{auth}`, Lua 원자적 저장/삭제, `CustomOAuth2AuthorizationService`)
4. refresh **회전**(`reuseRefreshTokens(false)` + `RotatingRefreshTokenPolicy`)
5. gRPC `auth.v1` 제공(access/refresh 조회, blacklist 등록/조회, authorizedClient 저장/삭제)

외부 OIDC 로그인·쿠키·리다이렉트는 `oauth2-client`, JWT 검증은 `spring-cloud-api-gateway`의 몫이다. DB(JPA)는 쓰지 않고 사용자 정보는 user-service gRPC로 조회한다. `oauth2-authorization-server/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **Grant/토큰 설정은 인증 전반의 계약**: `AuthorizationServerConfig`의 grant 타입, TTL, `reuseRefreshTokens(false)`, `SELF_CONTAINED` 토큰 포맷을 임의로 바꾸지 않는다. TTL·issuer·키는 `git-config-repo/dynamic/jwt.yml`과 한 쌍으로 검토한다.
- **access claim은 게이트웨이 인가와 연동**: `TokenConfig`의 커스터마이저가 넣는 `roles`·`id`(`JwtClaimKey`) claim은 게이트웨이 `hasRole`/`X-User-Id` 전파의 근거다. 이름·의미를 바꾸면 게이트웨이·하위 서비스가 깨진다(→ `../.claude/rules/external-contracts.md`).
- **서명은 Vault 위임 방식 유지**: `Rs256JwtEncoder`는 개인키를 들고 있지 않고 Config Server `/sign`으로 서명한다. 검증측은 JWKS로 공개키를 받는다. `kid`(`keyName:keyVersion`)·서명 방식을 바꾸면 발급·검증 전체에 영향. 개인키를 코드/설정에 넣지 않는다(→ `../.claude/rules/security.md`).
- **Redis 키·Lua는 저장 계약**: `RedisKey`의 `{auth}:*` 패턴과 해시태그 `{auth}`, Lua 3종(`storeTokens`/`storeRefreshToken`/`deleteTokens`)의 키 순서·원자성을 함부로 바꾸지 않는다. 저장 로직 변경 시 Lua와 어댑터의 KEYS/ARGV 인자 순서를 함께 맞춘다.
- **refresh 회전 불변식**: `RotatingRefreshTokenPolicy`는 매 발급마다 새 refresh를 만들고 저장한다. 재사용 허용으로 바꾸는 것은 보안 결정이므로 승인 없이 하지 않는다.
- **gRPC 계약(`auth.v1`)**: `protobuf/.../auth/v1/auth-service.proto` 변경은 소비자(gateway, oauth2-client) 재빌드와 field number 규칙을 지킨다. proto 재생성 `./gradlew :protobuf:build`.
- **client secret / registered client**: id/secret/registration-id는 `oauth2.registered-client.*`(Vault `${my.*}`)에서 오며 Config/Vault 양쪽이 일치해야 한다. secret은 `PasswordEncoderConfig`가 등록하는 `PasswordEncoder` bean(delegating, 기본 bcrypt)으로 앱 기동 시 해시해 저장한다 — `AuthorizationServerConfig`가 이 bean을 주입받는다.
- 도메인 모듈이 없다. 상태는 Spring Security 값 타입 + Redis에 있으니, "도메인 엔티티"를 새로 만들기 전에 기존 구조(application이 오케스트레이션)를 우선한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`...adapter-in/.../config/AuthorizationServerConfig.java`](oauth2-authorization-server-adapter-in/src/main/java/org/example/oauth2/authorizationserver/adapter/in/config/AuthorizationServerConfig.java) | issuer, RegisteredClient(grant/TTL/refresh 정책, secret 해시) |
| [`...adapter-in/.../config/PasswordEncoderConfig.java`](oauth2-authorization-server-adapter-in/src/main/java/org/example/oauth2/authorizationserver/adapter/in/config/PasswordEncoderConfig.java) | client secret 해시용 `PasswordEncoder` bean |
| [`...adapter-in/.../config/TokenConfig.java`](oauth2-authorization-server-adapter-in/src/main/java/org/example/oauth2/authorizationserver/adapter/in/config/TokenConfig.java) | TokenGenerator + access claim(`roles`,`id`) 커스터마이저 |
| [`...adapter-in/.../config/SecurityFilterChainConfig.java`](oauth2-authorization-server-adapter-in/src/main/java/org/example/oauth2/authorizationserver/adapter/in/config/SecurityFilterChainConfig.java) | 인가 서버 필터체인, 성공 핸들러 연결 |
| [`...application/.../authorization/application/CustomOAuth2AuthorizationService.java`](oauth2-authorization-server-application/src/main/java/org/example/oauth2/authorizationserver/authorization/application/CustomOAuth2AuthorizationService.java) | Redis 기반 Authorization 저장/복원 |
| [`...application/.../authorization/application/CustomAuthenticationSuccessHandler.java`](oauth2-authorization-server-application/src/main/java/org/example/oauth2/authorizationserver/authorization/application/CustomAuthenticationSuccessHandler.java) | 발급 성공 시 refresh 회전·저장·응답 |
| [`...application/.../token/application/policy/RotatingRefreshTokenPolicy.java`](oauth2-authorization-server-application/src/main/java/org/example/oauth2/authorizationserver/token/application/policy/RotatingRefreshTokenPolicy.java) | refresh 회전 정책 |
| [`...adapter-out/.../token/adapter/out/vault/Rs256JwtEncoder.java`](oauth2-authorization-server-adapter-out/src/main/java/org/example/oauth2/authorizationserver/token/adapter/out/vault/Rs256JwtEncoder.java) | Vault Transit 위임 RS256 서명 |
| [`...adapter-out/.../token/adapter/out/redis/`](oauth2-authorization-server-adapter-out/src/main/java/org/example/oauth2/authorizationserver/token/adapter/out/redis/) | access/refresh/blacklist/authorizedClient Redis 어댑터 |
| [`...adapter-out/.../resources/META-INF/scripts/`](oauth2-authorization-server-adapter-out/src/main/resources/META-INF/scripts/) | `storeTokens`/`storeRefreshToken`/`deleteTokens` Lua |
| [`...client/.../GrpcOauth2AuthorizationServerClient.java`](oauth2-authorization-server-client/src/main/java/org/example/oauth2/authorizationserver/client/GrpcOauth2AuthorizationServerClient.java) | 소비자용 gRPC 클라이언트 |
| `../protobuf/src/main/proto/auth/v1/auth-service.proto` | gRPC `auth.v1` 계약 |
| `../git-config-repo/dynamic/jwt.yml` | issuer/키/TTL/JWKS/sign URI |
| `../git-config-repo/dynamic/oauth2-authorization-server.yml` | 포트·registered-client·gRPC 설정 |

## 검증 명령

- 컴파일: `./gradlew :oauth2-authorization-server:oauth2-authorization-server-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :oauth2-authorization-server:oauth2-authorization-server-adapter-out:test` 등
- 서비스 CI: `./gradlew oauth2AuthorizationServerCi`

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/OAUTH2_AUTHORIZATION_SERVER.md §14`](../docs/modules/OAUTH2_AUTHORIZATION_SERVER.md)와 [`../TODO.md`](../TODO.md).

- 발급 토큰의 `aud` claim 유무·검증 필요성(커스터마이저는 `roles`/`id`만 추가)
- 미사용으로 보이는 `mysql.*` 설정
- 토큰 엔드포인트 TLS 미적용(`# TODO: tsl`)
