# SPRING_CLOUD_CONFIG — spring-cloud-config 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - 엔드포인트/시큐리티(`JwksController`, `adapter/in/config/SecurityConfig`) 변경
>   - 서명/JWKS 로직(`JwtSigningService`, `JwksService`, `JwkSetFactory`, `RsaPublicKeyParser`) 변경
>   - Vault Transit 어댑터(`VaultTransitSigner`, `VaultTransitKeyReader`) 또는 요청 계약(`VaultSignRequest`) 변경
>   - `spring-cloud-config-bootstrap/.../application.yml`(config backend·api-path·vault.transit) 변경
>   - `git-config-repo/dynamic/jwt.yml`(`jwks-uri`/`sign-uri`/`key-name`/`key-version`) 변경

## 1. 문서 목적과 기준 시점

`spring-cloud-config` 모듈의 구조·역할·계약·근거를 사람과 AI가 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다. 짧은 작업 규칙은 [`../../spring-cloud-config/CLAUDE.md`](../../spring-cloud-config/CLAUDE.md)에 있다.

## 2. 모듈 역할

두 가지 역할을 겸하는 **인프라 · 크립토 허브**다.

1. **Spring Cloud Config Server**(`@EnableConfigServer`): git(`git-config-repo`) + Vault(KV v2) 백엔드에서 런타임 설정을 제공한다. 모든 실행 서비스가 `spring.config.import: configserver:http://crypto-spring-cloud-config:8888`로 이 서버에서 설정을 로드한다(서비스에 로컬 `application-*.yml` 없음).
   - Git 저장소 루트의 `application.yml`은 모든 Config Client 응답에 공통 병합된다. 공개·내부·discovery·provider·datastore URI의 정본이며, 개별 설정은 `${uri.*}`로 참조한다.
2. **JWT 서명/JWKS 대행**(Vault Transit): 헥사고날 어댑터로 두 엔드포인트를 노출한다.
   - `GET /.well-known/jwks.json?keyName=` — Vault Transit의 최신 public key로 JWKS를 만들어 반환. **JWT 검증측**(`spring-cloud-api-gateway`, `oauth2-client`)이 사용한다.
   - `POST /sign` — `header.payload` digest를 Vault Transit로 RS256 서명. **JWT 발급측**(`oauth2-authorization-server`의 `Rs256JwtEncoder`)이 사용한다.

즉 실제 개인키는 Vault Transit에만 있고, 이 서버는 **서명 대행(sign)과 공개키 노출(JWKS)의 단일 창구**다. 자체 DB·gRPC 없음.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:spring-cloud-config:*`(헥사고날 멀티모듈). 실행 모듈 `:spring-cloud-config-bootstrap`, docker 이미지 `crypto-spring-cloud-config`.
- 실행 클래스: `org.example.configserver.Main`(`@EnableConfigServer` + `@ConfigurationPropertiesScan`). 포트 `8888`.
- **자기 설정은 로컬 `application.yml`**에서 로드한다(자신이 Config Server이므로 config repo에서 받지 않는다).
- Config 백엔드: **Vault**(`order: 1`, AppRole, KV v2, `applicationName: mysql,mongo,oauth2-client,monitoring`) + **git**(`order: 2`, `search-paths`: 루트·`/dynamic`·`/infrastructure`, `default-label: main`).
- Spring Cloud Bus(Kafka, `spring-cloud-starter-bus-kafka`) → `busrefresh` 브로드캐스트.
- 핵심 라이브러리: `spring-cloud-config-server`, `spring-vault-core`, `spring-security-oauth2-jose`(Nimbus JOSE, JWKSet 생성), `spring-boot-starter-web`, `common-core`, `common-actuator-webmvc`.

## 4. 모듈 구조 (헥사고날)

설정 제공(Config Server)은 프레임워크가 담당하고, 헥사고날 구조는 **JWKS/서명 도메인**에만 적용된다. `-domain`/`-client`/`-contract` 모듈 없음.

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `-application` | application | `JwtSigningService`, `JwksService`, digest/서명 파싱/공개키 파싱, `VaultTransit*Port`(out) | `common-core`, `spring-security-oauth2-jose` |
| `-adapter-in` | adapter-in | `JwksController`(`/.well-known/jwks.json`, `/sign`), `SecurityConfig`(RSA `KeyFactory` bean) | application, `spring-boot-starter-web` |
| `-adapter-out` | adapter-out | `VaultTransitSigner`/`VaultTransitKeyReader`(`VaultTemplate`), `VaultTransitProperties` | application, `spring-vault-core` |
| `-bootstrap` | 실행 | `Main`(`@EnableConfigServer`), 로컬 `application.yml` | 위 3개 + config-server/bus/vault/actuator |

의존 방향: adapter-in/out → application(포트 인터페이스를 adapter-out이 구현). domain 모듈 없이 application이 DTO/포트를 보유한다.

## 5. 주요 클래스와 책임

| 클래스 | 계층 | 책임 |
|---|---|---|
| `Main` | bootstrap | `@EnableConfigServer` 실행 진입점 |
| `JwksController` | adapter-in | `GET ${api-path.jwks}` → JWKS, `POST ${api-path.sign}` → 서명. 경로는 프로퍼티 주입(기본 `/.well-known/jwks.json`, `/sign`) |
| `SecurityConfig` | adapter-in | RSA `KeyFactory` bean 제공(공개키 파싱용). **SecurityFilterChain은 정의하지 않음**(→ §12) |
| `JwksService` | application | Vault에서 최신 public key 조회 → `RSAPublicKey` 파싱 → JWKS 생성 |
| `JwkSetFactory` | application | `RSAPublicKey`+kid로 Nimbus `RSAKey`(RS256, use=sig) → JWKSet JSON |
| `RsaPublicKeyParser` | application | PEM(`X509EncodedKeySpec`)을 `RSAPublicKey`로 파싱 |
| `JwtSigningService` | application | `header.payload` digest → Vault 서명 → Base64Url 변환 → `SignResponse(kid,alg,sig)` |
| `JwtSigningInputDigester` | application | `header.payload`를 US-ASCII로 SHA-256 digest(Base64) |
| `VaultSignatureParser` | application | Vault `vault:vN:base64` 서명에서 마지막 `:` 뒤를 raw로 디코드 후 Base64Url 인코딩 |
| `VaultTransitKeyReader` | adapter-out | `VaultTemplate.read(transit/keys/{keyName})` → 최신 버전 public key |
| `VaultTransitSigner` | adapter-out | `VaultTemplate.write(transit/sign/{keyName})`(prehashed, sha2-256, pkcs1v15) → signature |

## 6. 주요 흐름

### 6.1 설정 제공 (Config Server)
```
각 서비스 부팅: spring.config.import=configserver:http://crypto-spring-cloud-config:8888
 → Config Server가 profile(git,vault)로 병합:
     Vault(order 1, KV v2, AppRole) + git(order 2, git-config-repo: 루트/dynamic/infrastructure)
 → 서비스에 property source 응답
busrefresh(Kafka bus)로 런타임 갱신 브로드캐스트
```

### 6.2 JWKS 제공 (JWT 검증측 대상)
```
GET /.well-known/jwks.json?keyName={key}
 → JwksService: VaultTransitKeyReader.readLatestKey(key)  (transit/keys/{key})
 → RsaPublicKeyParser.parse(PEM) → JwkSetFactory.create(RSAPublicKey, kid=key:version)
 → JWKS JSON (RS256, use=sig)
소비: gateway ReactiveJwtDecoderConfig, oauth2-client JwtDecoderConfig (withJwkSetUri)
```

### 6.3 JWT 서명 대행 (JWT 발급측 대상)
```
POST /sign  { keyName, keyVersion, headerB64u, payloadB64u }
 → JwtSigningInputDigester: SHA-256(header.payload) → Base64
 → VaultTransitSigner.sign(keyName, keyVersion, digest)  (transit/sign/{keyName}, prehashed pkcs1v15 sha2-256)
 → VaultSignatureParser: vault 서명 → Base64Url
 → SignResponse { kid=keyName:keyVersion, alg=RS256, sigB64u }
소비: oauth2-authorization-server Rs256JwtEncoder (jwtProperties.signUri)
```

## 7. 엔드포인트 · 키(kid) 계약

- **`GET /.well-known/jwks.json?keyName=`**: 필수 쿼리 `keyName`. 응답은 표준 JWKS(`keys[]`, RS256, `use=sig`, `kid`).
- **`POST /sign`**: 요청 `SignRequest{keyName, keyVersion, headerB64u, payloadB64u}`, 응답 `SignResponse{kid, alg, sigB64u}`.
- **kid 형식 = `keyName:keyVersion`**: 서명(`SignResponse.kid`)과 JWKS(`VaultTransitPublicKeyInfo.kid()`)가 **동일 규칙**을 써야 검증측이 kid로 키를 매칭한다. 이 형식을 한쪽만 바꾸면 검증이 깨진다.
- **알고리즘**: RS256. Vault Transit 서명은 `prehashed=true`, `hash_algorithm=sha2-256`, `signature_algorithm=pkcs1v15`(`VaultSignRequest`).
- **Vault 경로**: 서명 `transit/sign/{keyName}`, 공개키 `transit/keys/{keyName}`(`vault.transit.{sign,key}-path-prefix`).
- **엔드포인트 URI는 외부 계약**: `git-config-repo/dynamic/jwt.yml`의 `jwks-uri`·`sign-uri`가 이 서버 경로를 가리킨다. 경로 변경 시 jwt.yml과 소비 서비스를 함께 본다(→ `external-contracts.md`).

## 8. 설정

이 서버의 **자기 설정은 로컬** `spring-cloud-config-bootstrap/src/main/resources/application.yml`이다(Config Server 자신이라 config repo에서 받지 않는다).

- `server.port: 8888`, `spring.profiles.active: git,vault`.
- `api-path.jwks: /.well-known/jwks.json`, `api-path.sign: /sign`(컨트롤러가 프로퍼티로 주입).
- `spring.cloud.config.server.git`(uri=`${CONFIG_REPO_URI}`, search-paths, `default-label: main`, order 2) + `.vault`(AppRole `${VAULT_ROLE_ID}`/`${VAULT_SECRET_ID}`, KV v2, order 1).
- `spring.cloud.stream.kafka.binder.brokers`(bus), `management.endpoints.web.exposure.include: health,info,busrefresh`.
- `deployment.control.token: ${DEPLOY_TOKEN}`, health group `readiness: [readinessState, deploymentReadiness]`.
- `vault.transit.{sign,key}-path-prefix`.

**소비측 설정**(이 서버가 제공): `git-config-repo/dynamic/jwt.yml`의 `jwt.jwks-uri`·`jwt.sign-uri`·`key-name`(`my-authorization-server-jwt`)·`key-version`.

## 9. 테스트 현황

- adapter-in: `JwksControllerTest`(GET JWKS / POST sign / SignRequest 매핑).
- adapter-out: `VaultTransitKeyReaderTest`(경로·null·타입 방어), `VaultTransitSignerTest`(sign 경로·요청 필드·응답 파싱).
- application: `JwtSigningServiceTest`(digest→서명→변환 순서·kid 조합), `VaultSignatureParserTest`, `RsaPublicKeyParserTest`(PEM 파싱·예외).
- 모두 단위 테스트(`VaultTemplate` 등 외부는 mock). Testcontainers/실 Vault 미사용.

## 10. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :spring-cloud-config:spring-cloud-config-application:compileJava`(대상 서브모듈).
- 서브모듈 테스트: `./gradlew :spring-cloud-config:spring-cloud-config-adapter-out:test` 등.
- 서비스 CI: `./gradlew springCloudConfigCi`(루트 `build.gradle`).
- 전체 build/test, `bootRun`, 배포는 명시적 요청 없이 수행하지 않는다.

## 11. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `spring-cloud-config-bootstrap/.../application.yml` | config 백엔드(git/vault)·api-path·vault.transit. 전체 서비스 설정 로드에 영향 |
| `JwksController.java` | JWKS·서명 엔드포인트. 발급/검증 양측 계약 |
| `JwtSigningService.java` / `VaultTransitSigner.java` | 서명 로직·Vault 요청 필드. 발급 토큰 유효성 |
| `JwksService.java` / `JwkSetFactory.java` | JWKS·kid 생성. 검증측 키 매칭 |
| `SecurityConfig.java` | 엔드포인트 보호 정책(현재 SecurityFilterChain 없음 → §12) |
| `git-config-repo/dynamic/jwt.yml` | jwks-uri/sign-uri/key-name/key-version(소비측 계약) |

## 12. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. spring-cloud-config 관련 항목:

- **TODO 1.10** — `/sign`·JWKS 엔드포인트 인증 부재 관찰(모듈에 `SecurityFilterChain` 없음). `/sign`은 임의 payload를 실 키로 RS256 서명할 수 있어 네트워크 격리 전제 여부 확인 필요.

## 13. 관련 문서와 rules

- 루트: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 연관 서비스: 발급/서명 소비 [`OAUTH2_AUTHORIZATION_SERVER.md`](OAUTH2_AUTHORIZATION_SERVER.md), 검증(JWKS) 소비 [`API_GATEWAY.md`](API_GATEWAY.md), [`OAUTH2_CLIENT.md`](OAUTH2_CLIENT.md)
- 모듈 작업 규칙: [`../../spring-cloud-config/CLAUDE.md`](../../spring-cloud-config/CLAUDE.md)
- rules: `../../.claude/rules/{security,external-contracts,architecture,testing}.md`
- 미해결 관찰: [`../../TODO.md`](../../TODO.md)
