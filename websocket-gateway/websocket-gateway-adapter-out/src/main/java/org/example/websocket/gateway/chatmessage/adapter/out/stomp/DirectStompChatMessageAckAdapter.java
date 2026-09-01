package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessageAckPayload;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageAckPort;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ACK 를 brokerChannel 없이 clientOutboundChannel 로 직접 보낸다. 도입 근거는 PR #267 —
 * {@code convertAndSendToUser} 가 사용자 목적지를 세션 수만큼 brokerChannel 로 되돌려
 * 거절된 broker 태스크의 97% 가 ACK 였다.
 */
@Slf4j
@Component
public class DirectStompChatMessageAckAdapter implements ChatMessageAckPort {

    private final LocalSessionCache localSessionCache;
    private final MessageChannel clientOutboundChannel;
    private final MessageConverter messageConverter;
    private final String ackDestination;

    private final Counter sent;
    private final Counter failed;

    public DirectStompChatMessageAckAdapter(
            LocalSessionCache localSessionCache,
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel,
            @Qualifier("brokerMessageConverter") MessageConverter messageConverter,
            ApiPathProperties apiPathProperties,
            MeterRegistry registry
    ) {
        this.localSessionCache = localSessionCache;
        this.clientOutboundChannel = clientOutboundChannel;
        this.messageConverter = messageConverter;
        this.ackDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ACK_QUEUE.destination();

        this.sent = Counter.builder("chat.message.ack.direct.sent")
                .description("brokerChannel 을 건너뛰고 보낸 ACK 프레임 수")
                .register(registry);
        this.failed = Counter.builder("chat.message.ack.direct.failed")
                .description("세션·구독 부재나 전송 오류로 직접 보내지 못한 ACK 수")
                .register(registry);
    }

    @Override
    public void success(String userId, ChatMessageAckResult result) {
        send(userId, StompChatMessageAckPayload.ofSuccess(
                result.messageId(), result.clientMessageId(), result.success(), result.timestamp()
        ));
    }

    @Override
    public void failure(String userId, String clientMessageId, String errorCode) {
        send(userId, StompChatMessageAckPayload.ofFailure(clientMessageId, errorCode));
    }

    private void send(String userId, StompChatMessageAckPayload payload) {
        Set<String> sessions = localSessionCache.findSessions(userId);

        if (sessions.isEmpty()) {
            failed.increment();
            return;
        }

        boolean sentAny = false;

        for (String sessionId : sessions) {
            String subscriptionId = localSessionCache.findAckSubscriptionId(sessionId);

            if (subscriptionId == null) {
                continue;
            }

            if (sendToSession(sessionId, subscriptionId, payload)) {
                sentAny = true;
            }
        }

        if (!sentAny) {
            failed.increment();
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
