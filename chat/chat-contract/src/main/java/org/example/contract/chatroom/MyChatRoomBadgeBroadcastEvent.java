package org.example.contract.chatroom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

@ToString
@Getter
public class MyChatRoomBadgeBroadcastEvent extends AbstractOutboxEvent {

    private final MyChatRoomBadgePayload payload;

    @JsonCreator
    public MyChatRoomBadgeBroadcastEvent(@JsonProperty("payload") MyChatRoomBadgePayload payload) {
        super(KafkaTopic.CHAT_ROOM_BROADCAST.getTopicName(), payload.id(), payload.id());
        this.payload = payload;
    }

    @Override
    public OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    public String getMessageType() {
        return MyChatRoomBadgeBroadcastEvent.class.getName();
    }

    @Override
    public OutboxDomainType getDomainType() {
        return OutboxDomainType.CHAT;
    }
}
