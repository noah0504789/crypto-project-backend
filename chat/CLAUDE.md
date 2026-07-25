# chat — 모듈 작업 지침

이 파일은 `chat/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·흐름·계약·근거는 [`../docs/modules/CHAT.md`](../docs/modules/CHAT.md)를 참고한다.

이 모듈의 변경은 대부분 **Kafka/Outbox/DLQ·트랜잭션 경계·Redis Key·Proto/gRPC**에 걸린다 — `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이므로 수정 전 영향 분석·계획을 먼저 제시한다.

## 모듈 역할과 적용 범위

실시간 채팅의 소유 서비스(헥사고날 멀티모듈, 실행 모듈 `chat-bootstrap`). 두 서브도메인 `chatroom`·`chatmessage`를 담당한다:

1. 방 생성/수정/삭제, 입장/퇴장, 읽음 활동(activity) 갱신(REST)
2. 인기방/내 방/방 상세/메시지 목록 조회(REST, 커서 페이지네이션)
3. 메시지 저장·하드삭제(gRPC `chatmessage.v1`, `websocket-gateway`가 호출)
4. 방/메시지 이벤트의 비동기 영속(MongoDB)·캐시 복구·DLQ 처리

실시간 브로드캐스트(프론트로 STOMP push)는 이 모듈이 아니라 `websocket-gateway`의 몫이다 — chat은 저장/카운팅/캐시 후 Outbox로 `chatmessage-broadcast-event`/`chatroom-broadcast-event`를 발행할 뿐이다. `chat/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 핵심 아키텍처 (변경 전 반드시 이해)

**쓰기는 캐시-우선 + Outbox, 영속(MongoDB)은 Kafka consumer가 비동기로 수행한다.** 이 비대칭을 깨지 않는다.

- 명령 서비스는 (a) `OutboxEventListPublishPort.publish`로 Outbox 이벤트 발행, (b) Redis 캐시 동기 반영만 한다. **Mongo write를 명령 서비스에서 직접 하지 않는다**(메시지 `save`·방 create/update/join/leave/activity/delete 모두).
- 실제 Mongo 영속은 `chatRoomEventConsumer`/`chatMessageEventConsumer`가 받은 이벤트를 `ChatRoomEventService`/`ChatMessageEventService`가 처리하며 일어난다.
- 읽기는 캐시-우선, 미스 시 `*QueryRepairService`가 분산 락(`DistributedLockExecutor`, `CACHE_WARM_UP`) 하에 Mongo 로드 + 캐시 워밍업.
- 이 흐름(§5 CHAT.md)을 우회해 컨트롤러/서비스에서 Repository·StreamBridge를 직접 부르지 않는다.

## 주요 변경 규칙

- **의존 방향 유지**: adapter-in/out → application → domain. `chat-domain`은 프레임워크 비의존(코어만). domain 객체가 Repository/Redis/Kafka를 직접 호출하지 않는다(→ `../.claude/rules/architecture.md`).
- **Command/Query 분리 유지**: `*CommandService`(쓰기) / `*QueryService`(읽기) / `*QueryRepairService`(캐시 미스 복구) / `*EventService`(비동기 영속·보상). UseCase 인터페이스(`ChatRoom/ChatMessageCommandUseCase`·`QueryUseCase`)로만 어댑터가 호출한다.
- **Port & Adapter**: 영속성은 `*PersistencePort` ↔ `Mongo*Adapter`, 캐시는 `*CachePort` ↔ `Redis*Adapter`로만. 서비스에서 Mongo Repository/RedisTemplate을 직접 주입하지 않는다.
- **트랜잭션 매니저 이름은 계약**: Mongo write 경로는 `@Transactional("chatMongoTransactionManager")`(replica-set 트랜잭션, `MongoConfig`). 이름을 바꾸거나 기본 매니저로 대체하지 않는다. `chatroom` 명령 서비스는 의도적으로 트랜잭션 없이 Outbox+캐시로만 동작한다 — 함부로 `@Transactional`을 붙이지 않는다.
- **Outbox/DLQ 흐름 보존**: 도메인 명령 → Outbox 발행 → outbox-poller → Kafka. `@Retryable`(3회, backoff 100ms×2) + `@Recover`→DLQ 이벤트 발행 패턴을 유지한다. 발행/영속 실패를 삼키지 말고 재시도 상태 또는 DLQ 전이를 남긴다. `ChatMessageEventService`의 멱등 처리(`DuplicateChatMessageException`은 `noRetryFor`)를 깨지 않는다.
- **캐시 폴백 유지**: 명령 중 캐시 동기 반영이 실패하면 `cache*Safely(...)`가 로그 후 캐시-복구 Outbox 이벤트(`ChatRoomCacheSaveEvent`/`...InvalidateEvent` 등)를 발행한다. 이 보상 경로를 제거하지 않는다.
- **Redis Key는 계약**: `common-core/RedisKey`의 `CHAT_*` enum(pattern + 인자 수)으로만 키를 만든다. hash tag `{chat}`(클러스터 슬롯 고정)를 영향 분석 없이 바꾸지 않는다. 내 방 정렬 스코어 규칙(`MyChatRoomScoreCalculator`의 unread 가중치)은 Redis active zset·`MongoChatRoomMembership.score` 양쪽에 걸린 계약이다.
- **Kafka 토픽/바인딩은 계약**: `common-core/KafkaTopic`의 chat 항목과 `../git-config-repo/dynamic/chat-service.yml`의 `spring.cloud.stream.*`. broadcast 이벤트(`ChatMessageBroadcastEvent`/`MyChatRoomBadgeEvent`, `chat-contract`)는 `websocket-gateway`가 역직렬화하는 payload 계약이다 — 필드/토픽 변경 시 소비자와 함께 본다(→ `../.claude/rules/external-contracts.md`).
- **gRPC 계약(`chatmessage.v1`) 변경은 external-contracts 절차**: `../protobuf/.../chatmessage/v1/chatmessage-service.proto`를 바꾸면 소비자(`websocket-gateway`)를 함께 재빌드하고 field number 재사용을 금지한다. proto 재생성: `./gradlew :protobuf:build`.
- **도메인 상태 변경은 도메인 메서드로**: 멤버십은 `ChatRoom.addMember/removeMember`, 쓰기 권한 검증은 `ChatRoom.validateWritable`(멤버 아니면 `ChatRoomMembershipNotFoundException`). 마지막 멤버 퇴장은 `isLastMember` → 방 삭제로 전환되는 규칙을 유지한다.
- **REST 경로·포트·DB·Kafka 설정은 원격 Config**: `../git-config-repo/dynamic/chat-service.yml`(`api-path.chat.*`, REST 8080/gRPC 18080, `mongo.*`, `mysql.event.*`, stream 바인딩). 경로를 바꾸면 게이트웨이 route/security와 함께 검토한다.
- **Mongo 인덱스/partial filter는 계약**: `chat_room`(`idx_category_msgCnt`, `title` unique partial), `chat_message`(`idx_room_created_id`), `chat_room_membership`(unique `{room_id,member_id}`, `my_rooms`). 커서 조회 성능·유니크가 걸려 있어 영향 분석 없이 바꾸지 않는다. `autoIndexCreation=true`.
- 인가/헤더 신뢰 관련 변경은 `../.claude/rules/security.md`도 함께 적용한다(§확인 필요).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`chat-adapter-in/.../web/ChatRoomController.java`](chat-adapter-in/src/main/java/org/example/chat/chatroom/adapter/in/web/ChatRoomController.java) | 방 REST 10개(popular/me/room/members/activity/create/update/delete) |
| [`chat-adapter-in/.../web/ChatMessageController.java`](chat-adapter-in/src/main/java/org/example/chat/chatmessage/adapter/in/web/ChatMessageController.java) | 메시지 목록 조회(커서) |
| [`chat-adapter-in/.../grpc/GrpcChatMessageService.java`](chat-adapter-in/src/main/java/org/example/chat/chatmessage/adapter/in/grpc/GrpcChatMessageService.java) | gRPC `save`/`HardDelete`, 취소/데드라인 감지 |
| [`chat-adapter-in/.../stream/KafkaChatRoomBinder.java`](chat-adapter-in/src/main/java/org/example/chat/chatroom/adapter/in/stream/KafkaChatRoomBinder.java) · [`.../KafkaChatMessageBinder.java`](chat-adapter-in/src/main/java/org/example/chat/chatmessage/adapter/in/stream/KafkaChatMessageBinder.java) | Kafka consumer 함수 빈(이벤트/DLQ) |
| [`chat-application/.../chatroom/application/service/ChatRoomCommandService.java`](chat-application/src/main/java/org/example/chat/chatroom/application/service/ChatRoomCommandService.java) | 방 명령: Outbox 발행 + 캐시 동기 반영 |
| [`chat-application/.../chatroom/application/service/ChatRoomEventService.java`](chat-application/src/main/java/org/example/chat/chatroom/application/service/ChatRoomEventService.java) | 방 이벤트 비동기 영속·캐시 복구, `@Retryable`/`@Recover`→DLQ |
| [`chat-application/.../chatmessage/application/service/ChatMessageCommandService.java`](chat-application/src/main/java/org/example/chat/chatmessage/application/service/ChatMessageCommandService.java) | 메시지 save/hardDelete, Outbox 3종 발행 + 캐시 |
| [`chat-application/.../chatmessage/application/service/ChatMessageEventService.java`](chat-application/src/main/java/org/example/chat/chatmessage/application/service/ChatMessageEventService.java) | 메시지 비동기 영속(멱등) + 방 카운터/스코어 |
| [`chat-domain/.../chatroom/domain/model/ChatRoom.java`](chat-domain/src/main/java/org/example/chat/chatroom/domain/model/ChatRoom.java) | 방 도메인(멤버십·쓰기검증·popularity) |
| [`chat-domain/.../chatroom/domain/service/MyChatRoomScoreCalculator.java`](chat-domain/src/main/java/org/example/chat/chatroom/domain/service/MyChatRoomScoreCalculator.java) | 내 방 정렬 스코어(unread 가중치) |
| [`chat-domain/.../chatroom/domain/service/ChatRoomPopularityCalculator.java`](chat-domain/src/main/java/org/example/chat/chatroom/domain/service/ChatRoomPopularityCalculator.java) | 인기도 산식 단일 정의처 `calculate(ChatRoom)`(현재 `msgCnt`, Redis zset 스코어원) |
| [`chat-adapter-out/.../persistence/MongoChatMessageAdapter.java`](chat-adapter-out/src/main/java/org/example/chat/chatmessage/adapter/out/persistence/MongoChatMessageAdapter.java) | 메시지 영속 포트 구현(MongoDB) |
| [`chat-adapter-out/.../scheduler/ChatMessageScheduler.java`](chat-adapter-out/src/main/java/org/example/chat/chatmessage/adapter/out/scheduler/ChatMessageScheduler.java) | 매일 03:00 캐시에서 7일 초과 메시지 제거 |
| [`chat-contract/.../chatmessage/ChatMessageBroadcastEvent.java`](chat-contract/src/main/java/org/example/contract/chatmessage/ChatMessageBroadcastEvent.java) | Kafka broadcast payload 계약(→ websocket-gateway) |
| `../git-config-repo/dynamic/chat-service.yml` | REST 경로·포트·Mongo/MySQL·Kafka 바인딩(Config Server 원격) |
| `../protobuf/src/main/proto/chatmessage/v1/chatmessage-service.proto` | gRPC `chatmessage.v1` 계약 |

## 검증 명령

- 컴파일: `./gradlew :chat:chat-application:compileJava`(대상 서브모듈 단위)
- 서브모듈 테스트: `./gradlew :chat:chat-application:test`, `:chat:chat-adapter-in:test`, `:chat:chat-adapter-out:test`, `:chat:chat-domain:test`
- 서비스 CI: `./gradlew chatCi`(빌드+테스트+ArchUnit 포함)
- 집계 task `:chat:test`는 대체로 빈 task다 — 서브모듈 또는 `chatCi`로 실행한다.

전체 build, 전체 test, `bootRun`, 애플리케이션 실행, 배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/CHAT.md §16`](../docs/modules/CHAT.md)와 [`../TODO.md`](../TODO.md).

- 방 `create`/`update`/`delete` 및 방/메시지 조회의 인가 부재(`ChatRoomController` `// TODO: 인가 처리하기`, `update`/`delete`는 `X-User-Id` 미수신, 멤버십 검사 없음) — 메시지 `save`(gRPC)는 `validateWritable`로 검증하는 것과 대비
- 인기도 산식은 `ChatRoomPopularityCalculator.calculate(ChatRoom)` **한 곳**. 인기방 zset은 실시간 증분이 아니라 `ChatRoomPopularityScheduler`(3시간) → `PopularChatRoomRefreshService`가 category별 Mongo top-100을 `rebuildPopularIndex`(DEL 후 재계산)로 주기 재구축한다. 메시지 저장은 popular zset을 안 건드림(`msgCnt`만). 후보 선정은 Mongo `sort(msgCnt)`. 산식에 항 추가는 `calculate`만 고치면 됨(TODO 2.3)
