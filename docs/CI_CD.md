# CI / CD

이 문서는 `.github/workflows/`의 GitHub Actions 워크플로우 5개를 사람이 읽기 위해 정리한 것이다. 근거는 각 워크플로우 YAML과 `scripts/ci/`이며, 값·의도를 코드만으로 판단할 수 없는 항목은 §7 `확인 필요`로 분리했다. 빌드/CI Gradle task 개요는 `docs/ARCHITECTURE.md §10`, 배포 스크립트 실체는 이 저장소가 아니라 별도 infra 저장소에 있다.

## 1. 워크플로우 개요

| 이름 | 파일 | 트리거 | 러너 | 목적 |
|---|---|---|---|---|
| Backend CI | `ci.yml` | PR→main, push→main, 수동 | `ubuntu-latest` | 변경 영향 모듈만 빌드·테스트·Docker 이미지 |
| Backend CD | `cd.yml` | 수동만 | self-hosted `[local, backend]` · env `production` | 서비스별 운영 배포 |
| Spring Cloud Config Bus Refresh | `spring-cloud-config-bus.yml` | push→main (`git-config-repo/**`) | self-hosted · env `production` | 동적 설정 변경 시 busrefresh |
| Production Environment Test | `production-environment-test.yml` | 수동 | self-hosted · env `production` | production 승인·환경 스모크 |
| Self-hosted Runner Test | `self-hosted-runner-test.yml` | 수동 | self-hosted `[local, backend]` | 러너 연결 확인 |

`environment: production`인 워크플로우는 GitHub Environment 보호 규칙(승인)을 거친다.

## 2. Backend CI (`ci.yml`)

**핵심: 변경 영향 모듈만 선별해 빌드/도커한다**(`scripts/ci/affected_modules.py`). 전체를 매번 돌리지 않는다.

- 트리거: `pull_request`(main), `push`(main), `workflow_dispatch`(입력 `ci_task`=`serviceCi`, `base_ref`=기본 `origin/main`).
- 러너: `ubuntu-latest`. Java 17(temurin, gradle 캐시) + Python(`.python-version` = 3.12.8).
- 단계 흐름:
  1. checkout(`fetch-depth: 0` — diff 계산에 전체 히스토리 필요)
  2. `pytest scripts/ci` — CI 스크립트 자체 테스트
  3. diff 범위 결정(아래 표)
  4. 영향 Gradle task 산출: `affected_modules.py --mode build --include-arch-test` → `./gradlew clean <tasks>` (없으면 skip)
     - 수동 실행일 때는 대신 `./gradlew clean serviceCi`(전체)
  5. 영향 Docker 서비스 산출: `affected_modules.py --mode docker` → Buildx 빌드
  6. 이미지 push(조건부, 아래)
  7. 테스트 리포트 업로드(`always()`, `**/build/reports/tests/test/`·`**/build/test-results/test/`)

- diff base 결정:

  | 트리거 | base | head |
  |---|---|---|
  | pull_request | `origin/<base_ref>` | `github.sha` |
  | workflow_dispatch | `inputs.base_ref` | `github.sha` |
  | push | `github.event.before` | `github.sha` |

- Docker 빌드/푸시:
  - 이미지 태그: 커밋 short SHA(7자). push 시 추가로 `:latest` 태그도 push.
  - **DockerHub 로그인·push는 `push`(main) 또는 수동일 때만** 수행 → PR에서는 빌드까지만, 레지스트리 push 안 함.
  - 시크릿 `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`. Dockerfile은 각 실행 모듈 디렉토리(`<service>/Dockerfile`).

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

설정 저장소(`git-config-repo/**`) 변경이 main에 push되면 실행돼, 동적 설정을 무중단으로 반영한다.

- 트리거: `push`(main), `paths: git-config-repo/**`. 러너 self-hosted, env `production`.
- 변경 파일을 `dynamic/`·`infrastructure/`로 분류해 분기:
  - **동적만 변경** → `POST ${CONFIG_SERVER_URL}/actuator/busrefresh`(헤더 `X-Deploy-Token: ${DEPLOY_TOKEN}`). 각 서비스가 설정을 재로딩.
  - **인프라 변경 포함** → busrefresh **스킵**(로그만 남김). 인프라 설정은 재배포가 필요하다.
  - 둘 다 아니면 아무것도 안 함.
- 인프라+동적을 한 커밋에 섞으면 busrefresh는 스킵되지만, 인프라 변경이 유발하는 재배포 시 서비스가 Config Server에서 동적 값까지 다시 읽으므로 동적 변경도 함께 반영된다. busrefresh는 **동적-only 변경을 재배포 없이 반영하기 위한 최적화**다.
- 변수 `CONFIG_SERVER_URL`, 시크릿 `DEPLOY_TOKEN`. busrefresh 엔드포인트는 `DeploymentControlAuthWebFilter`가 `X-Deploy-Token`으로 보호한다(→ `docs/modules/API_GATEWAY.md`, `.claude/rules/security.md`).

## 5. 운영 유틸 워크플로우

- `production-environment-test.yml`: 수동, self-hosted + env `production`. 러너/사용자/날짜만 출력 — **production Environment 승인 흐름과 러너 동작을 점검하는 스모크 테스트**.
- `self-hosted-runner-test.yml`: 수동, self-hosted(환경 게이트 없음). hostname/whoami/uname/docker 버전 출력 — **러너 연결·도구 확인용**.

## 6. 시크릿 · 변수 · 스크립트

- GitHub Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DEPLOY_TOKEN`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID`.
- GitHub Variables(vars): `INFRA_REPO_DIR`, `CONFIG_REPO_URI`, `CONFIG_SERVER_URL`.
- CI 스크립트(`scripts/ci/`): `affected_modules.py`(엔트리), `affected_modules_core.py`(로직), `test_affected_modules.py`(pytest — CI가 매 실행 시 검증). 변경 파일 → 영향 모듈 → gradle task(`--mode build`) 또는 docker 대상(`--mode docker`) 산출.
- Dockerfile: 각 실행 모듈 디렉토리(`docs/ARCHITECTURE.md §10`, 12개). docker-compose·배포 스크립트는 별도 infra 저장소.

## 7. 확인 필요

확인 필요·미결 항목의 단일 관리처는 `TODO.md`다(이 문서는 번호만 참조).

- CD 배포 대상 드롭다운(`cd.yml`)에 `notification`·`market-detection` 없음 → **TODO 4.1 배포 대상 누락**.
