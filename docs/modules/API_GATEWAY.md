# API_GATEWAY — spring-cloud-api-gateway 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `fix/api-gateway-reactive-blacklist`
> - **기준 일자**: 2026-08-20
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - Route(`ReactiveRouteConfig`) 변경
>   - Security matcher(`ReactiveSecurityConfig.authorizeExchange`) 변경
>   - JWT 검증 체인(`ReactiveJwtDecoderConfig`) 변경
>   - CORS 설정(`CorsConfig`) 변경
>   - Config Server의 `api-gateway.yml` 또는 `jwt.yml` 변경

## 1. 문서 목적과 기준 시점

이 문서는 `spring-cloud-api-gateway` 모듈의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 기준 시점과 검증 범위는 위 메타데이터를 따른다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../spring-cloud-api-gateway/CLAUDE.md`](../../spring-cloud-api-gateway/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

Reactive Spring Cloud Gateway 기반 OAuth2 Resource Server. 외부 HTTP·WebSocket 요청의 단일 진입점으로, JWT 검증·사용자 식별 헤더 전파·라우팅·CORS·공개 경로 제어를 담당한다. 하위 서비스(user/chat/oauth2-client/websocket-gateway)의 비즈니스 로직은 다루지 않는다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:spring-cloud-api-gateway` (단일 프로젝트, 서브모듈 분리 없음). 근거: `settings.gradle:55`.
- 실행 클래스: `org.example.apigateway.Main`(`@SpringBootApplication(scanBasePackages="org.example")`). 근거: `spring-cloud-api-gateway/src/main/java/org/example/apigateway/Main.java`.
- 배포 대상: `build.gradle:5` `ext.dockerImageName = "crypto-spring-cloud-api-gateway"`. `Dockerfile`(`FROM eclipse-temurin:17-jre`, `EXPOSE 8000`).
- 주요 의존성(`build.gradle`): `spring-cloud-gateway`, `spring-cloud-loadbalancer`, `spring-boot-starter-webflux`, `spring-boot-starter-security` + `spring-security-oauth2-resource-server`/`-jose`, `spring-cloud-config-client`, `spring-cloud-eureka-client`, `spring-cloud-starter-bus-kafka`, `grpc-netty` + `grpc-client-spring-boot-starter`, `common:common-core`, `common:common-actuator-webflux`, `oauth2-authorization-server:oauth2-authorization-server-client`.
- 포트: `8000`. Config Server 원격 설정 `git-config-repo/dynamic/api-gateway.yml:2` `server.port: 8000` + HTTP/2·SSL(PKCS12 keystore) 활성(`api-gateway.yml:3-11`). `Dockerfile`의 `EXPOSE 8000`과 일치.
- Config Server 연동: `spring-cloud-api-gateway/src/main/resources/application.yml:3` `spring.config.import: configserver:http://crypto-spring-cloud-config:8888`, `application.yml:6` `spring.cloud.config.name: api-gateway,eureka-client,jwt,frontend,kafka,monitoring`. 공유 `api-contract.*`는 Config Repository 루트 `application.yml`에서 자동 병합된다.
- Eureka Client: 의존성 `spring-cloud-eureka-client` + `git-config-repo/infrastructure/eureka-client.yml`(defaultZone, lease 설정). Route도 `lb://<서비스명>` 형식으로 Eureka 등록 이름을 참조한다.

## 4. 주요 클래스와 책임

| 클래스 | 경로 | 책임 |
|---|---|---|
| `ReactiveRouteConfig` | `src/main/java/org/example/apigateway/config/ReactiveRouteConfig.java` | `RouteLocator` Bean 6개 정의(OAuth2 Client / User Service / Market Service / Notification / WebSocket Gateway / Chat Service 그룹). WebSocket 그룹 안에는 개별 Route 4건이 있음(§9) |
| `ReactiveSecurityConfig` | `src/main/java/org/example/apigateway/config/ReactiveSecurityConfig.java` | `SecurityWebFilterChain`, 경로별 인가, 403 응답 |
| `ReactiveJwtDecoderConfig` | `src/main/java/org/example/apigateway/config/ReactiveJwtDecoderConfig.java` | JWKS 기반 로컬 검증기와 비동기 blacklist 검증기를 조합 |
| `CorsConfig` | `src/main/java/org/example/apigateway/config/CorsConfig.java` | CORS 정책 Bean 3종 |
| `IdentityPropagationGlobalFilter` | `src/main/java/org/example/apigateway/filter/IdentityPropagationGlobalFilter.java` | 일반 HTTP 요청의 클라이언트 `X-User-Id`·`X-From` 제거 + 검증된 JWT `id`로 `X-User-Id` 전파(`GlobalFilter`, `HIGHEST_PRECEDENCE`) |
| `WebsocketHandshakeAuthWebFilter` | `src/main/java/org/example/apigateway/filter/WebsocketHandshakeAuthWebFilter.java` | WebSocket 핸드셰이크 전용 쿼리 토큰 인증(`WebFilter`, `@Order(-1000)`) |
| `BlacklistTokenService` | `src/main/java/org/example/apigateway/oauth2/application/service/BlacklistTokenService.java` | `Mono<Boolean>` 기반 블랙리스트 조회 유스케이스 |
| `GrpcBlacklistTokenClientAdapter` | `src/main/java/org/example/apigateway/oauth2/adapter/out/grpc/GrpcBlacklistTokenClientAdapter.java` | 공용 client의 `CompletableFuture<BoolValue>` blacklist 조회를 구독 시점에 `Mono<Boolean>`으로 변환 |
| `BlacklistAwareReactiveJwtDecoder` | `src/main/java/org/example/apigateway/oauth2/validator/BlacklistAwareReactiveJwtDecoder.java` | Nimbus JWT 검증 성공 후 비동기 blacklist 검증을 연결 |
| `ReactiveBlacklistTokenValidator` | `src/main/java/org/example/apigateway/oauth2/validator/ReactiveBlacklistTokenValidator.java` | 비동기 조회 결과가 blacklist이면 `invalid_token`으로 거부 |
| `RequiredUserIdClaimValidator` | `src/main/java/org/example/apigateway/oauth2/validator/RequiredUserIdClaimValidator.java` | `OAuth2TokenValidator<Jwt>` — `id` claim 필수 검증 |

## 5. 일반 HTTP 요청 처리 흐름

```
외부 요청
  → [Route 매칭] ReactiveRouteConfig가 정의한 6개 RouteLocator Bean(그룹) 중 path로 매칭 → 그룹 내 개별 Route 결정
  → [인가 판단] ReactiveSecurityConfig.securityWebFilterChain의 authorizeExchange
      permitAll / hasRole(USER) / anyExchange().denyAll()(기본값) 중 하나로 결정
  → [JWT 검증] oauth2ResourceServer(jwt(...)) → ReactiveJwtDecoderConfig가 만든 디코더
      Authorization: Bearer <JWT> 헤더에서 토큰 추출(Spring Security 기본 리졸버)
  → [식별 전파] IdentityPropagationGlobalFilter
      exchange.getPrincipal()의 JwtAuthenticationToken → tokenAttributes["id"] → X-User-Id 헤더 set
  → [Route 필터] X-From: gateway 추가, (user/chat만) rewritePath, X-Gateway: reactive 응답 추가
  → [전달] Spring Cloud LoadBalancer가 lb://<서비스명>을 Eureka 인스턴스로 해석해 프록시
  → [응답] 정상 응답 그대로 반환 / 401(인증 실패, 기본 처리) / 403(인가 실패, writeError 커스텀)
```
근거: `ReactiveRouteConfig.java`, `ReactiveSecurityConfig.java:44-95`, `ReactiveJwtDecoderConfig.java`, `IdentityPropagationGlobalFilter.java`.

## 6. WebSocket 핸드셰이크 처리 흐름

일반 HTTP Bearer 인증과 **별도의 인증 경로**다.

```
클라이언트 → /ws, /ws-native 경로 요청(쿼리: ?access_token=<JWT>)
  → WebsocketHandshakeAuthWebFilter(@Order(-1000), 다른 필터보다 먼저 실행)
      경로가 /ws로 시작하지 않거나 OPTIONS면 통과
      access_token 쿼리 파라미터 없음/공백 → 401
      jwtDecoder.decode(accessToken) 실패 → 401
      id claim 없음/공백 → 401
      성공 → X-User-Id 헤더 set + ReactiveSecurityContextHolder에 JwtAuthenticationToken 주입
  → ReactiveRouteConfig.websocketGatewayRoutes로 lb:ws://websocket-gateway 또는 lb://websocket-gateway 전달
```
근거: `WebsocketHandshakeAuthWebFilter.java`, `ReactiveRouteConfig.java:49-79`. 쿼리 파라미터 이름은 `AuthTokenKey.ACCESS_TOKEN_QUERY`(`common-core/.../enums/AuthTokenKey.java`) = `access_token`.

## 7. JWT 인증·검증 구조

- 디코더: `NimbusReactiveJwtDecoder.withJwkSetUri(jwtProperties.jwksUri())`. JWKS URI는 `git-config-repo/dynamic/jwt.yml`이 공통 `application.yml`의 `uri.internal.config-server`를 참조해 구성한다(Config Server가 JWKS 제공).
- 검증 순서(`ReactiveJwtDecoderConfig`):
  1. `NimbusReactiveJwtDecoder`가 서명/JWKS와 `JwtValidators.createDefaultWithIssuer(...)`의 기본·issuer 검증을 수행한다. issuer URI는 `git-config-repo/dynamic/jwt.yml`이 공통 `application.yml`의 `uri.internal.oauth2-authorization-server`를 참조한다.
  2. 같은 로컬 검증 체인의 `RequiredUserIdClaimValidator`가 `id` claim(`JwtClaimKey.USER_ID`)의 존재와 비공백 여부를 확인한다.
  3. 위 검증이 성공한 JWT만 `BlacklistAwareReactiveJwtDecoder`의 `flatMap`을 거쳐 `ReactiveBlacklistTokenValidator`로 전달된다.
  4. 공용 `Oauth2AuthorizationServerClient`가 future stub으로 blacklist를 조회해 `CompletableFuture<BoolValue>`를 반환하고, `GrpcBlacklistTokenClientAdapter`가 이를 `Mono<Boolean>`으로 변환한다. 블랙리스트 토큰은 기존과 동일하게 `invalid_token`으로 실패한다.
- 형식·서명·issuer·`id` 검증에 실패한 토큰은 원격 blacklist 조회를 시작하지 않는다. gRPC 오류는 인증 실패 경로로 전파하는 fail-closed 동작이며, 요청 취소 시 gRPC future도 취소한다.
- **audience(`aud`) 검증은 확인되지 않음.** 위 검증기 외에 aud를 확인하는 코드는 없다(`ReactiveJwtDecoderConfig.java` 전체 검토 기준).
- 사용 claim: `id`(`JwtClaimKey.USER_ID`), `roles`(`JwtClaimKey.ROLES`) — `common/common-core/.../enums/JwtClaimKey.java`.
- Access Token 읽는 위치: 일반 요청은 `Authorization: Bearer` 헤더(Spring Security 기본), WebSocket은 `?access_token=` 쿼리 파라미터(§6).

## 8. 인가 규칙과 공개 경로

`ReactiveSecurityConfig.securityWebFilterChain`의 `authorizeExchange`는 **선언 순서대로 먼저 매칭되는 규칙이 적용**되며, 마지막 `anyExchange().denyAll()`이 기본값이다. 근거: `ReactiveSecurityConfig.java:51-84`.

> **이 문서에서 `permitAll`의 의미**: Gateway의 `SecurityWebFilterChain`이 해당 경로에 JWT 인증을 강제하지 않는다는 뜻일 뿐이다. 시스템 전체에서 무조건 공개 API라는 뜻은 아니다. 하위 서비스가 자체 인증·인가를 적용하거나 별도 필터를 둘 수 있으며, 이는 각 서비스 코드로만 확인 가능하다(§18.1, §18.4 참고). 예를 들어 `/internal/deployment/**`는 Gateway JWT 기준으로는 `permitAll`이지만 `DeploymentControlAuthWebFilter`가 별도 `X-Deploy-Token`으로 보호한다(§9, §14).

| 순서 | 대상 | 결과 |
|---|---|---|
| 1 | `OPTIONS /**` | permitAll |
| 2 | `POST /internal/deployment/**` | permitAll(JWT 우회, 별도 보호는 §14 참고) |
| 3 | `/oauth2/**`, `/login/oauth2/code/**` | permitAll |
| 4 | `GET /ws/info/**` | permitAll |
| 5 | `/msg/**` | permitAll |
| 6 | `GET /user/me`, `GET /user/*/profile` | `hasRole(USER)` |
| 7 | `GET /ws/**`, `/ws-native`, `/ws-native/**` | `hasRole(USER)` |
| 8 | `GET /chat/rooms/me`, `GET /chat/room/me/*` | `hasRole(USER)` |
| 9 | `/chat/room/*/members`, `/chat/room/*/activity` | `hasRole(USER)` |
| 10 | `GET /chat/room/*/messages` | `hasRole(USER)` |
| 11 | `GET /markets` | permitAll(마켓 카탈로그 공개) |
| 12 | `/price-alerts/**`(GET·PUT) | `hasRole(USER)` |
| 13 | `/notifications/**`(GET·PATCH) | `hasRole(USER)` |
| 14 | `/actuator/**` | permitAll |
| 15 | `/auth/**` | permitAll |
| 16 | `/user/**`(나머지) | permitAll |
| 17 | `/chat/**`(나머지) | permitAll |
| 기본값 | 그 외 전부 | `denyAll` |

역할 문자열은 `RoleKey.REQUIRED_USER`="USER"(prefix 없이 `JwtGrantedAuthoritiesConverter.setAuthorityPrefix("")`로 `roles` claim 값을 그대로 authority로 사용, `ReactiveSecurityConfig.java:97-106`).

## 9. Route 계약 표

`ReactiveRouteConfig`는 `RouteLocator` Bean 6개(`oauth2ClientRoutes`, `userRoutes`, `marketRoutes`, `notificationRoutes`, `websocketGatewayRoutes`, `chatRoutes`)를 정의한다. 이 중 `websocketGatewayRoutes` 하나에 개별 Route 4건이 포함되어, 아래 표의 행 수는 Bean 개수(6개)보다 많다.

| 외부 경로 | 대상 서비스 | URI/Service ID | 적용 필터 | 인증 필요 | 근거 |
|---|---|---|---|---|---|
| `/oauth2/**`, `/login/oauth2/code/*`, `/auth/**` | oauth2-client | `lb://oauth2-client` | `X-From` 추가, `X-Gateway` 응답 추가 | 아니오 | `ReactiveRouteConfig.oauth2ClientRoutes` |
| `/user/**` | user-service | `lb://user-service` | `X-From` 추가, `rewritePath(/user(?<seg>/.*)?$ → /api/v1/user${seg})`, `X-Gateway` 응답 | `GET /user/me`,`/user/*/profile`만 `hasRole(USER)` | `ReactiveRouteConfig.userRoutes` |
| `/markets`,`/markets/**`,`/price-alerts`,`/price-alerts/**` | market-service | `lb://market-service` | `X-From` 추가, `rewritePath(/(?<seg>.*) → /api/v1/${seg})`, `X-Gateway` 응답 | `GET /markets`는 permitAll, `/price-alerts/**`는 `hasRole(USER)` | `ReactiveRouteConfig.marketRoutes` |
| `/notifications`,`/notifications/**` | notification-service | `lb://notification-service` | `X-From` 추가, `rewritePath(/(?<seg>.*) → /api/v1/${seg})`, `X-Gateway` 응답 | `hasRole(USER)` | `ReactiveRouteConfig.notificationRoutes` |
| `/ws-native`, `/ws-native/**`(upgrade) | websocket-gateway | `lb:ws://websocket-gateway` | 없음 | `GET`만 `hasRole(USER)` + 핸드셰이크 인증 | `websocketGatewayRoutes("ws-native-upgrade")` |
| `/ws/**` + `Upgrade: websocket` 헤더 | websocket-gateway | `lb:ws://websocket-gateway` | 없음 | 동일 | `websocketGatewayRoutes("ws-upgrade")` |
| `/ws/**`,`/ws-native`,`/ws-native/**`(HTTP) | websocket-gateway | `lb://websocket-gateway` | `dedupeResponseHeader`(CORS 3종) | `GET /ws/info/**`는 permitAll, 나머지 `GET`은 `hasRole(USER)` | `websocketGatewayRoutes("ws-http")` |
| `/msg/**` | websocket-gateway | `lb://websocket-gateway` | 없음 | 아니오 | `websocketGatewayRoutes("sockjs-route")` |
| `/chat/**` | chat-service | `lb://chat-service` | `X-From` 추가, `rewritePath(/chat(?<seg>/.*)?$ → /api/v1/chat${seg})`, `X-Gateway` 응답 | 일부 `GET`만 `hasRole(USER)`(§8 참고), 나머지 permitAll | `ReactiveRouteConfig.chatRoutes` |
| `POST /internal/deployment/**` | Gateway 자신 | N/A(Route 아님, 로컬 컨트롤러) | `DeploymentControlAuthWebFilter`(`X-Deploy-Token`) | JWT는 permitAll, Deploy Token 별도 필요 | `ReactiveSecurityConfig.java:56`, `common/common-actuator-webflux/.../DeploymentControlAuthWebFilter.java` |
| `/actuator/**` | Gateway 자신 | N/A | 없음 | 아니오 | `ReactiveSecurityConfig.java:78` |

**oauth2-authorization-server로 향하는 HTTP Route는 없다.** 연결은 공용 `Oauth2AuthorizationServerClient.existsBlacklist`가 수행하는 `auth.v1.BlacklistTokenService/Exists` gRPC 호출뿐이며, `grpc.client.oauth2-authorization-server-client.address`는 공통 `application.yml`의 `uri.discovery.oauth2-authorization-server`를 참조한다.

## 10. Header 및 Path Rewrite 계약

Gateway가 생성·추가하는 헤더는 다음 세 가지다. 셋 다 Route 필터 또는 인증 필터에서 코드로 추가되며, 이 문서에서 "Gateway가 생성하는 헤더"라고 하면 이 목록을 가리킨다.

| 헤더 | 방향 | 추가 위치 | 값의 근거 |
|---|---|---|---|
| `X-From: gateway` | 요청 | `ReactiveRouteConfig`(`addRequestHeader`, `ReactiveRouteConfig.java:26,40,87`) | 고정 문자열. oauth2-client/user/chat route에서 추가, websocket-gateway route에는 없음 |
| `X-Gateway: reactive` | 응답 | `ReactiveRouteConfig`(`addResponseHeader`) | 고정 문자열. oauth2-client/user/chat route에서 추가 |
| `X-User-Id`(`HttpHeaderKey.USER_ID`) | 요청 | 일반 HTTP: `IdentityPropagationGlobalFilter`. WebSocket: `WebsocketHandshakeAuthWebFilter` | 검증된 JWT의 `id` claim에서만 생성(§5, §6) |

`X-User-Id`는 일반 HTTP와 WebSocket에서 서로 다른 필터 체인(`GlobalFilter` vs `WebFilter`)이 독립적으로 주입한다는 점이 차이다. 두 경로 모두 값의 출처는 검증된 JWT의 `id` claim으로 동일하다.

**클라이언트가 같은 이름으로 헤더를 직접 보낸 경우**: `IdentityPropagationGlobalFilter`(`Ordered.HIGHEST_PRECEDENCE`)가 **모든 요청 입구에서 클라이언트가 보낸 `X-User-Id`·`X-From`을 먼저 제거**하고, 인증된 경우에만 JWT `id`로 `X-User-Id`를 다시 `set`한다. 따라서 `permitAll`·미인증 경로로 위조 헤더를 보내도 하위 서비스로 새지 않는다(게이트웨이 **경유** 스푸핑 차단). 게이트웨이를 **우회**한 서비스 직접 접근 차단은 별개 방어(인프라: 서비스 포트 host-local 바인딩 + 방화벽) — infra `TODO.md` "보안 · 네트워크 노출".

- Path Rewrite: `user`, `chat` route만 `/api/{userApiVersion|chatApiVersion}/...`로 rewrite(`api-path.route.user-api-version=v1`, `chat-api-version=v1`, `git-config-repo/dynamic/api-gateway.yml:48-50`). `oauth2-client`, `websocket-gateway` route는 rewrite 없이 그대로 전달.
- Query Parameter Token: WebSocket 핸드셰이크 전용 `?access_token=`(§6). 일반 REST에는 쿼리 토큰 사용 없음.
- Cookie: Gateway 코드에서 쿠키를 직접 다루는 로직 없음(그대로 프록시). Refresh 쿠키 속성 자체는 oauth2-client 책임 범위(이 문서 범위 밖).

## 11. 연관 서비스 연결

### user-service
- Route: `/user/**` → `lb://user-service`, rewrite `→ /api/v1/user${seg}`.
- 전달 인증 정보: `X-User-Id`(인증된 경우만), `X-From: gateway`.
- 공유 계약: `X-User-Id` 헤더, path rewrite 버전(`v1`), `GET /user/me`·`/user/*/profile`의 gateway 레벨 `hasRole(USER)` 강제.

### oauth2-client
- Route: `/oauth2/**`, `/login/oauth2/code/*`, `/auth/**` → `lb://oauth2-client`(permitAll, JWT 미검증 프록시).
- 전달 인증 정보: 없음(이 경로들은 gateway에서 인증하지 않음).
- 공유 계약: 로그인/콜백/로그아웃 경로 이름.

### oauth2-authorization-server
- Route: 없음(HTTP 미연결).
- 전달 인증 정보: 없음. 비동기 gRPC로 blacklist 존재 여부만 조회(`GrpcBlacklistTokenClientAdapter`).
- 공유 계약: JWKS(issuer가 발급한 키), issuer 문자열, gRPC blacklist 조회 메서드.

### websocket-gateway
- Route: `/ws-native`, `/ws-native/**`, `/ws/**`, `/msg/**` — `websocketGatewayRoutes` Bean 그룹에 속한 개별 Route 4건(§9 Route 계약 표 참고).
- 전달 인증 정보: `X-User-Id`(핸드셰이크 시 `WebsocketHandshakeAuthWebFilter`가 주입).
- 공유 계약: `access_token` 쿼리 파라미터, `X-User-Id` 헤더, `/ws`·`/ws-native`·`/msg` prefix.

### chat-service
- Route: `/chat/**` → `lb://chat-service`, rewrite `→ /api/v1/chat${seg}`.
- 전달 인증 정보: `X-User-Id`(인증된 경우만), `X-From: gateway`.
- 공유 계약: `X-User-Id` 헤더, path rewrite 버전(`v1`), 채팅방 관련 GET 경로들의 gateway 레벨 `hasRole(USER)` 강제(§8).

### market-service
- Route: `/markets`,`/markets/**`,`/price-alerts`,`/price-alerts/**` → `lb://market-service`, rewrite `/(?<seg>.*) → /api/v1/${seg}`.
- 전달 인증 정보: `X-User-Id`(`/price-alerts/**` 인증 경로), `X-From: gateway`.
- 공유 계약: `GET /markets`는 permitAll(카탈로그), `/price-alerts/**`는 `hasRole(USER)` → `PriceAlertSettingController`가 `X-User-Id`(publicId)로 본인 스코프.

### notification-service
- Route: `/notifications`,`/notifications/**` → `lb://notification-service`, rewrite `/(?<seg>.*) → /api/v1/${seg}`.
- 전달 인증 정보: `X-User-Id`(인증 경로), `X-From: gateway`.
- 공유 계약: `/notifications/**` `hasRole(USER)` → `NotificationController`가 `X-User-Id`(receiverId)로 본인 스코프. 실시간 push는 별도(websocket-gateway STOMP).

## 12. Config Server 및 Eureka 의존성

- Config Server: `spring.config.import: configserver:http://crypto-spring-cloud-config:8888`, 조합 프로파일 `api-gateway,eureka-client,jwt,frontend,monitoring`(`application.yml:3-7`).
- 서버 포트·라우팅 패턴 등 실질적 동작값은 로컬이 아니라 원격 `git-config-repo/dynamic/api-gateway.yml`, `git-config-repo/dynamic/jwt.yml`, `git-config-repo/infrastructure/{frontend,eureka-client,monitoring}.yml`에 있다.
- 외부 REST/WebSocket 경로 문자열의 정본은 `git-config-repo/application.yml`의 `api-contract.*`이며,
  `api-gateway.yml`은 기존 `api-path.*` 구조로 이를 매핑해 security matcher와 route에서 소비한다.
- Eureka: `git-config-repo/infrastructure/eureka-client.yml` — `defaultZone`은 공통 `application.yml`의 `uri.internal.eureka-server`를 참조하며, lease renewal 10s/expiration 30s.
- `lb://`, `lb:ws://` 뒤의 이름은 대상 서비스가 Eureka에 등록하는 `spring.application.name`과 일치해야 라우팅이 성립한다(개별 서비스의 실제 등록 이름은 이 문서 범위 밖, §18 참고).

## 13. CORS 정책

`CorsConfig.java` 기준.

- Origin: `frontend.origin`은 공통 `application.yml`의 `uri.public.frontend-origin`을 참조하며 설정값 1개만 허용.
- Methods: `OPTIONS, GET, POST, PUT, PATCH, DELETE` (**DELETE 포함**).
- Headers: `*`(모두 허용).
- `allowCredentials: true`.
- Exposed headers: `Authorization`, `Set-Cookie`.
- `maxAge: 3600`.
- 적용 대상: `/**`(`UrlBasedCorsConfigurationSource`).

## 14. 오류 응답 처리

| 상황 | 처리 | 근거 |
|---|---|---|
| 일반 HTTP 인증 실패(401) | Spring Security `oauth2ResourceServer` 기본 처리(커스텀 `AuthenticationEntryPoint` 미설정) | `ReactiveSecurityConfig.java` 전체(커스텀 코드 없음) |
| 일반 HTTP 인가 실패(403) | 커스텀 `accessDeniedHandler` — CORS 헤더 재설정 + JSON body(`timestamp`,`status`,`error:"FORBIDDEN"`,`message`,`path`) | `ReactiveSecurityConfig.java:91-92,108-148` |
| WebSocket 핸드셰이크 인증 실패(401) | `WebsocketHandshakeAuthWebFilter.unauthorized` — 상태코드만 설정, JSON body 없음 | `WebsocketHandshakeAuthWebFilter.java:97-100` |
| 배포 제어 인증 실패(401) | `DeploymentControlAuthWebFilter` — `{"message":"Unauthorized deployment control request"}` | `common/common-actuator-webflux/.../DeploymentControlAuthWebFilter.java` |

세 인증 실패 처리(일반 401 / WebSocket 401 / 배포 제어 401)는 서로 다른 필터·바디 형식을 사용하며 통일되어 있지 않다.

## 15. 테스트 현황

| 클래스 | 검증 항목 |
|---|---|
| `endpoint/ReactiveSecurityE2ETest` | 토큰 없음 401, role 없음 403(JSON body), `/auth/logout` permitAll 라우팅, `/price-alerts/me`·`/notifications/me` 401/403 |
| `endpoint/ReactiveRouteE2ETest` | `/user/me` rewrite+라우팅, `/chat/rooms/me` rewrite+라우팅, `/auth/logout` 라우팅 |
| `endpoint/IdentityPropagationE2ETest` | id claim 있음/없음 전파, permitAll 경로 클라이언트 `X-User-Id` strip, 인증 요청 위조 `X-User-Id` override |
| `endpoint/GatewayCorsConfigTest` | CORS Origin 허용(테스트 전용 wildcard `TestGatewayCorsConfig`로 실제 `CorsConfig` 대체) |
| `filter/IdentityPropagationGlobalFilterTest` | 필터 단위 동작 |
| `filter/WebsocketHandshakeAuthWebFilterTest` | non-ws 경로 통과, OPTIONS 통과, 토큰 없음/빈값 401, JWT 디코드 실패 401, id claim 없음 401, 정상 시 헤더+SecurityContext 설정, roles claim 없어도 정상 처리 |
| `validator/BlacklistAwareReactiveJwtDecoderUnitTest` | delegate 검증 성공 후 blacklist 검증 연결, delegate 실패 시 원격 조회 생략 |
| `validator/ReactiveBlacklistTokenValidatorUnitTest` | 블랙리스트 없음→성공, 있음→`invalid_token` 실패, gRPC 오류 전파 |
| `oauth2/adapter/out/grpc/GrpcBlacklistTokenClientAdapterUnitTest` | 구독 전 공용 client 호출 없음, 성공·오류·구독 취소 전파 |
| `validator/RequiredUserIdClaimValidatorTest` | id claim 있음/없음/빈문자열/null |

**테스트 공백**
- issuer 검증 자체(`JwtValidators.createDefaultWithIssuer`)의 실패 케이스 단위 테스트 없음.
- `/internal/deployment/**` + `DeploymentControlAuthWebFilter`의 gateway 레벨 통합 테스트 없음(테스트는 `common-actuator-webflux` 쪽에만 존재).
- 공용 `GrpcOauth2AuthorizationServerClient` 테스트는 deadline 적용을 검증하지만 실제 시간 경과에 의한 `DEADLINE_EXCEEDED` 발생은 검증하지 않음.
- `ReactiveRouteConfig`에 정의된 모든 외부 Path·대상 Service ID·RewritePath·인증 요구사항이 §9 Route 계약 표와 실제로 일치하는지 자동 검증하는 파라미터화 테스트 또는 계약 테스트가 없다. 현재 `ReactiveRouteE2ETest`는 `/user/me`, `/chat/rooms/me`, `/auth/logout` 등 대표 경로 일부만 검증하며, §9 표 전체를 커버하지 않는다.

## 16. 컴파일·테스트·CI 명령

- 컴파일: `./gradlew :spring-cloud-api-gateway:compileJava`
- 테스트: `./gradlew :spring-cloud-api-gateway:test`
- 서비스 CI: `./gradlew gatewayCi`(루트 `build.gradle:16,48` — `:spring-cloud-api-gateway:build` 포함, 전체 집계 `serviceCi`에도 포함)

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 이 문서 작성 과정에서 실행하지 않았다.

## 17. 변경 위험도가 높은 파일

| 파일 | 위험 사유 |
|---|---|
| `config/ReactiveSecurityConfig.java` | `authorizeExchange` 순서/패턴 변경이 `anyExchange().denyAll()` 기본값과 맞물려 전체 서비스 접근 불가 또는 과다 노출로 직결 |
| `config/ReactiveJwtDecoderConfig.java` | JWT 검증 체인(issuer/blacklist/id) 변경은 전체 인증 우회/차단 위험 |
| `oauth2/validator/{BlacklistAwareReactiveJwtDecoder,ReactiveBlacklistTokenValidator}.java` | 로컬 검증과 원격 blacklist 조회 순서·오류 의미 변경 시 인증 우회/전체 차단 위험 |
| `oauth2/adapter/out/grpc/GrpcBlacklistTokenClientAdapter.java` | `CompletableFuture`를 Reactor 구독·오류·취소에 연결하는 방식 변경 시 Gateway 요청 처리 자원에 영향 |
| `config/ReactiveRouteConfig.java` | Route path/RewritePath 변경은 프론트·하위 서비스에 동시 영향(외부 계약) |
| `filter/IdentityPropagationGlobalFilter.java` | `X-User-Id` 헤더 계약 변경 시 모든 하위 서비스 영향 |
| `filter/WebsocketHandshakeAuthWebFilter.java` | websocket-gateway 및 부하 테스트가 의존 |
| `config/CorsConfig.java` | origin/method/credential 변경은 프론트 E2E 직접 영향 |
| `git-config-repo/dynamic/{api-gateway,jwt}.yml` | 원격 config이지만 포트/JWKS/issuer/TTL 등 동작을 실질적으로 결정 |

## 18. 관련 문서와 rules

- [`../../spring-cloud-api-gateway/CLAUDE.md`](../../spring-cloud-api-gateway/CLAUDE.md) — 이 모듈 작업 시 지켜야 할 짧은 규칙
- [`../../CLAUDE.md`](../../CLAUDE.md) — 루트 공통 규칙
- [`../../.claude/rules/external-contracts.md`](../../.claude/rules/external-contracts.md) — Route/JWT/CORS 등 외부 계약 변경 절차
- [`../../.claude/rules/security.md`](../../.claude/rules/security.md) — OAuth2/JWT 핵심 규칙
- [`../../.claude/rules/testing.md`](../../.claude/rules/testing.md) — 테스트·CI 명령 전체 목록
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — 전체 시스템 구조
- [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md) — 서비스 간 흐름(§6~§7이 이 모듈과 직접 관련)
