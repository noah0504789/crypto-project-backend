package org.example.chat.chatroom.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.application.port.in.ChatRoomEventHandler;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

import static org.example.common.enums.KafkaTopic.CHAT_ROOM;

@ToString
@Getter
public class ChatRoomUpdatedEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final ChatRoomUpdatedPayload updated;

    @JsonCreator
    public ChatRoomUpdatedEvent(
            @JsonProperty("id") String id,
            @JsonProperty("updated") ChatRoomUpdatedPayload updated) {
        super(CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.updated = updated;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
