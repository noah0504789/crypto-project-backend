---
name: analyze-module
description: crypto-project-backend에서 특정 Gradle 모듈/서비스를 분석할 때 사용하는 절차. 모듈의 계층 구성·의존성·포트/어댑터·설정·외부 계약을 코드 근거와 함께 파악한다. 모듈 구조 파악, 문서화, 변경 전 영향 이해가 필요할 때 사용한다.
---

# 모듈 분석 절차

대상 모듈을 코드 근거로 분석한다. 추측하지 않고, 근거가 부족하면 `확인 불가`로 표시한다. (읽기 전용 — 파일 수정 금지)

## 1. 모듈 식별
- `settings.gradle`에서 대상과 서브모듈(domain/application/adapter-in/adapter-out/client/contract/bootstrap)을 확인한다.
- `./gradlew projects`로 실제 프로젝트 목록을 대조한다.

## 2. 빌드·의존성
- 각 서브모듈 `build.gradle`의 적용 plugin과 `project(...)` 의존을 읽어 의존 방향을 파악한다(안쪽 domain을 향하는지).
- `ext.dockerImageName` 유무로 실행/배포 대상인지 확인한다. `@SpringBootApplication`(`org.example.*.Main`) 위치로 실행 모듈을 확인한다.

## 3. 계층 구성
- application: `port/in`(UseCase), `port/out`, `*CommandService`/`*QueryService`, 트랜잭션 경계(named tx manager 포함).
- domain: Entity/VO/도메인 이벤트/enum, 상태 변경 도메인 메서드.
- adapter-in: REST Controller / gRPC Service / STOMP Controller / Kafka 바인더.
- adapter-out: JPA/Mongo/Redis/Kafka/gRPC Client 구현이 어떤 port를 구현하는지 매핑.

## 4. 설정
- `*-bootstrap/src/main/resources/application.yml`에서 app name, import하는 config 프로파일을 본다.
- **서버 port·상세 값은 로컬이 아니라 `git-config-repo/{dynamic,infrastructure}/`에 있다.** 로컬에 없으면 `확인 불가(원격 config)`로 표기한다.

## 5. 외부 계약
- 이 모듈이 생산/소비하는 REST 경로, gRPC(proto), Kafka topic/binding, Redis key, STOMP destination을 목록화한다(→ `.claude/rules/external-contracts.md`).

## 6. 테스트·CI
- 서브모듈 test 위치, 관련 서비스 Ci task(`<service>Ci`)를 확인한다(→ `.claude/rules/testing.md`).

## 결과 형식
- 모듈 경로 / 역할 / 실행 여부 / 주요 의존 / 저장소·외부 시스템 / inbound·outbound 어댑터 / 공유 계약. 각 항목에 파일 경로를 붙이고, 미확인은 `확인 불가`로 남긴다.
