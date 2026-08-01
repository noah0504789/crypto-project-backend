# websocket-gateway — 모듈 작업 지침

이 파일은 `websocket-gateway/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/WEBSOCKET_GATEWAY.md`](../docs/modules/WEBSOCKET_GATEWAY.md)를 참고한다.

STOMP destination·Kafka 소비·gRPC 계약에 걸친 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 프론트·k6 부하 테스트가 계약에 의존하므로 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

프론트와 STOMP over WebSocket으로 연결되는 실시간 게이트웨이(헥사고날, 실행 모듈 `websocket-gateway-bootstrap`). 담당:

1. 인바운드: STOMP `/msg/chat.send` → chat gRPC(`chatmessage.v1`) 저장 → ACK
2. 아웃바운드: `chatmessage-broadcast-event`/`chatroom-broadcast-event`/`web-notification-broadcast-event` 소비 → 로컬 세션 보유자에게 STOMP push
3. 세션 위치 관리(로컬 Caffeine + Redis `{session}`)

메시지 영속·수신자 판정은 이 모듈이 아니다(chat/notification). websocket-gateway는 **연결·라우팅·push**만 하고 gRPC 서버는 노출하지 않는다. `websocket-gateway/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **STOMP 계약 보존**: destination(`common-core/StompDestination`: `/topic/chat/{roomId}`, `/queue/chat/ack`, `/queue/chat/badge`, `/topic/notification/`), 인바운드 `/msg/chat.send`, endpoint(`/ws`, `/ws-native`), prefix(`/msg`, `/user`)는 프론트·k6 부하 테스트가 의존하는 외부 계약이다. 변경 전 의존성 확인(→ `../.claude/rules/external-contracts.md`).
- **wire payload 계약**: `StompChatMessagePayload`(flat)는 내부 Kafka `ChatMessageBroadcastEvent`(nested `payload`+`memberIds`)와 **다르다**. `memberIds`는 로컬 세션 라우팅용이라 wire에 싣지 않는다. 이 flat 변환(`ChatMessageBroadcastEventMapper`)을 유지한다.
- **로컬 세션 필터링 유지**: 모든 push 어댑터는 `LocalSessionCache.hasUser(...)`로 이 인스턴스 연결자만 전송한다. Kafka 소비 group은 **인스턴스별 고유**(`...-${app.instance-id}`)여야 전 인스턴스가 이벤트를 받아 각자 자기 세션 보유자에게 전달한다. 이 group 규칙을 공유 group으로 바꾸면 push가 유실된다(→ external-contracts).
- **송신 보상 로직 유지**: `ChatMessageSendService`는 gRPC 저장 실패 중 `DEADLINE_EXCEEDED`면 `hardDelete`로 보상한다(저장됐을 수 있는 메시지 제거). messageId는 gRPC 호출 전에 게이트웨이가 생성한다(클라이언트 상관용). 이 순서·보상을 깨지 않는다.
- **세션 이중 관리 정합성**: 로컬(Caffeine)과 Redis(`{session}:user:{userId}`)를 함께 갱신한다(connect save, subscribe refreshTtl, disconnect `deleteIfServerMatches`). disconnect는 serverId 일치할 때만 삭제(재접속 레이스 방지) — 이 조건을 제거하지 않는다. Redis key는 `common-core/RedisKey.SESSION_INFO`로만, hash tag `{session}` 유지.
- **best-effort push 인지**: 브로드캐스트 소비자는 DLQ/재시도가 없다(실시간 push는 유실 시 클라이언트 REST 재조회 전제). 이를 durable로 바꾸려면 chat/notification 영속 경로와 함께 설계한다.
- **공유 Inbox 적용 금지**: 브로드캐스트 consumer는 인스턴스별 group으로 모든 gateway가 자기 로컬 세션에 전송해야 한다. 공유 `(consumer_name,event_id)` 선점은 다른 인스턴스 push를 막으므로 적용하지 않고, 클라이언트가 `messageId`/`notificationId`로 중복 제거한다.
- **gRPC 소비 계약**: `chatmessage.v1`(save/hardDelete)은 chat이 서버, 여기가 클라이언트다. proto 변경은 chat과 함께(external-contracts). client 설정은 `websocket-gateway.yml`의 `grpc.client.chat-client`.
- **DataSource/JPA 자동설정 제외 유지**: 이 서비스는 DB가 없지만 소비 계약(`chat-contract`/`notification-contract` → `common-outbox` → `common-jpa`)이 `spring-boot-starter-data-jpa`를 전이로 끌어온다. `websocket-gateway.yml`의 `spring.autoconfigure.exclude`(`DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`)를 제거하면 `DataSourceAutoConfiguration`이 강제 활성화돼 부팅이 실패한다. 지우지 않는다(상세: `../docs/modules/WEBSOCKET_GATEWAY.md §3`).
- **핸드셰이크 인증**: `StompConfig.determineUser`는 `X-User-Id` 헤더로 Principal을 만든다(없으면 거부). 헤더 주입/토큰 전달 방식 변경은 게이트웨이·oauth2-client 핸드셰이크와 함께 본다(→ `../.claude/rules/security.md`).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`...adapter-in/.../websocket/stomp/StompController.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/stomp/StompController.java) | `/msg/chat.send` 수신 |
| [`...adapter-in/.../websocket/config/StompConfig.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/config/StompConfig.java) | STOMP endpoint/broker/executor/핸드셰이크 |
| [`...adapter-in/.../stream/KafkaWebsocketGatewayBinder.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/stream/KafkaWebsocketGatewayBinder.java) | 3개 브로드캐스트 consumer |
| [`...adapter-in/.../websocket/WebSocketSessionEventHandler.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/WebSocketSessionEventHandler.java) | connect/subscribe/disconnect 세션 |
| [`...application/.../chatmessage/application/service/ChatMessageSendService.java`](websocket-gateway-application/src/main/java/org/example/websocket/gateway/chatmessage/application/service/ChatMessageSendService.java) | 송신·ACK·hardDelete 보상 |
| [`...application/.../session/application/cache/LocalSessionCache.java`](websocket-gateway-application/src/main/java/org/example/websocket/gateway/session/application/cache/LocalSessionCache.java) | 로컬 세션 캐시(push 판정) |
| [`...adapter-out/.../chatmessage/adapter/out/grpc/GrpcChatMessageCommandAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/grpc/GrpcChatMessageCommandAdapter.java) | chat gRPC save/hardDelete |
| [`...adapter-out/.../chatmessage/adapter/out/stomp/StompChatMessageBroadcastAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/stomp/StompChatMessageBroadcastAdapter.java) | `/topic/chat/{roomId}` push |
| [`...adapter-out/.../session/adapter/out/redis/RedisSessionLocationAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/session/adapter/out/redis/RedisSessionLocationAdapter.java) | Redis 세션 위치(TTL) |
| `../git-config-repo/dynamic/websocket-gateway.yml` | STOMP·Kafka 소비·gRPC client 설정(Config Server 원격) |

## 검증 명령

- 컴파일: `./gradlew :websocket-gateway:websocket-gateway-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :websocket-gateway:websocket-gateway-application:test`, `:websocket-gateway:websocket-gateway-adapter-out:test`
- 서비스 CI: `./gradlew websocketGatewayCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/WEBSOCKET_GATEWAY.md §9`](../docs/modules/WEBSOCKET_GATEWAY.md)와 [`../TODO.md`](../TODO.md).

- `RedisSessionLocationAdapter`의 세션 TTL 3분 하드코딩(`// TODO: 주입받기`)
- WebSocket 핸드셰이크 `?access_token=` 토큰 전달·`X-User-Id` 주입 경로(TODO 1.5)
