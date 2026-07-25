# CHAT — chat 서비스 상세 기준 문서

> - **문서 상태**: 검증 완료
> - **기준 브랜치**: `main`
> - **기준 일자**: 2026-07-24
> - **검증 기준**: 실제 애플리케이션 코드 및 Config Repository(`git-config-repo/`)
> - **재검증 조건**: 아래 중 하나라도 변경되면 이 문서를 다시 검증한다.
>   - REST 경로(`git-config-repo/dynamic/chat-service.yml`의 `api-path.chat.*`) 또는 `ChatRoomController`/`ChatMessageController` 변경
>   - gRPC 계약(`protobuf/src/main/proto/chatmessage/v1/chatmessage-service.proto`) 변경
>   - Kafka 바인딩(`chat-service.yml`의 `spring.cloud.stream.*`) 또는 토픽 카탈로그(`common-core/KafkaTopic`)의 chat 항목 변경
>   - Redis 키(`common-core/RedisKey`의 `CHAT_*`) 또는 캐시 인덱스 구조 변경
>   - 도메인 모델(`ChatRoom`, `ChatMessage`, `ChatRoomCategory`, `MyChatRoomScoreCalculator`) 변경
>   - Mongo 문서/인덱스(`MongoChatRoom`, `MongoChatMessage`, `MongoChatRoomMembership`) 변경
>   - 비동기/보상 흐름(`ChatRoomEventService`, `ChatMessageEventService`, `*DlqService`, `ChatMessageScheduler`) 변경

## 1. 문서 목적과 기준 시점

이 문서는 `chat` 서비스의 구조·요청 흐름·계약·근거를 사람과 AI가 필요할 때 찾아 읽기 위한 상세 문서다. 문서와 코드가 어긋나면 코드가 기준이다(루트 `docs/ARCHITECTURE.md` 원칙과 동일). 짧은 작업 규칙은 [`../../chat/CLAUDE.md`](../../chat/CLAUDE.md)에 있으며 여기서는 반복하지 않는다.

## 2. 모듈 역할

실시간 채팅의 소유 서비스. **채팅방(chatroom)** 과 **채팅 메시지(chatmessage)** 두 서브도메인을 담당한다. 방 생성/수정/삭제, 입장/퇴장, 읽음 활동(activity) 갱신, 인기방/내 방/방 상세/메시지 목록 조회, 그리고 메시지 저장·하드삭제를 처리한다.

외부에는 두 인터페이스를 노출한다.
- **REST**(게이트웨이 경유): 방 목록·상세·멤버십·활동 및 메시지 목록 조회. `ChatRoomController`/`ChatMessageController`.
- **gRPC**(`chatmessage.v1`, 내부 서비스용): 메시지 저장/하드삭제. `websocket-gateway`가 STOMP로 받은 메시지를 이 gRPC로 전달한다.

실시간 브로드캐스트(프론트로 STOMP push)는 chat이 아니라 `websocket-gateway`의 책임이다 — chat은 저장/카운팅/캐시 후 Outbox로 `chatmessage-broadcast-event`/`chatroom-broadcast-event`를 발행하고, websocket-gateway가 이를 소비해 push한다. 실시간 송신 흐름 전체는 [`../SERVICE_FLOWS.md` §8–9](../SERVICE_FLOWS.md)를 참조한다.

## 3. 실행 구조와 주요 의존성

- Gradle 경로: `:chat:*` (헥사고날 멀티모듈). 실행 모듈은 `:chat:chat-bootstrap`(`ext.dockerImageName = "crypto-chat-service"`).
- 실행 클래스: `org.example.chat.Main`(`@SpringBootApplication(scanBasePackages="org.example")`, `@ConfigurationPropertiesScan`).
- app name: `chat-service`. 포트: REST `8080`, gRPC `18080`. 컨텍스트 경로 `/api/v1`(`server.servlet.context-path: /api/${server.version}`).
- 저장소: **MongoDB**(주 저장소, 방·메시지·멤버십), **Redis Cluster**(조회 캐시/인덱스, `{chat}` hash tag), **MySQL**(Outbox/DLQ 이벤트 저장 — `common-outbox` 경유, `mysql.event.*` DB).
- Config Server 연동: `application.yml`의 `spring.config.import: configserver:...`, `spring.cloud.config.name: chat-service,eureka-client,mysql,mongo,redis,kafka,monitoring`.
- 부트스트랩 의존성: `common-actuator-webmvc`, Config Client, Eureka Client, `spring-cloud-starter-bus-kafka`, Micrometer/Prometheus.

## 4. 모듈 구조 (헥사고날)

두 서브도메인 `chatroom`·`chatmessage`가 계층별로 나뉜다.

| Gradle 모듈 | 계층 | 핵심 내용 | 주요 의존 |
|---|---|---|---|
| `chat-domain` | domain | `ChatRoom`, `ChatMessage`, `ChatRoomCategory`, `MyChatRoomScoreCalculator`(프레임워크 비의존) | `common-core` |
| `chat-application` | application | UseCase/Service, Port(in/out), Command/Query/Result, 이벤트·DLQ 이벤트, 매퍼, 검증, 예외 | `chat-domain`(api), `chat-contract`, `common-outbox`, `common-redis`, `common-redisson`, data-jpa/data-mongodb, stream-kafka, caffeine |
| `chat-adapter-in` | adapter-in | REST(`ChatRoom/ChatMessageController`), gRPC(`GrpcChatMessageService`), Kafka 바인더(`KafkaChatRoom/ChatMessageBinder`) | `common-web`, `common-grpc`, `common-outbox`, `protobuf`, `chat-application` |
| `chat-adapter-out` | adapter-out | Mongo/Redis 어댑터, ObjectId 생성기, 스케줄러, infra config(Mongo/Redis/Retry/Schedule/Datasource) | `common-id`, `common-web`, `common-redis`, `common-mongo`, `chat-application`, aop, caffeine |
| `chat-bootstrap` | 실행 | `Main`, `application.yml` | 위 4개 + actuator/config/eureka/bus/prometheus |
| `chat-client` | 클라이언트 | 다른 서비스가 쓰는 gRPC 클라이언트(`ChatMessageClient`/`GrpcChatMessageClient`) | `protobuf`, grpc-client-starter |
| `chat-contract` | 계약 | Outbox 브로드캐스트 이벤트/페이로드(`ChatMessageBroadcastEvent`, `MyChatRoomBadgeEvent` 등) | `common-outbox` |

의존 방향: adapter-in/out → application → domain. `chat-client`/`chat-contract`는 **소비자용 산출물**로, chat 자신이 아니라 `websocket-gateway`가 의존한다(gRPC 호출 및 broadcast 이벤트 역직렬화).

## 5. 아키텍처 핵심 — 쓰기는 캐시-우선 + Outbox, 영속은 비동기

chat의 쓰기 경로는 **동기적으로 Redis 캐시에 반영하고 영속(MongoDB)은 Kafka를 통해 비동기로 수행**하는 구조다. 이 원칙을 먼저 이해해야 나머지 절이 읽힌다.

1. **명령(Command)**: `ChatRoomCommandService`/`ChatMessageCommandService`가 (a) Outbox 이벤트를 발행(`OutboxEventListPublishPort.publish` → MySQL Outbox 테이블 기록)하고, (b) Redis 캐시를 동기 반영한다.
2. **폴링/발행**: `outbox-poller`가 Outbox 레코드를 폴링해 Kafka 토픽으로 발행한다(chat 밖 공용 서비스).
3. **비동기 영속**: chat의 Kafka consumer(`chatRoomEventConsumer`/`chatMessageEventConsumer`)가 이벤트를 받아 `ChatRoomEventService`/`ChatMessageEventService`가 **MongoDB에 실제 write**를 수행한다(`@Transactional("chatMongoTransactionManager")`).
4. **보상**: 각 EventService는 `@Retryable`(3회, backoff 100ms×2) 후 `@Recover`로 DLQ 이벤트를 발행한다. DLQ는 `chatRoomDlqEventConsumer`/`chatMessageDlqEventConsumer`가 소비해 `*DlqService`로 처리하고 `DlqService.complete/fail`로 상태를 남긴다.
5. **캐시 폴백**: 명령 중 캐시 동기 반영이 실패하면(§7 `cache*Safely`) 로그 후 별도 캐시-복구 Outbox 이벤트(`ChatRoomCacheSaveEvent`/`...InvalidateEvent` 등)를 발행해 비동기로 캐시를 재구성/무효화한다.

읽기 경로는 **캐시-우선, 미스 시 Mongo 로드 + 워밍업**이다(§8). 즉 정상 흐름에서 REST 조회는 Redis만 조회하며, 캐시가 비었거나 인덱스가 없으면 `*QueryRepairService`가 분산 락(`DistributedLockExecutor`, `CACHE_WARM_UP`) 아래 Mongo에서 로드해 캐시를 채운 뒤 반환한다.

## 6. 주요 클래스와 책임

| 클래스 | 경로(요약) | 책임 |
|---|---|---|
| `ChatRoomController` | `chat-adapter-in/.../chatroom/adapter/in/web/` | 방 REST 10개 엔드포인트(§9) |
| `ChatMessageController` | `chat-adapter-in/.../chatmessage/adapter/in/web/` | 메시지 목록 조회 1개(§9) |
| `GrpcChatMessageService` | `chat-adapter-in/.../chatmessage/adapter/in/grpc/` | gRPC `save`/`HardDelete`(§10), 취소/데드라인 감지 |
| `KafkaChatRoomBinder` / `KafkaChatMessageBinder` | `chat-adapter-in/.../adapter/in/stream/` | Kafka consumer 함수 빈(이벤트/DLQ) |
| `ChatRoomCommandService` | `chat-application/.../chatroom/application/service/` | 방 create/update/join/leave/activity/delete — Outbox 발행 + 캐시 동기 반영(§7) |
| `ChatRoomQueryService` | `chat-application/.../chatroom/application/service/` | 인기방/내 방/방 상세 조회(캐시-우선), lastRead·unread 계산 |
| `ChatRoomQueryRepairService` | `chat-application/.../chatroom/application/service/` | 캐시 미스 복구(분산 락 하에 Mongo 로드 + 워밍업) |
| `ChatRoomEventService` | `chat-application/.../chatroom/application/service/` | 방 이벤트 비동기 영속 + 캐시 복구, `@Retryable`/`@Recover`→DLQ |
| `ChatRoomDlqService` | `chat-application/.../chatroom/application/service/` | 방 DLQ 이벤트 재처리 |
| `ChatMessageCommandService` | `chat-application/.../chatmessage/application/service/` | 메시지 save/hardDelete(§10), Outbox 3종 발행 + 캐시 |
| `ChatMessageQueryService` | `chat-application/.../chatmessage/application/service/` | 메시지 목록 조회(캐시-우선, 미스 시 repair) |
| `ChatMessageQueryRepairService` | `chat-application/.../chatmessage/application/service/` | 메시지 캐시 미스 복구 |
| `ChatMessageEventService` | `chat-application/.../chatmessage/application/service/` | 메시지 이벤트 비동기 영속(멱등) + 방 카운터/스코어 갱신, →DLQ |
| `MyChatRoomScoreCalculator` | `chat-domain/.../chatroom/domain/service/` | 내 방 정렬 스코어(안읽음 가중치) |
| `MongoChatMessageAdapter` / `MongoChatRoomAdapter` | `chat-adapter-out/.../persistence/` | `*PersistencePort` 구현(MongoDB) |
| `RedisChatMessageAdapter` / `RedisChatRoomAdapter` | `chat-adapter-out/.../cache/` | `*CachePort` 구현(Redis Cluster) |
| `RedisCollectionRegistry` | `chat-application/.../infra/redis/` | master/replica `RedisSet`/`RedisZSet` 캐싱 획득 |
| `ChatMessageScheduler` | `chat-adapter-out/.../scheduler/` | 매일 03:00 캐시에서 7일 초과 메시지 제거(§13) |
| `ObjectIdChatRoomIdGeneratorAdapter` | `chat-adapter-out/.../id/` | 방 id = Mongo `ObjectId`(hex) 생성 |

## 7. 방 명령 흐름 (ChatRoomCommandService)

각 명령은 **Outbox 이벤트 발행 → 캐시 동기 반영**의 2단계다. `chatroom` 명령 서비스는 `@Transactional`을 쓰지 않는다(영속은 비동기 이벤트가 담당).

| 명령 | Outbox 이벤트(→`chatroom-event`) | 캐시 동기 반영 | 비고 |
|---|---|---|---|
| `create(cmd)` | `ChatRoomPersistedEvent` | `cache.save` | id는 `idGenerator.generate()`(ObjectId). 이어서 `activity(id, hostId, 0, 0)` 호출로 호스트 활동 시딩 |
| `save(domain)` | `ChatRoomPersistedEvent` | `cache.save` | create가 내부적으로 사용 |
| `update(cmd)` | `ChatRoomUpdatedEvent` | `cache.updateRoom` | 캐시 갱신 위해 Mongo에서 `oldTitle` 조회(제목 유니크 인덱스 갱신용) |
| `join(id, memberId)` | `ChatRoomJoinedEvent` | `cache.joinMembership` | Mongo에서 방 로드 후 `addMember`. 이미 멤버면 false 반환(no-op) |
| `leave(id, memberId)` | `ChatRoomLeavedEvent` | `cache.leaveMembership` | **마지막 멤버면 `delete`로 전환** |
| `activity(cmd)` | `ChatRoomActiveEvent` | `updateLastReadSeq` + `updateActivityScore` | 읽음 seq/시각 반영 |
| `delete(id)` | `ChatRoomDeletedEvent` | `cache.deleteRoom` | 방 로드 후 category/title/memberIds 확보 |

- 이벤트 발행 실패는 `TemporaryOutboxPersistenceException`은 그대로 전파(재시도 대상), 그 외는 `ChatRoomEventPublishException`으로 감싼다.
- **캐시 폴백**: `cache*Safely(...)`가 `RuntimeException`을 잡아 로그 후 캐시-복구 Outbox 이벤트를 추가 발행한다.
  - save 실패 → `ChatRoomCacheSaveEvent`(Mongo에서 최신 반영해 warmUp)
  - update 실패 → `ChatRoomCacheUpdateEvent`(oldTitle 포함, `recoverRoomUpdate`)
  - join/leave 실패 → `ChatRoomCacheInfoInvalidateEvent`(방 정보 무효화)
  - activity 실패 → `ChatRoomCacheActivityInvalidateEvent`(멤버 활동 무효화)
  - delete 실패 → `ChatRoomCacheDeleteEvent`
- 방을 찾지 못하면 `ChatRoomNotFoundException`(`update`/`join`/`leave`/`delete`).

## 8. 조회 흐름 (Query — 캐시-우선 + repair)

- **방 상세**(`getRoom`): `cache.findById` → 미스 시 `queryRepairService.repairRoom`(분산 락, Mongo `findByIdWithLatestMessage` + `warmUp`, 없으면 `ChatRoomNotFoundException`).
- **내 방 상세/목록**(`getMyRoom`/`listMyRooms`): 방 + `lastReadSeq`(캐시 → 미스 시 Mongo)로 `MyChatRoomSummary` 구성. lastRead 캐시 미스면 `refreshActiveCacheSafely`로 캐시 재적재(unread 여부에 따라 스코어 계산).
- **인기방 목록**(`listPopularRooms`): 카테고리별 zset 인덱스 조회. 커서 유무로 first/next 페이지 분기.
- **메시지 목록**(`listMessages`): 캐시 zset 조회 → **비면** `queryRepairService.repairLatest`/`repairPrev`(Mongo 로드 + 캐시 워밍업).
- `ChatRoomCacheLookupResult`가 캐시 조회 결과를 `hasNoIndex()`(인덱스 자체 없음 → 전체 repair) / `isAllHit()` / 부분 히트(miss id만 개별 repair 후 원래 순서로 merge)로 구분한다.
- 조회는 커서 페이지네이션이며, 컨트롤러가 `limit+1`로 조회 후 `CursorPages.from(result, limit, mapper)`로 다음 커서 유무를 판정한다.

## 9. REST API 계약

베이스 `${api-path.chat.base:/chat}`, 전체 경로에 컨텍스트 `/api/v1`가 붙는다. 경로 문자열은 `git-config-repo/dynamic/chat-service.yml`의 `api-path.chat.*`에서 주입.

| 메서드 | 전체 경로 | 헤더/파라미터 | 응답 |
|---|---|---|---|
| GET | `/api/v1/chat/rooms/popular` | `category`(enum, 필수), `limit`(기본 10), 커서(`ChatRoomCursor`) | 200 `CursorPage<ChatRoomResponse>` |
| GET | `/api/v1/chat/rooms/me` | `X-User-Id`, `limit`(기본 10), 커서(`MyChatRoomCursor`) | 200 `CursorPage<MyChatRoomResponse>` |
| GET | `/api/v1/chat/room/{roomId}` | path `roomId` | 200 `ChatRoomResponse` |
| GET | `/api/v1/chat/room/{roomId}/me` | `X-User-Id`, path `roomId` | 200 `MyChatRoomResponse` |
| POST | `/api/v1/chat/room/{roomId}/members` | `X-User-Id` | 신규 멤버 201(`Location`) / 기존 204 |
| DELETE | `/api/v1/chat/room/{roomId}/members` | `X-User-Id` | 204 No Content |
| PUT | `/api/v1/chat/room/{roomId}/activity` | `X-User-Id`, `lastMsgReadSeq`, `lastMsgCreatedAtMs` | 204 No Content |
| POST | `/api/v1/chat/room` | `X-User-Id`(host), `ChatRoomCreateRequest` | 201(`Location: /home`) |
| PATCH | `/api/v1/chat/room/{roomId}` | path `roomId`, `ChatRoomUpdateRequest` | 빈 body → 400, 아니면 204 |
| DELETE | `/api/v1/chat/room/{roomId}` | path `roomId` | 204 No Content |
| GET | `/api/v1/chat/room/{roomId}/messages` | path `roomId`, `limit`(기본 20), 커서(`ChatMessageCursor`) | 200 `CursorPage<ChatMessageResponse>` |

- `X-User-Id`는 게이트웨이가 검증된 JWT의 `id` claim에서 주입(`common-core/HttpHeaderKey.USER_ID_VALUE`). 컨트롤러는 이 값을 그대로 신뢰한다.
- **인가**: `create`는 인증된 사용자를 host로 방 생성(게이트웨이 `hasRole(USER)`), `update`/`delete`는 `X-User-Id` → `ChatRoom.validateHost`(소유자만), 메시지 목록 조회는 `ChatRoom.validateMember`(멤버만). **방 상세**(`GET /room/{roomId}`, `ChatRoomResponse`)는 방 레벨 공개 메타데이터(per-user 데이터 없음)라 멤버십 검사 없이 공개 열람이다 — 유저별 데이터는 `GET /room/{roomId}/me`(`MyChatRoomResponse`, `X-User-Id` 필수)로 분리돼 있다.
- 검증 규칙(`ChatRoomCreateRequest`): `title` `@UniqueChatRoomTitle`+`@NotBlank`+`@Size(max=100)`, `description` `@NotBlank`+`@Size(max=2000)`, `category` `@NotNull`. 메시지는 `chat-bootstrap`이 아니라 `chat-service.yml`의 `spring.messages.basename: messages,common-validation-messages`.
- `@UniqueChatRoomTitle` → `UniqueChatRoomTitleValidator`가 `ChatRoomQueryUseCase.existsByTitle`로 확인(캐시 `existsByTitle` → 미스 시 Mongo). null/blank는 통과.

## 10. gRPC 계약 (`chatmessage.v1`)

proto: `protobuf/src/main/proto/chatmessage/v1/chatmessage-service.proto`. 서버 구현 `GrpcChatMessageService`(adapter-in). 소비자: `websocket-gateway`(`ChatMessageClient`/`GrpcChatMessageClient` 경유).

| RPC | 요청 | 응답 | 용도 |
|---|---|---|---|
| `save` | `GrpcChatMessageRequest{clientMessageId, messageId, roomId, writerId, content}` | `GrpcChatMessageResponse{success, id, ts}` | 메시지 저장(방 검증→Outbox 발행→캐시) |
| `HardDelete` | `GrpcChatMessageHardDeleteRequest{messageId, roomId, reason}` | `GrpcChatMessageHardDeleteResponse{success, messageId, deleted, alreadyDeleted, notFound}` | 메시지 물리 삭제 + 방 카운터/스코어 보정 |

- **`save`**(`ChatMessageCommandService.save`, `@Transactional("chatMongoTransactionManager")` + `@Retryable(TemporaryOutboxPersistenceException, 3회)`):
  1. Mongo에서 방 로드(`findById`) → 없으면 `ChatRoomNotFoundException`.
  2. `chatRoom.validateWritable(writerId)` — writerId가 멤버가 아니면 `ChatRoomMembershipNotFoundException`.
  3. `ChatMessage.create(messageId, roomId, writerId, content)`(messageId는 클라이언트/게이트웨이가 부여한 ObjectId).
  4. Outbox 3종 발행: `ChatMessagePersistEvent`(→`chatmessage-event`, 영속용), `ChatMessageBroadcastEvent`(→`chatmessage-broadcast-event`, websocket-gateway push용), `MyChatRoomBadgeEvent`(→`chatroom-broadcast-event`, 뱃지용).
  5. Redis 캐시 저장(`chatMessageCachePort.save`) — 실패 시 `ChatMessageCacheException`.
  - **메시지 자체의 Mongo 저장은 여기서 하지 않는다.** `chatmessage-event`를 받은 `ChatMessageEventService.handle`이 비동기로 Mongo에 저장하고 방 `msgCnt` 증가·멤버십 스코어를 갱신한다(`DuplicateChatMessageException`은 `noRetryFor`로 멱등 처리).
- **`HardDelete`**(`hardDelete`, `@Transactional("chatMongoTransactionManager")` + `@Retryable(TemporaryChatPersistenceException, 3회)`): Mongo `hardDeleteById` → 없으면 skip. 삭제되면 `decrementMessageCount`, `findLatestMessageExcluding`로 fallback 시각 산출, `refreshMembershipScores` 후 캐시 하드삭제(`hardDeleteCacheSafely`, 실패는 로그만).
- 취소/데드라인: `save`/`hardDelete` 진입·완료 시 `Context.current().isCancelled()`를 검사해 `ChatMessageGrpcCancelledException`을 던진다. gRPC 예외 변환은 `GrpcChatMessageExceptionAdvice`.
- **계약 주의**: 이 proto는 외부 계약이다(→ `websocket-gateway`). field number 재사용 금지, 변경 시 server(chat)·client(websocket-gateway) 재빌드. 상세 절차는 `../../.claude/rules/external-contracts.md`.

## 11. Kafka 계약 (토픽·바인딩)

토픽 카탈로그: `common-core/KafkaTopic`. chat 바인딩: `chat-service.yml`의 `spring.cloud.function.definition` + `spring.cloud.stream.bindings`.

| 토픽 | 방향 | 이벤트 | 처리 |
|---|---|---|---|
| `chatroom-event` (`.dlq`) | chat 소비(group `chat`) | `ChatRoom*Event`(persist/update/join/leave/deleted/active) + 캐시-복구 이벤트 | `ChatRoomEventService` → Mongo/캐시. 실패→DLQ |
| `chatmessage-event` (`.dlq`) | chat 소비(group `chat`) | `ChatMessagePersistEvent` | `ChatMessageEventService` → Mongo 저장 + 카운터/스코어. 실패→DLQ |
| `chatmessage-broadcast-event` | chat 생산(Outbox) | `ChatMessageBroadcastEvent{payload, memberIds, clientMessageId}` | **websocket-gateway** 소비 → STOMP push |
| `chatroom-broadcast-event` | chat 생산(Outbox) | `MyChatRoomBadgeEvent{payload}` | **websocket-gateway** 소비 → 뱃지 push |

- consumer 함수: `chatRoomEventConsumer`, `chatMessageEventConsumer`, `chatRoomDlqEventConsumer`, `chatMessageDlqEventConsumer`(모두 group `chat`, `ack-mode: record`, `start-offset: latest`).
- 이벤트 payload는 `@JsonCreator`/`@JsonProperty` record·클래스로 직렬화 계약이다. `ChatMessageBroadcastEvent`(nested `payload`+`memberIds`)는 websocket-gateway가 프론트로 보내는 flat `StompChatMessagePayload`와 **다르다** — 변환은 gateway 책임(→ `docs/ARCHITECTURE.md §7.4`).
- 직접 발행이 아니라 Outbox 흐름(도메인 명령 → Outbox → outbox-poller → Kafka)을 보존한다.
- DLQ 헤더 계약: `transaction_id`, `dlq_id`(`common-core/KafkaHeaderKey`). DLQ consumer는 `event.handle(handler)` 후 `DlqService.complete/fail`.

## 12. 도메인 모델

### `ChatRoom` (`chat-domain/.../chatroom/domain/model/ChatRoom.java`)
- 필드: `id`(ObjectId hex), `hostId`, `title`, `description`, `category`, `memberIds:Set<String>`, `msgCnt`, `lastMsgId`/`lastMsgContent`/`lastMsgCreatedAt`(최신 메시지 조인 결과), `createdAt`.
- 팩토리: `create(...)`(호스트를 멤버로 시딩, `msgCnt=0`), `rehydrate(...)`(영속 복원), `rehydrateWithLatest(...)`(최신 메시지 포함 복원).
- 행위: `validateWritable(writerId)`(멤버 아니면 `ChatRoomMembershipNotFoundException`), `addMember`/`removeMember`(멱등 boolean), `isLastMember`(마지막 멤버 → 퇴장 시 삭제 전환), `hasUnread(lastReadSeq)`(`lastReadSeq < msgCnt`), `popularity()`.
- **인기도 산식은 `ChatRoomPopularityCalculator.calculate(ChatRoom)`(chatroom domain service) 한 곳에만 있다** — 현재 `msgCnt` 단일 항(가중치 1.0). 인기방 zset은 실시간 증분이 아니라 **주기 재계산**으로 유지한다:
  - `ChatRoomPopularityScheduler`(3시간마다, `@Scheduled`) → `PopularChatRoomRefreshService.refresh()`가 category별 Mongo 상위 후보(top-100)를 로드해 `ChatRoomCachePort.rebuildPopularIndex`(`rebuildPopularRoomIndex.lua`: DEL 후 `calculate()` 스코어로 zset 재구축).
  - 메시지 저장(`storeChatMessage.lua`)은 popular zset을 건드리지 않는다(`msgCnt` HINCRBY만). `ChatMessageCachePort.save`는 `category` 파라미터를 받지 않는다.
  - on-read 캐시 미스 복구(`ChatRoomQueryRepairService` → `warmUpList`)도 `calculate()`로 zset을 채운다(cold start·TTL 만료 대비).
  - 스케줄러는 category별 **전 방을 스캔**해 `calculate`로 Mongo `popularity` 필드를 bulk 갱신한 뒤 상위 100개로 Redis zset을 재구축한다(정확한 top-N 위해 풀스캔). Mongo 인기방 정렬/커서는 저장된 `popularity` 필드(`idx_category_popularity`) 기준.
  - `popularity`는 `round(calculate)`(Long)로 저장 — Redis zset score(double)와 소수 산식 시 경계에서 미세 오차 가능(정밀 불필요 전제). 커서(`ChatRoomCursor.lastPopularity`)는 Long 유지(프론트 API 계약 무변경).
  실행 간 zset은 다소 stale(수용). 항 추가(멤버 수·최근성 등)는 `calculate`에 가중치만 더하면 되며 §16 참고.

### `ChatMessage` (`chat-domain/.../chatmessage/domain/model/ChatMessage.java`)
- 필드: `id`(ObjectId hex), `roomId`, `writerId`, `content`, `createdAt`.
- 시간 변환은 `ServiceZoneUtils.ZONE_ID` 기준(`createdAtInstant`, `toEpochMillis`).

### `ChatRoomCategory`
- `FREE`, `STUDY`, `CRYPTO_CURRENCY`.

### `MyChatRoomScoreCalculator` (내 방 정렬 스코어)
- `unread(ms) = ms + 100_000_000_000_000L`(안읽음 가중치), `read(ms) = ms`. → 안읽은 방이 항상 상단 정렬.
- `rescoreKeepingUnreadState(score, fallbackMs)`: 기존 unread 상태를 보존한 채 재산정. `MongoChatRoomMembership.score`와 Redis active zset 스코어의 공통 규칙.
- **설계 의도**: 이 스코어 산정은 엄밀히는 `ChatRoom`(및 멤버십)의 도메인 로직이라 `ChatRoom`에 두는 것이 원칙에 맞다. 다만 unread 가중치 상수·재산정 규칙을 한곳에 모아 **가독성을 높이려고 상태 없는(`private` 생성자 + `static` 메서드) 도메인 서비스로 의도적으로 분리**했다. 여전히 `chat-domain` 소속 도메인 로직이며 `ChatRoom.hasUnread`와 짝을 이룬다 — 스코어 규칙을 바꾸면 두 곳(도메인 서비스 + Redis/Mongo 스코어 기록 경로)을 함께 본다.

## 13. 영속성 · 스키마 (MongoDB)

DB `chat`(authSource `chat`). `MongoConfig`가 커넥션 풀(min 20/max 200), `WriteConcern.ACKNOWLEDGED`, primary read, 스네이크케이스 필드 네이밍, `chatMongoTransactionManager`(replica-set 트랜잭션)를 구성한다. `autoIndexCreation=true`.

| 컬렉션 | 인덱스 | 비고 |
|---|---|---|
| `chat_room` | `idx_category_popularity` `{category:1, popularity:-1, _id:-1}` partial `{deleted:false}`; `title` unique partial `{deleted:false}` | soft-delete(`deleted`/`deletedAt`). 인기방 정렬/커서(저장된 `popularity` 필드)·후보 풀스캔 지원 |
| `chat_message` | `idx_room_created_id` `{room_id:1, created_at:-1, _id:-1}` partial `{deleted:false}` | 방별 최신/이전 커서 조회 |
| `chat_room_membership` | unique `{room_id, member_id}`; `my_rooms` `{member_id, score:-1, _id:-1}` | `id = "roomId|memberId"`, `lastMsgReadSeq`, `score`(unread 가중치 포함) |

- 도메인 ↔ Mongo 매핑은 각 `Mongo*.fromDomain`/`toDomain`(+`toDomainWithLatest`)에서 수행.
- 메시지 조회: `listLatestMessages`(정렬 desc + limit), `listMessagesBefore`(커스텀 repo `listMessagesBefore`), `findLatestMessageExcluding`(하드삭제 후 방 최신 시각 보정).
- `hardDeleteById`는 커스텀 repo가 처리하고 boolean(삭제 여부)을 반환한다. 잘못된 ObjectId 문자열은 `InvalidResourceRequestException`, 그 외 Mongo 예외는 `MongoChatPersistenceExceptionTranslator`가 chat 예외(`Temporary*`/`Duplicate*` 등)로 변환한다.
- 방 id는 애플리케이션이 생성한 `ObjectId`(`ObjectIdChatRoomIdGeneratorAdapter` → `common-id/ObjectIdGenerator`).

## 14. 캐시 · 인덱스 (Redis Cluster)

키는 `common-core/RedisKey` enum으로만 생성(`keyFor(...)`가 인자 수 검증). 모든 키는 hash tag `{chat}`로 슬롯을 고정한다. `RedisCollectionRegistry`가 master/replica `RedisTemplate` 기반 `RedisSet`/`RedisZSet`을 Caffeine 캐시로 재사용한다(쓰기=master, 조회=replica zset 사용 가능).

| RedisKey | 패턴 | 자료구조 | 용도 |
|---|---|---|---|
| `CHAT_ROOM_INFO` | `{chat}:room:%s` | hash | 방 정보 캐시 |
| `CHAT_ROOM_LAST_READ_SEQ` | `{chat}:room:%s:last_read` | hash/value | 멤버별 마지막 읽음 seq |
| `CHAT_ROOM_TITLE_UNIQUE_INDEX` | `{chat}:room:title:idx` | set | 제목 유니크 인덱스(`existsByTitle`) |
| `CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX` | `{chat}:popular-room:%s` | zset | 카테고리별 인기방(score=popularity) |
| `CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX` | `{chat}:active-room:%s` | zset | 멤버별 내 방(score=활동 스코어, unread 가중치) |
| `CHAT_MESSAGE_INFO` | `{chat}:message:%s` | zset | 방별 메시지(값=직렬화 메시지) |
| `CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX` | `{chat}:room:%s:message-access` | zset | 방별 메시지 접근시각 인덱스(TTL 제거용) |

- `%s` 인자: room/message는 roomId, popular은 category, active는 memberId.
- 캐시 조회 실패의 fail-open 정책·코덱은 `common-redis`(`RedisValueCodec`, `redisChatMessageCodec` 등)에 있다.

## 15. 스케줄러 · 트랜잭션 · 재시도

- **스케줄러**(`ChatMessageScheduler`, `@Scheduled(cron="0 0 3 * * *")`): 매일 03:00, `CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX`를 `SCAN`하며 접근시각이 7일(`Duration.ofDays(7)`) 초과인 메시지를 방별 message zset과 access zset에서 제거한다. 실패 시 `ChatCacheException`. `@EnableScheduling`은 `ScheduleConfig`.
- **트랜잭션 경계**: 모든 Mongo write 경로는 named 매니저 `@Transactional("chatMongoTransactionManager")`. `ChatMessageCommandService`(save/hardDelete), `ChatMessageEventService`(persist), `ChatRoomEventService`의 leave/delete/cache-warm 핸들러가 사용. `chatroom` 명령 서비스(create/update/join/activity)는 트랜잭션 없이 Outbox+캐시로만 동작한다.
- **재시도/보상**: `@Retryable`(`TemporaryChatPersistenceException`/`TemporaryChatCacheException`/`TemporaryOutboxPersistenceException`, maxAttempts 3, backoff 100ms×2) + `@Recover`. Recover는 각 이벤트별 DLQ 이벤트를 발행하며, DLQ 발행조차 실패하면 `[RECOVER-FALLBACK]` 로그만 남긴다(`RetryConfig`).

## 16. 확인 필요 항목

미해결 확인/결정 항목은 [`../../TODO.md`](../../TODO.md)에서 통합 관리한다. chat 관련 항목:


## 17. 테스트 현황

계층별 테스트가 존재한다(세부 내용은 이 문서 검증 범위 밖, 필요 시 파일 직접 확인).
- domain: `ChatRoomTest`
- application: `ChatRoomCommandServiceTest`, `ChatRoomQueryServiceTest`, `ChatRoomQueryRepairServiceTest`, `ChatRoomEventServiceTest`, `ChatRoomDlqServiceTest`, `ChatMessageCommandServiceTest`, `ChatMessageQueryServiceTest`, `ChatMessageQueryRepairServiceTest`, `ChatMessageEventServiceTest`, `UniqueChatRoomTitleValidatorTest`
- adapter-in: `ChatRoomControllerMvcTest`, `ChatMessageControllerMvcTest`, `GrpcChatMessageServiceTest`, `GrpcChatMessageExceptionAdviceTest`, `KafkaChatMessageBinderTest`, `KafkaChatRoomBinderTest`, `GlobalExceptionHandlerTest`
- adapter-out: `Mongo*RepositoryImplTest`/`Mongo*AdapterTest`(room·message·membership), `RedisChatRoomAdapterTest`, `RedisChatMessageAdapterTest`, `ChatMessageSchedulerTest`, `MongoChatPersistenceExceptionTranslatorTest`

## 18. 컴파일 · 테스트 · CI 명령

- 컴파일(가장 좁게): `./gradlew :chat:chat-application:compileJava` 등 서브모듈 단위.
- 서브모듈 테스트: `./gradlew :chat:chat-application:test`, `:chat:chat-adapter-in:test`, `:chat:chat-adapter-out:test`, `:chat:chat-domain:test`.
- 서비스 CI(빌드+테스트+ArchUnit): `./gradlew chatCi`(루트 `build.gradle`). ArchUnit(`:common:common-arch-test:test`) 포함.
- 전체 build/test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 19. 변경 위험도가 높은 파일

| 파일 | 이유 |
|---|---|
| `protobuf/.../chatmessage/v1/chatmessage-service.proto` | gRPC 외부 계약. 변경 시 websocket-gateway 재빌드 |
| `common-core/KafkaTopic`(chat 항목) · `chat-service.yml` stream 바인딩 | 토픽·바인딩 계약. producer/consumer 타입·DLQ 함께 |
| `common-core/RedisKey`(`CHAT_*`) | 캐시 키·hash tag 계약. 인덱스 구조와 함께 |
| `chat-contract/.../ChatMessageBroadcastEvent`·`MyChatRoomBadgeEvent` | Kafka broadcast payload 계약(→ websocket-gateway 역직렬화) |
| `Mongo*`(room/message/membership) 인덱스·partial filter | 커서 조회 성능·유니크 제약 |
| `ChatRoom`/`MyChatRoomScoreCalculator` | 도메인 불변식·정렬 스코어 규칙 |
| `git-config-repo/dynamic/chat-service.yml` | REST 경로·포트·Kafka/DB. 게이트웨이 route와 함께 봐야 함 |

## 20. 관련 문서와 rules

- 루트 구조/흐름: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../SERVICE_FLOWS.md`](../SERVICE_FLOWS.md)(§8–9 채팅 흐름), 코드 스타일 [`../CODE_STYLE.md`](../CODE_STYLE.md)
- 실시간 push 상대편: [`API_GATEWAY.md`](API_GATEWAY.md)(경로·헤더 전파), websocket-gateway는 별도 모듈 문서 미작성(코드: `websocket-gateway/.../adapter/in/websocket/`, `.../adapter/out/.../stomp/`)
- 계약/보안/아키텍처/테스트 rules: `../../.claude/rules/{external-contracts,security,architecture,testing}.md`
- 미해결 관찰 항목 집계: [`../../TODO.md`](../../TODO.md)
