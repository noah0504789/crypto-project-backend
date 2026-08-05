# SERVICE FLOWS

이 문서는 `crypto-project-backend`의 주요 기능이 실제로 어떻게 실행되는지를 사람이 읽기 위한 흐름 설명이다.
각 흐름은 다음 형식을 따른다.

```
진입점 → Application Service → Domain → Outbound Adapter → 외부 시스템 또는 다음 서비스
```

모든 흐름은 현재 코드에서 추적한 결과이며 근거 파일 경로를 함께 표기한다. 상태 표기:
- **구현됨**: 진입점부터 아웃바운드까지 코드로 추적됨
- **확인 필요**: 존재하지만 값·의도를 코드만으로 판단할 수 없음(임의로 설계/버그로 판정하지 않음)

전체 구조는 `docs/ARCHITECTURE.md`를 참고한다.

---

## 1. 로컬 회원가입 — 구현됨

```
POST /user/sign-up (UserController)
 → UserCommandUseCase.signUpLocal (UserCommandService)
 → User.ofLocal (도메인, 기본 role USER 부여) · PasswordEncoder(BCrypt)
 → UserPersistencePort → JpaUserAdapter (MySQL)
 → 201 Created (Location: /)
```

근거: `user/user-adapter-in/.../web/UserController.java`, `user/user-application/.../account/application/service/UserCommandService.java`, `user/user-adapter-out/.../account/adapter/out/JpaUserAdapter.java`. user 서비스 전체 상세는 `docs/modules/USER.md`.

---

## 2. OAuth2 로그인 — 구현됨

```
외부 provider(Google/Kakao) redirect
 → oauth2-client oauth2Login → CustomOidcUserService (프로필 추출)
 → (gRPC user.v1) UserClient.findByEmail / UserClient.signUpOauth2 (find-or-create; 구현 GrpcUserClient)
 → CustomOAuth2LoginSuccessHandler: RFC-8693 token-exchange(my-authorization-server)
 → refresh token 쿠키 설정 + SPA redirect(successRedirectUri)
```

근거: `oauth2-client/oauth2-client-application/.../oidc/.../CustomOidcUserService.java`, `.../oidc/profile/extractor/{Google,Kakao}OidcProviderProfileExtractor.java`, `oauth2-client/.../handler/CustomOAuth2LoginSuccessHandler.java`.

제공자는 Google·Kakao만 확인됨(Naver 등은 미확인). oauth2-client 전체 상세(로그인/재발급/로그아웃 흐름·쿠키·확인 필요)는 `docs/modules/OAUTH2_CLIENT.md`.

---

## 3. Access / Refresh Token 발급 — 구현됨

```
Authorization Server token endpoint
 → tokenGenerator(JwtGenerator) + jwtCustomizer(claim roles, id 주입)
 → Rs256JwtEncoder: header+payload base64url → Config Server /sign(Vault Transit)로 서명
 → CustomAuthenticationSuccessHandler: access+refresh 응답 작성
 → RedisAccessTokenAdapter / RedisRefreshTokenAdapter (Redis 저장, rotating refresh)
```

근거: `oauth2-authorization-server-adapter-in/.../config/TokenConfig.java`, `-adapter-out/.../token/adapter/out/vault/Rs256JwtEncoder.java`, `-application/.../authorization/application/CustomAuthenticationSuccessHandler.java`, `-adapter-out/.../token/adapter/out/redis/RedisRefreshTokenAdapter.java`. 서버 전체 상세(Grant·서명·Redis 저장·gRPC 계약·확인 필요)는 `docs/modules/OAUTH2_AUTHORIZATION_SERVER.md`.

토큰 claim은 `roles`, `id`가 확인됨. TTL·`aud` 검증 관련은 §끝 "확인 필요" 참조.

---

## 4. Token 갱신 — 구현됨

```
POST /auth/refresh (AuthController, oauth2-client)
 → RefreshTokenService.reissue → OAuth2RefreshTokenGrantRequest(my-authorization-server)
 → Authorization Server: reuseRefreshTokens(false) + RotatingRefreshTokenPolicy(신규 refresh 생성)
 → 새 refresh 쿠키 + Authorization 헤더 응답
```

근거: `oauth2-client/oauth2-client-adapter-in/.../web/AuthController.java`, `oauth2-client-application/.../token/application/service/RefreshTokenService.java`, `oauth2-authorization-server/.../RotatingRefreshTokenPolicy.java`.

---

## 5. Logout — 구현됨

```
POST /auth/logout (oauth2-client 로그아웃 URL)
 → CustomLogoutSuccessHandler:
     1) bearer access token → subject 해석
     2) BlacklistTokenService.register (access token 블랙리스트, auth 서버 gRPC)
     3) refresh 쿠키 삭제(maxAge 0)
     4) authorizedClientService.removeAuthorizedClient (email 기준 삭제)
 → 이후 게이트웨이 BlacklistTokenValidator가 블랙리스트 토큰 차단
```

근거: `oauth2-client/.../CustomLogoutSuccessHandler.java`, `oauth2-client-adapter-in/.../config/SecurityFilterChainConfig.java`, `spring-cloud-api-gateway/.../BlacklistTokenValidator.java`.

---

## 6. API Gateway 인증 — 구현됨

```
외부 요청 → spring-cloud-api-gateway
 → ReactiveSecurityConfig(oauth2ResourceServer, anyExchange denyAll 기본)
 → ReactiveJwtDecoderConfig: NimbusReactiveJwtDecoder(JWKS) + [issuer 검증 + BlacklistTokenValidator + RequiredUserIdClaimValidator]
 → IdentityPropagationGlobalFilter: id claim → X-User-Id 헤더로 하위 서비스 전파
 → ReactiveRouteConfig 라우팅(lb://…, /api/v1 rewrite)
```

근거: `spring-cloud-api-gateway/.../config/{ReactiveSecurityConfig,ReactiveJwtDecoderConfig,ReactiveRouteConfig}.java`, `.../filter/IdentityPropagationGlobalFilter.java`, `.../validator/RequiredUserIdClaimValidator.java`.

JWKS는 Config Server의 `/.well-known/jwks.json`에서 제공. `aud` 검증 여부는 §끝 "확인 필요" 참조.

---

## 7. WebSocket 연결 및 인증 — 구현됨

```
클라이언트 → gateway WebsocketHandshakeAuthWebFilter(order -1000)
 → ?access_token JWT 검증(id claim 필수) → X-User-Id 주입
 → websocket-gateway /ws(SockJS) 또는 /ws-native 핸드셰이크
 → StompConfig.determineUser: X-User-Id를 STOMP Principal로 설정
```

근거: `spring-cloud-api-gateway/.../filter/WebsocketHandshakeAuthWebFilter.java`, `websocket-gateway/.../adapter/in/websocket/config/StompConfig.java`.

---

## 8. 채팅 메시지 전송 — 구현됨

```
STOMP @MessageMapping("/chat.send") (StompController, websocket-gateway)
 → ChatMessageSendUseCase(ChatMessageSendService)
 → ChatMessageCommandPort → GrpcChatMessageCommandAdapter (gRPC chatmessage.v1)
 → chat GrpcChatMessageService.save → ChatMessageCommandService.save
     (방 검증, Redis 캐시 반영, Outbox 이벤트 기록)
 → 비동기 ACK: ChatMessageAckPort (/queue/chat/ack)
 → DEADLINE_EXCEEDED 시 gRPC HardDelete 보상 호출
```

근거: `websocket-gateway/.../adapter/in/websocket/StompController.java`, `.../adapter/out/.../GrpcChatMessageCommandAdapter.java`, `chat/chat-adapter-in/.../grpc/GrpcChatMessageService.java`, `chat/chat-application/.../chatmessage/application/service/ChatMessageCommandService.java`.

---

## 9. 채팅 메시지 저장 및 조회 — 구현됨

```
저장:
 ChatMessageCommandService.save → Outbox 기록
 → outbox-poller → Kafka(chatmessage-event)
 → chat ChatMessageEventService.handle → MongoChatMessageAdapter.save (MongoDB, 방 카운터/스코어 갱신)

조회:
 GET /chat/room/{roomId}/messages (ChatMessageController)
 → ChatMessageQueryService → MongoChatMessageAdapter / RedisChatMessageAdapter(캐시)
```

근거: `chat/chat-application/.../chatmessage/application/service/{ChatMessageCommandService,ChatMessageQueryService}.java`, `chat/chat-adapter-out/.../persistence/MongoChatMessageAdapter.java`, `chat/chat-adapter-in/.../web/ChatMessageController.java`. chat 서비스 전체 상세(방/메시지 명령·조회·캐시·Kafka·DLQ·확인 필요)는 `docs/modules/CHAT.md`.

---

## 10. Outbox 및 DLQ 처리 — 구현됨

```
각 서비스 도메인 이벤트 → EventUtils.raise → OutboxEventListListener → Outbox/DLQ 테이블(MySQL)

outbox-poller:
 OutboxEventScheduler(@Scheduled general/broadcast) · DlqEventScheduler(@Scheduled dlq)
 → OutboxService/DlqService.publishPending
 → KafkaEventPublisher(StreamBridge.send(destination, message))
     헤더: transaction_id, dlq_id, __TypeId__, KafkaHeaders.KEY
 → 성공: markPublished / 실패: increaseRetryCnt → isRetryExhausted 시 markFailed
 → DLQ 폴러 on/off: POST /dlq-poller/start · /stop (DlqPollerController)
```

근거: `common/common-outbox/.../adapter/in/OutboxEventListListener.java`, `outbox-poller/.../outbox/OutboxEventScheduler.java`, `.../dlq/DlqEventScheduler.java`, `.../infra/event/KafkaEventPublisher.java`, `.../dlq/DlqPollerController.java`.

---

## 11. Upbit WebSocket 데이터 수집 — 구현됨

```
Upbit WebSocket (OkHttp)
 → UpbitWebsocketClientStarter / UpbitWebsocketListener / UpbitWebsocketService (ticker 구독·역직렬화)
 → 큐 → KafkaMarketDetectionBinder.upbitTickerEventSupplier
 → Kafka(upbit-ticker-event)
```

근거: `market-detection/market-detection-bootstrap/.../upbit/{UpbitWebsocketClientStarter,UpbitWebsocketListener,UpbitWebsocketService}.java`, `.../adapter/in/stream/KafkaMarketDetectionBinder.java`.

---

## 12. 시장 데이터 처리 (Kafka Streams) — 구현됨

```
Kafka(upbit-ticker-event)
 → KafkaMarketDetectionBinder.upbitTickerAlertEventProcessor (KStream)
 → UpbitTickerProcessor.process:
     WindowStore(upbit-ticker-store, window/retention 3m)로 이동평균·변동률 계산
```

근거: `market-detection/.../adapter/in/stream/KafkaMarketDetectionBinder.java`, `.../upbit/UpbitTickerProcessor.java`, `.../upbit/StateStoreConfig.java`.

---

## 13. 마켓 알림 생성 — 구현됨

```
UpbitTickerProcessor
 → PriceAlertChangeRateThreshold.matchedBy로 임계치 매칭
 → PriceAlertDetectedEvent 발행(KStream output → price-alert-detected-event)
```

근거: `market-detection/.../upbit/UpbitTickerProcessor.java`, `market-detection/market-detection-contract/.../PriceAlertDetectedEvent.java`.

> 참고: 기존 문서는 이 출력 이벤트를 `UpbitTickerAlertEvent`/`WebNotificationBroadcastEvent`로 기술했으나, 실제 발행 계약은 `PriceAlertDetectedEvent`이며 notification 서비스가 이를 소비해 후속 이벤트를 만든다(§14).

---

## 14. 알림 전달 — 구현됨

```
notification KafkaNotificationBinder.priceAlertDetectedEventConsumer (price-alert-detected-event 소비)
 → PriceAlertNotificationCommandService.create
     수신자 조회: PriceAlertRecipientQueryPort → PriceAlertRecipientQueryAdapter
                → (gRPC market.v1) PriceAlertSettingClient.findReceiverIds(marketCode, changeRate)
 → Outbox 기록(NotificationSaveEvent + WebNotificationBroadcastEvent)

저장:
 notificationEventConsumer → NotificationEventService → MongoNotificationAdapter (MongoDB)

웹 전달:
 Outbox → outbox-poller → Kafka(web-notification-broadcast-event)
 → websocket-gateway KafkaWebsocketGatewayBinder.webNotificationBroadcastEventConsumer
 → STOMP push (/topic/notification/…)
```

근거: `notification/notification-adapter-in/.../stream/KafkaNotificationBinder.java`, `notification/notification-application/.../service/PriceAlertNotificationCommandService.java`, `notification/notification-adapter-out/.../grpc/PriceAlertRecipientQueryAdapter.java`, `websocket-gateway/.../adapter/in/stream/KafkaWebsocketGatewayBinder.java`.

---
