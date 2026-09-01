package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.common.enums.StompDestination;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.chatroom.adapter.out.stomp.payload.StompMyChatRoomBadgePayload;
import org.example.websocket.gateway.chatroom.application.port.out.MyChatRoomBadgePort;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.websocket.adapter.out.stomp.DirectStompSessionSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 뱃지를 brokerChannel 없이 로컬 세션의 clientOutboundChannel 로 직접 보낸다. */
@Component
public class DirectStompMyChatRoomBadgeAdapter implements MyChatRoomBadgePort {

    private final LocalSessionCache localSessionCache;
    private final DirectStompSessionSender sessionSender;
    private final String badgeDestination;

    private final Counter sent;
    private final Counter failed;

    public DirectStompMyChatRoomBadgeAdapter(
            LocalSessionCache localSessionCache,
            DirectStompSessionSender sessionSender,
            ApiPathProperties apiPathProperties,
            MeterRegistry registry
    ) {
        this.localSessionCache = localSessionCache;
        this.sessionSender = sessionSender;
        this.badgeDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ROOM_BADGE_QUEUE.destination();

        this.sent = Counter.builder("chat.badge.direct.sent")
                .description("brokerChannel 을 건너뛰고 보낸 뱃지 프레임 수")
                .register(registry);
        this.failed = Counter.builder("chat.badge.direct.failed")
                .description("세션·구독 부재나 전송 오류로 직접 보내지 못한 뱃지 수")
                .register(registry);
    }

    @Override
    public boolean send(MyChatRoomBadgeCommand command, String txId) {
        StompMyChatRoomBadgePayload payload = StompMyChatRoomBadgePayload.from(command);
        boolean sentAny = false;

        for (String memberId : command.memberIds()) {
            if (sendToUser(memberId, payload)) {
                sentAny = true;
            } else {
                failed.increment();
            }
        }

        return sentAny;
    }

    private boolean sendToUser(String userId, StompMyChatRoomBadgePayload payload) {
        Set<String> sessions = localSessionCache.findSessions(userId);

        if (sessions.isEmpty()) {
            return false;
        }

        List<SessionTarget> targets = new ArrayList<>(sessions.size());

        for (String sessionId : sessions) {
            String subscriptionId = localSessionCache.findBadgeSubscriptionId(sessionId);

            if (subscriptionId == null) {
                return false;
            }

            targets.add(new SessionTarget(sessionId, subscriptionId));
        }

        for (SessionTarget target : targets) {
            if (!sessionSender.send(badgeDestination, target.sessionId(), target.subscriptionId(), payload)) {
                return false;
            }

            sent.increment();
        }

        return true;
    }

    private record SessionTarget(String sessionId, String subscriptionId) {
    }
}
