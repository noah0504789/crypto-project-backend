package org.example.websocket.gateway.adapter.in.event.chatmessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.example.websocket.gateway.adapter.in.event.chatmessage.dto.ChatMessageResponse;
import org.example.common.enums.StompTopic;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageBroadcastEventConsumer {

    @Value("${spring.cloud.stream.instance-index:unknown}")
    private String instanceIndex;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    public void handle(ChatMessageBroadcastEvent event, String txId) {
        ChatMessagePayload payload = event.payload();

        if (!hasAnyLocalMember(event.memberIds())) {
            log.debug("STOMP skip. no local member session. txId={}, roomId={}, serverId={}", txId, payload.roomId(), instanceIndex);
            return;
        }

        sendChatMessage(payload, event.clientMessageId(), txId);

        log.debug("✅ STOMP 성공: txId={}, roomId={}, serverId={}", txId, payload.roomId(), instanceIndex);
    }

    private boolean hasAnyLocalMember(Set<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return false;

        return memberIds.stream().anyMatch(localSessionCache::hasUser);
    }

    private void sendChatMessage(ChatMessagePayload payload, String clientMessageId, String txId) {
        String destination = StompTopic.CHAT_ROOM.destination(payload.roomId());
        ChatMessageResponse response = ChatMessageResponse.fromPayload(payload, clientMessageId);

        try {
            stompTemplate.convertAndSend(destination, response);
        } catch (Exception e) {
            log.error("❌ STOMP 실패: txId={}, roomId={}, destination={}, serverId={}, error={}", txId, payload.roomId(), destination, instanceIndex, e.getMessage(), e);
        }
    }
}
