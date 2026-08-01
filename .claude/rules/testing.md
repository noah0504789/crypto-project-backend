# 테스트 · 빌드 규칙

이 파일은 테스트 작성·실행·검증 시 읽는다. 변경 후 검증 절차는 `verify-change` skill을 사용한다. 테스트 4계층(단위·통합·E2E·부팅 스모크) 구조·Testcontainers 하니스·부팅 스모크 설계의 상세는 [`../../docs/TESTING.md`](../../docs/TESTING.md)를 본다.

## 프레임워크
- JUnit 5, Mockito, AssertJ. 통합 테스트는 `common-test`의 Testcontainers/embedded(mysql·redis·mongo) 우선.
- `org.junit.Assert` 사용 금지, AssertJ 사용. Mockito BDD(`given`/`then`) 가능.
- **부팅 스모크(각 실행 모듈 `BootSmokeTest`)**: 실제 `git-config-repo` 설정 + Testcontainers로 전체 `Main` 컨텍스트가 뜨는지 검증한다. 자동설정·컴포넌트 스캔·`@Conditional`·빈 와이어링 오류를 잡는 유일한 층이다. 하니스(`common-test` 이니셜라이저·`SmokeConfigRepo`·`smoke.config.repo` 시스템 프로퍼티)와 서비스별 특이점은 `docs/TESTING.md §3–4`. 새 실행 서비스를 추가하면 `BootSmokeTest`도 추가한다.

## 작성 원칙
- 테스트 메서드에 한글 `@DisplayName`, `given/when/then` 구조.
- 단위 테스트에서는 Spring Context를 띄우지 않는다.
- 외부 시스템·Repository·`StreamBridge`·gRPC Client는 mock 처리한다.
- 도메인 상태 변경은 실제 도메인 객체로 검증한다.
- 통합 테스트는 필요한 경우에만 `@SpringBootTest`/`@DataJpaTest`/`@JdbcTest`/`@WebMvcTest`/`@WebFluxTest`.
- 실패한 테스트를 통과시키려고 Assertion을 약화하지 않는다(→ `git-safety.md`).
- 제네릭 반환값이 AssertJ에서 ambiguous하면 타입 힌트: `assertThat(result.<String>getAttribute("id"))`.

## 실행 명령 (실재하는 것만)
먼저 모듈/Task 존재를 확인한다.
```bash
./gradlew projects
./gradlew :<module>:tasks --all
```
- 가장 좁은 범위부터: `./gradlew :<service>:<submodule>:test`, 컴파일만은 `:...:compileJava`.
  - 예: `./gradlew :chat:chat-application:test`, `./gradlew :common:common-jpa:test`
- 서비스 전체 CI(빌드+테스트+ArchUnit)는 **루트 task**로 실행: `./gradlew chatCi` · `userCi` · `marketCi` · `notificationCi` · `oauth2AuthorizationServerCi` · `oauth2ClientCi` · `websocketGatewayCi` · `gatewayCi` · `springCloudConfigCi` · `marketDetectionCi` · `outboxPollerCi` · `eurekaServerCi`. 전체는 `./gradlew serviceCi`.
- 모든 서비스 CI에 `:common:common-arch-test:test`(ArchUnit)가 포함된다.

### 주의 (기존 문서 명령 정정)
- 집계(부모) 프로젝트 `:chat:test`, `:user:test` 등은 대부분 **테스트가 없는 빈 task**다. 실제 테스트는 서브모듈(`:chat:chat-application:test` 등)이나 서비스 Ci task로 실행한다.
- `commonCi`/`protobufCi` task는 없다.
- proto 생성: `./gradlew :protobuf:build`(stub 생성 후 `protos`를 mavenLocal publish). proto 변경 시 소비 서비스 재빌드.

## 완료 기준
빌드·테스트를 실행하지 못했으면 그 사실과 사유를 밝힌다(성공했다고 단정하지 않는다). 오래 걸리거나 상태를 바꾸는 전체 빌드/배포는 요청·승인 없이 실행하지 않는다.
