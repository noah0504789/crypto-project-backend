# AGENTS.md

이 문서는 Codex/AI 코딩 에이전트가 `crypto-project-backend`에서 작업할 때 반드시 따라야 하는 프로젝트 전용 운영 지침이다.

`docs/ARCHITECTURE.md`, `docs/CODE_STYLE.md`, `docs/CODEX_WORKFLOW.md`가 상세 기준이고, 이 파일은 작업 전 가장 먼저 읽는 요약 규칙이다.

## 1. 프로젝트 인식

이 저장소는 Java 17, Spring Boot 3.4.x, Spring Cloud 2024.x 기반의 멀티 모듈 마이크로서비스 프로젝트다.

최상위 `settings.gradle` 기준으로 다음 모듈들이 하나의 Gradle composite/multi-project 안에 포함되어 있다.

```text
common
protobuf
chat
websocket-gateway
user
oauth2-client
oauth2-authorization-server
spring-cloud-api-gateway
spring-cloud-config
spring-cloud-eureka-server
outbox-poller
market-detection
```

`git-config-repo`는 Spring Cloud Config가 읽는 설정 저장소이고, 애플리케이션 코드 모듈은 아니다.

작업 전에는 반드시 다음을 확인한다.

```bash
pwd
./gradlew projects
find . -maxdepth 2 -name build.gradle -o -name settings.gradle
```

특정 서비스만 수정해도 다른 서비스가 같은 공통 모듈이나 계약을 공유할 수 있다. 문자열, DTO, proto, Kafka event, Redis key, properties는 전체 검색 후 판단한다.

## 2. 응답 언어와 설명 방식

- 사용자에게 설명할 때는 한국어를 사용한다.
- 코드 주석은 꼭 필요한 경우에만 쓴다.
- 장황한 배경 설명보다 `원인 -> 수정 -> 영향 범위 -> 검증 명령`을 우선한다.
- 확실하지 않은 내용은 추측하지 말고 “확인 필요”라고 표시한다.
- 사용자가 이미 만든 변경은 임의로 되돌리지 않는다.
- 큰 변경은 바로 적용하지 말고 먼저 계획을 제시한다.

## 3. 수정 전 안전 규칙

수정 전 확인한다.

```bash
git status --short
```

하위 디렉토리가 별도 Git 저장소처럼 관리될 가능성이 있으면 해당 경로도 확인한다.

```bash
git -C chat status --short
git -C user status --short
git -C oauth2-client status --short
git -C oauth2-authorization-server status --short
git -C websocket-gateway status --short
```

다음 파일은 절대 노출하거나 커밋하지 않는다.

- Vault root token, unseal key, AppRole secret id
- `.env`, 실제 `client-secret`, DB password
- TLS private key, `.jks`, `.p12`, `.pem`, `.key`
- 개인 OAuth provider secret
- 운영 로그, build 산출물, `.idea`, `.gradle`

## 4. 계층과 의존 방향

기본 계층은 다음을 따른다.

```text
adapter-in / api
  -> application
      -> domain
  -> adapter-out / infra
common / contract / client
```

원칙:

- inbound adapter: REST Controller, gRPC Service, STOMP Controller, Security Handler
- application: UseCase, CommandService, QueryService, transaction boundary, orchestration
- domain: Entity, Aggregate, Value Object, Domain Event, enum, policy
- outbound adapter: JPA/Mongo/Redis/Kafka/Vault/gRPC client 구현체
- contract/client: 외부 서비스가 의존하는 DTO, event, gRPC wrapper

새 코드는 기존 서비스의 패키지 구조를 우선 따르되, 책임을 섞지 않는다.

## 5. 모듈별 역할 요약

| 모듈 | 역할 |
| --- | --- |
| `common:common-core` | 공통 예외, properties, validation |
| `common:common-jpa` | BaseEntity, read replica routing, datasource context |
| `common:common-event` | event marker/interface, notification event |
| `common:common-web` | REST exception handler, message converter |
| `common:common-grpc` | gRPC exception advice |
| `common:common-id` | Snowflake ID provider/generator |
| `common:common-outbox` | Outbox/DLQ entity, listener, service, poller properties |
| `common:common-redis` | Redis codec, fail-open aspect, hash operation helper |
| `common:common-redisson` | distributed lock |
| `common:common-mongo` | Mongo converter/naming strategy |
| `common:common-test` | Testcontainers와 테스트 공통 설정 |
| `protobuf` | gRPC proto/stub 생성 |
| `chat` | 채팅방/메시지, Mongo/Redis, gRPC, Outbox/DLQ |
| `websocket-gateway` | STOMP gateway, chat gRPC client, Kafka broadcast consumer |
| `user` | 사용자/역할, MySQL, read replica, gRPC user API |
| `oauth2-client` | 외부 OAuth login, internal auth server 연동, refresh/logout |
| `oauth2-authorization-server` | internal OAuth2 Authorization Server, JWT/refresh/access token store |
| `spring-cloud-api-gateway` | 외부 HTTPS entrypoint, JWT resource server, routing |
| `spring-cloud-config` | Config Server, Vault, JWKS/sign endpoint |
| `outbox-poller` | Outbox/DLQ Kafka 발행 worker |
| `market-detection` | Upbit websocket 수집, Kafka Streams, notification event |

## 6. 외부 계약 변경 규칙

다음은 외부 계약으로 간주한다. 수정 전 영향 범위와 마이그레이션 계획을 먼저 제시한다.

- `protobuf/src/main/proto/**/*.proto`
- Kafka topic, binding name, destination name
- Kafka headers: `transaction_id`, `dlq_id`, `__TypeId__`
- Redis key pattern과 hash tag: 예) `{auth}:...`, `{chat}:...`
- HTTP path, Gateway route, CORS, cookie name/path/domain
- JWT issuer, audience, claim key: `roles`, `id`, `sub`, `jti`
- gRPC service/method 이름과 deadline 정책
- DB schema, index, unique constraint

## 7. 테스트 원칙

- JUnit 5, Mockito, AssertJ를 기본으로 한다.
- 테스트 메서드는 한글 `@DisplayName`을 붙인다.
- given/when/then 구조를 유지한다.
- 단위 테스트에서는 Spring Context를 띄우지 않는다.
- 외부 시스템, Repository, StreamBridge, gRPC client는 mock 처리한다.
- 도메인 메서드와 entity 상태 전이는 실제 객체로 검증한다.
- 통합 테스트는 필요한 경우에만 `@SpringBootTest`, `@DataJpaTest`, `@JdbcTest`, `@WebFluxTest`, `@WebMvcTest`를 사용한다.
- AssertJ에서 `getAttribute()` 같은 제네릭 메서드는 타입 힌트를 명시한다.

예:

```java
assertThat(result.<String>getAttribute("email")).isEqualTo(email);
```

## 8. 핵심 패턴별 보존 규칙

### 8.1 Read replica

- `@ReadReplica`는 명시적 read replica 라우팅 지시다.
- `@Transactional(readOnly = true)`만으로 read로 보내지 않는다.
- 이미 write transaction이 열려 있으면 내부 `@ReadReplica` 호출도 write 우선이다.
- 관련 클래스: `DataSourceContextHolder`, `ReadReplicaAspect`, `ReplicationRoutingDataSource`.

### 8.2 Outbox/DLQ

- 도메인 메서드는 외부 시스템을 직접 호출하지 않고 이벤트를 남긴다.
- Outbox/DLQ 상태 변경은 entity 도메인 메서드로 수행한다.
- `transaction_id`, `dlq_id` header는 외부 계약이다.
- 발행 실패를 삼키지 말고 retry 상태 또는 DLQ 전이를 남긴다.

### 8.3 Redis

- Redis key는 enum 또는 key factory로 관리한다.
- cluster hash tag를 임의 변경하지 않는다.
- read cache 실패는 fail-open 가능하지만 command/write 실패는 복구 이벤트 또는 invalidate를 검토한다.
- key pattern 인자 수 검증 테스트를 우선 작성한다.

### 8.4 OAuth2/JWT

- internal access token claim 변경은 gateway, websocket, downstream service까지 영향이 간다.
- `CustomOidcUser#getName()`은 Spring Security principal name이다. 도메인 userId와 혼동하지 않는다.
- OAuth2AuthorizedClient 저장/삭제 기준과 로그아웃 기준은 반드시 일치시킨다.
- refresh token cookie path/domain/secure/httpOnly 변경은 프론트 E2E에 영향이 있다.

### 8.5 gRPC

- proto 변경은 publish와 모든 client/server 재생성이 필요하다.
- deadline exceeded, cancelled, internal error를 분리해서 본다.
- gRPC 예외 응답은 REST `GlobalExceptionHandler`가 아니라 `@GrpcAdvice`에서 처리한다.

### 8.6 WebSocket/STOMP

- STOMP destination, user queue, ack/broadcast payload는 프론트와 k6 테스트가 의존한다.
- `/msg/chat.send`, `/user/queue/chat/ack`, `/topic/chat/{roomId}` 같은 destination 변경은 외부 계약 변경이다.

## 9. 코드 스타일 핵심

- Entity 기본 생성자는 `protected`.
- Entity public setter, public all-args constructor, public builder는 피한다.
- DTO/request/response는 record 우선.
- `@Data`는 사용하지 않는다.
- 상태 변경은 `markPublished`, `markFailed`, `increaseRetryCnt` 같은 도메인 메서드로 표현한다.
- 단 한 번 쓰이는 지역 literal은 억지로 상수화하지 않는다.
- 외부 계약 문자열은 상수화한다.

## 10. 작업 완료 보고 형식

수정 후 응답에는 다음을 포함한다.

```text
변경 요약
- ...

검증
- 실행: ./gradlew :module:test
- 미실행: 사유

영향 범위
- ...

후속 TODO
- ...
```

테스트를 실행하지 못했으면 성공했다고 말하지 않는다.
