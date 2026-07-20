---
name: review-contract-impact
description: crypto-project-backend에서 외부 계약(REST·gRPC/proto·Kafka·Redis·STOMP·JWT·Cookie·DB Schema·Gateway route)을 변경하기 전에 영향 범위를 조사하는 절차. producer/consumer/client/test/설정/마이그레이션 영향과 호환성을 판단한다. 공유 계약을 바꾸기 전에 사용한다.
---

# 계약 영향 조사 절차

공유 계약의 이름·의미를 별도 설명 없이 바꾸지 않는다. 호환성을 깨는 변경은 구현 전에 승인을 받는다. 계약 정의는 `.claude/rules/external-contracts.md` 참고.

## 1. 계약 유형 식별
대상이 무엇인지 분류한다: proto/gRPC, Kafka topic·binding·header·payload, Redis key·hash tag, REST 경로·응답, Gateway route·CORS, Cookie, JWT claim, STOMP destination·payload, DB schema·index·unique.

## 2. 전체 검색 (producer + consumer 양방향)
- 문자열/타입/이름을 저장소 전체에서 검색한다(`git-config-repo/` 설정 포함). 한 파일만 보고 결론 내리지 않는다.
```bash
grep -Rn "<계약 문자열/타입>" --include="*.java" --include="*.proto" --include="*.yml" .
```
- proto: server(`@GrpcService`)와 모든 client(`*Client`/`@GrpcClient`) 재생성 대상.
- Kafka: producer 타입과 consumer `__TypeId__`/binding, DLQ 영향.
- Redis: key 사용처와 인자 수·hash tag 테스트.
- STOMP/Cookie: 프론트·`websocket-gateway/k6` 의존.
- gRPC 소비 매핑은 `docs/ARCHITECTURE.md §7.1` 참고.

## 3. 테스트·마이그레이션
- 계약을 검증하는 기존 테스트를 찾는다(인자 수/직렬화/라우팅 등).
- DB/스키마·Redis TTL·이벤트 payload 변경 시 마이그레이션·하위호환을 판단한다.

## 4. 판단·보고
- 호환/비호환을 명시하고 영향 서비스·파일을 나열한다.
- 비호환이면 구현 전 승인 요청. `DlqStatus.COMSUME_FAILED`처럼 오타로 보여도 직렬화 계약일 수 있으므로 임의 수정하지 않는다.
