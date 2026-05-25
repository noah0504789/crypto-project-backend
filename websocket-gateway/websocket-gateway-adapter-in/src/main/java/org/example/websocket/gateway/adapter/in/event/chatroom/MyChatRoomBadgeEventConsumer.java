package org.example.websocket.gateway.adapter.in.event.chatroom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompTopic;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.contract.chatroom.MyChatRoomPayload;
import org.example.websocket.gateway.adapter.in.event.chatroom.dto.MyChatRoomResponse;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyChatRoomBadgeEventConsumer {

    @Value("${spring.cloud.stream.instance-index:unknown}")
    private String instanceIndex;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    public void handle(MyChatRoomBadgeEvent event, String txId) {
        MyChatRoomPayload payload = event.payload();

        boolean sent = broadcastBadge(payload, txId);

        if (sent) {
            log.debug("✅ STOMP 처리 완료: txId={}, serverId={}", txId, instanceIndex);
            return;
        }

        log.debug("STOMP 전체 skip: txId={}, serverId={}", txId, instanceIndex);
    }

    private boolean broadcastBadge(MyChatRoomPayload payload, String txId) {
        String destination = StompTopic.CHAT_ROOM_BADGE.getPrefix();
        MyChatRoomResponse response = MyChatRoomResponse.fromPayload(payload);

        boolean sentAny = false;

        for (String memberId : payload.memberIds()) {
            if (!localSessionCache.hasUser(memberId)) {
                log.debug("STOMP skip. no local session. txId={}, memberId={}, serverId={}", txId, memberId, instanceIndex);
                continue;
            }

            if (sendToUser(memberId, destination, response, txId)) {
                sentAny = true;
            }
        }

        return sentAny;
    }

    private boolean sendToUser(String memberId, String destination, MyChatRoomResponse response, String txId) {
        try {
            stompTemplate.convertAndSendToUser(memberId, destination, response);

            log.debug("STOMP sent. txId={}, memberId={}, destination={}, serverId={}", txId, memberId, destination, instanceIndex);

            return true;
        } catch (Exception e) {
            log.error("❌ STOMP 실패: txId={}, memberId={}, destination={}, serverId={}, error={}", txId, memberId, destination, instanceIndex, e.getMessage(), e);

            return false;
        }
    }
}
