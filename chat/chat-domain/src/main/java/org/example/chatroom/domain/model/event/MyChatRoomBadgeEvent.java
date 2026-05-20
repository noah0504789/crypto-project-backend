package org.example.chatroom.domain.model.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.adapter.dto.MyChatRoomPayload;
import org.example.common.enums.KafkaTopic;
import org.example.outbox.domain.OutboxDispatchType;
import org.example.outbox.domain.event.AbstractOutboxEvent;

@ToString
@Getter
public class MyChatRoomBadgeEvent extends AbstractOutboxEvent {

    private final MyChatRoomPayload payload;

    @JsonCreator
    public MyChatRoomBadgeEvent(@JsonProperty("payload") MyChatRoomPayload payload) {
        super(KafkaTopic.CHAT_ROOM_BROADCAST.getTopicName(), payload.id(), payload.id());
        this.payload = payload;
    }

    @Override
    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    protected String getMessageType() {
        return org.example.contract.chatroom.MyChatRoomBadgeEvent.class.getName();
    }
}
