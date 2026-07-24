# spring-cloud-config — 모듈 작업 지침

이 파일은 `spring-cloud-config/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·역할·계약·근거는 [`../docs/modules/SPRING_CLOUD_CONFIG.md`](../docs/modules/SPRING_CLOUD_CONFIG.md)를 참고한다.

이 모듈은 **인프라·보안 핵심**이다. 전체 서비스의 설정 소스이자 JWT 서명/JWKS 창구이므로, 설정 백엔드·서명·엔드포인트 변경은 대부분 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상(Secret/Security, 배포/CI, 여러 모듈 영향)이며 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

두 역할을 겸한다(헥사고날 멀티모듈, 실행 모듈 `spring-cloud-config-bootstrap`, 포트 `8888`).

1. **Config Server**(`@EnableConfigServer`): git(`git-config-repo`) + Vault(KV v2) 백엔드 설정 제공. 모든 실행 서비스가 여기서 설정을 로드한다.
2. **JWT 서명/JWKS 대행**(Vault Transit):
   - `GET /.well-known/jwks.json?keyName=` → 검증측(gateway, oauth2-client)
   - `POST /sign` → 발급측(oauth2-authorization-server `Rs256JwtEncoder`)

개인키는 Vault Transit에만 있고 이 서버는 서명 대행·공개키 노출 창구다. 자체 DB·gRPC 없음. `spring-cloud-config/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **kid 규칙 일치**: 서명 응답 kid(`SignResponse`, `keyName:keyVersion`)와 JWKS kid(`VaultTransitPublicKeyInfo.kid()`)는 **같은 형식**이어야 검증측이 키를 매칭한다. 한쪽만 바꾸면 JWT 검증이 전부 깨진다(`JwtSigningService` ↔ `JwksService`/`JwkSetFactory`를 함께 본다).
- **엔드포인트 경로는 외부 계약**: `/.well-known/jwks.json`·`/sign`은 `git-config-repo/dynamic/jwt.yml`의 `jwks-uri`·`sign-uri`가 가리킨다. 경로/쿼리(`keyName`)를 바꾸면 jwt.yml과 소비 서비스(gateway·oauth2-client·oauth2-authorization-server)를 함께 수정한다(→ `../.claude/rules/external-contracts.md`).
- **서명 파라미터 보존**: Vault 서명은 `prehashed=true`·`hash_algorithm=sha2-256`·`signature_algorithm=pkcs1v15`(`VaultSignRequest`), 서명 대상은 `SHA-256(headerB64u.payloadB64u)`. RS256 계약이므로 digest 방식·알고리즘을 임의로 바꾸지 않는다.
- **Config 백엔드 우선순위**: Vault(`order 1`) + git(`order 2`), git `search-paths`(루트/`dynamic`/`infrastructure`)·`default-label: main`. 순서/경로 변경은 전체 서비스 설정 병합에 영향 → 변경 전 영향 분석.
- **자기 설정은 로컬**: 이 서버의 `application.yml`은 `spring-cloud-config-bootstrap` 로컬 파일이다(자신이 Config Server라 config repo에서 받지 않는다). 다른 서비스처럼 config repo로 옮기지 않는다.
- **Secret 취급**: `${CONFIG_REPO_URI}`, `${VAULT_ROLE_ID}`, `${VAULT_SECRET_ID}`, `${DEPLOY_TOKEN}` 등은 플레이스홀더로만 둔다. 실제 값·Vault root token/secret을 응답·커밋에 노출하지 않는다(→ `../.claude/rules/git-safety.md`).
- **헥사고날 유지**: 서명/JWKS 로직은 application, Vault 접근은 adapter-out(`VaultTransit*` → `VaultTransit*Port`)로 둔다. 컨트롤러/서비스에서 `VaultTemplate`을 직접 호출하지 않는다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`...bootstrap/.../Main.java`](spring-cloud-config-bootstrap/src/main/java/org/example/configserver/Main.java) | `@EnableConfigServer` 진입점 |
| [`spring-cloud-config-bootstrap/src/main/resources/application.yml`](spring-cloud-config-bootstrap/src/main/resources/application.yml) | config 백엔드(git/vault)·api-path·vault.transit·bus |
| [`...adapter-in/.../jwks/adapter/in/JwksController.java`](spring-cloud-config-adapter-in/src/main/java/org/example/configserver/jwks/adapter/in/JwksController.java) | `GET jwks` / `POST sign` |
| [`...adapter-in/.../adapter/in/config/SecurityConfig.java`](spring-cloud-config-adapter-in/src/main/java/org/example/configserver/adapter/in/config/SecurityConfig.java) | RSA `KeyFactory` bean |
| [`...application/.../sign/JwtSigningService.java`](spring-cloud-config-application/src/main/java/org/example/configserver/sign/JwtSigningService.java) | digest→서명→변환, kid 생성 |
| [`...application/.../jwks/JwksService.java`](spring-cloud-config-application/src/main/java/org/example/configserver/jwks/JwksService.java) | 공개키 조회→JWKS 생성 |
| [`...adapter-out/.../vault/adapter/out/VaultTransitSigner.java`](spring-cloud-config-adapter-out/src/main/java/org/example/configserver/vault/adapter/out/VaultTransitSigner.java) | `transit/sign/{keyName}` 서명 |
| [`...adapter-out/.../vault/adapter/out/VaultTransitKeyReader.java`](spring-cloud-config-adapter-out/src/main/java/org/example/configserver/vault/adapter/out/VaultTransitKeyReader.java) | `transit/keys/{keyName}` 공개키 |
| `../git-config-repo/dynamic/jwt.yml` | jwks-uri/sign-uri/key-name/key-version(소비측 계약) |

## 검증 명령

- 컴파일: `./gradlew :spring-cloud-config:spring-cloud-config-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :spring-cloud-config:spring-cloud-config-adapter-out:test` 등
- 서비스 CI: `./gradlew springCloudConfigCi`

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/SPRING_CLOUD_CONFIG.md §12`](../docs/modules/SPRING_CLOUD_CONFIG.md)와 [`../TODO.md`](../TODO.md).

- 모듈에 `SecurityFilterChain`이 없어 `/sign`·JWKS 엔드포인트가 애플리케이션 인증 없이 열려 있음(`/sign`은 실 키로 임의 payload RS256 서명 가능). 네트워크 격리 전제 여부 확인.
