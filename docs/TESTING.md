# TESTING — 테스트 구조 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 일자**: 2026-08-01
> - **검증 기준**: 실제 테스트 코드(`**/src/test`), `common-test`, `build-logic`, Config Repository(`git-config-repo/`)

이 문서는 이 저장소의 테스트를 **어떤 층으로 나누고, 각 층을 어떤 스타일로 작성하며, 인프라(Testcontainers)와 부팅 검증을 어떤 공통 장치로 반복 없이 구성하는지**를 정리한다. 짧은 실행/작성 규칙은 [`../.claude/rules/testing.md`](../.claude/rules/testing.md)에 있고, 여기서는 구조와 근거를 다룬다. 문서와 코드가 어긋나면 코드가 기준이다.

## 1. 테스트 층 (4계층)

| 층 | 목적 | Spring Context | 외부 인프라 | 대표 위치 |
|---|---|---|---|---|
| **단위(Unit)** | 도메인·정책·매퍼·서비스 로직 | 안 띄움 | 없음(전부 mock) | `*-domain`, `*-application`(예: `ChatMessageSendServiceTest`, `MyChatRoomScoreCalculator` 테스트) |
| **통합(Integration, sliced)** | 특정 어댑터/슬라이스를 실제 인프라와 | `@SpringBootTest(classes = {...})` 로 **빈 선별** 또는 slice | Testcontainers(해당 것만) | `*-adapter-out`(JPA/Mongo/Redis 어댑터), `RedisSessionLocationAdapterTest` 등 |
| **E2E(엔드포인트)** | 컨트롤러~보안~변환까지 요청 흐름 | `@SpringBootTest` + `@AutoConfigureMockMvc`/`WebTestClient`, 외부 의존은 mock | 대개 없음(mock) | gateway `ReactiveSecurityE2ETest`, oauth2-client `AuthLogoutE2ETest` |
| **부팅 스모크(Boot Smoke)** | 실행 서비스의 **전체 ApplicationContext**가 실제 설정으로 끝까지 뜨는지 | `@SpringBootTest`(진짜 `Main`) | Testcontainers + 실제 `git-config-repo` 설정 | 각 실행 모듈 `BootSmokeTest`(13개) |

핵심 구분:
- **통합/E2E는 `@SpringBootTest(classes = {...})`로 필요한 빈만 올린다** — 전체 컴포넌트 스캔/자동설정을 타지 않으므로 빠르지만, 자동설정·컴포넌트 스캔·`@Conditional`·빈 와이어링 오류는 **잡지 못한다**.
- **부팅 스모크만 진짜 `Main`을 올린다** — 그래서 위 오류(예: DB 없는 서비스의 `DataSourceAutoConfiguration`, 스캔된 `OutboxService`/`DlqService`/`SnowflakeIdProvider`)를 CI가 잡는 유일한 층이다.
- `@Validated @ConfigurationProperties` 바인딩과 필수 `${key}` placeholder 해석도 ApplicationContext 기동 시 수행된다. 제약 위반은 ApplicationContext 생성을 중단하므로 설정 누락·오타를 fail-fast로 검출한다. 컴파일·단위 테스트 성공만으로 설정 키 존재를 확인할 수 없으므로, 설정 변경은 관련 서비스의 부팅 스모크까지 통과해야 완료다.

## 2. 작성 스타일 (공통)

- JUnit 5 + Mockito + AssertJ. `org.junit.Assert` 금지, AssertJ 사용.
- 테스트 메서드에 **한글 `@DisplayName`**, `given/when/then` 구조.
- **단위 테스트는 Spring Context를 띄우지 않는다.** 외부 시스템·Repository·`StreamBridge`·gRPC Client는 mock.
- 도메인 상태 변경은 실제 도메인 객체로 검증한다.
- 실패한 테스트를 통과시키려 Assertion을 약화하지 않는다(→ `../.claude/rules/git-safety.md`).
- 통합 테스트의 인프라는 **직접 컨테이너를 띄우지 않고** `common-test`의 재사용 이니셜라이저를 쓴다(§3).

## 2.1 클래스 네이밍 컨벤션

테스트 층을 **클래스 이름 접미사**로 드러낸다. 이름만으로 어떤 층인지, 무엇을 필요로 하는지(인프라/컨텍스트) 알 수 있게 한다.

| 층 | 접미사 | 판별 기준 |
|---|---|---|
| 단위 | `XxxUnitTest` | Spring Context·컨테이너 없음. Mockito(`@ExtendWith(MockitoExtension.class)`)로 협력자 mock |
| 통합 | `XxxIntegrationTest` | 실제 인프라(Testcontainers)·slice(`@DataJpaTest` 등)·부분 `@SpringBootTest`로 어댑터/리포지토리를 실제로 검증 |
| E2E | `XxxE2ETest` | `MockMvc`/`WebTestClient`로 컨트롤러~보안~변환까지 엔드포인트 흐름 |
| 부팅 스모크 | `BootSmokeTest`(모듈당 1개) | 진짜 `Main` 전체 컨텍스트 부팅(§4) |

규칙:
- **이름이 층을 말한다**: `*AdapterTest`처럼 층이 모호한 접미사 대신 위 4종만 쓴다. 예: mock 기반 어댑터 테스트는 `...AdapterUnitTest`, Testcontainer 기반은 `...AdapterIntegrationTest`.
- **테스트 헬퍼는 접미사 대상이 아니다**: `Test*Config`·`Test*DependencyConfig`(테스트 전용 빈/설정)는 테스트 클래스가 아니므로 이름을 바꾸지 않는다.
- **ArchUnit**(`ModuleArchitectureTest`/`PackageArchitectureTest`)는 구조 검증 특수 케이스로 현 이름을 유지한다.

> 저장소 전체가 이 컨벤션을 따른다(테스트 클래스명 정합화 완료). 새 테스트도 이 접미사 규칙으로 작성한다.

## 3. Testcontainers 인프라 (`common-test`)

모든 컨테이너는 `common-test`에 **재사용 가능한 컴포넌트**로 정의되어 있어, 각 테스트는 컨테이너 기동/프로퍼티 주입을 다시 작성하지 않는다. `@Reuse(true)`로 실행 간 컨테이너를 재활용한다.

| 컴포넌트 | 종류 | 하는 일 |
|---|---|---|
| `ReadWriteMysqlTestContainerInitializer` | `ApplicationContextInitializer` | MySQL write/read 2개 기동 → `spring.datasource.write/read.*` 주입, `spring.test.database.replace=none` |
| `MongoDBTestContainerInitializer` | `ApplicationContextInitializer` | Mongo(단일 노드 replica set) 기동 → `spring.data.mongodb.uri` 주입 |
| `RedisTestContainerInitializer` | `ApplicationContextInitializer` | 단독 Redis 기동 → `spring.data.redis.host/port` 주입 |
| `KafkaTestContainerInitializer` | `ApplicationContextInitializer` | Kafka 기동 → `spring.cloud.stream.kafka.binder.brokers` 등 주입 |
| `KafkaTestContainerExtension` | `BeforeAllCallback` | Kafka 기동 + 브로커 시스템 프로퍼티(레거시 경로) |
| `SmokeConfigRepo` | 정적 유틸 | 저장소의 `git-config-repo` 절대경로를 찾아 노출(§4) |

- **주입 방식**: 이니셜라이저는 `TestPropertyValues`로 컨테이너 접속 정보를 Environment에 넣는다. 테스트는 `@ContextConfiguration(initializers = {...})`로 필요한 것만 조합한다.
- **왜 이니셜라이저인가**: `@DynamicPropertySource`를 테스트마다 쓰지 않고, 컨테이너 1개 = 이니셜라이저 1개로 만들어 **서비스마다 조합만** 하면 되도록 반복을 제거했다.

## 4. 부팅 스모크 하니스 (반복 제거 설계)

부팅 스모크는 "Config Server 없이 **실제** `git-config-repo` 설정으로 진짜 `Main`을 띄운다"는 어려운 부분을 공통 장치로 해결한다.

### 4.1 구성 요소

1. **`build-logic/crypto-bootstrap.gradle`** — 모든 실행 모듈의 test JVM에 시스템 프로퍼티 주입:
   ```groovy
   systemProperty 'smoke.config.repo', "${rootProject.projectDir}/git-config-repo"
   ```
   JVM 시작 시점에 세팅되므로 `spring.config.import`의 `${smoke.config.repo}` placeholder가 확실히 해석된다.
2. **`src/test/resources/application.yml`(서비스별)** — main의 `application.yml`(Config Server import)을 **test classpath에서 덮어써(shadow)**, `configserver:` 대신 실제 config-repo 파일을 직접 import:
   ```yaml
   spring:
     config:
       import:
         - optional:file:${smoke.config.repo}/application.yml
         - optional:file:${smoke.config.repo}/infrastructure/<...>.yml
         - optional:file:${smoke.config.repo}/dynamic/<service>.yml
     cloud:
       config: { enabled: false }
       bus: { enabled: false }
   ```
   import 목록은 그 서비스의 `spring.cloud.config.name`과 **동일한 프로파일 집합**이라, 실제 배포와 같은 설정 그래프로 부팅한다(예: idgen 유무에 따른 `@Conditional`이 실제와 동일하게 평가됨).
3. **Testcontainers 이니셜라이저**(§3) — config-repo가 가리키는 실제 인프라 호스트(`mysql-master`, `kafka-0`, …)를 컨테이너 접속 정보로 덮어쓴다.
4. **`BootSmokeTest`** — `@SpringBootTest(webEnvironment = RANDOM_PORT|NONE)` + 필요한 이니셜라이저 조합. 본문은 빈 `contextLoads()`.

### 4.2 우선순위 규칙(중요)

`spring.config.import`로 가져온 설정은 **가져온 쪽(test `application.yml`)보다 우선**한다. 그래서 imported 값을 이겨야 하는 override는 **`@SpringBootTest(properties = ...)`**(최상위 우선순위)에 둔다. 대표 예:
- `spring.jpa.hibernate.ddl-auto=create-drop` / `spring.sql.init.mode=never` — schema.sql/DB 이름 대신 Hibernate가 컨테이너에 스키마 생성.
- `grpc.server.port=0` — imported 서비스 설정의 고정 gRPC 포트를 랜덤 포트로.
- `mongo.db=test` — MongoConfig가 읽는 db 이름을 컨테이너 기본 db로.

### 4.3 서비스별 특이점(왜 그렇게 했나)

| 서비스 | 인프라 | 특이 처리 |
|---|---|---|
| websocket-gateway | kafka | DB 없음. redis 클러스터 설정은 `validate:false`라 lazy → 컨테이너 불필요 |
| market-detection | kafka(streams) | `ApplicationReadyEvent`의 Upbit WebSocket 접속 스타터를 `@MockitoBean`으로 차단 |
| user / market | mysql R/W, kafka | Hibernate `ddl-auto=create-drop`로 스키마 생성. market은 idgen 포함(Snowflake) |
| notification | mysql R/W, mongo, kafka | `mongo.db=test`로 db 이름 대체 |
| chat | mysql R/W, mongo, kafka | eager 클러스터 접속하는 `RedissonClient`를 `@MockitoBean`. redisson은 앱 런타임에만 있어 `testCompileOnly`로 타입 참조(lettuce 제외 전파 방지) |
| outbox-poller | mysql R/W, kafka | DB 릴레이. 스키마는 Hibernate 생성 |
| oauth2-authorization-server | kafka | DB 없음. redis·Vault 위임 서명 lazy. RegisteredClient용 시크릿은 더미 프로퍼티 |
| oauth2-client | 없음 | 외부 OIDC 디스커버리를 피하려 provider `issuer-uri`를 비우고 명시 엔드포인트 사용. bus off로 kafka 불요 |
| spring-cloud-api-gateway | 없음 | WebFlux. 기존 E2E test `application.yml`(Config Server 대체) 재사용, JWKS lazy |
| spring-cloud-eureka-server | 없음 | 외부 의존 없이 부팅 |
| spring-cloud-config | 없음 | 실제 백엔드(git+Vault) 대신 `native` 백엔드로 로컬 config-repo 서빙 + `VaultTemplate`은 `@MockitoBean` |

### 4.4 반복을 줄인 지점 요약

- **컨테이너·경로 탐색·config 로딩을 공통화**: 이니셜라이저(§3) + `SmokeConfigRepo` + Gradle 시스템 프로퍼티로, 서비스별 테스트는 "무엇을 조합할지"만 선언한다.
- **설정 중복 금지**: 스모크는 별도 설정을 복제하지 않고 **실제 `git-config-repo`를 그대로 import**한다 → 설정-코드 불일치까지 검출, 유지보수 이중화 없음.
- **override는 최소·명시적**: 컨테이너로 대체 못 하는 부분(외부 접속·시크릿·고정 포트)만 `properties`/`@MockitoBean`으로 좁게 처리한다.

## 5. 실행

- 서비스 부팅 스모크(예): `./gradlew :websocket-gateway:websocket-gateway-bootstrap:test --tests '*BootSmokeTest*'`
- 서비스 CI(부팅 스모크 포함): `./gradlew <service>Ci` (예: `websocketGatewayCi`)
- 전체: `./gradlew serviceCi` — Testcontainers를 다수 기동하므로 시간이 오래 걸린다. 요청·승인 없이 상시 실행하지 않는다.
- **`serviceCi` 동시 실행 금지**: 여러 실행이 동일한 reusable Kafka/MySQL/Mongo와 Docker 자원을 함께 초기화·종료하면 Gradle test worker가 실패 로그 없이 대기하는 경합이 생길 수 있다. 실행 전 기존 `GradleWrapperMain serviceCi` 프로세스를 확인하고 반드시 한 번에 하나만 실행한다.
- Docker가 필요하다(Testcontainers). 컨테이너는 `@Reuse(true)`로 재사용된다.

### 5.1 재사용 컨테이너의 수명

이 저장소는 `common-test` 컨테이너의 `.withReuse(true)`와 `testcontainers.properties`의 `testcontainers.reuse.enable=true`를 함께 사용한다. 따라서 reusable 컨테이너는 테스트 JVM 종료 후 자동 제거되지 않고 다음 실행을 위해 남는다. 이는 Testcontainers 기본 동작과 다른 의도된 정책이다.

- 컨테이너 재기동 시간을 줄이는 대신 Docker 자원과 테스트 데이터가 실행 사이에 유지된다.
- Ryuk이나 `Gradle Test Executor`까지 장시간 남아 있으면 정상적인 reuse가 아니라 이전 테스트 프로세스가 종료되지 않은 상태인지 확인한다.
- 정리가 필요할 때는 Testcontainers 라벨과 session ID로 테스트 컨테이너만 식별한다. 개발용 Docker Compose 스택을 함께 제거하지 않는다.

## 7. 모듈별 테스트 커버리지

각 실행 서비스·공통 모듈에 어떤 층이 존재하는지(2026-08-01 기준, 코드 스캔).

| 모듈 | 단위 | 통합 | E2E | 부팅 스모크 | 비고 |
|---|:--:|:--:|:--:|:--:|---|
| user | ✓ | ✗ | ✓ | ✓ | 어댑터 테스트는 mock 기반(단위). E2E는 `UserControllerWebMvcTest` |
| chat | ✓ | ✓ | ✓ | ✓ | Mongo/Redis 어댑터·리포지토리 Testcontainer 통합 |
| market | ✓ | ✗ | ✓ | ✓ | JPA 어댑터는 mock(단위). DB 통합은 부팅 스모크로만 |
| market-detection | ✓ | ✓ | — | ✓ | 도메인 계산 단위 + Streams `TopologyTestDriver`. 수집 테스트는 upbit-connector로 이동 |
| notification | ✓ | ✓ | ✓ | ✓ | Mongo 리포지토리 Testcontainer 통합 |
| websocket-gateway | ✓ | ✗ | ✗ | ✓ | 어댑터·세션 캐시 단위. 통합/E2E 미보유 |
| oauth2-authorization-server | ✓ | ✓ | ✓ | ✓ | Redis 어댑터 통합, 토큰 엔드포인트 통합/E2E |
| oauth2-client | ✓ | ✗ | ✓ | ✓ | 인증 흐름 E2E(`*E2ETest`) |
| spring-cloud-api-gateway | ✓ | △ | ✓ | ✓ | 라우팅/보안/식별 전파 E2E, CORS slice(`GatewayCorsConfigTest`) |
| spring-cloud-config | ✓ | ✗ | ✓ | ✓ | Vault Transit 서명·JWKS 단위, `JwksControllerTest` E2E |
| outbox-poller | ✓ | ✗ | ✗ | ✓ | 스케줄러·발행 단위 |
| spring-cloud-eureka-server | ✗ | ✗ | ✗ | ✓ | 자체 로직 없음 → 부팅 스모크만 |
| upbit-connector | ✓ | ✓ | ✗ | ✓ | 스로틀 정책은 `StepVerifier` 가상 시계, 발행 wire 계약은 Kafka Testcontainer |
| common-* | ✓ | ✓ | ✓ | — | 라이브러리(실행 모듈 아님). ReadReplica·RedisCluster 통합, actuator WebFlux E2E, ArchUnit |

- ✓ 있음 / ✗ 없음 / △ 부분(slice) / — 해당 없음.
- "통합 ✗"는 그 모듈의 어댑터가 mock 단위로만 검증되고 실제 인프라 통합은 부팅 스모크가 커버한다는 뜻이다(부팅까지만, 동작 세부는 아님).

## 8. 네이밍 정합화 (완료)

전 테스트 클래스명을 §2.1 컨벤션에 맞춰 정리했다(112개: Unit 91 / Integration 12 / E2E 9). 층은 실제 구현(Mockito·Testcontainers·MockMvc/WebTestClient·@SpringBootTest)으로 판별했다. `BootSmokeTest`·헬퍼(`Test*Config`)·ArchUnit(`*ArchitectureTest`)은 대상에서 제외했다. 동작 변경 없는 이름 정리다.

## 9. 관련 문서·규칙

- 실행/작성 짧은 규칙: [`../.claude/rules/testing.md`](../.claude/rules/testing.md)
- CI/CD(affected 빌드): [`CI_CD.md`](CI_CD.md)
- 모듈별 상세: [`modules/`](modules/)
