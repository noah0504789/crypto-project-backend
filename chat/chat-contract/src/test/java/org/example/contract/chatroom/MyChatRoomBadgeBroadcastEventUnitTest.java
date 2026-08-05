package org.example.contract.chatroom;

import org.example.common.outbox.domain.OutboxDispatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MyChatRoomBadgeBroadcastEventUnitTest {

    @Test
    @DisplayName("내 채팅방 배지 이벤트는 브로드캐스트 레인으로 발행한다")
    void getDispatchType_shouldReturnBroadcast() {
        MyChatRoomBadgePayload payload = MyChatRoomBadgePayload.ofLastMessage(
                "room-id",
                Set.of("member-id"),
                "message",
                Instant.EPOCH
        );

        MyChatRoomBadgeBroadcastEvent event = new MyChatRoomBadgeBroadcastEvent(payload);

        assertThat(event.getDispatchType()).isEqualTo(OutboxDispatchType.BROADCAST);
    }
}
