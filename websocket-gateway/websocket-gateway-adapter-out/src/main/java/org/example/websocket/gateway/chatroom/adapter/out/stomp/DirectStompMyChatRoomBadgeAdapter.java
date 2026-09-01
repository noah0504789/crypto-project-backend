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
    private final Counter skipped;
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
        this.skipped = Counter.builder("chat.badge.direct.skipped")
                .description("로컬 세션이 없어 정상적으로 건너뛴 뱃지 대상 사용자 수")
                .register(registry);
        this.failed = Counter.builder("chat.badge.direct.failed")
                .description("로컬 세션은 있지만 구독 부재나 전송 오류로 직접 보내지 못한 뱃지 대상 사용자 수")
                .register(registry);
    }

    @Override
    public boolean send(MyChatRoomBadgeCommand command, String txId) {
        StompMyChatRoomBadgePayload payload = StompMyChatRoomBadgePayload.from(command);
        boolean sentAny = false;

        for (String memberId : command.memberIds()) {
            switch (sendToUser(memberId, payload)) {
                case SENT -> sentAny = true;
                case SKIPPED -> skipped.increment();
                case FAILED -> failed.increment();
            }
        }

        return sentAny;
    }

    private SendResult sendToUser(String userId, StompMyChatRoomBadgePayload payload) {
        Set<String> sessions = localSessionCache.findSessions(userId);

        if (sessions.isEmpty()) {
            return SendResult.SKIPPED;
        }

        List<SessionTarget> targets = new ArrayList<>(sessions.size());

        for (String sessionId : sessions) {
            String subscriptionId = localSessionCache.findBadgeSubscriptionId(sessionId);

            if (subscriptionId == null) {
                return SendResult.FAILED;
            }

            targets.add(new SessionTarget(sessionId, subscriptionId));
        }

        for (SessionTarget target : targets) {
            if (!sessionSender.send(badgeDestination, target.sessionId(), target.subscriptionId(), payload)) {
                return SendResult.FAILED;
            }

            sent.increment();
        }

        return SendResult.SENT;
    }

    private enum SendResult {
        SENT,
        SKIPPED,
        FAILED
    }

    private record SessionTarget(String sessionId, String subscriptionId) {
    }
}
