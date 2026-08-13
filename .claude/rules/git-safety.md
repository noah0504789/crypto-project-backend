# Git · 안전 규칙

이 파일은 CLAUDE.md에서 `@import`로 항상 로드된다. 모든 작업에 무조건 적용되며, 모델이 읽을지 판단하지 않는다.

## 파일 변경 전
- 파일을 수정하기 전에 Git 상태를 확인한다: `git status --short`.
- 사용자가 이미 작업한 내용을 명시적 승인 없이 덮어쓰거나 되돌리지 않는다.
- 요청 범위와 무관한 리팩터링을 함께 수행하지 않는다.
- build 산출물, `.idea/`, `.gradle/`, secret 파일을 수정 대상으로 삼지 않는다.

## Git · 배포
- 사용자가 명시적으로 요청하지 않는 한 `commit`, `push`, `merge`, `rebase`, 배포를 수행하지 않는다.
- 운영 환경에 접근하거나 운영 시스템을 수정하지 않는다.
- 명시적 승인 없이 DB, Redis, Kafka, Docker, 파일 시스템에 파괴적인 명령을 실행하지 않는다.
- 새 clone에서는 `./gradlew installGitHooks`를 한 번 실행해 versioned pre-commit hook을 활성화한다. hook은 Java·Gradle·품질 설정 변경을 커밋하기 전 포맷과 공백 오류를 검증한다.

## 민감 정보 (출력·커밋 금지)
아래는 응답에 출력하거나 커밋하지 않는다.
- `.env` 값, 비밀번호, Client Secret, 외부 OAuth 공급자 Secret
- Vault Root Token, Unseal Key, AppRole Secret ID
- TLS Private Key, `.jks` / `.p12` / `.pem` / `.key` 파일
- 운영 환경 로그·인증 정보
- Vault 확인 시 root token/secret을 응답에 노출하지 않는다.

## 테스트·검증 무결성
- 실패한 테스트를 삭제·비활성화하거나 검증 수준을 낮춰 문제를 "해결"하지 않는다.
- 테스트/빌드를 실행하지 못했으면 성공했다고 말하지 않는다. 미실행 사실과 사유를 밝힌다.

## Plan Mode 우선 (수정 전 분석)
다음은 파일 수정 전에 먼저 영향 범위를 분석하고 계획을 제시한다.
- 여러 모듈에 영향을 주는 변경, 대규모 리팩터링
- OAuth2 / Spring Security 변경
- Kafka / Outbox / DLQ 변경
- Redis Key / 데이터 일관성 / 트랜잭션 경계 변경
- Proto / gRPC 계약, DB Schema 변경
- 배포 / CI-CD 변경
