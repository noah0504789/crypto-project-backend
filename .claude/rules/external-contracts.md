# 외부 계약 규칙

이 파일은 외부 계약(다른 서비스·프론트·저장소가 의존하는 인터페이스)을 변경할 때 읽는다. 계약의 구체 값 목록은 `docs/ARCHITECTURE.md` §6–7, 흐름은 `docs/SERVICE_FLOWS.md`를 참고한다.

## 계약 유형과 규칙

아래 표의 왼쪽 칸에 해당하면 외부 계약이다. **공유 계약의 이름·의미를 별도 설명 없이 바꾸지 않는다.**

| 유형 | 무엇이 계약인가 | 바꿀 때 함께 보는 것 |
|---|---|---|
| **gRPC / Proto** | `protobuf/src/main/proto/**/*.proto` 의 service·method·message·field number, deadline 정책 | field number 재사용 금지(삭제는 `reserved` 검토). server·client 모두 재생성/재빌드하고 `protobuf` publish 영향을 본다. 4개 proto 소비 매핑은 `docs/ARCHITECTURE.md §7.1` — `market.v1`(→notification·upbit-connector), `chatmessage.v1`(→websocket-gateway), `user.v1`(→oauth2-*), `auth.v1`(→api-gateway·oauth2-client) |
| **Kafka** | topic·binding·destination·header·event payload | producer/consumer 타입 일치와 DLQ 영향. 헤더 계약은 `transaction_id`·`dlq_id`·`__TypeId__`·`KafkaHeaders.KEY`(`common-core/KafkaHeaderKey`). 직접 발행보다 Outbox 흐름(domain event → Outbox → poller → Kafka)을 보존한다. `__TypeId__` 는 조건부다(아래) |
| **Redis** | key pattern, cluster hash tag | key 는 `common-core/RedisKey` enum 으로만 만든다. hash tag `{chat}`·`{auth}`·`{session}`·`{noti}` 유지, 인자 수 검증 테스트 유지. hash tag 변경은 클러스터 슬롯 이동이다 |
| **REST / Gateway / CORS** | 외부 경로·응답 형식, Gateway route, rewrite, CORS | 경로는 route(`ReactiveRouteConfig`, `git-config-repo/dynamic/api-gateway.yml`)와 rewrite(`/api/v1/...`)를 함께 본다. CORS(`CorsConfig`)는 `allowCredentials=true`, expose `Authorization`/`Set-Cookie` — origin 변경은 프론트에 바로 닿는다 |
| **STOMP / WebSocket** | destination과 wire payload | destination(`/msg/chat.send`, `/topic/chat/{roomId}`, `/queue/chat/ack`, `/queue/chat/badge`, `/topic/notification/`)은 프론트와 `websocket-gateway/k6` 가 의존한다. 아웃바운드 payload 구조는 `docs/ARCHITECTURE.md §7.4`, 봉투 주의는 아래 |
| **JWT / Cookie** | issuer, claim, 토큰 동작, refresh 쿠키 속성 | claim(현재 `roles`·`id`) 변경은 gateway·websocket-gateway·하위 서비스에 영향. 발급(`Rs256JwtEncoder`, Vault Transit) ↔ 검증(gateway `ReactiveJwtDecoderConfig`) 양쪽을 함께 본다. 쿠키 속성은 `.claude/rules/security.md` |
| **DB Schema** | 테이블·컬럼·index·unique constraint, `@Enumerated(STRING)` enum 이름 | `schema.sql`·JPA 매핑·기존 데이터 마이그레이션을 함께 본다. unique/index 예: user `public_id`, market `uk_markets_market_code`, price_alert_setting 복합 unique |

## 변경 절차 (필수)

1. 저장소 전체에서 사용 위치를 검색한다(문자열·타입·proto·binding 이름).
2. Producer, Consumer, Client, Test, 설정 파일(`git-config-repo/` 포함)을 모두 확인한다.
3. 호환성과 마이그레이션 영향을 설명한다.
4. 호환성을 깨는 변경은 구현 전에 승인을 받는다.
5. **변경 결과를 문서와 PR 양쪽에 남긴다.** 계약 문서(`docs/ARCHITECTURE.md` §6–7, `docs/SERVICE_FLOWS.md`, 해당 `docs/modules/*.md`)를 실제 값에 맞게 갱신하고, PR 본문 `## 참고사항`에 하위 호환 여부·영향받는 소비처·배포 순서를 적는다(→ `commit-pr.md`).

이 절차는 `review-contract-impact` skill 로 수행한다.

## 표에 담기지 않는 것

### `__TypeId__` 는 바인딩 구성에 따라 wire 까지 가지 않는다

`KafkaEventFactory` 가 Spring 메시지에 넣더라도, 바인딩에 `value.serializer`(JsonSerializer)를 지정하면 직렬화기가 타입 헤더 소유권을 가져가 값이 사라지거나 payload 런타임 클래스로 덮인다(`spring.json.add.type.headers=true` 인 경우). 오버라이드가 없는 바인딩(payload = JSON 문자열, binder 기본 StringSerializer)에서만 넣은 값이 그대로 전달된다.

- 헤더 기반 역직렬화: outbox 계열(chat·notification 소비자, `spring.json.trusted.packages: "*"`).
- 선언 타입 기반 역직렬화: `upbit-ticker-event`(Kafka Streams 함수 시그니처 타입). 이 토픽 소비자는 헤더에 의존하지 않는다.
- 실측 근거와 재현 테스트: `docs/modules/UPBIT_CONNECTOR.md` §6.1, `KafkaUpbitTickerPublishIntegrationTest`.

### STOMP wire 와 내부 Kafka 이벤트는 형태가 다르다

`/topic/chat/{roomId}` 는 봉투 `StompChatMessageBatchPayload{ roomId, messages[] }`이고 각 원소가 `StompChatMessagePayload` 다. 내부 Kafka `ChatMessageBroadcastEvent`(nested `payload`)와 **다르며**, 이 이벤트는 **멤버 목록을 싣지 않는다** — 로컬 전달 판정은 게이트웨이의 구독 레지스트리(`hasLocalSubscriber(roomId)`)가 한다(PR #271). **배칭 설정을 꺼도 1건짜리 봉투로 나가므로 wire 형식은 일정하다.**

### 확인 필요 (사실 그대로 유지, 판정 금지)

- `aud`/`jti` 검증은 코드에서 확인되지 않는다(게이트웨이는 issuer + blacklist + `id` 만 본다). 문서·계약에 `aud` 를 넣기 전 실제 검증 여부를 확인한다.
- `DlqStatus` 소비 실패 상태는 `CONSUME_FAILED` 다(과거 `COMSUME_FAILED` 오타는 코드에서 정정됨). `@Enumerated(STRING)` 이라 이름이 그대로 저장되므로 값 변경은 저장된 row 에 영향을 준다.
