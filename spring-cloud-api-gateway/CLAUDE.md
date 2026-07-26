# spring-cloud-api-gateway — 모듈 작업 지침

이 파일은 `spring-cloud-api-gateway/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/API_GATEWAY.md`](../docs/modules/API_GATEWAY.md)를 참고한다.

## 모듈 역할과 적용 범위

이 모듈은 서브모듈 분리 없는 단일 Gradle 프로젝트(`build.gradle`)이며, 다음 여섯 가지를 담당한다.

1. 외부 HTTP·WebSocket 요청의 단일 진입점
2. Eureka 기반 라우팅(`lb://`, `lb:ws://`)
3. OAuth2 Resource Server 방식 JWT 검증(issuer + blacklist + 필수 `id` claim)
4. 검증된 사용자 식별값의 `X-User-Id` 헤더 전파
5. Path Rewrite, CORS, 경로별 공개/인증 접근 제어
6. oauth2-authorization-server와의 gRPC blacklist 조회(HTTP Route는 없음)

하위 서비스(user/chat/oauth2-client/websocket-gateway) 자체의 로직·인가는 이 문서의 범위가 아니다. 이 모듈에 코드 변경이 없는 작업(예: 다른 서비스만 수정)에는 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- `ReactiveSecurityConfig`의 `authorizeExchange` matcher 선언 순서는 의미가 있다(먼저 매칭되는 규칙이 우선 적용). 임의로 순서를 바꾸지 않는다.
- 기본 정책 `anyExchange().denyAll()`을 유지한다. 새 경로를 추가할 때는 `permitAll` 또는 `hasRole(...)` 중 하나를 반드시 명시한다 — 누락하면 자동으로 403이 아니라 예상과 다른 인가 결과가 날 수 있다.
- 공개 경로(`permitAll`)를 추가하거나 기존 패턴을 확대할 때는 보안 영향을 먼저 검토한다(`../.claude/rules/git-safety.md` Plan Mode 대상: OAuth2/Security 변경). `permitAll`은 Gateway가 이 경로에 JWT를 강제하지 않는다는 뜻일 뿐, 하위 서비스까지 공개라는 뜻은 아니다 — 실제 공개 범위는 하위 서비스 코드로 별도 확인한다.
- Route를 변경할 때는 외부 Path, 내부 Service ID(`lb://...`), RewritePath 여부, 인증 요구사항(`ReactiveSecurityConfig`의 대응 규칙)을 하나의 계약으로 함께 검토한다. Route만 바꾸고 Security 규칙을 빠뜨리면(또는 반대) 계약이 깨진다. `ReactiveRouteConfig`는 6개의 `RouteLocator` Bean(그룹)을 정의하며(oauth2-client/user/market/notification/websocket-gateway/chat), 그중 WebSocket 그룹 안에는 개별 Route가 여러 개 있다 — "Bean 개수"는 개별 Route 총개수가 아니다.
- `lb://`, `lb:ws://` 뒤의 서비스 이름은 대상 서비스의 `spring.application.name`(Eureka 등록 이름)과 정확히 일치해야 한다.
- Route/Security/JWT 관련 값을 바꿀 때는 로컬 코드뿐 아니라 Config Server 원격 설정도 함께 확인한다: `git-config-repo/dynamic/api-gateway.yml`(포트, `api-path.*` 패턴, gRPC 클라이언트), `git-config-repo/dynamic/jwt.yml`(issuer, JWKS, TTL).
- `X-User-Id`, `X-From`, `X-Gateway` 헤더는 외부 계약으로 취급한다(→ `../.claude/rules/external-contracts.md`). 이름·의미를 임의로 바꾸지 않는다.
- `X-User-Id` 값은 검증된 JWT의 `id` claim에서만 생성한다. 클라이언트가 보낸 원본 헤더를 그대로 신뢰하거나 전달하지 않는다. `X-User-Id`·`X-From` 같은 내부 신뢰 헤더를 변경할 때는 (1) `permitAll` 경로에서 외부 입력이 제거되는지, (2) 하위 서비스가 이 값을 어떻게 소비하는지를 함께 확인한다(둘 다 미확인 상태, 상세는 API_GATEWAY.md §18.1).
- WebSocket 핸드셰이크(`WebsocketHandshakeAuthWebFilter`)는 일반 HTTP Bearer 인증과 별도의 인증 흐름이다(쿼리 파라미터 `access_token` 사용). 이 필터를 일반 HTTP 인증 로직과 동일하게 취급하지 않는다.
- CORS를 변경할 때는 origin, `allowCredentials`, methods, exposed headers를 함께 확인한다(하나만 바꾸면 프론트 E2E가 깨질 수 있다).
- Route·Header·JWT·CORS 변경은 관련 루트 rules를 함께 적용한다: `../.claude/rules/external-contracts.md`, `../.claude/rules/security.md`.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`src/main/java/org/example/apigateway/config/ReactiveRouteConfig.java`](src/main/java/org/example/apigateway/config/ReactiveRouteConfig.java) | `RouteLocator` Bean 6개 정의(oauth2-client/user/market/notification/websocket-gateway/chat 그룹) |
| [`src/main/java/org/example/apigateway/config/ReactiveSecurityConfig.java`](src/main/java/org/example/apigateway/config/ReactiveSecurityConfig.java) | 경로별 인가 규칙, 403 응답 처리 |
| [`src/main/java/org/example/apigateway/config/ReactiveJwtDecoderConfig.java`](src/main/java/org/example/apigateway/config/ReactiveJwtDecoderConfig.java) | JWKS 기반 JWT 디코더, 검증 체인(issuer+blacklist+id) |
| [`src/main/java/org/example/apigateway/config/CorsConfig.java`](src/main/java/org/example/apigateway/config/CorsConfig.java) | CORS 정책 |
| [`src/main/java/org/example/apigateway/filter/IdentityPropagationGlobalFilter.java`](src/main/java/org/example/apigateway/filter/IdentityPropagationGlobalFilter.java) | 일반 HTTP 요청의 `X-User-Id` 전파 |
| [`src/main/java/org/example/apigateway/filter/WebsocketHandshakeAuthWebFilter.java`](src/main/java/org/example/apigateway/filter/WebsocketHandshakeAuthWebFilter.java) | WebSocket 핸드셰이크 전용 쿼리 토큰 인증 |
| [`src/main/java/org/example/apigateway/oauth2/adapter/out/grpc/GrpcBlacklistTokenClientAdapter.java`](src/main/java/org/example/apigateway/oauth2/adapter/out/grpc/GrpcBlacklistTokenClientAdapter.java) | oauth2-authorization-server gRPC blacklist 조회 |
| `../git-config-repo/dynamic/api-gateway.yml` | 포트, `api-path.*` 라우팅 패턴, gRPC 클라이언트 설정(Config Server 원격) |
| `../git-config-repo/dynamic/jwt.yml` | issuer, JWKS URI, TTL(Config Server 원격) |

## 검증 명령

- 컴파일: `./gradlew :spring-cloud-api-gateway:compileJava`
- 테스트: `./gradlew :spring-cloud-api-gateway:test`
- 서비스 CI: `./gradlew gatewayCi` (루트 `build.gradle`에 정의, `:spring-cloud-api-gateway:build` 포함)

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

아래는 확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다.

- 인증되지 않은 `permitAll` 요청에서 클라이언트가 보낸 `X-User-Id`·`X-From`이 제거되는지
- 하위 서비스가 `X-User-Id`·`X-From`을 어떤 방식으로 신뢰하거나 재검증하는지(user-service 등 개별 분석 필요)
- WebSocket `?access_token=` 쿼리 파라미터 노출을 개선할 방법
- JWT `aud`(audience) 검증 추가 필요성
- `/user/**`, `/chat/**`의 Gateway `permitAll` 범위와 하위 서비스 자체 인가의 일치 여부
