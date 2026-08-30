# websocket-gateway — 모듈 작업 지침

이 파일은 `websocket-gateway/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/WEBSOCKET_GATEWAY.md`](../docs/modules/WEBSOCKET_GATEWAY.md)를 참고한다.

STOMP destination·Kafka 소비·gRPC 계약에 걸친 변경은 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다. 프론트·k6 부하 테스트가 계약에 의존하므로 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

프론트와 STOMP over WebSocket으로 연결되는 실시간 게이트웨이(헥사고날, 실행 모듈 `websocket-gateway-bootstrap`). 담당:

1. 인바운드: STOMP `/msg/chat.send` → chat gRPC(`chatmessage.v1`) 저장 → ACK
2. 아웃바운드: `chatmessage-broadcast-event`/`chatroom-broadcast-event`/`web-notification-broadcast-event` 소비 → 로컬 세션 보유자에게 STOMP push
3. 세션 위치 관리(로컬 `LocalSessionCache` + Redis `{session}`)

메시지 영속·수신자 판정은 이 모듈이 아니다(chat/notification). websocket-gateway는 **연결·라우팅·push**만 하고 gRPC 서버는 노출하지 않는다. `websocket-gateway/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **STOMP 계약 보존**: destination(`common-core/StompDestination`: `/topic/chat/{roomId}`, `/queue/chat/ack`, `/queue/chat/badge`, `/topic/notification/`), 인바운드 `/msg/chat.send`, endpoint(`/ws`, `/ws-native`), prefix(`/msg`, `/user`)는 프론트·k6 부하 테스트가 의존하는 외부 계약이다. 변경 전 의존성 확인(→ `../.claude/rules/external-contracts.md`).
- **wire payload 계약**: `/topic/chat/{roomId}`로 나가는 wire 는 봉투 `StompChatMessageBatchPayload{ roomId, messages[] }`이고, 각 원소가 flat `StompChatMessagePayload`다. 내부 Kafka `ChatMessageBroadcastEvent`(nested `payload`)와 **다르다** — 변환(`ChatMessageBroadcastEventMapper`)과 배칭은 게이트웨이 책임이다. **배칭을 꺼도 1건짜리 봉투로 나간다**(설정으로 wire 형식이 갈리지 않는 것이 계약의 일부다).
- **로컬 전달 판정 유지**: 방 브로드캐스트는 `LocalSessionCache.hasLocalSubscriber(roomId)`(SUBSCRIBE/UNSUBSCRIBE 로 유지되는 방별 세션 레지스트리), 뱃지·알림은 `hasUser(userId)`로 이 인스턴스 연결자에게만 보낸다. 이벤트에 멤버 목록을 실어 판정하던 방식은 outbox 행 크기가 방 크기에 비례해 걷어냈다(PR #271) — 되돌리지 않는다. Kafka 소비 group은 **인스턴스별 고유**(`...-${app.instance-id}`)여야 전 인스턴스가 이벤트를 받아 각자 자기 세션 보유자에게 전달한다. 이 group 규칙을 공유 group으로 바꾸면 push가 유실된다(→ external-contracts).
- **송신 보상 로직 유지**: `ChatMessageSendService`는 gRPC 저장 실패 중 `DEADLINE_EXCEEDED`면 `hardDelete`로 보상한다(저장됐을 수 있는 메시지 제거). messageId는 gRPC 호출 전에 게이트웨이가 생성한다(클라이언트 상관용). 이 순서·보상을 깨지 않는다.
- **세션 이중 관리 정합성**: 로컬(`LocalSessionCache`, 축출 없는 맵)과 Redis(`{session}:user:{userId}`, TTL 3분)를 함께 갱신한다(connect save, subscribe refreshTtl, disconnect `deleteIfServerMatches`). disconnect는 serverId 일치할 때만 삭제(재접속 레이스 방지) — 이 조건을 제거하지 않는다. Redis key는 `common-core/RedisKey.SESSION_INFO`로만, hash tag `{session}` 유지.
- **best-effort push 인지**: 브로드캐스트 소비자는 DLQ/재시도가 없고, STOMP executor 큐가 포화하면 push 태스크를 버린다(`stomp.executor.rejected{pool, kind}` — `kind` 로 브로드캐스트·ACK·뱃지·알림을 갈라 읽는다. 합산하면 안 된다). **유실을 클라이언트가 감지해 재조회하는 경로는 없다** — 프론트는 재연결 시 재구독만 하고 wire payload에 방별 순번이 없어 갭 감지가 불가능하다(→ `../TODO.md` 5.5). "재조회로 복구된다"를 전제로 설계하지 않는다. durable로 바꾸려면 chat/notification 영속 경로와 함께 설계한다. 어느 거절이 무엇을 잃는지는 `../docs/SERVICE_FLOWS.md` §15 표를 본다.
- **공유 Inbox 적용 금지**: 브로드캐스트 consumer는 인스턴스별 group으로 모든 gateway가 자기 로컬 세션에 전송해야 한다. 공유 `(consumer_name,event_id)` 선점은 다른 인스턴스 push를 막으므로 적용하지 않고, 클라이언트가 `messageId`/`notificationId`로 중복 제거한다.
- **gRPC 소비 계약**: `chatmessage.v1`(save/hardDelete)은 chat이 서버, 여기가 클라이언트다. proto 변경은 chat과 함께(external-contracts). client 설정은 `websocket-gateway.yml`의 `grpc.client.chat-client`.
- **DataSource/JPA 자동설정 제외 유지**: 이 서비스는 DB가 없지만 소비 계약(`chat-contract`/`notification-contract` → `common-outbox` → `common-jpa`)이 `spring-boot-starter-data-jpa`를 전이로 끌어온다. `websocket-gateway.yml`의 `spring.autoconfigure.exclude`(`DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`)를 제거하면 `DataSourceAutoConfiguration`이 강제 활성화돼 부팅이 실패한다. 지우지 않는다(상세: `../docs/modules/WEBSOCKET_GATEWAY.md §3`).
- **common 영속 서비스 빈 스캔 제외 유지**: 같은 이유로 `Main`의 `@ComponentScan`이 `org.example.common.(outbox|dlq|inbox).*`를 `excludeFilters`로 제외한다(`OutboxService`·`DlqService` 등이 JPA Repository를 요구). 이 서비스는 이벤트 클래스만 소비하고 outbox/DLQ/Inbox는 쓰지 않는다. 이 필터를 지우면 부팅이 실패한다.
- **발신자가 결과를 모르는 실패를 만들지 않는다**: 거절·검증 실패·서버 오류 모두 `clientMessageId` 를 담은 실패 ACK 로 돌려준다(→ `../docs/SERVICE_FLOWS.md` §15). 새 실패 경로를 만들 때 이 원칙부터 확인한다 — 현재 유일한 예외는 inbound 큐 거절이다(`../TODO.md` 5.14).
- **ACK 는 `brokerChannel` 을 지나지 않는다**: `DirectStompChatMessageAckAdapter` 가 `LocalSessionCache` 에서 세션과 **구독 ID** 를 찾아 `clientOutboundChannel` 로 직접 넣는다(PR #267). 구독 ID 가 빠지면 클라이언트가 프레임을 매칭하지 못해 **조용히 전달되지 않는다** — 세션 등록·제거 경로를 늘릴 때 구독 ID 정리도 함께 본다. 실패 시 기존 경로로 폴백하며 `chat.message.ack.direct.fallback` 로 관측한다.
- **배칭·conflation 창을 임의로 바꾸지 않는다**: 메시지는 방 단위 100ms 배칭(한 건도 버리지 않음, 상한 초과 시 즉시 전송), 뱃지는 방 단위 200ms conflation(마지막 1건만 남기고 버림)이다. 뱃지에 개인별 필드가 생기면 conflation 이 깨진다. 설정은 `dynamic/websocket-gateway.yml` 에 있으나 **busrefresh 로 반영되지 않는다(재배포 필요)**.
- **핸드셰이크 인증**: `StompConfig.determineUser`는 `X-User-Id` 헤더로 Principal을 만든다(없으면 거부). 헤더 주입/토큰 전달 방식 변경은 게이트웨이·oauth2-client 핸드셰이크와 함께 본다(→ `../.claude/rules/security.md`).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`...adapter-in/.../websocket/stomp/StompController.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/stomp/StompController.java) | `/msg/chat.send` 수신 |
| [`...adapter-in/.../websocket/config/StompConfig.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/config/StompConfig.java) | STOMP endpoint/broker/executor/핸드셰이크 |
| [`...adapter-in/.../stream/KafkaWebsocketGatewayBinder.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/stream/KafkaWebsocketGatewayBinder.java) | 3개 브로드캐스트 consumer |
| [`...adapter-in/.../websocket/WebSocketSessionEventHandler.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/WebSocketSessionEventHandler.java) | connect/subscribe/disconnect 세션 |
| [`...application/.../chatmessage/application/service/ChatMessageSendService.java`](websocket-gateway-application/src/main/java/org/example/websocket/gateway/chatmessage/application/service/ChatMessageSendService.java) | 송신·ACK·hardDelete 보상 |
| [`...application/.../session/application/cache/LocalSessionCache.java`](websocket-gateway-application/src/main/java/org/example/websocket/gateway/session/application/cache/LocalSessionCache.java) | 로컬 세션·구독 인덱스(push 판정, ACK 구독 ID). 상한 축출을 두지 않는다 — 축출이 정리 경로를 끊는다 |
| [`...adapter-out/.../chatmessage/adapter/out/grpc/GrpcChatMessageCommandAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/grpc/GrpcChatMessageCommandAdapter.java) | chat gRPC save/hardDelete |
| [`...adapter-out/.../chatmessage/adapter/out/stomp/StompChatMessageBroadcastAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/stomp/StompChatMessageBroadcastAdapter.java) | `/topic/chat/{roomId}` push |
| [`...adapter-out/.../chatmessage/adapter/out/stomp/BatchingChatMessageBroadcastAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/stomp/BatchingChatMessageBroadcastAdapter.java) | 방 단위 100ms 배칭(`@Primary`) |
| [`...adapter-out/.../chatroom/adapter/out/stomp/CoalescingMyChatRoomBadgeAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatroom/adapter/out/stomp/CoalescingMyChatRoomBadgeAdapter.java) | 뱃지 방 단위 200ms conflation(`@Primary`) |
| [`...adapter-out/.../chatmessage/adapter/out/stomp/DirectStompChatMessageAckAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/chatmessage/adapter/out/stomp/DirectStompChatMessageAckAdapter.java) | ACK 직접 전송(`brokerChannel` 우회, `@Primary`) |
| [`...adapter-in/.../websocket/config/ExecutorConfig.java`](websocket-gateway-adapter-in/src/main/java/org/example/websocket/gateway/adapter/in/websocket/config/ExecutorConfig.java) | STOMP 풀·거절 정책(shedding + `kind` 태그) |
| [`...adapter-out/.../session/adapter/out/redis/RedisSessionLocationAdapter.java`](websocket-gateway-adapter-out/src/main/java/org/example/websocket/gateway/session/adapter/out/redis/RedisSessionLocationAdapter.java) | Redis 세션 위치(TTL) |
| `../git-config-repo/dynamic/websocket-gateway.yml` | STOMP·Kafka 소비·gRPC client·`websocket.session.ttl` 설정(Config Server 원격) |

## 검증 명령

- 컴파일: `./gradlew :websocket-gateway:websocket-gateway-application:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :websocket-gateway:websocket-gateway-application:test`, `:websocket-gateway:websocket-gateway-adapter-out:test`
- 서비스 CI: `./gradlew websocketGatewayCi`

전체 build, 전체 test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/WEBSOCKET_GATEWAY.md §9`](../docs/modules/WEBSOCKET_GATEWAY.md)와 [`../TODO.md`](../TODO.md).

- WebSocket 핸드셰이크 `?access_token=` 토큰 전달·`X-User-Id` 주입 경로(TODO 1.5)
- 브로드캐스트 유실을 클라이언트가 감지·복구할 경로 부재(TODO 5.5)
