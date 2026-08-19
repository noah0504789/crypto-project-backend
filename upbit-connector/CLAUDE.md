# upbit-connector — 모듈 작업 지침

이 파일은 `upbit-connector/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·계획·근거는 [`../docs/modules/UPBIT_CONNECTOR.md`](../docs/modules/UPBIT_CONNECTOR.md)를 참고한다.

Kafka 바인딩 추가·REST 계약 추가·`market-detection` 수집 코드 이관은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

Upbit 외부 API 통신을 전담하는 리액티브 커넥터 서비스. 계층 모듈 구성이다.

| 모듈 | 역할 |
|---|---|
| `-contract` | `UpbitTickerEvent`(소비자와 공유) |
| `-application` | 수집 정책(`UpbitTickerCollectService`)과 out 포트 |
| `-adapter-out` | Upbit WebSocket 접속·역직렬화·설정·수명주기 |
| `-bootstrap` | `Main`만. 다른 클래스를 두면 ArchUnit이 막는다 |

**현재 1단계까지다.** 실시간 시세를 받아 종목별로 스로틀해 로그로 흘린다. Kafka 발행·REST 조회는 아직 없다. 문서·주석에 아직 없는 동작을 있는 것처럼 쓰지 않는다.

- 이 저장소에서 **WebFlux를 쓰는 두 번째 모듈**이다(첫 번째는 `spring-cloud-api-gateway`). 나머지 서비스는 전부 MVC/블로킹이므로, 다른 모듈의 패턴을 그대로 복사하면 블로킹 코드가 섞인다.
- DB 없음. gRPC 서버 없음.

## 주요 변경 규칙

- **블로킹 호출 금지**: 이벤트 루프에서 JDBC·`RestTemplate`·`OkHttp` 동기 호출·`Thread.sleep`을 쓰지 않는다. 불가피한 블로킹 호출(예: 기존 gRPC 클라이언트)은 `subscribeOn(Schedulers.boundedElastic())`으로 격리하고 왜 필요한지 주석에 남긴다.
- **스로틀·백프레셔는 정책이다**: Upbit ticker는 종목 수 × 초당 수 건으로 들어온다. 스로틀 없이 그대로 흘리면 Kafka·다운스트림이 밀린다. `market-detection`의 기존 스로틀(코드별 publish interval + 유계 ready queue)과 **의미가 같은지**를 확인하지 않은 채 연산자만 바꿔 끼우지 않는다. 차이는 `../docs/modules/UPBIT_CONNECTOR.md` §4에 정리돼 있다.
- **`market-detection`을 함께 고치지 않는다**: 수집 이관은 검증(같은 문서 §5) 이후 별도 작업이다. 이 모듈 작업 중에 `market-detection/`을 수정하지 않는다.
- **타입 헤더에 의존하지 않는다**: 이 바인딩(JsonSerializer)에서는 `__TypeId__`가 브로커까지 가지 않는다. 소비자는 선언된 타입으로 읽어야 한다. 근거·실측은 `../docs/modules/UPBIT_CONNECTOR.md` §6.1.
- **토픽 이중 발행 주의**: `upbit-ticker-event`는 현재 `market-detection`이 발행한다. 이 모듈에서 같은 토픽에 발행을 붙이면 producer가 둘이 된다. 계약 절차(`../.claude/rules/external-contracts.md`)를 먼저 거친다.
- **생성자는 `@RequiredArgsConstructor`**: 손으로 쓴 주입 생성자를 두지 않는다. 협력 객체(`HttpClient` 등)는 `infra/config`의 `@Bean`으로 분리한다(기준: `../docs/CODE_STYLE.md` §6·§7).
- **시간 조회**: `System.nanoTime()`·`System.currentTimeMillis()`를 직접 호출하지 않고 `common-time`의 `Clock`을 주입받는다.
- **설정은 원격**: 런타임 설정은 `../git-config-repo/dynamic/upbit-connector.yml`에 둔다. 실행용 로컬 `application-*.yml`을 만들지 않는다(test classpath의 스모크 전용 `application.yml`은 예외).
- **설정 변경은 재시작으로 반영한다**: Kafka Bus에 연결하지 않는다(의도). busrefresh로는 이미 조립된 파이프라인이 바뀌지 않는다. 근거는 `../docs/modules/UPBIT_CONNECTOR.md` §7.1.
- **부팅 스모크 유지**: 의존성·자동설정을 바꾸면 `BootSmokeTest`가 실제 `git-config-repo` 설정으로 부팅되는지 확인한다. import 목록은 `spring.cloud.config.name`과 같은 집합을 유지한다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`upbit-connector-application/.../service/UpbitTickerCollectService.java`](upbit-connector-application/src/main/java/org/example/upbitconnector/application/service/UpbitTickerCollectService.java) | 종목별 스로틀 정책 |
| [`upbit-connector-adapter-out/.../upbit/UpbitWebsocketTickerStreamAdapter.java`](upbit-connector-adapter-out/src/main/java/org/example/upbitconnector/adapter/out/upbit/UpbitWebsocketTickerStreamAdapter.java) | WebSocket 접속·재연결·역직렬화 |
| [`upbit-connector-adapter-out/.../upbit/UpbitTickerCollectStarter.java`](upbit-connector-adapter-out/src/main/java/org/example/upbitconnector/adapter/out/upbit/UpbitTickerCollectStarter.java) | 수집 구독 수명주기 |
| [`upbit-connector-bootstrap/src/main/java/org/example/upbitconnector/Main.java`](upbit-connector-bootstrap/src/main/java/org/example/upbitconnector/Main.java) | 실행 진입점 |
| [`upbit-connector-bootstrap/src/main/resources/application.yml`](upbit-connector-bootstrap/src/main/resources/application.yml) | Config Server import·프로파일 집합 |
| [`upbit-connector-bootstrap/src/test/resources/application.yml`](upbit-connector-bootstrap/src/test/resources/application.yml) | 스모크 전용 설정(Config Server 대체) |
| [`upbit-connector-bootstrap/src/test/java/org/example/upbitconnector/BootSmokeTest.java`](upbit-connector-bootstrap/src/test/java/org/example/upbitconnector/BootSmokeTest.java) | 부팅 검증 |
| `../git-config-repo/dynamic/upbit-connector.yml` | 런타임 설정(포트 등) |

## 검증 명령

- 컴파일: `./gradlew :upbit-connector:upbit-connector-bootstrap:compileJava`
- 테스트: `./gradlew :upbit-connector:upbit-connector-bootstrap:test`(부팅 스모크 포함)
- 서비스 CI: `./gradlew upbitConnectorCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 상세는 [`../TODO.md`](../TODO.md) 4.9(첫 배포 전 `.deploy/upbit-connector.current-image` 초기화)와 4.11(REST 조회 API 미구현).
