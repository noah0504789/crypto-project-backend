# EUREKA_SERVER — spring-cloud-eureka-server 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - `spring-cloud-eureka-server/src/main/java/.../Main.java`(`@EnableEurekaServer`) 또는 `application.yml` 변경
>   - `git-config-repo/infrastructure/eureka-server.yml`(포트·self-preservation·eviction) 변경
>   - `git-config-repo/infrastructure/eureka-client.yml`(instance-id·lease·gRPC metadata·defaultZone) 변경

## 1. 문서 목적과 기준 시점

`spring-cloud-eureka-server` 모듈의 구조·역할·계약·근거를 사람과 AI가 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다. 짧은 작업 규칙은 [`../../spring-cloud-eureka-server/CLAUDE.md`](../../spring-cloud-eureka-server/CLAUDE.md)에 있다.

## 2. 모듈 역할

**서비스 디스커버리 레지스트리**(Netflix Eureka Server). 각 서비스가 부팅 시 자신을 등록하고, 다른 서비스의 위치를 조회한다. 이 프로젝트에서는 두 용도로 쓰인다.

1. **HTTP 로드밸런싱 대상 조회**: 게이트웨이 등이 `lb://` 라우팅으로 인스턴스를 찾는다.
2. **gRPC 서비스 디스커버리**: 클라이언트가 `eureka.instance.metadata-map`의 `gRPC_port`·`gRPC_service_config`(round_robin)로 대상 gRPC 엔드포인트를 찾는다(→ §5).

레지스트리 기능만 담당하며 커스텀 비즈니스 코드·저장소·gRPC 서버 없음.

## 3. 실행 구조와 주요 의존성

| 구분 | 내용 |
|---|---|
| Gradle·배포 | `:spring-cloud-eureka-server` 단일 모듈, Docker 이미지 `crypto-spring-cloud-eureka-server` |
| 진입점·네트워크 | `org.example.eurekaserver.Main`, 포트 `8761`, 커스텀 소스 `Main.java` 1개 |
| 프레임워크 | `spring-cloud-starter-netflix-eureka-server`, `spring-cloud-starter-config` |
| 공통 모듈 | `common-actuator-webmvc` |
| 원격 설정 | Config Server에서 `eureka-server,monitoring`을 `label: main`으로 로드 |

의존성 전체 그래프는 [`docs/dependencies.md`](../dependencies.md)에서 확인할 수 있다.

## 4. 설정

런타임 설정 실체는 `git-config-repo/infrastructure/`에 있다(로컬 아님).

### 4.1 서버 (`eureka-server.yml`)
- `server.port: 8761`.
- `eureka.client.register-with-eureka: false`, `fetch-registry: false` → **자신은 레지스트리에 등록하지 않는 standalone 단일 노드**(peer 복제 없음).
- `eureka.server.enable-self-preservation: false`, `eviction-interval-timer-in-ms: 30000`, `response-cache-update-interval-ms: 10000`, `defaultOpenForTrafficCount: 0`.

### 4.2 클라이언트 공통 (`eureka-client.yml`, 소비 서비스가 import)
- `client.serviceUrl.defaultZone`은 공통 `application.yml`의 `uri.internal.eureka-server`를 참조해 `/eureka/` 경로를 구성한다.
- `instance.lease-renewal-interval-in-seconds: 10`, `lease-expiration-duration-in-seconds: 30`.
- `client.registry-fetch-interval-seconds: 10`, `healthCheck.enabled: true`, `fetch-registry: true`.
- `instance.instance-id: ${spring.application.name}:${server.port}:${app.instance-id}`.
- `instance.metadata-map`: `gRPC_port: ${grpc.server.port}`, `gRPC_service_config: '{"loadBalancingConfig":[{"round_robin":{}}]}'`.

## 5. 서비스 등록 · 디스커버리 계약

- **등록 서비스(9개)**: `config.name`에 `eureka-client`를 포함하는 서비스 — user, oauth2-authorization-server, oauth2-client, spring-cloud-api-gateway, chat, websocket-gateway, market, market-detection, notification.
- **미등록**: `spring-cloud-config`(Config Server 자신), `spring-cloud-eureka-server`(자신), `outbox-poller`(eureka-client 미포함).
- **gRPC 디스커버리 계약**: gRPC 서버를 띄우는 서비스는 `metadata-map.gRPC_port`로 gRPC 포트를 광고하고, 클라이언트는 이 metadata로 대상을 찾아 round_robin 로드밸런싱한다. `grpc.server.port`·metadata 키(`gRPC_port`, `gRPC_service_config`)는 **디스커버리 계약**이므로 임의 변경 시 gRPC 클라이언트 연결에 영향(→ `external-contracts.md`).
- **instance-id 형식**(`app:port:instance-id`)과 `defaultZone` URL도 계약이다. 변경 시 전체 클라이언트가 영향받는다.

## 6. 테스트 현황

| 대상 | 현황 | CI 검증 |
|---|---|---|
| `spring-cloud-eureka-server` | 단위/통합 테스트 없음. 커스텀 로직 없는 `@EnableEurekaServer` 실행 모듈 | `:spring-cloud-eureka-server:build` |
| 공통 아키텍처 | 서비스별 테스트 없음 | `:common:common-arch-test:test`(ArchUnit) |

## 7. 컴파일 · 빌드 · CI 명령

- 빌드: `./gradlew :spring-cloud-eureka-server:build`.
- 서비스 CI: `./gradlew eurekaServerCi`(루트 `build.gradle` — build + ArchUnit).
- `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 8. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `spring-cloud-eureka-server/src/main/resources/application.yml` | config import·config.name. 자기 설정 로드 경로 |
| `git-config-repo/infrastructure/eureka-server.yml` | 포트·self-preservation·eviction. 레지스트리 안정성 |
| `git-config-repo/infrastructure/eureka-client.yml` | defaultZone·lease·instance-id·gRPC metadata. 전체 클라이언트 등록/디스커버리 계약 |
| `Main.java` | `@EnableEurekaServer` 진입점 |

## 9. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. eureka-server 관련 항목:

- **TODO 4.3** — 단일 노드(peer 복제 없음) + `enable-self-preservation: false`. 네트워크 순단 시 정상 인스턴스 eviction 가능성. 운영 HA/self-preservation 정책 확인.

## 10. 관련 문서와 rules

- 루트: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md), [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 연관: 설정 소스 [`SPRING_CLOUD_CONFIG.md`](SPRING_CLOUD_CONFIG.md), gRPC 디스커버리 소비 [`API_GATEWAY.md`](API_GATEWAY.md)
- 모듈 작업 규칙: [`../../spring-cloud-eureka-server/CLAUDE.md`](../../spring-cloud-eureka-server/CLAUDE.md)
- rules: `../../.claude/rules/{external-contracts,architecture,testing}.md`
