package org.example.chat.chatroom.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chat.chatroom.domain.event.handler.ChatRoomEventHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;


@Getter
@ToString
public class ChatRoomPersistedEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final ChatRoomPayload payload;

    @JsonCreator
    public ChatRoomPersistedEvent(@JsonProperty("payload") ChatRoomPayload payload) {
        super(KafkaTopic.CHAT_ROOM.getTopicName(), payload.id(), payload.id());
        this.payload = payload;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
