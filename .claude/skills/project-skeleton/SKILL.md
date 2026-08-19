---
name: project-skeleton
description: crypto-project-backend에 새 서비스 모듈의 프로젝트 스켈레톤을 세울 때 따르는 절차. Gradle 등록·설정·부팅 스모크·문서/하네스 갱신까지 빠지는 항목 없이 처리한다. 새 서비스 추가, 서비스 이름 변경, 기존 서비스 분리 시 사용한다.
---

# 프로젝트 스켈레톤 절차

실행 서비스 추가는 파일 하나로 끝나지 않는다. Gradle·설정·테스트·문서·하네스가 **서로를 참조**하기 때문에 한 곳만 빠져도 문서와 코드가 어긋난다. 이 skill은 그 참조 관계를 순서대로 훑는다.

멀티모듈·CI 영향이 있으므로 `.claude/rules/git-safety.md`의 **Plan Mode 우선** 대상이다. 수정 전 계획을 먼저 제시한다.

## 0. 시작 전 확인

- `git status --short`
- 모듈 형태 결정: **계층 모듈**(`-application`/`-adapter-in`/`-adapter-out`/`-bootstrap`, 필요 시 `-domain`/`-contract`/`-client`) vs **단일 모듈**(하위 모듈 없이 디렉터리 하나. 선례: `outbox-poller`, `spring-cloud-api-gateway`).
  - **`-bootstrap` 모듈에는 `Main`만 둔다.** ArchUnit `PackageArchitectureTest.bootstrap_modules_should_only_contain_application_entrypoints`가 강제한다. `market-detection`이 `-bootstrap`에 로직을 담고 있는 것은 **문서화된 legacy 예외**이며 새 모듈의 본보기가 아니다.
  - 계층 모듈을 고르면 의존 방향도 검사된다: application은 adapter/bootstrap에 의존 불가, adapter는 다른 adapter/bootstrap에 의존 불가(`ModuleArchitectureTest`).
- 이름은 **역할 서술형**으로 짓는다. 예약된 접미사를 피한다: `-client`(gRPC 클라이언트 라이브러리), `-contract`(공유 이벤트/DTO), `-adapter*`(헥사고날 계층), `-gateway`(클라이언트 접점).
- 포트 확보: `grep -rn "port:" git-config-repo/dynamic/` 로 사용 중인 값을 확인하고 겹치지 않게 고른다.

## 1. Gradle 등록

1. `<service>/build.gradle` — `id 'crypto-common-library'`(부모 집계용)
2. `<service>/<service>-bootstrap/build.gradle` — `id 'crypto-bootstrap'` + `ext.dockerImageName = "crypto-<service>"`
   - 의존성은 version catalog(`libs.*`)와 `project(':common:...')`만 쓴다. 버전을 직접 적지 않는다.
   - actuator는 웹 스택에 맞춰 고른다: MVC면 `common:common-actuator-webmvc`, WebFlux면 `common:common-actuator-webflux`
3. `settings.gradle` — 부모와 서브모듈을 모두 `include`
4. 루트 `build.gradle` — `<service>Ci` task 등록 + `serviceCi`의 `dependsOn` 목록에 추가

> `scripts/ci/affected_modules_core.py`는 `settings.gradle`과 `ext.dockerImageName`에서 자동 파생한다. **CI 스크립트에 하드코딩할 목록은 없다.**

## 2. 애플리케이션 · 설정

5. `Main` 클래스 — 패키지 `org.example.<servicename>`, `@SpringBootApplication(scanBasePackages = "org.example")` + `@ConfigurationPropertiesScan(basePackages = "org.example")`
   - DB 없는 서비스가 계약 전이로 `common-outbox/-dlq/-inbox`를 끌어오면 `@ComponentScan` 제외 필터가 필요하다(선례: `market-detection/.../Main.java`)
6. `src/main/resources/application.yml` — `spring.application.name`, `spring.config.import: configserver:...`, `spring.cloud.config.name`(로드할 프로파일 집합)
   - **인프라를 쓰면 그 프로파일을 반드시 넣는다.** Kafka를 쓰면 `kafka`, DB면 `mysql`/`mongo`, Redis면 `redis`. 빠뜨려도 부팅 스모크는 test yml이 직접 import 하므로 통과하고, **운영에서만 브로커/DB를 못 찾는다**(실제 발생).
   - **실행용 로컬 `application-*.yml`을 만들지 않는다.** 런타임 설정은 원격이다.
7. `git-config-repo/dynamic/<service>.yml` — `server.port` 등 서비스 설정
8. `Dockerfile` — 다른 실행 모듈과 동일한 형태(`FROM eclipse-temurin:17-jre`, `EXPOSE <port>`)

## 3. 부팅 스모크 (필수)

9. `src/test/resources/application.yml` — main의 `application.yml`을 test classpath에서 shadow해 실제 `git-config-repo`를 직접 import.
   **import 목록은 `spring.cloud.config.name`과 같은 집합**이어야 실제 배포와 동일한 설정 그래프로 뜬다.
10. `src/test/java/.../BootSmokeTest.java` — `@SpringBootTest` + 필요한 Testcontainers 이니셜라이저 조합. 본문은 빈 `contextLoads()`.
    - 하니스 상세와 서비스별 특이점: `docs/TESTING.md` §3–4
    - 외부 접속을 `ApplicationReadyEvent`에서 수행하는 빈은 `@MockitoBean`으로 차단한다
11. 실행해서 **실제로 통과시킨다**: `./gradlew :<service>:<service>-bootstrap:test`

## 4. 문서 · 하네스 갱신

여기가 가장 많이 빠지는 단계다. 아래는 전부 **개수나 목록을 직접 들고 있는** 위치다.

| 파일 | 갱신 내용 |
|---|---|
| `<service>/CLAUDE.md` | 모듈 작업 지침(역할·고유 제약·주요 파일·검증 명령·확인 필요) |
| `docs/modules/<NAME>.md` | 모듈 상세 문서 |
| `docs/README.md` | 실행 서비스 표에 행 추가, `### 실행 서비스 (N)`, 커버리지 문단 |
| `CLAUDE.md`(루트) | 실행 서비스 개수, `docs/modules/*.md` 커버 목록 |
| `README.md`(루트) | 실행 서비스 개수(2곳) |
| `docs/ARCHITECTURE.md` | Gradle 프로젝트 수·실행 앱 수, §4 서비스 카탈로그 표 + 역할 요약, §9 `dynamic/`(N) 목록, §10 Dockerfile 개수 |
| `docs/TESTING.md` | `BootSmokeTest`(N개), §7 모듈별 커버리지 표 |
| `docs/CI_CD.md` | Dockerfile 개수, Kafka Bus 연결 서비스 문구 |
| `.claude/rules/testing.md` | `*Ci` task 나열, 실행 서비스 개수 |
| `.claude/agents/module-explorer.md` | 프로젝트/서비스 개수 |

개수는 추측하지 말고 실측한다.

```bash
./gradlew projects | grep -c "Project '"        # root 포함 전체
ls git-config-repo/dynamic | wc -l
find . -name Dockerfile -not -path "*/build/*" | wc -l
find . -name BootSmokeTest.java -not -path "*/build/*" | wc -l
```

## 5. 배포 (조건부 — 함부로 추가하지 않는다)

`.github/workflows/cd.yml`의 배포 대상 목록은 **별도 infra 저장소의 compose 서비스와 짝**을 이룬다. infra 쪽 준비 없이 추가하면 배포가 실패한다.

- infra 저장소에 대응 서비스가 있는지 먼저 확인한다
- 없으면 추가하지 말고, 모듈 문서의 "확인 필요 항목"과 `TODO.md`에 남긴다

Kafka Bus(`spring-cloud-starter-bus-kafka`)도 같다. 설정 재배포 전파가 필요한 서비스만 포함한다(→ `docs/CI_CD.md` §4).

## 6. 검증

```bash
./gradlew <service>Ci        # build + test + ArchUnit + 의존 순서
git diff                     # 무관한 변경·secret 없는지
git diff --check             # 공백 오류
```

**커밋 뒤 실제로 추적되는지 확인한다.**

```bash
git ls-files <service> | wc -l          # 예상 파일 수와 맞는지
git status --ignored --short <service> | grep '^!!'   # 소스가 무시되고 있지 않은지
```

`.gitignore`의 `out/`·`bin/` 같은 규칙이 `adapter/out`·`port/out` 같은 **소스 디렉터리를 함께 잡아먹은 사례가 있다**(→ 루트 `.gitignore`의 `!**/src/main/**/out/` 예외). 로컬에는 파일이 있어 빌드가 통과하므로 커밋 내용만으로는 드러나지 않는다. 새 모듈에서는 클론 후 빌드로 확인하는 것이 가장 확실하다.

`verify-change` skill의 보고 형식으로 결과를 남긴다. 실행하지 못한 항목은 사유와 함께 밝힌다.

## 7. 조용히 통과하는 실수들

여기 있는 것들은 **빌드·테스트가 초록불인데도 잘못된 상태**다. 실패로 드러나지 않으니 마지막에 한 번 훑는다.

| 빠뜨린 것 | 겉으로는 | 실제 결과 |
|---|---|---|
| 인프라 프로파일을 `spring.cloud.config.name`에 안 넣음 | 부팅 스모크 통과 | 스모크는 test yml이 직접 import 해서 뜬다. **운영에서만** 브로커·DB를 못 찾는다 |
| 부모 집계 프로젝트에 `build.gradle` 없음 | `settings.gradle` include 완료 | convention plugin이 안 붙어 `build`·`test` task가 생기지 않는다. CI에서 조용히 빠진다 |
| `.gitignore`에 소스가 걸림(`out/`·`bin/` 등) | 로컬 빌드 통과 | 커밋에 파일이 없다. 다른 사람이 클론하면 컴파일이 깨진다 |
| 문서에 미구현 동작을 구현된 것처럼 씀 | 문서가 그럴듯함 | 다음 사람이 있다고 믿고 호출·설계한다. 스켈레톤이면 스켈레톤이라고 쓴다 |
| 기존 서비스에서 책임을 옮기며 같은 Kafka 토픽에 producer를 추가 | 양쪽 다 정상 기동 | 같은 토픽에 중복 발행된다. 옮기기 전 `review-contract-impact`로 소비처부터 확인한다 |
