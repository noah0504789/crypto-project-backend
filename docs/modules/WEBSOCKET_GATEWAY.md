# WEBSOCKET_GATEWAY — websocket-gateway 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-25
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - STOMP 엔드포인트·prefix·destination(`StompConfig`, `common-core/StompDestination`, `websocket-gateway.yml`) 변경
>   - Kafka 소비 바인딩(`websocket-gateway.yml`의 `spring.cloud.stream.*`) 또는 소비 계약(`ChatMessageBroadcastEvent`, `MyChatRoomBadgeEvent`, `WebNotificationEvent`) 변경
>   - gRPC 소비 계약(`chatmessage.v1`, `GrpcChatMessageCommandAdapter`) 변경
>   - STOMP wire payload(`StompChatMessagePayload`, `*AckPayload`, `*BadgePayload`, `*WebNotificationPayload`) 변경
>   - 세션 위치(`LocalSessionCache`, `RedisSessionLocationAdapter`, `WebSocketSessionEventHandler`) 변경

## 1. 문서 목적과 기준 시점

이 문서는 `websocket-gateway` 서비스의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../websocket-gateway/CLAUDE.md`](../../websocket-gateway/CLAUDE.md)에 있으며 여기서는 반복하지 않는다. 아웃바운드 wire payload 구조 요약은 [`../ARCHITECTURE.md §7.4`](../ARCHITECTURE.md)에도 있다.

## 2. 모듈 역할

프론트와 STOMP over WebSocket으로 연결되는 **실시간 게이트웨이**. 두 방향을 담당한다.

1. **인바운드(송신)**: 클라이언트의 STOMP `/msg/chat.send`를 받아 chat 서비스에 gRPC(`chatmessage.v1`)로 저장 요청하고, 결과를 ACK로 돌려준다.
2. **아웃바운드(수신·push)**: chat/notification이 Outbox로 발행한 브로드캐스트 Kafka 이벤트를 소비해, **해당 인스턴스에 연결된 사용자에게만** STOMP로 push한다.

메시지 영속·수신자 판정은 이 모듈이 아니라 chat/notification의 몫이다. websocket-gateway는 **연결·라우팅·push**만 책임진다(상태는 세션 위치 캐시뿐). gRPC 서버는 노출하지 않는다(`grpc.server.enabled: false`).

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:websocket-gateway:*` (헥사고날 멀티모듈). 실행 모듈 `:websocket-gateway:websocket-gateway-bootstrap`(`ext.dockerImageName = "crypto-websocket-gateway"`).
- 실행 클래스: `org.example.websocket.gateway.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`).
- app name: `websocket-gateway`. 포트 `8100`(컨텍스트 경로 없음).
- 저장소: **Redis Cluster**(세션 위치, hash tag `{session}`)만. DB 없음. 로컬 세션은 Caffeine.
  - DB가 없는데도 소비하는 이벤트 계약(`chat-contract`/`notification-contract` → `common-outbox` → `common-jpa`; broadcast 이벤트가 `AbstractOutboxEvent`를 상속하고 그 base가 JPA `@Entity Outbox`를 참조)이 `spring-boot-starter-data-jpa`를 **전이로** classpath에 끌어온다. 그대로 두면 `DataSourceAutoConfiguration`이 강제 활성화돼 datasource url 없이 부팅이 깨진다. 그래서 `websocket-gateway.yml`에서 `spring.autoconfigure.exclude`로 `DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`을 제외한다. **이 제외를 지우면 부팅이 실패한다**(전이 JPA는 계약 구조상 제거하기 어렵다).
- gRPC 클라이언트: `chat-client`(공통 `application.yml`의 `uri.discovery.chat-service` 참조, plaintext, max inbound 16MB). gRPC 서버 비활성.
- Config Server 연동: `spring.cloud.config.name: websocket-gateway,eureka-client,kafka,redis,monitoring`. 공유 `api-contract.*`는 Config Repository 루트 `application.yml`에서 자동 병합된다.
- 관측성: bootstrap이 **OpenTelemetry javaagent**(`copyOtelAgent`)를 빌드 산출물에 포함. Micrometer/Prometheus + `ws_active_sessions` gauge.

## 4. 모듈 구조 (헥사고날)

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `websocket-gateway-application` | application | UseCase/Service, Port(in/out), Command/Result, `LocalSessionCache` | `common-grpc`, caffeine |
| `websocket-gateway-adapter-in` | adapter-in | STOMP(`StompController`, `StompConfig`, 세션 이벤트), Kafka 바인더, 이벤트→커맨드 매퍼 | `websocket-gateway-application`, `chat-contract`, `notification-contract`, starter-websocket, stream-kafka |
| `websocket-gateway-adapter-out` | adapter-out | gRPC(chat), STOMP push 어댑터, Redis 세션, id 생성, metrics | `common-core/id/grpc/redis`, `chat-client`, grpc-netty/client-starter |
| `websocket-gateway-bootstrap` | 실행 | `Main`, `application.yml`, OTEL agent | 위 3개 + actuator/config/eureka/bus/prometheus |

- 서브도메인: `chatmessage`(송신·브로드캐스트), `chatroom`(뱃지), `notification`(push), `session`(위치).
- 소비 계약을 위해 adapter-in이 `chat-contract`(`ChatMessageBroadcastEvent`, `MyChatRoomBadgeEvent`)·`notification-contract`(`WebNotificationEvent`)에, adapter-out이 `chat-client`(gRPC)에 의존한다.

## 5. 인바운드 — 메시지 송신 (STOMP → gRPC → ACK)

```
클라이언트 STOMP SEND /msg/chat.send  (StompChatMessageSendRequest)
  → StompController @MessageMapping("/chat.send")  (@Valid)
  → StompChatMessageMapper.toCommand  (messageId = ObjectId 생성, gateway-side)
  → ChatMessageSendService.send
      → ChatMessageCommandPort.save  → gRPC chat chatmessage.v1 save (비동기 CompletableFuture)
          · 성공 → ChatMessageAckPort.success → /user/queue/chat/ack (성공 ACK)
          · 실패 → GrpcClientException.resolve(code)
                    · recordable → metrics
                    · DEADLINE_EXCEEDED → gRPC hardDelete 보상(저장됐을 수 있는 메시지 제거)
                    · → ChatMessageAckPort.failure → /user/queue/chat/ack (실패 ACK, code)
```

- 요청 검증(`StompChatMessageSendRequest`): `clientMessageId`/`roomId`/`writerId` `@NotBlank`, `content` `@NotBlank`+`@Size(max=1000)`. STOMP 예외는 `StompChatMessageExceptionHandler`.
- **messageId는 게이트웨이가 gRPC 호출 전에 생성**(`MessageIdGenerateAdapter` → `ObjectIdGenerator`)해 클라이언트가 `clientMessageId`로 상관(correlate)할 수 있게 한다.
- **DEADLINE_EXCEEDED 보상**: 저장 응답이 데드라인을 넘기면 메시지가 chat에 저장됐을 수 있으므로 `hardDelete`(reason=save timeout)로 되돌린다(중복/유령 메시지 방지). hardDelete 실패는 로그+metrics만.
- 저장은 `chatmessage.v1` gRPC 계약(→ [`CHAT.md §10`](CHAT.md)). ACK/실패코드는 프론트 계약.

## 6. 아웃바운드 — 브로드캐스트 push (Kafka → STOMP)

`KafkaWebsocketGatewayBinder`가 3개 Kafka consumer를 등록한다. 모두 **인스턴스별 고유 group**(`...-${app.instance-id}`)·`concurrency: 2`·`ack-mode: record`라, 전 인스턴스가 같은 이벤트를 받아 **자기 로컬 세션 보유자에게만** 전달한다.

### 컨슈머 멱등 전략

| 컨슈머 이벤트 | 하는 일 | 사용한 전략 |
|---|---|---|
| `ChatMessageBroadcastEvent` | 로컬 세션의 채팅방 구독자에게 STOMP 메시지 push | 각 클라이언트가 안정적인 `messageId`로 중복 제거; 서버 push는 best-effort |
| `MyChatRoomBadgeEvent` | 로컬 사용자 세션에 방 배지 상태 push | 최신 상태를 다시 적용할 수 있는 payload와 클라이언트 상태 갱신으로 수렴 |
| `WebNotificationEvent` | 로컬 사용자 세션에 알림 push | 안정적인 `notificationId`로 클라이언트 중복 제거; 영속 알림함 REST 조회로 reconciliation |

세 consumer는 인스턴스별 group으로 모든 gateway 인스턴스가 처리해야 한다. 공유 `(consumer_name,event_id)` Inbox를 적용하면 한 인스턴스의 선점이 다른 인스턴스의 로컬 세션 전송을 차단하므로 사용하지 않는다. Kafka `event_id`는 로그·추적에 사용하며, 서버 측 중복 억제가 필요해지면 공유 키가 아니라 `(instanceId,eventId)` 범위의 짧은 best-effort 기록만 고려한다. STOMP 전송은 DB 트랜잭션으로 원자화할 수 없으므로 exactly-once로 간주하지 않는다.

| consumer | 소비 토픽 | 이벤트 | push 대상(로컬 세션 보유 시) |
|---|---|---|---|
| `chatMessageBroadcastEventConsumer` | `chatmessage-broadcast-event` | `ChatMessageBroadcastEvent{payload, memberIds, clientMessageId}` | `/topic/chat/{roomId}`(방 공유 토픽), memberIds에 로컬 세션 있으면 전송 |
| `myChatRoomBadgeEventConsumer` | `chatroom-broadcast-event` | `MyChatRoomBadgeEvent` | 멤버별 `/user/queue/chat/badge` |
| `webNotificationEventConsumer` | `web-notification-broadcast-event` | `WebNotificationEvent` | 수신자 `/user/topic/notification/` |

- **로컬 세션 필터링**: 각 push 어댑터가 `LocalSessionCache.hasUser(...)`로 이 인스턴스에 연결된 사용자만 전송한다. 없으면 skip(로그). 사용자가 붙어 있는 인스턴스가 실제 전달을 담당한다.
- **memberIds는 로컬 라우팅용**(wire에 싣지 않음): `ChatMessageBroadcastEvent`는 nested `payload`+`memberIds`지만, `/topic/chat/{roomId}`로 나가는 wire는 flat `StompChatMessagePayload{ messageId, roomId, writerId, content, timestamp(long), clientMessageId }`다(§8). 변환은 `ChatMessageBroadcastEventMapper` → `StompChatMessagePayload.from`.
- **best-effort**: 이 소비자들은 DLQ 소비/재시도가 없다(`ack-mode: record`). 실시간 push는 유실돼도 클라이언트가 REST로 재조회 가능한 성격이라 durable 영속(chat/notification)과 구분된다.

## 7. 세션 위치 관리

연결된 사용자의 위치를 **로컬(Caffeine) + 전역(Redis)** 이중으로 관리한다(`WebSocketSessionEventHandler`, `@EventListener`).

- **`LocalSessionCache`**(Caffeine, max 500k): `sessionId→userId`, `userId→Set<sessionId>`. **이 인스턴스에 연결된 세션만**. push 대상 판정(`hasUser`)의 근거.
- **`RedisSessionLocationAdapter`**(`SessionLocationPort`, Redis hash `{session}:user:{userId}` field=sessionId value=serverId): 어느 사용자가 어느 인스턴스(serverId)에 붙었는지. TTL 3분.
- 이벤트: **connect** → 로컬 register + Redis save(+TTL); **subscribe** → Redis `refreshTtl`; **disconnect** → `deleteIfServerMatches`(serverId 일치할 때만 삭제, 재접속 레이스 방지) + 로컬 remove. `ws_active_sessions` gauge 갱신.
- **핸드셰이크 인증**: `StompConfig`의 `determineUser`가 `X-User-Id` 헤더로 `Principal`을 만든다(없으면 연결 거부). 이 헤더는 업스트림(게이트웨이/oauth2-client 핸드셰이크 필터)이 주입한다(웹소켓 토큰 전달 방식은 TODO 1.5와 연결).

## 8. STOMP 계약 (프론트·부하테스트 의존)

- **엔드포인트**(`websocket.yml`): SockJS `/ws`, native `/ws-native`. `setAllowedOriginPatterns("*")`.
- **prefix**: application `/msg`(@MessageMapping), user `/user`(`convertAndSendToUser`), broker simple `/topic`,`/queue`.
- **destination**(`common-core/StompDestination`):

| 방향 | destination | 전송 방식 | payload |
|---|---|---|---|
| inbound | `/msg/chat.send` | `@MessageMapping("/chat.send")` | `StompChatMessageSendRequest{clientMessageId, roomId, writerId, content}` |
| outbound | `/topic/chat/{roomId}` | `convertAndSend`(방 공유) | `StompChatMessagePayload{messageId, roomId, writerId, content, timestamp(long), clientMessageId}` |
| outbound | `/user/queue/chat/ack` | `convertAndSendToUser` | `StompChatMessageAckPayload`(성공/실패, errorCode) |
| outbound | `/user/queue/chat/badge` | `convertAndSendToUser` | `StompMyChatRoomBadgePayload` |
| outbound | `/user/topic/notification/` | `convertAndSendToUser` | `StompWebNotificationPayload` |

- 이들은 프론트와 `websocket-gateway/k6` 부하 테스트가 의존하는 **외부 계약**이다. 변경 전 의존성 확인(→ `../../.claude/rules/external-contracts.md`). 특히 `/topic/chat/{roomId}` wire는 내부 Kafka `ChatMessageBroadcastEvent`와 구조가 다르다(flat vs nested).
- 메시지 변환: `MappingJackson2MessageConverter`(JSON), 커스텀 executor(broker/inbound/outbound `ThreadPoolTaskExecutor`), broker cacheLimit 8192.

## 9. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. websocket-gateway 관련 항목:

- **TODO 2.6** — `RedisSessionLocationAdapter.SESSION_TTL`이 3분으로 하드코딩(`// TODO: 주입받기`). subscribe마다 갱신되나 값 자체는 Config 주입이 아니다. 주입/조정 여부 확인 필요.
- **TODO 1.5**(기존, oauth2-client) — WebSocket 핸드셰이크의 `?access_token=` 쿼리 토큰 전달과 `X-User-Id` 주입 경로. 핸드셰이크 인증 전제와 연결.

## 10. 테스트 현황

- application: `ChatMessageSendServiceTest`, `LocalSessionCacheTest`
- adapter-out: `GrpcChatMessageCommandAdapterTest`, `RedisSessionLocationAdapterTest`
- 부하: `chat_ws_gw_stress_test_result`(리포지토리 루트, k6 계열 산출물)

## 11. 컴파일 · 테스트 · CI 명령

- 컴파일: `./gradlew :websocket-gateway:websocket-gateway-application:compileJava` 등 서브모듈 단위.
- 서브모듈 테스트: `./gradlew :websocket-gateway:websocket-gateway-application:test`, `:websocket-gateway:websocket-gateway-adapter-out:test`.
- 서비스 CI: `./gradlew websocketGatewayCi`(빌드+테스트+ArchUnit 포함).
- 전체 build/test, `bootRun`, 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 12. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `common-core/StompDestination` · `StompConfig` | STOMP destination/endpoint/prefix 계약(프론트·k6) |
| `StompChatMessagePayload`/`*AckPayload`/`*BadgePayload`/`*WebNotificationPayload` | wire payload 계약 |
| `KafkaWebsocketGatewayBinder` · `websocket-gateway.yml` stream | 소비 토픽·group(per-instance)·concurrency |
| `GrpcChatMessageCommandAdapter` | `chatmessage.v1` 소비(저장·hardDelete 보상) |
| `LocalSessionCache`/`RedisSessionLocationAdapter`/`WebSocketSessionEventHandler` | 세션 위치·push 라우팅 정합성 |

## 13. 관련 문서와 rules

- 상류(메시지 저장·브로드캐스트 발행): [`CHAT.md`](CHAT.md)(`chatmessage.v1`, broadcast 이벤트), [`NOTIFICATION.md`](NOTIFICATION.md)(`web-notification-broadcast-event`)
- 루트 흐름: [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md)(§8 채팅 송신), wire payload [`../ARCHITECTURE.md §7.4`](../ARCHITECTURE.md)
- 인증·헤더 전파: [`API_GATEWAY.md`](API_GATEWAY.md)
- 계약/보안/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,security,architecture,testing}.md`
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
