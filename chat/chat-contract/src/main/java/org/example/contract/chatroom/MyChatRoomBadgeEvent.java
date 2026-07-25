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
public class MyChatRoomBadgeEvent extends AbstractOutboxEvent {

    private final MyChatRoomBadgePayload payload;

    @JsonCreator
    public MyChatRoomBadgeEvent(@JsonProperty("payload") MyChatRoomBadgePayload payload) {
        super(KafkaTopic.CHAT_ROOM_BROADCAST.getTopicName(), payload.id(), payload.id());
        this.payload = payload;
    }

    @Override
    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    protected String getMessageType() {
        return MyChatRoomBadgeEvent.class.getName();
    }

    @Override
    protected OutboxDomainType getDomainType() {
        return OutboxDomainType.CHAT;
    }
}
