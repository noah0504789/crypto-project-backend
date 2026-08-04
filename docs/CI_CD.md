# CI / CD

이 문서는 `.github/workflows/`의 GitHub Actions 워크플로우 6개를 사람이 읽기 위해 정리한 것이다. 근거는 각 워크플로우 YAML과 `scripts/ci/`이며, 값·의도를 코드만으로 판단할 수 없는 항목은 §7 `확인 필요`로 분리했다. 빌드/CI Gradle task 개요는 `docs/ARCHITECTURE.md §10`, 배포 스크립트 실체는 이 저장소가 아니라 별도 infra 저장소에 있다.

## 1. 워크플로우 개요

| 이름 | 파일 | 트리거 | 러너 | 목적 |
|---|---|---|---|---|
| Backend CI | `ci.yml` | PR→main, push→main, 수동 | `ubuntu-latest` | 영향 모듈만 빌드·테스트, 머지 시 PR 이미지 승격 |
| PR image cleanup | `pr-image-cleanup.yml` | PR closed | `ubuntu-latest` | `pr-<번호>` 태그 삭제 |
| Backend CD | `cd.yml` | 수동만 | self-hosted `[local, backend]` · env `production` | 서비스별 운영 배포 |
| Spring Cloud Config Bus Refresh | `spring-cloud-config-bus.yml` | push→main (`git-config-repo/**`) | self-hosted · env `production` | 동적 설정 변경 시 busrefresh |
| Production Environment Test | `production-environment-test.yml` | 수동 | self-hosted · env `production` | production 승인·환경 스모크 |
| Self-hosted Runner Test | `self-hosted-runner-test.yml` | 수동 | self-hosted `[local, backend]` | 러너 연결 확인 |

`environment: production`인 워크플로우는 GitHub Environment 보호 규칙(승인)을 거친다.

## 2. Backend CI (`ci.yml`)

**두 축이다.** ① 변경 영향 모듈만 빌드한다(`scripts/ci/affected_modules.py`). ② **PR 과 머지에서 같은 내용을 두 번 빌드하지 않는다.**

이벤트마다 job 이 하나씩 대응하며, 공통 셋업(Java·Python·실행 권한·`pytest scripts/ci`)은 `.github/actions/setup-build` composite action 에 있다.

| job | name | 트리거 | 하는 일 |
|---|---|---|---|
| `pull-request-ci` | `Affected module CI` | `pull_request` | affected Gradle CI → Docker 빌드 → `pr-<번호>` 태그로 push |
| `merge-ci` | `Promote or rebuild` | `push`(main) | tree 비교 후 **승격** 또는 풀빌드 |
| `full-rebuild` | `Full rebuild (manual)` | `workflow_dispatch` | `serviceCi`(전체) + Docker 빌드·push |

> **`pull-request-ci` 의 `name` 을 바꾸지 않는다.** 룰셋 `main-protection` 의 required status check 가 `"Affected module CI"` 를 참조한다. 어긋나면 모든 PR 이 머지 불가가 된다.

### 2.1 중복 빌드 제거 (승격)

머지 커밋의 **tree 해시**가 PR head 의 tree 와 같으면 파일 내용이 비트 단위로 동일하다는 뜻이다. PR 에서 이미 검증·빌드했으므로 다시 만들지 않고 이미지 태그만 옮긴다.

```
merge-ci:
  PR 번호를 커밋 subject "… (#N)" 에서 추출
  → GitHub API 로 PR head 의 tree 해시 조회      (브랜치가 삭제돼도 조회된다)
  → main 의 tree 와 비교
      같으면  : docker buildx imagetools create 로 :<sha7>·:latest 태그만 추가 (레이어 전송 없음)
      다르면  : 지금까지처럼 affected Gradle CI + Docker 빌드·push
```

안전 근거: 룰셋의 `strict_required_status_checks_policy: true`(브랜치 최신 강제) 때문에 squash 후 tree 일치가 거의 항상 성립한다. 중간에 다른 PR 이 머지돼 내용이 달라지면 tree 가 어긋나 **자동으로 풀빌드로 떨어진다.**

### 2.2 Docker 태그 규칙

| 시점 | 태그 | 비고 |
|---|---|---|
| PR | `pr-<PR번호>` | `latest` 안 붙임(미머지 코드). push 마다 덮어써 태그가 쌓이지 않는다 |
| 머지 | `<sha7>`, `latest` | 승격이면 태그만 추가, 아니면 새로 빌드해 push |
| fork PR | (push 안 함) | 시크릿을 못 받는다. 빌드만 해 Dockerfile 유효성만 확인 |

PR 태그는 PR 이 닫히면 `pr-image-cleanup.yml` 이 지운다(§5). `<sha7>`·`latest` 는 건드리지 않는다.

### 2.3 빌드 캐시

`gradle.properties` 의 `org.gradle.caching=true` 로 task 출력을 재사용한다. `actions/setup-java` 의 `cache: gradle` 이 `~/.gradle` 을 보존하므로 실행 간에 살아남는다. `clean` 을 해도 입력 해시가 같은 task 는 캐시에서 복원되며, `test` task 도 대상이라 안 바뀐 서비스의 Testcontainers 기동이 통째로 사라진다.

**`org.gradle.parallel` 은 켜지 않는다.** 여러 모듈의 `BootSmokeTest` 가 Testcontainers 를 동시에 띄우며 기동 타임아웃으로 CI 가 깨졌고, 전체 시간도 오히려 늘었다(실측 7분 → 13분).

GitHub Actions 캐시는 브랜치 스코프라 **PR 이 만든 캐시를 머지 실행이 읽지는 못한다.** 캐시는 각 실행을 빠르게 할 뿐이고, 중복 제거는 §2.1 이 담당한다.

### 2.4 diff base

| 트리거 | base | head |
|---|---|---|
| pull_request | `origin/<base_ref>` | `github.sha` |
| workflow_dispatch | `inputs.base_ref` | `github.sha` |
| push | `github.event.before` | `github.sha` |

`fetch-depth: 0` 은 diff 계산에 전체 히스토리가 필요해서다. 시크릿은 `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`(태그 삭제를 위해 **Delete 스코프 필요**). Dockerfile 은 각 실행 모듈 디렉토리(`<service>/Dockerfile`).

## 3. Backend CD (`cd.yml`)

**수동 전용 운영 배포**. self-hosted 러너 `[local, backend]` + `environment: production`(승인 게이트).

- 입력:
  - `service`: 배포 대상(choice, 아래 10종)
  - `image_tag`: 배포할 Docker 태그(기본 `latest`)
  - `scale`: blue/green 서비스의 목표 컨테이너 수(`current` 또는 `1`~`5`). recreate 계열은 무시됨.
- 흐름: 입력 출력 → Docker 접근 확인 → infra 저장소 갱신(`INFRA_REPO_DIR`에서 `git pull --ff-only`) → `$INFRA_REPO_DIR/service`에서 서비스별 배포 스크립트 실행.
- **배포 스크립트는 이 저장소에 없다.** infra 저장소의 `service/scripts/deploy/*.sh`를 호출한다.

- 서비스별 배포 전략:

  | 전략 | scale | 대상 서비스 |
  |---|---|---|
  | validated-recreate | 무시 | config, eureka-server, api-gateway, oauth2-authorization-server, oauth2-client |
  | safe recreate | 무시 | outbox-poller |
  | blue/green | 사용 | user, market, websocket-gateway, chat |

- 시크릿: `DOCKERHUB_USERNAME`, `DEPLOY_TOKEN`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID`. 변수(vars): `INFRA_REPO_DIR`, `CONFIG_REPO_URI`.
- 입력 검증: `INFRA_REPO_DIR`/시크릿/입력 공백 여부, `scale`이 `current` 또는 양의 정수인지 확인 후 진행.

## 4. Spring Cloud Config Bus Refresh (`spring-cloud-config-bus.yml`)

설정 저장소(`git-config-repo/**`) 변경이 main에 push되면 실행돼, **동적 설정을 재배포·재시작 없이 실행 중인 전 서비스에 반영**한다. Config Server 내부(bus/JWKS/서명) 상세는 `docs/modules/SPRING_CLOUD_CONFIG.md §6.1`.

### 왜 필요한가
런타임 설정은 서비스에 로컬 `application-*.yml`이 없고 전부 Config Server(→ `git-config-repo`)에서 로드된다(`docs/ARCHITECTURE.md §9`). 값 하나 바꿀 때마다 서비스를 재배포/재시작하면 비용·다운타임이 크다. 자주 조정되는 **동적 값**(`git-config-repo/dynamic/`: 라우팅·CORS·임계값·경로·TTL 등)은 커밋 push만으로 즉시 반영하고 싶다 → Spring Cloud Bus의 busrefresh.

예: `git-config-repo/dynamic/market-detection.yml`의 변동률 임계값이나 `api-gateway.yml`의 라우팅/CORS를 바꿔 main에 push하면, 재배포 없이 실행 중인 인스턴스가 새 값을 재로딩한다.

### 어떻게 동작하나
- 실행 서비스 11개(**eureka-server 제외**)와 Config Server가 모두 `spring-cloud-starter-bus-kafka`를 포함해 **같은 Kafka Bus에 연결**돼 있다(각 `*-bootstrap/build.gradle`).
- Config Server는 busrefresh 액추에이터를 노출한다(`management.endpoints.web.exposure.include: health,info,busrefresh`).
- `POST /actuator/busrefresh` → Config Server가 `RefreshRemoteApplicationEvent`를 Kafka Bus로 **브로드캐스트** → Bus에 붙은 모든 서비스가 수신 → 각자 Config Server에서 설정을 **재조회**하고 빈을 리바인딩한다. (로컬 `/actuator/refresh`가 호출된 단일 인스턴스만 갱신하는 것과 달리, `busrefresh`는 bus 전체에 전파된다.)
- 갱신되는 값은 `@ConfigurationProperties` 바인딩(예: `*Properties`)이다. 이 코드베이스는 `@RefreshScope`를 쓰지 않으므로(0건), refresh 시 `@ConfigurationProperties` 리바인딩으로 반영된다.

### end-to-end 연동
```
git-config-repo/dynamic/*.yml 수정 → main push
 → GitHub Actions(spring-cloud-config-bus.yml): 변경 파일을 dynamic/infrastructure로 분류
 → dynamic만 변경: curl -X POST $CONFIG_SERVER_URL/actuator/busrefresh
 → Config Server: RefreshRemoteApplicationEvent를 Kafka Bus로 브로드캐스트
 → 각 서비스(bus 참여): Config Server 재조회 → @ConfigurationProperties 리바인딩
```

### 트리거·분기 규칙
- 트리거: `push`(main), `paths: git-config-repo/**`. 러너 self-hosted, env `production`.
- 변경 파일을 `dynamic/`·`infrastructure/`로 분류:
  - **동적만 변경** → busrefresh 호출.
  - **인프라 변경 포함** → busrefresh **스킵**(로그만). 인프라 설정은 재배포가 필요.
  - 둘 다 아니면 아무것도 안 함.
- 인프라+동적을 한 커밋에 섞으면 busrefresh는 스킵되지만, 인프라 변경이 유발하는 재배포에서 서비스가 Config Server의 동적 값까지 다시 읽으므로 동적 변경도 반영된다. busrefresh는 **동적-only 변경을 재배포 없이 반영하기 위한 최적화**다.

### 보안·설정
- 변수 `CONFIG_SERVER_URL`, 시크릿 `DEPLOY_TOKEN`. 워크플로우는 `X-Deploy-Token` 헤더를 함께 보낸다.
- 단, config server의 앱 계층 `DeploymentControlAuthFilter`(`common-actuator-webmvc`)는 **`/internal/deployment/**`만 검사**하고 `/actuator/busrefresh`는 대상이 아니다. 모듈에 `SecurityFilterChain`도 없어(→ `docs/modules/SPRING_CLOUD_CONFIG.md §12`) busrefresh는 앱 계층 인증이 사실상 없고 **네트워크 격리에 의존**한다 → **TODO 1.10**.

## 5. 운영 유틸 워크플로우

- `production-environment-test.yml`: 수동, self-hosted + env `production`. 러너/사용자/날짜만 출력 — **production Environment 승인 흐름과 러너 동작을 점검하는 스모크 테스트**.
- `self-hosted-runner-test.yml`: 수동, self-hosted(환경 게이트 없음). hostname/whoami/uname/docker 버전 출력 — **러너 연결·도구 확인용**.
- `pr-image-cleanup.yml`: PR 이 닫히면(`pull_request: closed`) CI 가 올린 `pr-<번호>` 태그를 DockerHub 에서 지운다. 대상 이미지는 각 실행 모듈 `build.gradle` 의 `ext.dockerImageName` 에서 읽는다(정본). `<sha7>`·`latest` 는 건드리지 않는다.
  - **실패해도 job 을 죽이지 않는다**(경고만). 정리 실패가 개발 흐름을 막지 않게 한 것이며, `DOCKERHUB_TOKEN` 에 Delete 스코프가 없으면 403 경고가 남는다.
  - fork PR 은 대상에서 제외된다(시크릿 없음 → 애초에 push 되지 않았다).

## 6. 시크릿 · 변수 · 스크립트

- GitHub Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`(태그 삭제를 위해 **Read/Write/Delete** 스코프), `DEPLOY_TOKEN`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID`.
- GitHub Variables(vars): `INFRA_REPO_DIR`, `CONFIG_REPO_URI`, `CONFIG_SERVER_URL`.
- CI 스크립트(`scripts/ci/`): `affected_modules.py`(엔트리), `affected_modules_core.py`(로직), `test_affected_modules.py`(pytest — CI가 매 실행 시 검증). 변경 파일 → 영향 모듈 → gradle task(`--mode build`) 또는 docker 대상(`--mode docker`) 산출.
- Dockerfile: 각 실행 모듈 디렉토리(`docs/ARCHITECTURE.md §10`, 12개). docker-compose·배포 스크립트는 별도 infra 저장소.

## 7. 확인 필요

확인 필요·미결 항목의 단일 관리처는 `TODO.md`다(이 문서는 번호만 참조).

- CD 배포 대상 드롭다운(`cd.yml`)에 `notification`·`market-detection` 없음 → **TODO 4.1 배포 대상 누락**.
