# 외부 계약 규칙

이 파일은 외부 계약(다른 서비스·프론트·저장소가 의존하는 인터페이스)을 변경할 때 읽는다. 계약의 구체 값 목록은 `docs/ARCHITECTURE.md` §6–7, 흐름은 `docs/SERVICE_FLOWS.md`를 참고한다.

## 외부 계약으로 취급하는 항목
- `protobuf/src/main/proto/**/*.proto` (gRPC service/method/message)
- HTTP 경로·응답 형식, Gateway Route, CORS, Cookie name/path/domain
- Kafka Topic / Binding / Destination / Header / Event Payload
- Redis Key Pattern과 Cluster Hash Tag
- WebSocket/STOMP Destination과 Payload
- JWT Issuer / Claim / Token 동작
- gRPC Service 이름·Method·Payload·Deadline 정책
- DB Schema / Index / Unique Constraint

## 변경 절차 (필수)
1. 저장소 전체에서 사용 위치를 검색한다(문자열·타입·proto·binding 이름).
2. Producer, Consumer, Client, Test, 설정 파일(`git-config-repo/` 포함)을 모두 확인한다.
3. 호환성과 마이그레이션 영향을 설명한다.
4. 호환성을 깨는 변경은 구현 전에 승인을 받는다.
- 공유 계약의 이름/의미를 별도 설명 없이 변경하지 않는다.
- 이 절차는 `review-contract-impact` skill로 수행한다.

## 유형별 규칙
### gRPC / Proto
- proto field number 재사용 금지, 삭제 시 `reserved` 검토.
- 변경 시 server·client 모두 재생성/재빌드, `protobuf` publish 영향 확인.
- 4개 proto와 소비 매핑: `market.v1`(→notification/market-detection), `chatmessage.v1`(→websocket-gateway), `user.v1`(→oauth2-*), `auth.v1`(→api-gateway/oauth2-client). 근거 `docs/ARCHITECTURE.md §7.1`.

### Kafka
- topic/binding/destination/header/`__TypeId__`/payload 변경은 producer·consumer 타입 일치와 DLQ 영향을 함께 본다.
- 헤더 계약: `transaction_id`, `dlq_id`, `__TypeId__`, `KafkaHeaders.KEY`.
- 직접 발행보다 Outbox 흐름(domain event → Outbox → poller → Kafka)을 우선 보존한다.

### Redis
- key는 `common-core/RedisKey` enum으로만. hash tag `{chat}`/`{auth}`/`{session}` 유지. 인자 수 검증 테스트 유지.

### REST / Gateway / CORS
- 경로 변경은 Gateway route(`ReactiveRouteConfig`, `git-config-repo/dynamic/api-gateway.yml`)와 rewrite(`/api/v1/...`) 영향을 확인한다.
- CORS(`CorsConfig`)는 `allowCredentials=true`, expose `Authorization`/`Set-Cookie`. origin 변경은 프론트 영향.

### STOMP / WebSocket
- destination(`/msg/chat.send`, `/topic/chat/{roomId}`, `/queue/chat/badge`, `/queue/chat/ack`, `/topic/notification/`)과 payload는 프론트·`websocket-gateway/k6` 부하 테스트가 의존한다. 변경 전 의존성 확인.
- 아웃바운드 wire payload 구조는 `docs/ARCHITECTURE.md §7.4` 참고. 특히 `/topic/chat/{roomId}`는 flat `StompChatMessagePayload{ messageId, roomId, writerId, content, timestamp, clientMessageId }`이며 내부 Kafka `ChatMessageBroadcastEvent`(nested `payload`+`memberIds`)와 다르다.

### JWT
- claim 변경(현재 `roles`, `id` 확인됨)은 gateway, websocket-gateway, 하위 서비스에 영향.
- 발급(`Rs256JwtEncoder`, Vault Transit) ↔ 검증(gateway `ReactiveJwtDecoderConfig`) 양쪽을 함께 본다.
- **확인 필요(사실 그대로 유지, 판정 금지)**: `aud`/`jti` 검증은 코드에서 확인되지 않음(게이트웨이는 issuer+blacklist+`id`만). 문서·계약에 `aud`를 넣기 전 실제 검증 여부를 확인한다. `DlqStatus.COMSUME_FAILED` 철자는 직렬화 계약일 수 있어 임의 수정 금지.

### DB Schema
- `schema.sql`, JPA 매핑, 기존 데이터 마이그레이션을 함께 본다. unique/index(예: user `public_id`, market `uk_markets_market_code`, price_alert_setting 복합 unique) 변경은 계약이다.
