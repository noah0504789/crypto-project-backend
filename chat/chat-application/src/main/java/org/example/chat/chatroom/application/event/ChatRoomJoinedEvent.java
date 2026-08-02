package org.example.chat.chatroom.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.application.port.in.ChatRoomEventHandler;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

import static org.example.common.enums.KafkaTopic.CHAT_ROOM;

@ToString
@Getter
public class ChatRoomJoinedEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final String memberId;

    @JsonCreator
    public ChatRoomJoinedEvent(
            @JsonProperty("id") String id,
            @JsonProperty("memberId") String memberId) {
        super(CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.memberId = memberId;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }

    @Override
    public OutboxDomainType getDomainType() {
        return OutboxDomainType.CHAT;
    }
}
