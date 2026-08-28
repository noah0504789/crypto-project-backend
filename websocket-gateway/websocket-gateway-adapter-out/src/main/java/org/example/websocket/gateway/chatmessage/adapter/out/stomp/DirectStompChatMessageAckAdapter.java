package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessageAckPayload;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageAckPort;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.session.application.cache.AckSubscriptionRegistry;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ACK 를 brokerChannel 을 거치지 않고 clientOutboundChannel 로 직접 보낸다.
 * 근거와 수치는 {@code TODO.md} 5.8.
 *
 * <p>브로커가 해주던 사용자 목적지 해석을 대신하므로 세션 ID 와 구독 ID 를 직접 채운다.
 * 구독 ID 가 없으면 STOMP MESSAGE 프레임이 반쪽이 되어 클라이언트가 매칭하지 못하므로,
 * 하나라도 빠지면 보내지 않고 기존 경로로 넘긴다.
 */
@Slf4j
@Primary
@Component
public class DirectStompChatMessageAckAdapter implements ChatMessageAckPort {

    private final StompChatMessageAckAdapter delegate;
    private final ChatMessageAckDirectProperties properties;
    private final LocalSessionCache localSessionCache;
    private final AckSubscriptionRegistry ackSubscriptionRegistry;
    private final MessageChannel clientOutboundChannel;
    private final MessageConverter messageConverter;
    private final String ackDestination;

    private final Counter sent;
    private final Counter fallback;

    public DirectStompChatMessageAckAdapter(
            StompChatMessageAckAdapter delegate,
            ChatMessageAckDirectProperties properties,
            LocalSessionCache localSessionCache,
            AckSubscriptionRegistry ackSubscriptionRegistry,
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel,
            @Qualifier("brokerMessageConverter") MessageConverter messageConverter,
            ApiPathProperties apiPathProperties,
            MeterRegistry registry
    ) {
        this.delegate = delegate;
        this.properties = properties;
        this.localSessionCache = localSessionCache;
        this.ackSubscriptionRegistry = ackSubscriptionRegistry;
        this.clientOutboundChannel = clientOutboundChannel;
        this.messageConverter = messageConverter;
        this.ackDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ACK_QUEUE.destination();

        this.sent = Counter.builder("chat.message.ack.direct.sent")
                .description("brokerChannel 을 건너뛰고 보낸 ACK 프레임 수")
                .register(registry);
        this.fallback = Counter.builder("chat.message.ack.direct.fallback")
                .description("세션·구독 정보가 없어 기존 경로로 넘긴 ACK 수")
                .register(registry);
    }

    @Override
    public void success(String userId, ChatMessageAckResult result) {
        send(userId, StompChatMessageAckPayload.ofSuccess(
                result.messageId(), result.clientMessageId(), result.success(), result.timestamp()
        ), () -> delegate.success(userId, result));
    }

    @Override
    public void failure(String userId, String clientMessageId, String errorCode) {
        send(userId, StompChatMessageAckPayload.ofFailure(clientMessageId, errorCode),
                () -> delegate.failure(userId, clientMessageId, errorCode));
    }

    private void send(String userId, StompChatMessageAckPayload payload, Runnable fallbackSend) {
        if (!properties.enabled()) {
            fallbackSend.run();
            return;
        }

        Set<String> sessions = localSessionCache.findSessions(userId);

        if (sessions.isEmpty()) {
            fallback.increment();
            fallbackSend.run();
            return;
        }

        boolean sentAny = false;

        for (String sessionId : sessions) {
            String subscriptionId = ackSubscriptionRegistry.findSubscriptionId(sessionId);

            if (subscriptionId == null) {
                continue;
            }

            if (sendToSession(sessionId, subscriptionId, payload)) {
                sentAny = true;
            }
        }

        if (!sentAny) {
            fallback.increment();
            fallbackSend.run();
        }
    }

    private boolean sendToSession(String sessionId, String subscriptionId, StompChatMessageAckPayload payload) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(ackDestination);

        try {
            Message<?> message = messageConverter.toMessage(payload, accessor.getMessageHeaders());

            if (message == null) {
                return false;
            }

            boolean delivered = clientOutboundChannel.send(message);

            if (delivered) {
                sent.increment();
            }

            return delivered;
        } catch (Exception e) {
            log.error("[stomp] direct ack send failed. sessionId={}", sessionId, e);

            return false;
        }
    }
}
