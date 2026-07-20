---
name: verify-change
description: crypto-project-backend에서 코드 변경 후 완료를 선언하기 전에 수행하는 검증 절차. 좁은 범위 테스트·컴파일·서비스 CI·git diff·계약 영향을 순서대로 확인한다. 코드를 수정한 뒤, 작업을 마쳤다고 보고하기 전에 사용한다.
---

# 변경 검증 절차

"코드를 만들었다"는 이유만으로 완료로 보지 않는다. 아래를 확인한 뒤 결과를 사실대로 보고한다.

## 1. 상태 확인
- `git status --short`로 변경 파일이 의도한 범위인지 확인한다(무관한 파일 변경·생성 여부).

## 2. 컴파일·테스트 (좁은 범위 → 확대)
- 변경 서브모듈부터: `./gradlew :<service>:<submodule>:compileJava` → `:...:test`.
- 서비스 단위 검증이 필요하면 서비스 Ci task: `./gradlew <service>Ci`(예: `chatCi`). ArchUnit이 포함된다.
- 실행한 명령과 결과를 기록한다. 실행하지 못했으면 사유를 밝힌다. 오래 걸리거나 상태를 바꾸는 전체 빌드/배포는 승인 없이 실행하지 않는다.

## 3. 계약 영향
- 변경이 외부 계약(REST/gRPC/Kafka/Redis/STOMP/JWT/DB)에 닿으면 `review-contract-impact` skill로 producer/consumer/client/test/설정 영향을 확인한다.

## 4. diff 검토
- `git diff`로 관계없는 변경, secret, 불필요한 생성 파일이 없는지 본다(→ `.claude/rules/git-safety.md`).

## 5. 보고 형식
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
확인 항목: 요청 동작 구현 · 관련 테스트 통과 · 컴파일 성공 · 무관 파일 미변경 · 공유 계약 호환 · secret/불필요 파일 미추가 · 승인 범위 일치.
