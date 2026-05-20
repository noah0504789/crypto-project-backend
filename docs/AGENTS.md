# crypto-project Codex Instructions

이 문서는 Codex가 `crypto-project`에서 작업할 때 따라야 하는 프로젝트 전용 지침이다.

## 0. Project Scope

- 이 저장소는 여러 Java/Spring 기반 마이크로서비스를 포함한다.
- 최상위 경로 아래의 독립 Gradle 프로젝트는 각각 하나의 마이크로서비스로 본다.
- 각 서비스는 독립 Git 저장소일 수 있다.
- 작업 전에는 현재 서비스 디렉토리뿐 아니라 `crypto-project` 전체 구조를 먼저 확인한다.
- 공통 패턴을 바꿀 때는 다른 서비스에 같은 패턴이 있는지 검색한다.
- 큰 변경 전에는 바로 수정하지 말고 먼저 분석 결과와 계획을 제시한다.

## 1. Language

- 설명, 작업 요약, 분석 결과는 한국어로 작성한다.
- 코드 주석은 꼭 필요한 경우에만 작성한다.
- 불필요한 장황한 설명보다 변경 이유, 영향 범위, 검증 방법을 우선한다.

## 2. Java / Spring Baseline

- Java 17 기준으로 작성한다.
- Spring Boot 3.x 기준으로 작성한다.
- Gradle 기반 프로젝트로 본다.
- 기존 패키지 구조와 네이밍 스타일을 우선 유지한다.
- 기능 변경 없이 리팩토링할 때 public API는 요청 없이는 변경하지 않는다.

## 3. Architecture Baseline

권장 계층:

```text
adapter.in / api
application
domain
adapter.out / infra
common
```

의존 방향:

```text
adapter.in -> application -> domain
adapter.out -> application port + domain
domain -> 외부 기술 의존 최소화
```

현재 코드에 예외가 있더라도 새 변경은 이 방향으로 맞춘다. 대규모 패키지 이동은 별도 계획 없이 수행하지 않는다.

## 4. Service Map

- `chat`: 채팅방/메시지, Mongo/Redis, Outbox/DLQ, gRPC server
- `user`: 사용자/역할, JPA, MySQL read replica, gRPC server
- `outbox-poller`: Outbox/DLQ Kafka 발행
- `websocket-gateway`: STOMP/WebSocket, chat gRPC client, broadcast consumer
- `oauth2-authorization-server`: OAuth2 Authorization Server, token Redis store, token gRPC API
- `oauth2-client`: OAuth2 login client, refresh/logout
- `spring-cloud-api-gateway`: route, JWT validation, identity propagation
- `spring-cloud-config`: config server, Vault, JWKS/sign
- `spring-cloud-eureka-server`: service discovery
- `market-detection`: Upbit websocket, Kafka Streams, notification event
- `protobuf`: gRPC proto/stub
- `git-config-repo`: 중앙 설정
- `docker-compose`: local infra/service/monitoring

## 5. Test Style

- 테스트는 JUnit 5, Mockito, AssertJ를 사용한다.
- 테스트 메서드에는 한글 `@DisplayName`을 붙인다.
- 테스트는 given / when / then 구조를 사용한다.
- 단위테스트에서는 Spring Context를 띄우지 않는다.
- Repository, Publisher, Client, StreamBridge, 외부 시스템 의존성은 Mockito로 mock 처리한다.
- Entity의 도메인 메서드는 실제 객체로 테스트한다.
- 통합테스트가 필요한 경우에만 `@SpringBootTest`, `@DataJpaTest`, `@JdbcTest`, `@WebMvcTest`를 사용한다.
- 테스트 실패 시 root cause를 먼저 분석하고 최소 수정안을 제시한다.
- JUnit4 `org.junit.Assert` import는 사용하지 않는다.

## 6. Static Constants

- 반복되는 property prefix, path, header name, Kafka binding name, transaction manager name, Redis key prefix는 상수화를 검토한다.
- 단 한 번만 쓰이는 값은 과도하게 상수화하지 않는다.
- 외부 계약에 해당하는 문자열은 상수명을 명확하게 작성한다.
- 클래스 상단에 무분별하게 상수를 나열하지 않는다.
- 성격별로 Properties, enum, constants class, nested constants 중 적절한 위치를 제안한다.
- enum으로 표현 가능한 상태값, 타입값, 도메인 분류값은 enum 사용을 우선한다.
- properties key나 prefix는 설정 객체 또는 별도 constants로 모을 수 있는지 검토한다.
- path는 Controller 또는 API constants로 분리 가능한지 검토한다.

상수화 우선 후보:

- `transaction_id`
- `dlq_id`
- `__TypeId__`
- `X-User-Id`
- `chatMongoTransactionManager`
- `transactionManager`
- `/auth/refresh`
- `/queue/chat/ack`

## 7. Lombok / Constructor / Builder

- Entity의 기본 생성자는 protected로 제한한다.
- Lombok 사용 시 Entity는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 우선한다.
- 정적 팩토리 메서드를 사용하는 Entity는 public builder 노출을 피한다.
- Builder가 필요하면 `@Builder(access = AccessLevel.PRIVATE)`를 우선 검토한다.
- 무분별한 public `@AllArgsConstructor` 노출을 피한다.
- DTO, request, response는 record 사용 가능성을 우선 검토한다.
- Entity는 불변 객체처럼 만들 수 있는 부분과 JPA 요구사항 때문에 열어야 하는 부분을 구분한다.
- `@Data`는 사용하지 않는다.

## 8. Entity / Domain Rules

- JPA Entity의 `createdAt`, `updatedAt`은 `BaseEntity` 사용을 우선한다.
- `@CreationTimestamp`, `@UpdateTimestamp` 필드는 도메인 메서드에서 직접 수정하지 않는다.
- 상태 변경은 `markPublished`, `markFailed`, `increaseRetryCnt` 같은 도메인 메서드로 수행한다.
- 생성 정책은 정적 팩토리 메서드로 모은다.
- 관계 매핑은 EAGER를 기본값으로 두지 말고, 필요 시 fetch join 또는 `WithRoles` 쿼리를 검토한다.
- Mongo/Redis 중심 도메인 모델도 생성 정책과 이벤트 발생 정책을 도메인 메서드로 모은다.

## 9. Exception Handling

- Controller 계층 예외 응답은 `GlobalExceptionHandler`에서 처리한다.
- `GlobalExceptionHandler`가 없는 서비스는 후보로 표시한다.
- 이미 있는 `GlobalExceptionHandler`도 응답 형식, 상태 코드, 예외 매핑 일관성을 검수한다.
- gRPC 예외는 `@GrpcAdvice`에서 처리한다.
- 도메인/애플리케이션 예외는 의미 있는 커스텀 예외를 사용한다.
- 예외 메시지는 디버깅 가능한 식별자(id, publicId, roomId, messageId, keyName 등)를 포함한다.
- 외부 시스템 예외를 삼키는 경우 반드시 로그와 상태 전이를 남긴다.

## 10. Outbox / DLQ Rules

- Outbox / DLQ 서비스는 `@Transactional("transactionManager")` 설정을 임의로 제거하거나 변경하지 않는다.
- Outbox / DLQ Entity는 `BaseEntity`를 상속한다.
- Outbox 상태 변경은 `markPublished`, `markFailed`, `increaseRetryCnt` 같은 도메인 메서드로 수행한다.
- DLQ 상태 변경은 `markPublished`, `markPublishFailed` 같은 도메인 메서드로 수행한다.
- Publisher, StreamBridge, Repository는 단위테스트에서 mock 처리한다.
- EventPublisher 메시지 header name은 상수화 후보로 검토한다.
- Kafka topic/header 이름 변경은 외부 계약 변경으로 본다.

## 11. Read Replica Rules

- `@ReadReplica` 라우팅 구조는 `DataSourceContextHolder`, `ReadReplicaAspect`, `ReplicationRoutingDataSource` 흐름을 유지한다.
- `@Transactional(readOnly = true)`만으로 read replica에 라우팅되게 바꾸지 않는다.
- 라우팅 통합테스트에서는 Spring Cloud Config와 Eureka를 비활성화한다.
- Testcontainers reuse 설정은 `~/.testcontainers.properties`에 둔다.

## 12. Redis / Kafka / gRPC Rules

### Redis

- Redis key는 enum 기반 관리를 우선한다.
- key pattern의 placeholder 인자 수를 검증한다.
- cluster hash tag는 변경하지 않는다.
- 조회 실패는 fail-open 가능하지만 command 실패는 로그와 복구 이벤트를 검토한다.

### Kafka

- topic, destination, binding name은 enum/constants 후보이다.
- `transaction_id`, `dlq_id`, `__TypeId__`는 외부 계약 header이다.
- consumer header 조회 시 누락 가능성을 고려한다.
- DLQ consumer는 complete/fail 상태 전이를 남긴다.

### gRPC

- proto 변경은 외부 계약 변경이다.
- `protobuf` publish 영향 범위를 먼저 확인한다.
- deadline/cancel/persist/cache 실패를 구분한다.

## 13. Multi-module Direction

- 멀티모듈 전환은 DDD 기반으로 설계한다.
- 바로 파일을 이동하지 말고 먼저 모듈 후보와 의존 방향을 제시한다.
- `common`, `domain`, `application`, `infra`, `api` 책임을 구분한다.
- 순환 의존이 생기지 않도록 의존 방향을 먼저 확인한다.
- 한 번에 모든 서비스를 이동하지 않는다.
- 먼저 하나의 서비스 또는 공통 모듈 후보로 파일 이동 계획을 제시한다.
- 각 단계마다 build.gradle 변경, import 변경 범위, 실행할 테스트, rollback 방법을 제시한다.

## 14. Workflow

- 분석 요청을 받으면 바로 코드를 수정하지 않는다.
- 먼저 발견한 패턴, 문제점, 추천 컨벤션, 적용 우선순위, 위험 항목을 정리한다.
- 큰 작업은 작은 PR 단위로 쪼갠다.
- 수정 후에는 변경 요약, 실행한 테스트, 남은 TODO를 정리한다.
- 테스트 명령은 가능한 한 서비스 단위로 제안한다.
- 독립 Git 저장소가 여러 개이므로 수정 전 대상 서비스의 git 상태를 확인한다.
- 사용자가 만든 변경은 되돌리지 않는다.

## 15. Documentation

상세 문서는 다음 파일을 기준으로 한다.

- `docs/ARCHITECTURE.md`: 서비스 구조와 아키텍처
- `docs/CODE_STYLE.md`: 코드 컨벤션
- `docs/CODEX_WORKFLOW.md`: Codex 작업 절차
