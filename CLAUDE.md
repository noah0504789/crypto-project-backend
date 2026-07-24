# CLAUDE.md

이 파일은 모든 작업에 항상 필요한 짧은 공통 규칙만 담는다. 상세 규칙은 `.claude/rules/`, 사람이 읽는 설명은 `docs/`에 있다.

## 프로젝트 개요
Java 17 · Spring Boot 3.4.0 · Spring Cloud 2024.0.2 기반 헥사고날 멀티모듈 마이크로서비스. 실행 서비스 12개(Eureka, Config+Vault, gRPC, Kafka, MySQL/MongoDB, Redis Cluster). 런타임 설정은 `git-config-repo`에서 원격 로드된다(로컬 `application-*.yml` 없음).

활성 모듈·Task는 추측하지 말고 확인한다: `./gradlew projects`.

문서 지도:
- 전체 구조 `docs/ARCHITECTURE.md`, 주요 흐름 `docs/SERVICE_FLOWS.md`, 코드 작성 기준 `docs/CODE_STYLE.md`, CI/CD `docs/CI_CD.md`.
- 모듈별 상세는 `docs/modules/*.md`(현재 user·chat·market·market-detection·notification·outbox-poller·common·oauth2-authorization-server·oauth2-client·api-gateway·config·eureka 커버). 특정 모듈 디렉토리에서 작업하면 그 디렉토리의 `CLAUDE.md`(모듈 작업 규칙)가 자동 로드된다.
- 확인 필요·미결 항목의 **단일 관리처는 `TODO.md`**(docs/modules의 "확인 필요" 절은 TODO 번호만 참조).

## 안전 규칙
@.claude/rules/git-safety.md

## 규칙 참조 (작업 유형별 — 필요 시 해당 파일을 읽는다)
`.claude/rules/git-safety.md`만 항상 로드된다. 나머지는 아래 작업을 할 때 읽는다.

| 작업 | 읽을 규칙 | 관련 skill |
| --- | --- | --- |
| 계층·의존·트랜잭션·Outbox/DLQ·Read Replica·Redis·예외 | `.claude/rules/architecture.md` | analyze-module |
| REST/gRPC/Kafka/Redis/STOMP/JWT/DB 등 계약 변경 | `.claude/rules/external-contracts.md` | review-contract-impact |
| 인증·인가·OAuth2/JWT·Secret | `.claude/rules/security.md` | — |
| 테스트 작성·실행·검증 | `.claude/rules/testing.md` | verify-change |
| 커밋·PR 메시지 작성 | `.claude/rules/commit-pr.md` | — |

skill은 자동 노출된다: `analyze-module`(모듈 분석), `verify-change`(변경 검증), `review-contract-impact`(계약 영향).

## 의사소통
- 한국어로 설명한다. `원인 → 수정 → 영향 범위 → 검증` 순서를 따른다.
- 코드 설명 시 관련 클래스·메서드·파일 경로를 함께 제시한다.
- 근거를 확인할 수 없는 내용은 추측하지 않고 `확인 필요`로 표시한다. 코드만으로 의도를 알 수 없는 항목을 임의로 설계/버그로 판정하지 않는다.
- 코드 주석은 최소화하고, "무엇"보다 "왜"가 필요할 때만 쓴다.

## 코드 스타일
DTO/record·Entity·네이밍·상수화·예외·트랜잭션·Kafka/Redis·gRPC·테스트 등 코드 작성/리팩토링 기준은 **`docs/CODE_STYLE.md`**(단일 정본)를 따른다. 대상 모듈의 기존 스타일·구조를 우선한다.

## 작업 절차
1. Git 상태 확인 → 2. 관련 규칙/문서 확인 → 3. 전체 호출 흐름·공유 계약 검색 → 4. 파일 경로 근거로 현재 동작 설명 → 5. 원인/변경 식별 → 6. 최소 계획 제시 → 7. 필요한 파일만 수정 → 8. 가장 좁은 테스트/빌드 실행(`verify-change`) → 9. `git diff` 검토 → 10. 결과를 사실대로 보고.

`git-safety.md`의 Plan Mode 우선 목록(멀티모듈·보안·Kafka/Outbox·트랜잭션·Proto·Schema·CI/CD·대규모 리팩터링)에 해당하면 수정 전 분석·계획을 먼저 제시한다.

## 완료 보고 형식
```
변경 요약
- ...

검증
- 실행: ...
- 결과: ...
- 미실행 항목과 사유: ...

영향 범위
- ...

남은 사항
- ...
```
테스트/빌드를 실행하지 못했으면 실행하지 않았다는 사실과 이유를 밝힌다.

## 문서 관리
- 이 파일에는 반복적으로 필요한 짧은 규칙만 둔다. 상세는 `.claude/rules/`·`docs/`에 둔다.
- 코드와 일치하는지 확인한 후 문서를 변경한다. 문서와 코드가 충돌하면 임의로 한쪽을 택하지 말고 충돌을 먼저 보고한다(코드가 사실 기준).
