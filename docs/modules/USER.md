# USER — user 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-23
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - REST 경로(`git-config-repo/dynamic/user-service.yml`의 `api-path.user.*`) 또는 `UserController` 변경
>   - gRPC 계약(`protobuf/src/main/proto/user/v1/user-service.proto`) 변경
>   - 도메인 모델(`User`, `Role`, `RoleEnum`) 변경
>   - 스키마(`user/user-bootstrap/.../sql/schema.sql`) 변경
>   - 트랜잭션/데이터소스(`DataSourceConfig`, `UserQueryService`) 변경

## 1. 문서 목적과 기준 시점

이 문서는 `user` 서비스의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../user/CLAUDE.md`](../../user/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

사용자 계정의 소유 서비스. 로컬 회원가입, OAuth2 가입(find-or-create), 내 프로필/타인 프로필 조회, 닉네임 수정, 기본 권한(Role) 부여를 담당한다. 외부에는 **REST**(게이트웨이 경유)와 **gRPC**(`user.v1`, 내부 서비스용) 두 인터페이스를 노출한다. 인증(JWT 발급·검증)과 OAuth2 로그인 오케스트레이션은 이 서비스가 아니라 `oauth2-authorization-server`/`oauth2-client`/`spring-cloud-api-gateway`의 책임이다 — user는 그들이 gRPC로 호출하는 **계정 저장소**다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:user:*` (헥사고날 멀티모듈). 실행 모듈은 `:user:user-bootstrap`.
- 실행 클래스: `org.example.user.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`).
- app name: `user-service`. 포트: REST `8090`, gRPC `18090`(`git-config-repo/dynamic/user-service.yml`). 컨텍스트 경로 `/api/v1`(`server.servlet.context-path: /api/${server.version}`).
- 저장소: MySQL(`user` DB). 스키마는 `spring.sql.init`(`schema-locations: classpath:sql/schema.sql`, `mode: always`)로 초기화.
- Config Server 연동: `application.yml`의 `spring.config.import: configserver:...`, `spring.cloud.config.name: user-service,eureka-client,idgen,mysql,kafka,monitoring`. 공유 `api-contract.*`는 Config Repository 루트 `application.yml`에서 자동 병합된다.
- 부트스트랩 의존성: Config Client, Eureka Client, `spring-cloud-starter-bus-kafka`, Micrometer/Prometheus, `common-actuator-webmvc`.

## 4. 모듈 구조 (헥사고날)

두 서브도메인 `account`(핵심)와 `role`로 나뉜다.

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `user-domain` | domain | `User`, `Role`, `RoleEnum` (프레임워크 비의존) | `common-core` |
| `user-application` | application | UseCase/Service, Port(in/out), Command, 검증, 예외 | `user-domain`(api), `common-jpa`, `spring-security-crypto` |
| `user-adapter-in` | adapter-in | REST(`UserController`), gRPC(`GrpcUserService`), gRPC 예외 advice | `common-web`, `common-grpc`, `protobuf`, `user-application` |
| `user-adapter-out` | adapter-out | JPA 영속성(`JpaUser*`, `JpaRole*`), infra config(DataSource, PasswordEncoder) | `common-id`, `common-jpa`, `user-application`, `spring-security-crypto` |
| `user-bootstrap` | 실행 | `Main`, `application.yml`, `schema.sql`, messages | 위 4개 + config/eureka/bus/prometheus |
| `user-client` | 클라이언트 | 다른 서비스가 쓰는 gRPC 클라이언트(`UserClient`/`GrpcUserClient`) | `protobuf`, `common-core`, `user-contract` |
| `user-contract` | 계약 | gRPC 클라이언트 응답 DTO(`contract.user.UserResponse`, record) | (없음) |

의존 방향: adapter-in/out → application → domain. `user-client`/`user-contract`는 **소비자용 산출물**로, user 서비스 자신이 아니라 oauth2-client·oauth2-authorization-server가 의존한다.

## 5. 주요 클래스와 책임

| 클래스 | 경로(요약) | 책임 |
|---|---|---|
| `UserController` | `user-adapter-in/.../web/UserController.java` | REST 4개 엔드포인트(§6) |
| `GrpcUserService` | `user-adapter-in/.../grpc/GrpcUserService.java` | gRPC `FindByEmail`, `SignUpOauth2`(§7) |
| `GrpcUserMapper` | `user-adapter-in/.../grpc/GrpcUserMapper.java` | `User` → `GrpcUser` 변환(Timestamp 포함) |
| `UserCommandService` | `user-application/.../service/UserCommandService.java` | `signUpLocal`/`signUpOauth2`/`updateProfile` (write, `@Transactional`) |
| `UserQueryService` | `user-application/.../service/UserQueryService.java` | `findByPublicId`/`findByEmailWithRoles` (`@Transactional(readOnly=true)`) |
| `UserPersistencePort` | `user-application/.../port/out/UserPersistencePort.java` | 영속성 아웃바운드 포트 |
| `RolePersistencePort` | `user-application/.../role/.../port/out/RolePersistencePort.java` | Role 조회 포트 |
| `UniqueUserNicknameValidator` | `user-application/.../validation/` | 닉네임 중복 검증(`existsByNickname`) |
| `JpaUserAdapter` | `user-adapter-out/.../account/adapter/out/JpaUserAdapter.java` | `UserPersistencePort` 구현, 도메인↔JPA 매핑 |
| `JpaRoleAdapter` | `user-adapter-out/.../role/adapter/out/JpaRoleAdapter.java` | `RolePersistencePort` 구현 |
| `DataSourceConfig` | `user-adapter-out/.../infra/config/DataSourceConfig.java` | write/read Hikari + `ReplicationRoutingDataSource`(§10) |
| `PasswordEncoderConfig` | `user-adapter-out/.../infra/config/PasswordEncoderConfig.java` | `BCryptPasswordEncoder(5)` |
| `GrpcUserClient` | `user-client/.../GrpcUserClient.java` | 소비자용 gRPC 클라이언트(deadline 3500ms) |

## 6. REST API 계약

베이스 `/user`, 전체 경로는 컨텍스트 `/api/v1`가 붙는다. 경로 문자열은 `git-config-repo/dynamic/user-service.yml`의 `api-path.user.*`에서 주입(`@RequestMapping("${api-path.user.base}")` 등).

| 메서드 | 전체 경로 | 인증(게이트웨이) | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/v1/user/sign-up` | permitAll | `UserCreateRequest{email,nickname,password}` | **201 Created**(`Location: /`) |
| GET | `/api/v1/user/me/profile` | `hasRole(USER)` | 헤더 `X-User-Id`(publicId, UUID) | 200 `UserResponse` |
| GET | `/api/v1/user/{publicId}/profile` | `hasRole(USER)` | path `publicId` | 200 `UserResponse` |
| PATCH | `/api/v1/user/me/profile` | `hasRole(USER)` | 헤더 `X-User-Id` + `UserProfileUpdateRequest{nickname}` | 빈 body → 400, 아니면 204 No Content |

- `UserResponse`(REST, `adapter-in/.../web/dto/UserResponse`): `{ id=publicId(UUID), nickname, email, createdAt(Instant) }`. 내부 PK(`id`, Snowflake)는 노출하지 않고 `publicId`를 `id`로 내보낸다.
- `X-User-Id` 헤더는 게이트웨이가 검증된 JWT의 `id` claim에서 주입한다(`common-core/HttpHeaderKey.USER_ID_VALUE`). **UserController는 이 값을 그대로 신뢰**해 `publicId`로 사용하며 재검증하지 않는다.
- 게이트웨이 인가 근거: `spring-cloud-api-gateway/.../ReactiveSecurityConfig.java`. GET `me/profile`·`{publicId}/profile`만 `hasRole(USER)`, 그 외 `/user/**`는 `permitAll`.
- PATCH `/me/profile`는 게이트웨이 `ReactiveSecurityConfig`에서 `hasRole(USER)`를 요구한다. 애플리케이션에서도 `UserCommandService.updateProfile`가 `User.validateOwner(publicId)`로 소유자 인가를 명시 검증한다. `X-User-Id`는 게이트웨이가 입구에서 클라이언트 원본 헤더를 제거하고 검증된 JWT `id`로만 주입한다(`IdentityPropagationGlobalFilter`, 게이트웨이 경유 스푸핑 차단). 게이트웨이 **우회** 직접 접근 차단은 인프라 방어(서비스 포트 host-local 바인딩 등, infra `TODO.md` "보안 · 네트워크 노출").

검증 규칙(`UserCreateRequest`, `UserProfileUpdateRequest`):
- email: `@NotBlank` + `@Email`
- nickname: `@UniqueUserNickname` + 크기 2~20 + 패턴 `^[가-힣a-zA-Z0-9_]+$`. 회원가입은 `@NotBlank`, 프로필 수정은 `@NotBlankIfPresent`(부분 수정 허용)
- password: `@NotBlank` + 크기 8~60
- 메시지는 `user-bootstrap/.../messages.properties`(한국어).

## 7. gRPC 계약 (`user.v1`)

proto: `protobuf/src/main/proto/user/v1/user-service.proto`. 서버 구현 `GrpcUserService`(adapter-in). 소비자: `oauth2-client`, `oauth2-authorization-server`(`user-client`의 `UserClient` 경유).

| RPC | 요청 | 응답 | 용도 |
|---|---|---|---|
| `FindByEmail` | `GrpcFindByEmailRequest{email}` | `GrpcFindByEmailResponse{optional GrpcUser}` | 이메일로 사용자+권한 조회(없으면 user 미설정) |
| `SignUpOauth2` | `GrpcSignUpOauth2Request{sub,email,nickname}` | `GrpcSignUpOauth2Response{GrpcUser}` | OAuth2 가입(기본 role 부여). 로그인 시 find-or-create의 create 부분 |

- `GrpcUser`: `{ sub, nickname, email, roles[], created_at(Timestamp), id }`. `id`는 `publicId` 문자열.
- 클라이언트(`GrpcUserClient`) deadline `3500ms`. 예외는 `GlobalGrpcExceptionAdvice`(→ `common-grpc/BaseGrpcExceptionAdvice`).
- **계약 주의**: 이 proto는 외부 계약이다. field number 재사용 금지, 변경 시 server(user)·client(oauth2-*) 재빌드. 상세 절차는 루트 `.claude/rules/external-contracts.md`.

## 8. 도메인 모델

### `User` (`user-domain/.../account/domain/model/User.java`)
- 필드: `id`(내부 PK, Snowflake), `publicId`(UUID, 외부 식별자), `sub`(OAuth2 provider subject), `email`, `nickname`, `password`(로컬만), `roles: Set<Role>`, `createdAt`/`updatedAt`.
- 팩토리: `ofLocal(email,nickname,encodedPassword)`, `ofOAuth2(sub,email,nickname)`, `rehydrate(...)`(영속성 복원용).
- 행위: `addRole`, `updateNickname`, `hasRole`, `getRoleNames`, `toInstant`(`ServiceZoneUtils.ZONE_ID` 기준), 정적 `getDefaultRole()=USER`.
- `Permission` enum은 전체 주석 처리된 dead code다(현재 미사용).

### `Role` / `RoleEnum`
- `Role`: `{ id, RoleEnum name }`, 팩토리 `ofName`/`rehydrate`.
- `RoleEnum`: `USER`, `ADMIN`. `getName()`은 `RoleKey.PREFIX("ROLE_") + name` → `ROLE_USER`/`ROLE_ADMIN`.
- 회원가입 시 항상 기본 role `USER`를 부여(`UserCommandService.getDefaultRole()` → `RolePersistencePort.findByName(USER)`, 없으면 `RoleNotFoundException`).

## 9. 영속성 · 스키마

매핑: 도메인 ↔ JPA는 `JpaUser.fromDomain(user, roleResolver)` / `toDomain()`에서 수행. `roleResolver`는 도메인 `Role`을 DB의 `JpaRole`(name으로 조회)로 연결한다(`JpaUserAdapter.save`).

- `JpaUser`(`@Table("user")`): `id`(`@SnowflakeId`), `public_id`(UUID, unique, `updatable=false`), `sub`, `email`(not null), `nickname`, `password`, `roles`(`@OneToMany` → `JpaUserRole`, cascade ALL + orphanRemoval).
- `JpaRole`(`@Table("role")`): `id`(IDENTITY), `name`(`@Enumerated(STRING)`, unique).
- `JpaUserRole`(`@Table("user_role")`): `user`/`role` `@ManyToOne(LAZY)` 조인 엔티티.
- 조회 최적화: `findByEmailWithRoles`는 `left join fetch`로 roles·role을 함께 로드(N+1 회피).

스키마(`schema.sql`):
- `user`: PK `id`, unique `uk_user_public_id`, unique `uk_user_email`, unique `uk_user_nickname`.
- `role`: `name` unique. 초기 `INSERT ... ('USER')`로 기본 role 시드.
- `user_role`: unique `(user_id, role_id)`, FK 2개(`ON DELETE CASCADE`).

## 10. 트랜잭션 · 데이터소스 현황

- 쓰기: `UserCommandService`의 각 메서드 `@Transactional`.
- 읽기: `UserQueryService`의 각 메서드 `@Transactional(readOnly=true)`.
- 데이터소스는 단일이다: `DataSourceConfig`가 `spring.datasource.write` 하나의 Hikari(`@Primary`)만 만든다. Read Replica 라우팅(`ReplicationRoutingDataSource`/read 데이터소스)은 미사용이라 제거했다 — user 조회는 이 단일 데이터소스로 나간다.

## 11. 검증 · 예외

- 커스텀 제약 `@UniqueUserNickname` → `UniqueUserNicknameValidator`가 `UserPersistencePort.existsByNickname`로 중복 확인. null/blank는 통과(존재할 때만 검사).
- 예외: `UserNotFoundException`(publicId), `RoleNotFoundException`(RoleEnum) — 모두 `common/ResourceNotFoundException` 상속. REST 응답 형식은 모듈이 아니라 `common-web/GlobalExceptionHandler`가 관장. gRPC는 `GlobalGrpcExceptionAdvice`.

## 12. 설정 (Config Server: `user-service.yml`)

- 포트 REST `8090` / gRPC `18090`, 컨텍스트 `/api/v1`.
- `api-path.user`: `base:/user`, `sign-up:/sign-up`, `me:/me/profile`, `profile:/{publicId}/profile`.
- JPA: `open-in-view: false`, `defer-datasource-initialization: true`, `data.jpa.repositories.bootstrap-mode: deferred`.
- DB 자격: `mysql.username/password/db`는 `${mysql.user.*}` 플레이스홀더(Vault/Config).
- Password: `BCryptPasswordEncoder(strength=5)`.

## 13. 테스트 현황

테스트 파일(계층별로 존재):
- domain: `UserTest`
- application: `UserCommandServiceTest`, `UniqueUserNicknameValidatorTest`
- adapter-in: `UserControllerTest`, `UserControllerWebMvcTest`
- adapter-out: `JpaUserAdapterTest`

(개별 테스트 세부 내용은 이 문서 검증 범위 밖. 필요 시 파일을 직접 확인한다.)

## 14. 컴파일 · 테스트 · CI 명령

- 컴파일(가장 좁게): `./gradlew :user:user-application:compileJava` 등 서브모듈 단위.
- 서브모듈 테스트: `./gradlew :user:user-application:test`, `:user:user-adapter-in:test`, `:user:user-domain:test` …
- 서비스 CI(빌드+테스트+ArchUnit): `./gradlew userCi`(루트 `build.gradle:98`).
- 집계 task `:user:test`는 대체로 빈 task다. 실제 테스트는 서브모듈 또는 `userCi`로 실행한다.
- 전체 build/test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 15. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `protobuf/.../user/v1/user-service.proto` | gRPC 외부 계약. 변경 시 oauth2-* 재빌드 |
| `git-config-repo/dynamic/user-service.yml` | REST 경로·포트·DB. 게이트웨이 route/security와 함께 봐야 함 |
| `schema.sql` | DB 스키마·unique·시드 role. 마이그레이션 영향 |
| `User.java` / `RoleEnum.java` | 도메인 불변식·권한 문자열(`ROLE_*`) |
| `DataSourceConfig.java` | 라우팅 DataSource. read/write 분리 동작 |
| `UserResponse`(web/contract 2종) | REST/gRPC 응답 계약 |

## 16. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. user 관련 항목:

- **TODO 1.9** — BCrypt strength 5 (`PasswordEncoderConfig`)

## 17. 관련 문서와 rules

- 루트 구조/흐름: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), 코드 스타일 [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 게이트웨이(인가·헤더 전파): [`API_GATEWAY.md`](API_GATEWAY.md)
- 모듈 작업 규칙: [`../../user/CLAUDE.md`](../../user/CLAUDE.md)
- 계약/보안/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,security,architecture,testing}.md`
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
