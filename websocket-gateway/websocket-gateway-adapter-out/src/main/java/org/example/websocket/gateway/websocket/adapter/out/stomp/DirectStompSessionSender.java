package org.example.websocket.gateway.websocket.adapter.out.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

/** 세션과 구독을 지정한 STOMP MESSAGE 를 clientOutboundChannel 로 직접 보낸다. */
@Slf4j
@Component
public class DirectStompSessionSender {

    private final MessageChannel clientOutboundChannel;
    private final MessageConverter messageConverter;

    public DirectStompSessionSender(
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel,
            @Qualifier("brokerMessageConverter") MessageConverter messageConverter
    ) {
        this.clientOutboundChannel = clientOutboundChannel;
        this.messageConverter = messageConverter;
    }

    public boolean send(String destination, String sessionId, String subscriptionId, Object payload) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);

        try {
            Message<?> message = messageConverter.toMessage(payload, accessor.getMessageHeaders());

            return message != null && clientOutboundChannel.send(message);
        } catch (Exception e) {
            log.error("[stomp] direct send failed. destination={}, sessionId={}", destination, sessionId, e);

            return false;
        }
    }
}
