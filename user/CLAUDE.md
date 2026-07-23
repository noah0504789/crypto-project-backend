# user — 모듈 작업 지침

이 파일은 `user/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/USER.md`](../docs/modules/USER.md)를 참고한다.

## 모듈 역할과 적용 범위

사용자 계정의 소유 서비스(헥사고날 멀티모듈, 실행 모듈 `user-bootstrap`). 담당:

1. 로컬 회원가입(`POST /user/sign-up`, BCrypt)
2. OAuth2 가입(gRPC `SignUpOauth2`, find-or-create의 create)
3. 프로필 조회(내/타인)·닉네임 수정(REST)
4. 이메일→사용자+권한 조회(gRPC `FindByEmail`)
5. 기본 권한(`ROLE_USER`) 부여

인증·JWT 발급/검증·OAuth2 로그인 오케스트레이션은 이 모듈이 아니다(`oauth2-*`, `spring-cloud-api-gateway`). user는 그들이 gRPC로 부르는 계정 저장소다. `user/`에 코드 변경이 없는 작업에는 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **의존 방향 유지**: adapter-in/out → application → domain. `user-domain`은 프레임워크 비의존(코어만). domain 객체가 Repository/gRPC를 직접 호출하지 않는다. 상세는 `../.claude/rules/architecture.md`.
- **Command/Query 분리 유지**: `UserCommandService`(write, `@Transactional`) / `UserQueryService`(read, `@Transactional(readOnly=true)`). UseCase 인터페이스(`UserCommandUseCase`/`UserQueryUseCase`)를 통해서만 어댑터가 호출한다.
- **Port & Adapter**: 영속성은 `UserPersistencePort`/`RolePersistencePort`(application) ↔ `JpaUserAdapter`/`JpaRoleAdapter`(adapter-out)로만. 서비스에서 JPA Repository를 직접 주입하지 않는다.
- **도메인 상태 변경은 도메인 메서드로**: 닉네임은 `User.updateNickname`, 권한은 `User.addRole`. 엔티티 setter/public builder를 열지 않는다(현재 builder는 `PRIVATE`).
- **식별자 구분**: 내부 PK `id`(Snowflake)는 외부로 노출하지 않는다. 외부 식별자는 `publicId`(UUID)다. REST/gRPC 응답의 `id`는 `publicId`를 담는다 — 이 규칙을 깨지 않는다.
- **권한 문자열은 계약**: `RoleEnum.getName()`이 만드는 `ROLE_USER`/`ROLE_ADMIN`은 게이트웨이 `hasRole` 및 JWT `roles` claim과 맞물린 외부 계약이다(`RoleKey.PREFIX`). 임의 변경 금지(→ `../.claude/rules/external-contracts.md`).
- **gRPC 계약(`user.v1`) 변경은 external-contracts 절차**: `protobuf/.../user/v1/user-service.proto`를 바꾸면 소비자(oauth2-client, oauth2-authorization-server)를 함께 재빌드하고 field number 재사용을 금지한다. proto 재생성: `./gradlew :protobuf:build`.
- **REST 경로·포트·DB 설정은 원격 Config**: `git-config-repo/dynamic/user-service.yml`(`api-path.user.*`, 포트, `mysql.*`). 경로를 바꾸면 게이트웨이 route/security(`spring-cloud-api-gateway`)와 함께 검토한다.
- **스키마 변경은 계약**: `user-bootstrap/.../sql/schema.sql`의 unique(`uk_user_public_id`, `uk_user_email`), FK, 기본 role 시드(`INSERT ... 'USER'`)를 영향 분석 없이 바꾸지 않는다. `nickname`에는 현재 DB unique가 없다(§확인 필요).
- **X-User-Id 신뢰**: `UserController`는 게이트웨이가 넣는 `X-User-Id`(`HttpHeaderKey.USER_ID_VALUE`)를 `publicId`로 신뢰한다. 이 헤더 소비/신뢰 방식을 바꿀 때는 게이트웨이의 헤더 주입·외부 헤더 제거 여부와 함께 본다(미해결 항목, `../docs/modules/API_GATEWAY.md §18.1`).
- 보안 관련(비밀번호 인코딩, 권한, 헤더 신뢰) 변경은 `../.claude/rules/security.md`도 함께 적용한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`user-adapter-in/.../web/UserController.java`](user-adapter-in/src/main/java/org/example/user/account/adapter/in/web/UserController.java) | REST 4 엔드포인트(sign-up, me/profile GET·PATCH, {publicId}/profile) |
| [`user-adapter-in/.../grpc/GrpcUserService.java`](user-adapter-in/src/main/java/org/example/user/account/adapter/in/grpc/GrpcUserService.java) | gRPC `FindByEmail`, `SignUpOauth2` |
| [`user-application/.../service/UserCommandService.java`](user-application/src/main/java/org/example/user/account/application/service/UserCommandService.java) | 회원가입/프로필 수정(write) |
| [`user-application/.../service/UserQueryService.java`](user-application/src/main/java/org/example/user/account/application/service/UserQueryService.java) | 조회(read-only) |
| [`user-domain/.../account/domain/model/User.java`](user-domain/src/main/java/org/example/user/account/domain/model/User.java) | User 도메인(팩토리·불변식·권한) |
| [`user-domain/.../role/domain/model/RoleEnum.java`](user-domain/src/main/java/org/example/user/role/domain/model/RoleEnum.java) | 권한 enum + `ROLE_*` 문자열 |
| [`user-adapter-out/.../account/adapter/out/JpaUserAdapter.java`](user-adapter-out/src/main/java/org/example/user/account/adapter/out/JpaUserAdapter.java) | 영속성 포트 구현, 도메인↔JPA 매핑 |
| [`user-adapter-out/.../infra/config/DataSourceConfig.java`](user-adapter-out/src/main/java/org/example/user/infra/config/DataSourceConfig.java) | write/read 라우팅 DataSource |
| [`user-client/.../GrpcUserClient.java`](user-client/src/main/java/org/example/user/client/GrpcUserClient.java) | 소비자용 gRPC 클라이언트(deadline 3500ms) |
| `../git-config-repo/dynamic/user-service.yml` | REST 경로·포트·DB·JPA 설정(Config Server 원격) |
| `../protobuf/src/main/proto/user/v1/user-service.proto` | gRPC `user.v1` 계약 |

## 검증 명령

- 컴파일: `./gradlew :user:user-application:compileJava`(대상 서브모듈 단위)
- 서브모듈 테스트: `./gradlew :user:user-application:test`, `:user:user-adapter-in:test`, `:user:user-domain:test` 등
- 서비스 CI: `./gradlew userCi`(빌드+테스트+ArchUnit 포함)

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

아래는 확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/USER.md §16`](../docs/modules/USER.md)와 [`../TODO.md`](../TODO.md).

- user에 `@ReadReplica` 미적용 → 조회도 write 노드로 라우팅(라우팅 인프라만 존재)
- `nickname` DB unique 부재(애플리케이션 검증에만 의존, 동시성 중복 여지)
- `BCryptPasswordEncoder` strength 5(기본 10보다 낮음)
- PATCH `/me/profile`의 게이트웨이 인가 범위와 `X-User-Id` 신뢰 방식
