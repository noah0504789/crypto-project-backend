# spring-cloud-eureka-server — 모듈 작업 지침

이 파일은 `spring-cloud-eureka-server/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·역할·계약·근거는 [`../docs/modules/EUREKA_SERVER.md`](../docs/modules/EUREKA_SERVER.md)를 참고한다.

이 모듈은 **인프라 핵심**이다. 전체 서비스의 디스커버리 레지스트리이므로, 등록/디스커버리 계약(포트·defaultZone·instance-id·gRPC metadata) 변경은 여러 모듈에 영향을 주는 변경으로 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이며, 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

Netflix Eureka **서비스 디스커버리 레지스트리**(단일 모듈, `@EnableEurekaServer`, 포트 `8761`). 각 서비스의 등록·조회를 담당하며, HTTP `lb://` 라우팅과 **gRPC 서비스 디스커버리**(`metadata-map.gRPC_port` + round_robin) 양쪽의 기반이다.

커스텀 비즈니스 코드·저장소·gRPC 서버 없음. 소스는 `Main.java` 1개이고 나머지는 프레임워크 + 원격 설정으로 구성된다. `spring-cloud-eureka-server/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **설정 실체는 config repo에 있다**: 이 모듈의 로컬 `application.yml`은 config import(`config.name: eureka-server,monitoring`)만 담고, 실제 서버 동작은 `../git-config-repo/infrastructure/eureka-server.yml`, 클라이언트 공통은 `eureka-client.yml`에 있다. 서버 동작을 바꾸려면 로컬이 아니라 해당 config repo 파일을 본다.
- **디스커버리 계약 보존**: `defaultZone`(`http://crypto-spring-cloud-eureka-server:8761/eureka/`), `instance-id`(`${app}:${port}:${app.instance-id}`), gRPC metadata 키(`gRPC_port`, `gRPC_service_config`)는 전체 클라이언트가 의존하는 계약이다. 변경은 등록/디스커버리 전반에 영향 → `../.claude/rules/external-contracts.md` 절차로 진행한다.
- **gRPC 디스커버리 유지**: gRPC 클라이언트는 Eureka metadata(`gRPC_port`)로 대상을 찾는다. 포트/metadata 키 변경은 gRPC 연결을 깨뜨릴 수 있으니 서버·클라이언트 양쪽을 함께 본다.
- **레지스트리 안정성 파라미터**: `enable-self-preservation`, `eviction-interval-timer-in-ms`, lease(`lease-renewal`/`lease-expiration`)는 순단 시 인스턴스 유지/축출 동작을 바꾼다. 값 조정 전 운영 영향과 의도를 확인한다(→ 확인 필요 항목).
- **단일 노드 전제**: 현재 `register-with-eureka: false`·`fetch-registry: false`로 peer 복제 없는 standalone이다. HA(peer) 구성으로 바꾸는 것은 인프라 변경이므로 임의로 하지 않는다.

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`src/main/java/org/example/eurekaserver/Main.java`](src/main/java/org/example/eurekaserver/Main.java) | `@EnableEurekaServer` 진입점 |
| [`src/main/resources/application.yml`](src/main/resources/application.yml) | config import·config.name(자기 설정 로드) |
| `../git-config-repo/infrastructure/eureka-server.yml` | 포트·self-preservation·eviction(서버 동작) |
| `../git-config-repo/infrastructure/eureka-client.yml` | defaultZone·lease·instance-id·gRPC metadata(클라이언트 계약) |

## 검증 명령

- 빌드: `./gradlew :spring-cloud-eureka-server:build`
- 서비스 CI: `./gradlew eurekaServerCi`(build + `:common:common-arch-test:test`)

전체 build, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/EUREKA_SERVER.md §9`](../docs/modules/EUREKA_SERVER.md)와 [`../TODO.md`](../TODO.md).

- 단일 노드(peer 복제 없음) + `enable-self-preservation: false` — 네트워크 순단 시 정상 인스턴스도 eviction될 수 있음. 운영 HA/self-preservation 정책 확인.
